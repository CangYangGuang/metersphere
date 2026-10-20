package io.metersphere.api.grpc.scenario;

import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunContext;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioStepResultDTO;
import io.metersphere.api.grpc.service.GrpcCaseRunService;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class GrpcScenarioStepExecutor {

    @Resource
    private GrpcCaseRunService grpcCaseRunService;
    @Resource
    private GrpcScenarioVariableResolver grpcScenarioVariableResolver;
    @Resource
    private GrpcScenarioStepConfigResolver grpcScenarioStepConfigResolver;

    public GrpcScenarioStepResultDTO execute(ApiScenarioStepCommonDTO step, GrpcScenarioRunContext context) {
        GrpcScenarioStepResultDTO stepResult = new GrpcScenarioStepResultDTO();
        stepResult.setStepId(step.getUniqueId());
        stepResult.setStepName(step.getName());
        stepResult.setStepType(step.getStepType());
        stepResult.setResourceId(step.getResourceId());
        stepResult.setProjectId(step.getProjectId());

        ApiGrpcCaseConfigDTO caseConfig = grpcScenarioStepConfigResolver.resolve(step, context);
        if (caseConfig == null) {
            return fillError(stepResult, GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND,
                    GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND.name());
        }

        stepResult.setServiceName(caseConfig.getServiceName());
        stepResult.setMethodName(caseConfig.getMethodName());

        String serverAddress = resolveServerAddress(step, context, caseConfig);
        stepResult.setServerAddress(serverAddress);
        if (StringUtils.isBlank(serverAddress)) {
            return fillError(stepResult, GrpcResultCode.GRPC_ADDRESS_INVALID,
                    GrpcResultCode.GRPC_ADDRESS_INVALID.name());
        }
        if (StringUtils.isBlank(context.getDefaultServerAddress())) {
            context.setDefaultServerAddress(serverAddress);
        }

        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress(serverAddress);
        param.setRequestJson(grpcScenarioVariableResolver.resolve(caseConfig.getRequestJson(), context.getVariableContext()));
        param.setMetadata(grpcScenarioVariableResolver.resolveMetadata(caseConfig.getMetadata(), context.getVariableContext()));
        param.setTimeout(caseConfig.getTimeout());
        param.setServiceName(caseConfig.getServiceName());
        param.setMethodName(caseConfig.getMethodName());
        param.setDescriptorContent(caseConfig.getDescriptorContent());

        stepResult.setRequestJson(param.getRequestJson());
        GrpcCaseRunResultDTO runResult = StringUtils.isNotBlank(step.getResourceId())
                && StringUtils.equals(step.getStepType(), io.metersphere.api.constants.ApiScenarioStepType.API_CASE.name())
                ? grpcCaseRunService.run(step.getResourceId(), param)
                : grpcCaseRunService.runWithConfig(caseConfig, param);
        applyRunResult(stepResult, runResult);
        mergeExtractVariables(context, runResult.getExtractResults());
        return stepResult;
    }

    private String resolveServerAddress(ApiScenarioStepCommonDTO step, GrpcScenarioRunContext context,
                                        ApiGrpcCaseConfigDTO caseConfig) {
        String fromStepConfig = GrpcScenarioStepSupport.extractStringFromConfig(step.getConfig(), "serverAddress");
        if (StringUtils.isNotBlank(fromStepConfig)) {
            return grpcScenarioVariableResolver.resolve(fromStepConfig, context.getVariableContext());
        }
        if (caseConfig != null && StringUtils.isNotBlank(caseConfig.getServerAddress())) {
            return grpcScenarioVariableResolver.resolve(caseConfig.getServerAddress(), context.getVariableContext());
        }
        if (StringUtils.isNotBlank(context.getDefaultServerAddress())) {
            return grpcScenarioVariableResolver.resolve(context.getDefaultServerAddress(), context.getVariableContext());
        }
        String variableAddress = context.getVariableContext().get("grpcServerAddress");
        if (StringUtils.isBlank(variableAddress)) {
            variableAddress = context.getVariableContext().get("serverAddress");
        }
        return variableAddress;
    }

    private void mergeExtractVariables(GrpcScenarioRunContext context, java.util.List<ExtractResult> extractResults) {
        if (extractResults == null) {
            return;
        }
        for (ExtractResult extractResult : extractResults) {
            if (extractResult == null || StringUtils.isBlank(extractResult.getName())) {
                continue;
            }
            context.getVariableContext().put(extractResult.getName(), extractResult.getValue());
        }
    }

    private void applyRunResult(GrpcScenarioStepResultDTO stepResult, GrpcCaseRunResultDTO runResult) {
        stepResult.setSuccess(runResult.getSuccess());
        stepResult.setGrpcStatus(runResult.getGrpcStatus());
        stepResult.setGrpcCode(runResult.getGrpcCode());
        stepResult.setDuration(runResult.getDuration());
        stepResult.setResponseJson(runResult.getResponseJson());
        stepResult.setErrorCode(runResult.getErrorCode());
        stepResult.setErrorMessage(runResult.getErrorMessage());
        stepResult.setAssertionResults(runResult.getAssertionResults());
        stepResult.setExtractResults(runResult.getExtractResults());
    }

    private GrpcScenarioStepResultDTO fillError(GrpcScenarioStepResultDTO stepResult,
                                                GrpcResultCode resultCode,
                                                String message) {
        stepResult.setSuccess(false);
        stepResult.setErrorCode(resultCode.name());
        stepResult.setErrorMessage(message);
        stepResult.setDuration(0L);
        return stepResult;
    }
}
