package io.metersphere.api.grpc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class GrpcBatchGenerateRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String parentModuleId;
    private String serverAddress;
    private String protoContent;
    private String fileName;
    @Valid
    private List<GrpcProtoFileDTO> protoFiles;
    private String mainFileName;
    private String descriptorContent;
    private Boolean reflection;
    private String reflectionSourceType;
    private String reflectionServerAddress;
    private Boolean reflectionUseTls;
    private String reflectionAuthority;
    private String reflectionServiceFullName;
    private String reflectionDiscoveredAt;
    @NotEmpty
    private List<String> methodFullNames;
}
