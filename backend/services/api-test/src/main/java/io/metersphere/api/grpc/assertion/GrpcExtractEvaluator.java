package io.metersphere.api.grpc.assertion;

import io.metersphere.api.grpc.dto.GrpcCaseExtractConfigDTO;
import io.metersphere.api.grpc.dto.GrpcExtractItemDTO;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class GrpcExtractEvaluator {

    public List<ExtractResult> evaluate(GrpcCaseExtractConfigDTO config, GrpcInvokeResponse response) {
        if (config == null || CollectionUtils.isEmpty(config.getItems()) || response == null) {
            return new ArrayList<>();
        }
        List<ExtractResult> results = new ArrayList<>();
        for (GrpcExtractItemDTO item : config.getItems()) {
            if (!isEnabled(item)) {
                continue;
            }
            ExtractResult result = extractItem(item, response);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    private ExtractResult extractItem(GrpcExtractItemDTO item, GrpcInvokeResponse response) {
        String value = readJsonPath(response.getResponseJson(), item.getExpression());
        ExtractResult result = new ExtractResult();
        result.setName(item.getVariableName());
        result.setValue(value);
        result.setType("JSON_PATH");
        result.setExpression(item.getExpression());
        return result;
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

    private boolean isEnabled(GrpcExtractItemDTO item) {
        return item != null
                && BooleanUtils.isTrue(item.getEnable())
                && StringUtils.isNotBlank(item.getVariableName())
                && StringUtils.isNotBlank(item.getExpression());
    }
}
