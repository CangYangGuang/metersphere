package io.metersphere.api.grpc.report;

import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseReportContentDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.sdk.dto.api.result.ResponseResult;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class GrpcCaseReportConverter {

    public GrpcCaseReportContentDTO toReportContent(ApiGrpcCaseConfigDTO caseConfig,
                                                    GrpcCaseRunParamDTO param,
                                                    GrpcCaseRunResultDTO result,
                                                    String caseId,
                                                    String caseName) {
        GrpcCaseReportContentDTO content = new GrpcCaseReportContentDTO();
        content.setProtocol("GRPC");
        content.setMethod("GRPC");
        content.setResourceId(caseId);
        content.setName(caseName);
        content.setStepId(caseId);
        content.setSuccess(result.getSuccess());
        content.setIsSuccessful(result.getSuccess());
        content.setStatus(BooleanUtils.isTrue(result.getSuccess()) ? "SUCCESS" : "ERROR");
        content.setFakeErrorCode(result.getErrorCode());
        content.setGrpcStatus(result.getGrpcStatus());
        content.setGrpcCode(result.getGrpcCode());
        content.setDuration(result.getDuration());
        content.setErrorCode(result.getErrorCode());
        content.setErrorMessage(result.getErrorMessage());
        content.setServerAddress(param == null ? null : param.getServerAddress());
        content.setResponseJson(result.getResponseJson());
        content.setAssertionResults(result.getAssertionResults());
        content.setExtractResults(result.getExtractResults());
        content.setPreProcessorResults(result.getPreProcessorResults());
        content.setPostProcessorResults(result.getPostProcessorResults());

        if (caseConfig != null) {
            content.setServiceName(caseConfig.getServiceName());
            content.setMethodName(caseConfig.getMethodName());
            content.setRequestJson(resolveRequestJson(param, caseConfig));
        } else if (param != null) {
            content.setServiceName(param.getServiceName());
            content.setMethodName(param.getMethodName());
            content.setRequestJson(param.getRequestJson());
        }

        content.setUrl(buildUrl(param, content));
        content.setBody(content.getRequestJson());
        long startTime = System.currentTimeMillis();
        long duration = result.getDuration() != null ? result.getDuration() : 0L;
        content.setStartTime(startTime - duration);
        content.setEndTime(startTime);

        if (result.getAssertionResults() != null) {
            content.setAssertionTotal(result.getAssertionResults().size());
            content.setPassAssertionsTotal((int) result.getAssertionResults().stream()
                    .filter(item -> item != null && item.isPass())
                    .count());
        }

        ResponseResult responseResult = content.getResponseResult();
        responseResult.setBody(result.getResponseJson());
        responseResult.setResponseCode(result.getGrpcCode() == null ? StringUtils.EMPTY : String.valueOf(result.getGrpcCode()));
        responseResult.setResponseMessage(result.getGrpcStatus());
        responseResult.setResponseTime(duration);
        if (StringUtils.isNotBlank(result.getResponseJson())) {
            responseResult.setResponseSize(result.getResponseJson().getBytes().length);
        }
        responseResult.setExtractResults(result.getExtractResults());
        if (result.getAssertionResults() != null) {
            responseResult.getAssertions().addAll(result.getAssertionResults());
        }
        return content;
    }

    private String resolveRequestJson(GrpcCaseRunParamDTO param, ApiGrpcCaseConfigDTO caseConfig) {
        if (param != null && StringUtils.isNotBlank(param.getRequestJson())) {
            return param.getRequestJson();
        }
        return caseConfig.getRequestJson();
    }

    private String buildUrl(GrpcCaseRunParamDTO param, GrpcCaseReportContentDTO content) {
        StringBuilder builder = new StringBuilder();
        if (param != null && StringUtils.isNotBlank(param.getServerAddress())) {
            builder.append(param.getServerAddress());
        }
        if (StringUtils.isNotBlank(content.getServiceName())) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(content.getServiceName());
        }
        if (StringUtils.isNotBlank(content.getMethodName())) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(content.getMethodName());
        }
        return builder.toString();
    }
}
