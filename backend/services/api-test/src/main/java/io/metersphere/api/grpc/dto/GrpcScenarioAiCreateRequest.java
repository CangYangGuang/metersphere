package io.metersphere.api.grpc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class GrpcScenarioAiCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    private String projectId;

    @NotBlank
    private String moduleId;

    @Valid
    @NotNull
    private GrpcScenarioAiDraftDTO draft;
}
