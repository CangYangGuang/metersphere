package io.metersphere.api.grpc.scenario;

import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunContext;
import io.metersphere.api.grpc.service.GrpcCasePersistenceService;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import io.metersphere.api.grpc.util.GrpcProtocolUtils;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GrpcScenarioStepConfigResolver {

    @Resource
    private GrpcCasePersistenceService grpcCasePersistenceService;
    @Resource
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;

    public ApiGrpcCaseConfigDTO resolve(ApiScenarioStepCommonDTO step, GrpcScenarioRunContext context) {
        if (step == null) {
            return null;
        }
        ApiGrpcCaseConfigDTO fromDetail = extractFromStepDetail(step, context);
        if (fromDetail != null) {
            return fromDetail;
        }
        if (StringUtils.equals(step.getStepType(), ApiScenarioStepType.API_CASE.name())
                && StringUtils.isNotBlank(step.getResourceId())) {
            return grpcCasePersistenceService.loadDtoByCaseId(step.getResourceId());
        }
        if (StringUtils.equals(step.getStepType(), ApiScenarioStepType.API.name())
                && StringUtils.isNotBlank(step.getResourceId())
                && GrpcProtocolUtils.isGrpcProtocol(GrpcScenarioStepSupport.extractProtocol(step.getConfig()))) {
            return definitionToCaseConfig(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(step.getResourceId()));
        }
        return null;
    }

    private ApiGrpcCaseConfigDTO extractFromStepDetail(ApiScenarioStepCommonDTO step, GrpcScenarioRunContext context) {
        if (context == null || context.getStepDetails() == null || StringUtils.isBlank(step.getId())) {
            return null;
        }
        Object detail = context.getStepDetails().get(step.getId());
        if (detail == null) {
            return null;
        }
        Map<String, Object> detailMap = toMap(detail);
        if (detailMap == null) {
            return null;
        }
        Object grpcCaseConfig = detailMap.get("grpcCaseConfig");
        if (grpcCaseConfig == null) {
            return null;
        }
        ApiGrpcCaseConfigDTO config = JSON.parseObject(JSON.toJSONString(grpcCaseConfig), ApiGrpcCaseConfigDTO.class);
        if (config == null) {
            return null;
        }
        mergeServerAddressFromDetail(config, detailMap);
        if (StringUtils.isBlank(config.getApiDefinitionId()) && StringUtils.isNotBlank(step.getResourceId())
                && StringUtils.equals(step.getStepType(), ApiScenarioStepType.API.name())) {
            config.setApiDefinitionId(step.getResourceId());
        }
        if (StringUtils.isBlank(config.getApiCaseId()) && StringUtils.isNotBlank(step.getResourceId())
                && StringUtils.equals(step.getStepType(), ApiScenarioStepType.API_CASE.name())) {
            config.setApiCaseId(step.getResourceId());
        }
        return hasExecutableConfig(config) ? config : null;
    }

    private void mergeServerAddressFromDetail(ApiGrpcCaseConfigDTO config, Map<String, Object> detailMap) {
        if (StringUtils.isNotBlank(config.getServerAddress())) {
            return;
        }
        Object grpcState = detailMap.get("grpc");
        if (grpcState instanceof Map<?, ?> grpcMap) {
            Object serverAddress = grpcMap.get("serverAddress");
            if (serverAddress != null && StringUtils.isNotBlank(String.valueOf(serverAddress))) {
                config.setServerAddress(String.valueOf(serverAddress).trim());
            }
        }
    }

    private ApiGrpcCaseConfigDTO definitionToCaseConfig(ApiGrpcDefinitionDTO definition) {
        if (definition == null || StringUtils.isBlank(definition.getDescriptorContent())) {
            return null;
        }
        ApiGrpcCaseConfigDTO config = new ApiGrpcCaseConfigDTO();
        config.setServiceName(definition.getServiceName());
        config.setMethodName(definition.getMethodName());
        config.setDescriptorContent(definition.getDescriptorContent());
        config.setProtoContent(definition.getProtoContent());
        config.setFileName(definition.getFileName());
        config.setMainFileName(definition.getMainFileName());
        config.setProtoFiles(definition.getProtoFiles());
        config.setServerAddress(StringUtils.defaultIfBlank(
                definition.getServerAddress(), definition.getReflectionServerAddress()));
        config.setRequestJson(resolveRequestJsonTemplate(definition));
        return config;
    }

    private String resolveRequestJsonTemplate(ApiGrpcDefinitionDTO definition) {
        if (definition.getServices() == null) {
            return "{}";
        }
        for (ApiGrpcDefinitionDTO.GrpcServiceInfoDTO service : definition.getServices()) {
            if (service == null || !StringUtils.equalsAny(definition.getServiceName(), service.getName(), service.getFullName())
                    || service.getMethods() == null) {
                continue;
            }
            for (ApiGrpcDefinitionDTO.GrpcMethodInfoDTO method : service.getMethods()) {
                if (method != null && StringUtils.equalsAny(definition.getMethodName(), method.getName(), method.getFullName())) {
                    return StringUtils.defaultIfBlank(method.getRequestJsonTemplate(), "{}");
                }
            }
        }
        return "{}";
    }

    private boolean hasExecutableConfig(ApiGrpcCaseConfigDTO config) {
        return config != null
                && StringUtils.isNotBlank(config.getDescriptorContent())
                && StringUtils.isNotBlank(config.getServiceName())
                && StringUtils.isNotBlank(config.getMethodName());
    }

    private Map<String, Object> toMap(Object detail) {
        if (detail instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (detail instanceof CharSequence json) {
            return JSON.parseObject(json.toString(), LinkedHashMap.class);
        }
        if (detail instanceof byte[] bytes) {
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), LinkedHashMap.class);
        }
        return JSON.parseObject(JSON.toJSONString(detail), LinkedHashMap.class);
    }
}
