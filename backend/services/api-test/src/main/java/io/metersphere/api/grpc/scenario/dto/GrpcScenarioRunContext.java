package io.metersphere.api.grpc.scenario.dto;

import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.scenario.GrpcScenarioVariableContext;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class GrpcScenarioRunContext {

    private String reportId;
    private String projectId;
    private String environmentId;
    private String defaultServerAddress;
    private boolean stopOnFailure;
    private GrpcScenarioVariableContext variableContext = new GrpcScenarioVariableContext();
    private List<ApiScenarioStepCommonDTO> steps = new ArrayList<>();
    private Map<String, Object> stepDetails = new LinkedHashMap<>();
}
