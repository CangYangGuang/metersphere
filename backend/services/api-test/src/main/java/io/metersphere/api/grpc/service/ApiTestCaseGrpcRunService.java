package io.metersphere.api.grpc.service;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.dto.definition.ApiCaseRunRequest;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.grpc.dto.GrpcCaseDebugPayload;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.report.GrpcCaseReportPersistenceService;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.service.definition.ApiTestCaseService;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class ApiTestCaseGrpcRunService {

    @Resource
    private ApiTestCaseService apiTestCaseService;
    @Resource
    private ApiDefinitionMapper apiDefinitionMapper;
    @Resource
    private GrpcCaseRunService grpcCaseRunService;
    @Resource
    private GrpcCaseReportPersistenceService grpcCaseReportPersistenceService;

    public boolean isGrpcCase(String caseId) {
        ApiTestCase apiTestCase = apiTestCaseService.checkResourceExist(caseId);
        ApiDefinition apiDefinition = apiDefinitionMapper.selectByPrimaryKey(apiTestCase.getApiDefinitionId());
        return apiDefinition != null && StringUtils.equals(ApiConstants.GRPC_PROTOCOL, apiDefinition.getProtocol());
    }

    public GrpcCaseRunResultDTO run(String caseId, GrpcCaseRunParamDTO param) {
        return runAndPersistReport(caseId, param, null);
    }

    public GrpcCaseRunResultDTO run(ApiCaseRunRequest request) {
        return runAndPersistReport(request.getId(), extractParam(request), request.getReportId());
    }

    /**
     * 与 HTTP debug 一致：不保存报告，仅返回实时执行结果。
     */
    public GrpcCaseRunResultDTO debug(ApiCaseRunRequest request) {
        return grpcCaseRunService.run(request.getId(), extractParam(request));
    }

    private GrpcCaseRunResultDTO runAndPersistReport(String caseId, GrpcCaseRunParamDTO param, String reportId) {
        GrpcCaseRunResultDTO result = grpcCaseRunService.run(caseId, param);
        ApiTestCase apiTestCase = apiTestCaseService.checkResourceExist(caseId);
        String savedReportId = grpcCaseReportPersistenceService.saveReport(apiTestCase, param, result, reportId);
        result.setReportId(savedReportId);
        return result;
    }

    public GrpcCaseRunParamDTO extractParam(ApiCaseRunRequest request) {
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        if (request == null || request.getRequest() == null) {
            return param;
        }
        GrpcCaseDebugPayload payload = JSON.parseObject(JSON.toJSONString(request.getRequest()), GrpcCaseDebugPayload.class);
        if (payload == null) {
            return param;
        }
        param.setServerAddress(payload.getServerAddress());
        applyConfigOverride(param, payload.getGrpcCaseConfig());
        return param;
    }

    private void applyConfigOverride(GrpcCaseRunParamDTO param, ApiGrpcCaseConfigAddDTO config) {
        if (config == null) {
            return;
        }
        if (StringUtils.isNotBlank(config.getServiceName())) {
            param.setServiceName(config.getServiceName());
        }
        if (StringUtils.isNotBlank(config.getMethodName())) {
            param.setMethodName(config.getMethodName());
        }
        if (StringUtils.isBlank(param.getServerAddress()) && StringUtils.isNotBlank(config.getServerAddress())) {
            param.setServerAddress(config.getServerAddress());
        }
        if (StringUtils.isNotBlank(config.getDescriptorContent())) {
            param.setDescriptorContent(config.getDescriptorContent());
        }
        if (config.getRequestJson() != null) {
            param.setRequestJson(config.getRequestJson());
        }
        if (!CollectionUtils.isEmpty(config.getMetadata())) {
            param.setMetadata(config.getMetadata());
        }
        if (config.getTimeout() != null) {
            param.setTimeout(config.getTimeout());
        }
        if (config.getSettings() != null) {
            param.setSettings(config.getSettings());
        }
        if (config.getPreProcessorConfig() != null) {
            param.setPreProcessorConfig(config.getPreProcessorConfig());
        }
        if (config.getPostProcessorConfig() != null) {
            param.setPostProcessorConfig(config.getPostProcessorConfig());
        }
    }
}
