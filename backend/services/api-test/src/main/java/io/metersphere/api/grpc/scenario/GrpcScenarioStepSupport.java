package io.metersphere.api.grpc.scenario;

import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.service.ApiTestCaseGrpcRunService;
import io.metersphere.api.grpc.util.GrpcProtocolUtils;
import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GrpcScenarioStepSupport {

    private GrpcScenarioStepSupport() {
    }

    public static boolean isGrpcApiCaseStep(ApiScenarioStepCommonDTO step) {
        return isGrpcScenarioStep(step) && StringUtils.equals(step.getStepType(), ApiScenarioStepType.API_CASE.name());
    }

    public static boolean isGrpcScenarioStep(ApiScenarioStepCommonDTO step) {
        if (step == null) {
            return false;
        }
        String protocol = extractProtocol(step.getConfig());
        if (GrpcProtocolUtils.isGrpcProtocol(protocol)) {
            return isGrpcExecutableStepType(step.getStepType());
        }
        if (StringUtils.equals(step.getStepType(), ApiScenarioStepType.API_CASE.name())
                && StringUtils.isNotBlank(step.getResourceId())) {
            ApiTestCaseGrpcRunService grpcRunService = CommonBeanFactory.getBean(ApiTestCaseGrpcRunService.class);
            return grpcRunService != null && grpcRunService.isGrpcCase(step.getResourceId());
        }
        return false;
    }

    public static boolean isWaitStep(ApiScenarioStepCommonDTO step) {
        return step != null && StringUtils.equals(step.getStepType(), ApiScenarioStepType.CONSTANT_TIMER.name());
    }

    public static boolean isInProcessExecutableStep(ApiScenarioStepCommonDTO step) {
        return isGrpcScenarioStep(step) || isWaitStep(step);
    }

    private static boolean isGrpcExecutableStepType(String stepType) {
        return StringUtils.equalsAny(stepType,
                ApiScenarioStepType.API_CASE.name(),
                ApiScenarioStepType.CUSTOM_REQUEST.name(),
                ApiScenarioStepType.API.name());
    }

    public static String extractProtocol(Object config) {
        Map<String, Object> configMap = toConfigMap(config);
        if (configMap == null) {
            return null;
        }
        Object protocol = configMap.get("protocol");
        return protocol == null ? null : String.valueOf(protocol);
    }

    public static String extractStringFromConfig(Object config, String key) {
        Map<String, Object> configMap = toConfigMap(config);
        if (configMap == null || !configMap.containsKey(key)) {
            return null;
        }
        Object value = configMap.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static List<ApiScenarioStepCommonDTO> collectEnabledFlatSteps(List<? extends ApiScenarioStepCommonDTO> steps) {
        List<ApiScenarioStepCommonDTO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(steps)) {
            return result;
        }
        for (ApiScenarioStepCommonDTO step : steps) {
            if (step == null || BooleanUtils.isFalse(step.getEnable())) {
                continue;
            }
            result.add(step);
            if (CollectionUtils.isNotEmpty(step.getChildren())) {
                result.addAll(collectEnabledFlatSteps(step.getChildren()));
            }
        }
        return result;
    }

    public static boolean hasGrpcStep(List<? extends ApiScenarioStepCommonDTO> steps) {
        return collectEnabledFlatSteps(steps).stream().anyMatch(GrpcScenarioStepSupport::isGrpcScenarioStep);
    }

    public static boolean isMixedScenario(List<? extends ApiScenarioStepCommonDTO> steps) {
        return hasGrpcStep(steps) && !supportsInProcessRun(steps);
    }

    public static boolean supportsInProcessRun(List<? extends ApiScenarioStepCommonDTO> steps) {
        List<ApiScenarioStepCommonDTO> enabledSteps = collectEnabledFlatSteps(steps);
        if (enabledSteps.isEmpty()) {
            return false;
        }
        boolean hasGrpcStep = false;
        for (ApiScenarioStepCommonDTO step : enabledSteps) {
            if (!isInProcessExecutableStep(step)) {
                return false;
            }
            if (isGrpcScenarioStep(step)) {
                hasGrpcStep = true;
            }
        }
        return hasGrpcStep;
    }

    public static Long extractWaitDelay(Object config) {
        Map<String, Object> configMap = toConfigMap(config);
        if (configMap == null) {
            return null;
        }
        Object value = configMap.get("delay");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Map<String, Object> toConfigMap(Object config) {
        if (config == null) {
            return null;
        }
        if (config instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return JSON.parseObject(JSON.toJSONString(config), LinkedHashMap.class);
    }
}
