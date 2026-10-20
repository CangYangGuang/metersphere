package io.metersphere.api.grpc.dto;

import io.metersphere.system.dto.request.ai.AIChatRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class GrpcScenarioAiGenerateRequest extends AIChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    private String projectId;

    @NotBlank
    private String moduleId;

    private String sourceText;
    @Size(max = 200_000)
    private String projectContext;
    private String projectName;
    private String correction;
    private String serverAddress;
    private GrpcScenarioAiDraftDTO currentDraft;
}
