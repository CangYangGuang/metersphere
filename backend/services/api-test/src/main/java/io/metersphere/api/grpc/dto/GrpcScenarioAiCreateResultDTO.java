package io.metersphere.api.grpc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcScenarioAiCreateResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int createdCaseCount;
    private int updatedCaseCount;
    private int createdScenarioCount;
    private int updatedScenarioCount;
    private int skippedCount;
    private List<String> scenarioIds = new ArrayList<>();
    private List<Item> items = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item implements Serializable {
        private String type;
        private String name;
        private String action;
        private String resourceId;
        private String message;
    }
}
