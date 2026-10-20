package io.metersphere.api.dto.definition;

import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class ApiGrpcDefinitionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String protoId;
    private String fileName;
    private String mainFileName;
    private List<GrpcProtoFileDTO> protoFiles;
    private String packageName;
    private String serviceName;
    private String methodName;
    private String fullMethodName;
    private String serverAddress;
    private String methodType;

    @Schema(description = "是否可调用，仅 UNARY 为 true")
    private Boolean invokable;

    private String requestType;
    private String responseType;
    private String descriptorContent;
    private String protoContent;
    private List<GrpcServiceInfoDTO> services;

    @Schema(description = "是否为 Reflection descriptor-only 来源")
    private Boolean reflection;

    @Schema(description = "Reflection 来源类型")
    private String reflectionSourceType;

    @Schema(description = "Reflection 服务地址 host:port")
    private String reflectionServerAddress;

    @Schema(description = "Reflection 是否使用 TLS")
    private Boolean reflectionUseTls;

    @Schema(description = "Reflection TLS authority")
    private String reflectionAuthority;

    @Schema(description = "Reflection 发现的 Service 全名")
    private String reflectionServiceFullName;

    @Schema(description = "Reflection 发现时间 ISO-8601")
    private String reflectionDiscoveredAt;

    @Schema(description = "当前关联 proto 版本信息")
    private GrpcProtoVersionDTO protoVersion;

    @Schema(description = "同文件名 proto 版本历史")
    private List<GrpcProtoVersionDTO> protoVersionHistory;

    @Schema(description = "相对上一 proto 版本的变更影响提示")
    private GrpcProtoChangeImpactDTO protoChangeImpact;

    @Data
    public static class GrpcServiceInfoDTO implements Serializable {
        private String name;
        private String fullName;
        private List<GrpcMethodInfoDTO> methods;
    }

    @Data
    public static class GrpcMethodInfoDTO implements Serializable {
        private String name;
        private String fullName;
        private String methodType;
        private Boolean invokable;
        private String inputType;
        private String outputType;
        private String requestJsonTemplate;
    }
}
