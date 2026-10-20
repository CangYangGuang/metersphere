package io.metersphere.api.grpc.scenario.dto;

import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcScenarioStepResultDTO {

    private String stepId;
    private String stepName;
    private String stepType;
    private String resourceId;
    private String projectId;
    private String serviceName;
    private String methodName;
    private String serverAddress;
    private String requestJson;
    private Boolean success;
    private String grpcStatus;
    private Integer grpcCode;
    private Long duration;
    private String responseJson;
    private String errorCode;
    private String errorMessage;
    private List<ResponseAssertionResult> assertionResults = new ArrayList<>();
    private List<ExtractResult> extractResults = new ArrayList<>();
}
