package io.metersphere.api.grpc.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcScenarioAiDraftDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceSummary;
    private boolean protoRequired;
    private List<String> ambiguities = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<ScenarioDraft> scenarios = new ArrayList<>();

    @Data
    public static class ScenarioDraft implements Serializable {
        private String clientId;
        private String name;
        private String description;
        private String priority = "P1";
        private String safety = "SAFE";
        private boolean selected = true;
        private boolean suggested;
        private boolean blocked;
        private String ambiguity;
        private List<CaseDraft> cases = new ArrayList<>();
    }

    @Data
    public static class CaseDraft implements Serializable {
        private String clientId;
        private String apiDefinitionId;
        private String definitionName;
        private String serviceName;
        private String methodName;
        private String name;
        private String requestJson = "{}";
        private String serverAddress;
        private String priority = "P1";
        private boolean selected = true;
        private boolean suggested;
        private boolean blocked;
        private String ambiguity;
        private GrpcCaseAssertionConfigDTO assertionConfig = new GrpcCaseAssertionConfigDTO();
        private GrpcCaseExtractConfigDTO extractConfig = new GrpcCaseExtractConfigDTO();
    }
}
