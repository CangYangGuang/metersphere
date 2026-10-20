package io.metersphere.api.grpc.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcBatchGenerateResultDTO {
    private int createdCount;
    private int updatedCount;
    private int failedCount;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String fullMethodName;
        private String apiDebugId;
        private String apiDefinitionId;
        private String moduleId;
        private String action;
        private String errorMessage;
    }
}
