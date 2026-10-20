package io.metersphere.api.grpc.assertion;

import io.metersphere.api.grpc.dto.GrpcAssertionItemDTO;
import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.sdk.constants.MsAssertionCondition;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class GrpcAssertionEvaluator {

    public List<ResponseAssertionResult> evaluate(GrpcCaseAssertionConfigDTO config, GrpcInvokeResponse response) {
        if (config == null || CollectionUtils.isEmpty(config.getItems()) || response == null) {
            return new ArrayList<>();
        }
        List<ResponseAssertionResult> results = new ArrayList<>();
        for (GrpcAssertionItemDTO item : config.getItems()) {
            if (!isEnabled(item)) {
                continue;
            }
            ResponseAssertionResult result = evaluateItem(item, response);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    private ResponseAssertionResult evaluateItem(GrpcAssertionItemDTO item, GrpcInvokeResponse response) {
        GrpcAssertionType assertionType = resolveType(item.getType());
        if (assertionType == null) {
            return buildFailedResult(item, null, "unsupported assertion type");
        }
        String actualValue = resolveActualValue(assertionType, item, response);
        boolean pass = GrpcAssertionConditionMatcher.match(item.getCondition(), actualValue, item.getExpectedValue());
        ResponseAssertionResult result = new ResponseAssertionResult();
        result.setName(StringUtils.defaultIfBlank(item.getName(), assertionType.name()));
        result.setAssertionType(assertionType.name());
        result.setCondition(item.getCondition());
        result.setExpectedValue(item.getExpectedValue());
        result.setActualValue(actualValue);
        result.setPass(pass);
        result.setMessage(pass ? "assertion passed" : "assertion failed");
        if (assertionType == GrpcAssertionType.JSON_PATH) {
            result.setScript(item.getExpression());
        }
        return result;
    }

    private GrpcAssertionType resolveType(String type) {
        if (StringUtils.isBlank(type)) {
            return null;
        }
        try {
            return GrpcAssertionType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String resolveActualValue(GrpcAssertionType assertionType, GrpcAssertionItemDTO item,
                                      GrpcInvokeResponse response) {
        return switch (assertionType) {
            case JSON_PATH -> readJsonPath(response.getResponseJson(), item.getExpression());
            case GRPC_STATUS -> StringUtils.defaultString(response.getGrpcStatus());
            case GRPC_CODE -> response.getGrpcCode() == null ? StringUtils.EMPTY : String.valueOf(response.getGrpcCode());
        };
    }

    private String readJsonPath(String responseJson, String expression) {
        if (StringUtils.isBlank(responseJson) || StringUtils.isBlank(expression)) {
            return StringUtils.EMPTY;
        }
        Object value = GrpcJsonPathReader.read(responseJson, expression);
        return stringifyJsonPathValue(value);
    }

    private String stringifyJsonPathValue(Object value) {
        if (value == null) {
            return StringUtils.EMPTY;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return JSON.toJSONString(value);
    }

    private ResponseAssertionResult buildFailedResult(GrpcAssertionItemDTO item, String actualValue, String message) {
        ResponseAssertionResult result = new ResponseAssertionResult();
        result.setName(StringUtils.defaultIfBlank(item.getName(), "assertion"));
        result.setAssertionType(item.getType());
        result.setCondition(item.getCondition());
        result.setExpectedValue(item.getExpectedValue());
        result.setActualValue(actualValue);
        result.setPass(false);
        result.setMessage(message);
        return result;
    }

    private boolean isEnabled(GrpcAssertionItemDTO item) {
        return item != null
                && BooleanUtils.isTrue(item.getEnable())
                && StringUtils.isNotBlank(item.getCondition())
                && !StringUtils.equals(item.getCondition(), MsAssertionCondition.UNCHECK.name());
    }
}
