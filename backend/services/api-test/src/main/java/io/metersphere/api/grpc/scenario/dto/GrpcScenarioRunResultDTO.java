package io.metersphere.api.grpc.scenario.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcScenarioRunResultDTO {

    private Boolean success;
    private List<GrpcScenarioStepResultDTO> stepResults = new ArrayList<>();
}
