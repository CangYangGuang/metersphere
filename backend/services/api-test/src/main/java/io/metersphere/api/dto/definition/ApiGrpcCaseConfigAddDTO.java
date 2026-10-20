package io.metersphere.api.dto.definition;

import io.metersphere.api.dto.request.processors.MsProcessorConfig;
import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseExtractConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseSettingsDTO;
import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApiGrpcCaseConfigAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Service 名称，默认继承接口定义")
    @Size(max = 255)
    private String serviceName;

    @Schema(description = "Method 名称，默认继承接口定义")
    @Size(max = 255)
    private String methodName;

    @Schema(description = "gRPC 服务地址，例如 host:50051")
    @Size(max = 512)
    private String serverAddress;

    @Schema(description = "descriptor 快照，默认继承接口定义")
    private String descriptorContent;

    @Schema(description = "主 proto 快照")
    private String protoContent;

    @Schema(description = "主 proto 文件名")
    @Size(max = 512)
    private String fileName;

    @Schema(description = "bundle 主文件名")
    @Size(max = 512)
    private String mainFileName;

    @Schema(description = "proto bundle 快照")
    private List<GrpcProtoFileDTO> protoFiles = new ArrayList<>();

    @Schema(description = "绑定的 proto 快照 ID，更新用例时默认保留原版本")
    private String protoId;

    @Schema(description = "请求 JSON")
    private String requestJson;

    @Schema(description = "Metadata")
    private List<GrpcMetadataItemDTO> metadata = new ArrayList<>();

    @Schema(description = "超时 ms，默认 60000")
    private Integer timeout;

    @Schema(description = "断言配置")
    private GrpcCaseAssertionConfigDTO assertionConfig;

    @Schema(description = "变量提取配置")
    private GrpcCaseExtractConfigDTO extractConfig;

    @Schema(description = "运行设置：TLS / authority / 默认值 / 最大响应大小 / 连接超时")
    private GrpcCaseSettingsDTO settings;

    @Schema(description = "前置操作配置（复用 HTTP 处理器结构）")
    private MsProcessorConfig preProcessorConfig;

    @Schema(description = "后置操作配置（复用 HTTP 处理器结构）")
    private MsProcessorConfig postProcessorConfig;
}
