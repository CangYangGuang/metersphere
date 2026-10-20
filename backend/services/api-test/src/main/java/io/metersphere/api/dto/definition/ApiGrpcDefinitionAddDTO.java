package io.metersphere.api.dto.definition;

import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class ApiGrpcDefinitionAddDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "proto 原文；Reflection descriptor-only 保存时可为空")
    private String protoContent;

    @Schema(description = "FileDescriptorSet Base64；Reflection descriptor-only 保存时必填")
    private String descriptorContent;

    @Schema(description = "是否为 Reflection descriptor-only 来源")
    private Boolean reflection;

    @Schema(description = "Reflection 来源类型")
    @Size(max = 32)
    private String reflectionSourceType;

    @Schema(description = "Reflection 服务地址 host:port")
    @Size(max = 255)
    private String reflectionServerAddress;

    @Schema(description = "Reflection 是否使用 TLS")
    private Boolean reflectionUseTls;

    @Schema(description = "Reflection TLS authority")
    @Size(max = 255)
    private String reflectionAuthority;

    @Schema(description = "Reflection 发现的 Service 全名")
    @Size(max = 255)
    private String reflectionServiceFullName;

    @Schema(description = "Reflection 发现时间 ISO-8601")
    @Size(max = 64)
    private String reflectionDiscoveredAt;

    @Schema(description = "proto 文件名")
    @Size(max = 255)
    private String fileName;

    @Schema(description = "多 proto 文件列表")
    private List<GrpcProtoFileDTO> protoFiles;

    @Schema(description = "主入口 proto 文件名")
    @Size(max = 255)
    private String mainFileName;

    @Schema(description = "gRPC server address host:port")
    @Size(max = 512)
    private String serverAddress;

    @Schema(description = "Service 名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 255)
    private String serviceName;

    @Schema(description = "Method 名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 255)
    private String methodName;
}
