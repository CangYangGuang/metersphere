package io.metersphere.api.grpc.scenario;

import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.grpc.dto.GrpcCaseReportContentDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioStepResultDTO;
import io.metersphere.sdk.dto.api.result.RequestResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import io.metersphere.sdk.dto.api.result.ResponseResult;
import io.metersphere.sdk.dto.api.result.TaskResultDTO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GrpcScenarioResultConverter {

    public TaskResultDTO toTaskResult(GrpcScenarioRunResultDTO runResult) {
        TaskResultDTO taskResult = new TaskResultDTO();
        if (runResult == null || CollectionUtils.isEmpty(runResult.getStepResults())) {
            taskResult.setRequestResults(List.of());
            return taskResult;
        }
        List<RequestResult> requestResults = new ArrayList<>();
        for (GrpcScenarioStepResultDTO stepResult : runResult.getStepResults()) {
            requestResults.add(toRequestResult(stepResult));
        }
        taskResult.setRequestResults(requestResults);
        return taskResult;
    }

    public RequestResult toRequestResult(GrpcScenarioStepResultDTO stepResult) {
        GrpcCaseReportContentDTO requestResult = new GrpcCaseReportContentDTO();
        boolean waitStep = StringUtils.equals(stepResult.getStepType(), ApiScenarioStepType.CONSTANT_TIMER.name());
        requestResult.setStepId(stepResult.getStepId());
        requestResult.setName(stepResult.getStepName());
        requestResult.setResourceId(stepResult.getResourceId());
        requestResult.setProjectId(stepResult.getProjectId());
        requestResult.setMethod(waitStep ? "WAIT" : "GRPC");
        requestResult.setIsSuccessful(stepResult.getSuccess());
        requestResult.setStatus(BooleanUtils.isTrue(stepResult.getSuccess()) ? "SUCCESS" : "ERROR");
        requestResult.setFakeErrorCode(stepResult.getErrorCode());
        requestResult.setProtocol(waitStep ? "TIMER" : "GRPC");
        requestResult.setServiceName(stepResult.getServiceName());
        requestResult.setMethodName(stepResult.getMethodName());
        requestResult.setServerAddress(stepResult.getServerAddress());
        requestResult.setGrpcStatus(stepResult.getGrpcStatus());
        requestResult.setGrpcCode(stepResult.getGrpcCode());
        requestResult.setSuccess(stepResult.getSuccess());
        requestResult.setDuration(stepResult.getDuration());
        requestResult.setRequestJson(stepResult.getRequestJson());
        requestResult.setResponseJson(stepResult.getResponseJson());
        requestResult.setErrorCode(stepResult.getErrorCode());
        requestResult.setErrorMessage(stepResult.getErrorMessage());
        requestResult.setAssertionResults(stepResult.getAssertionResults());
        requestResult.setExtractResults(stepResult.getExtractResults());

        String url = buildUrl(stepResult);
        requestResult.setUrl(url);
        requestResult.setBody(stepResult.getRequestJson());

        ResponseResult responseResult = requestResult.getResponseResult();
        responseResult.setBody(stepResult.getResponseJson());
        responseResult.setResponseCode(stepResult.getGrpcCode() == null ? StringUtils.EMPTY : String.valueOf(stepResult.getGrpcCode()));
        responseResult.setResponseMessage(stepResult.getGrpcStatus());
        responseResult.setResponseTime(stepResult.getDuration() == null ? 0L : stepResult.getDuration());
        if (CollectionUtils.isNotEmpty(stepResult.getExtractResults())) {
            responseResult.setExtractResults(stepResult.getExtractResults());
        }
        if (CollectionUtils.isNotEmpty(stepResult.getAssertionResults())) {
            responseResult.getAssertions().addAll(stepResult.getAssertionResults());
            requestResult.setAssertionTotal(stepResult.getAssertionResults().size());
            requestResult.setPassAssertionsTotal((int) stepResult.getAssertionResults().stream()
                    .filter(ResponseAssertionResult::isPass)
                    .count());
        }
        return requestResult;
    }

    private String buildUrl(GrpcScenarioStepResultDTO stepResult) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotBlank(stepResult.getServerAddress())) {
            builder.append(stepResult.getServerAddress());
        }
        if (StringUtils.isNotBlank(stepResult.getServiceName())) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(stepResult.getServiceName());
        }
        if (StringUtils.isNotBlank(stepResult.getMethodName())) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(stepResult.getMethodName());
        }
        return builder.toString();
    }
}
