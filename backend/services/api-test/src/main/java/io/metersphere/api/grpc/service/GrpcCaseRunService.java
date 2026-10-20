package io.metersphere.api.grpc.service;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.grpc.assertion.GrpcAssertionEvaluator;
import io.metersphere.api.grpc.assertion.GrpcAssertionType;
import io.metersphere.api.grpc.assertion.GrpcExtractEvaluator;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.dto.GrpcCaseSettingsDTO;
import io.metersphere.api.grpc.dto.GrpcInvokeRequest;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.dto.GrpcProcessorResultDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.processor.GrpcProcessorRunner;
import io.metersphere.api.grpc.scenario.GrpcScenarioVariableContext;
import io.metersphere.api.grpc.scenario.GrpcScenarioVariableResolver;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.service.definition.ApiTestCaseService;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class GrpcCaseRunService {

    @Resource
    private ApiTestCaseService apiTestCaseService;
    @Resource
    private ApiDefinitionMapper apiDefinitionMapper;
    @Resource
    private GrpcCasePersistenceService grpcCasePersistenceService;
    @Resource
    private GrpcInvokeService grpcInvokeService;
    @Resource
    private GrpcAssertionEvaluator grpcAssertionEvaluator;
    @Resource
    private GrpcExtractEvaluator grpcExtractEvaluator;
    @Resource
    private GrpcProcessorRunner grpcProcessorRunner;
    @Resource
    private GrpcScenarioVariableResolver grpcScenarioVariableResolver;

    public GrpcCaseRunResultDTO run(String apiCaseId, GrpcCaseRunParamDTO param) {
        if (param == null) {
            param = new GrpcCaseRunParamDTO();
        }
        ApiTestCase apiTestCase = apiTestCaseService.checkResourceExist(apiCaseId);
        ApiDefinition apiDefinition = apiDefinitionMapper.selectByPrimaryKey(apiTestCase.getApiDefinitionId());
        if (apiDefinition == null || !StringUtils.equals(ApiConstants.GRPC_PROTOCOL, apiDefinition.getProtocol())) {
            return GrpcCaseRunResultDTO.error(GrpcResultCode.GRPC_DEFINITION_NOT_FOUND,
                    GrpcResultCode.GRPC_DEFINITION_NOT_FOUND.name());
        }

        ApiGrpcCaseConfigDTO caseConfig = grpcCasePersistenceService.loadDtoByCaseId(apiCaseId);
        if (caseConfig == null) {
            return GrpcCaseRunResultDTO.error(GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND,
                    GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND.name());
        }
        if (StringUtils.isBlank(param.getServerAddress())) {
            param.setServerAddress(caseConfig.getServerAddress());
        }
        overlayParamConfig(caseConfig, param);
        return runWithConfig(caseConfig, param);
    }

    private void overlayParamConfig(ApiGrpcCaseConfigDTO caseConfig, GrpcCaseRunParamDTO param) {
        if (param == null || caseConfig == null) {
            return;
        }
        if (param.getSettings() != null) {
            caseConfig.setSettings(param.getSettings());
        }
        if (param.getPreProcessorConfig() != null) {
            caseConfig.setPreProcessorConfig(param.getPreProcessorConfig());
        }
        if (param.getPostProcessorConfig() != null) {
            caseConfig.setPostProcessorConfig(param.getPostProcessorConfig());
        }
    }

    public GrpcCaseRunResultDTO runWithConfig(ApiGrpcCaseConfigDTO caseConfig, GrpcCaseRunParamDTO param) {
        if (caseConfig == null) {
            return GrpcCaseRunResultDTO.error(GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND,
                    GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND.name());
        }
        if (param == null) {
            param = new GrpcCaseRunParamDTO();
        }
        if (StringUtils.isBlank(param.getServerAddress())) {
            param.setServerAddress(caseConfig.getServerAddress());
        }
        if (StringUtils.isBlank(param.getServerAddress())) {
            return GrpcCaseRunResultDTO.error(GrpcResultCode.GRPC_ADDRESS_INVALID,
                    GrpcResultCode.GRPC_ADDRESS_INVALID.name());
        }

        GrpcScenarioVariableContext variableContext = new GrpcScenarioVariableContext();
        List<GrpcProcessorResultDTO> preResults = grpcProcessorRunner.runPre(caseConfig.getPreProcessorConfig(), variableContext);

        GrpcInvokeRequest invokeRequest = buildInvokeRequest(param, caseConfig);
        invokeRequest.setRequestJson(grpcScenarioVariableResolver.resolve(invokeRequest.getRequestJson(), variableContext));
        try {
            GrpcInvokeResponse response = grpcInvokeService.invoke(invokeRequest);
            GrpcCaseRunResultDTO result = GrpcCaseRunResultDTO.from(response);
            applyAssertionAndExtract(result, response, caseConfig);
            List<GrpcProcessorResultDTO> postResults = grpcProcessorRunner.runPost(
                    caseConfig.getPostProcessorConfig(), variableContext, response);
            result.setPreProcessorResults(preResults);
            result.setPostProcessorResults(postResults);
            mergePostExtractResults(result, postResults);
            return result;
        } catch (MSException exception) {
            GrpcResultCode resultCode = exception.getErrorCode() instanceof GrpcResultCode grpcResultCode
                    ? grpcResultCode : GrpcResultCode.GRPC_CALL_FAILED;
            GrpcCaseRunResultDTO result = GrpcCaseRunResultDTO.error(resultCode, exception.getMessage());
            result.setPreProcessorResults(preResults);
            return result;
        }
    }

    private void mergePostExtractResults(GrpcCaseRunResultDTO result, List<GrpcProcessorResultDTO> postResults) {
        if (CollectionUtils.isEmpty(postResults)) {
            return;
        }
        List<ExtractResult> extractResults = result.getExtractResults();
        if (extractResults == null) {
            extractResults = new ArrayList<>();
            result.setExtractResults(extractResults);
        }
        for (GrpcProcessorResultDTO postResult : postResults) {
            if (postResult != null && !CollectionUtils.isEmpty(postResult.getExtractResults())) {
                extractResults.addAll(postResult.getExtractResults());
            }
        }
    }

    private GrpcInvokeRequest buildInvokeRequest(GrpcCaseRunParamDTO param, ApiGrpcCaseConfigDTO caseConfig) {
        GrpcInvokeRequest invokeRequest = new GrpcInvokeRequest();
        invokeRequest.setServerAddress(param.getServerAddress());
        invokeRequest.setServiceName(StringUtils.defaultIfBlank(param.getServiceName(), caseConfig.getServiceName()));
        invokeRequest.setMethodName(StringUtils.defaultIfBlank(param.getMethodName(), caseConfig.getMethodName()));
        invokeRequest.setDescriptorContent(StringUtils.defaultIfBlank(param.getDescriptorContent(),
                caseConfig.getDescriptorContent()));
        invokeRequest.setRequestJson(StringUtils.defaultIfBlank(param.getRequestJson(), caseConfig.getRequestJson()));
        invokeRequest.setMetadata(resolveMetadata(param.getMetadata(), caseConfig.getMetadata()));
        invokeRequest.setTimeout(param.getTimeout() != null ? param.getTimeout() : caseConfig.getTimeout());
        applySettings(invokeRequest, caseConfig.getSettings());
        return invokeRequest;
    }

    private void applySettings(GrpcInvokeRequest invokeRequest, GrpcCaseSettingsDTO settings) {
        if (settings == null) {
            return;
        }
        invokeRequest.setUseTls(settings.getUseTls());
        invokeRequest.setVerifyServerCertificate(settings.getVerifyServerCertificate());
        invokeRequest.setServerNameOverride(settings.getServerNameOverride());
        invokeRequest.setAuthority(settings.getAuthority());
        invokeRequest.setIncludeDefaultValues(settings.getIncludeDefaultValues());
        invokeRequest.setMaxResponseSizeMb(settings.getMaxResponseSizeMb());
        invokeRequest.setConnectTimeout(settings.getConnectTimeout());
    }

    private void applyAssertionAndExtract(GrpcCaseRunResultDTO result, GrpcInvokeResponse response,
                                          ApiGrpcCaseConfigDTO caseConfig) {
        List<ResponseAssertionResult> assertionResults = grpcAssertionEvaluator.evaluate(
                caseConfig.getAssertionConfig(), response);
        List<ExtractResult> extractResults = grpcExtractEvaluator.evaluate(
                caseConfig.getExtractConfig(), response);
        result.setAssertionResults(assertionResults);
        result.setExtractResults(extractResults);
        if (!CollectionUtils.isEmpty(assertionResults)) {
            boolean allPass = assertionResults.stream().allMatch(item -> item != null && item.isPass());
            if (BooleanUtils.isTrue(result.getSuccess())) {
                result.setSuccess(allPass);
            } else if (allPass && isExpectedGrpcError(response, assertionResults)) {
                result.setSuccess(true);
            }
        }
    }

    private boolean isExpectedGrpcError(GrpcInvokeResponse response,
                                        List<ResponseAssertionResult> assertionResults) {
        boolean hasGrpcResponseStatus = StringUtils.isNotBlank(response.getGrpcStatus())
                || response.getGrpcCode() != null;
        if (!hasGrpcResponseStatus) {
            return false;
        }
        return assertionResults.stream()
                .filter(java.util.Objects::nonNull)
                .map(ResponseAssertionResult::getAssertionType)
                .anyMatch(type -> StringUtils.equalsAny(type,
                        GrpcAssertionType.GRPC_STATUS.name(), GrpcAssertionType.GRPC_CODE.name()));
    }

    private List<GrpcMetadataItemDTO> resolveMetadata(List<GrpcMetadataItemDTO> overrideMetadata,
                                                      List<GrpcMetadataItemDTO> persistedMetadata) {
        if (!CollectionUtils.isEmpty(overrideMetadata)) {
            return overrideMetadata;
        }
        if (persistedMetadata == null) {
            return new ArrayList<>();
        }
        return persistedMetadata;
    }
}
