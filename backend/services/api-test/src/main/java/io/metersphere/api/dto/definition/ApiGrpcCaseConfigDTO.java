package io.metersphere.api.dto.definition;

import io.metersphere.api.dto.request.processors.MsProcessorConfig;
import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseExtractConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseSettingsDTO;
import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApiGrpcCaseConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String apiCaseId;
    private String apiDefinitionId;
    private String protoId;

    @Schema(description = "用例绑定的 proto 版本信息")
    private GrpcProtoVersionDTO protoVersion;

    @Schema(description = "用例 proto 版本是否落后于接口定义当前版本")
    private Boolean protoVersionOutdated;

    @Schema(description = "用例 proto 相对接口当前 proto 的变更影响提示")
    private GrpcProtoChangeImpactDTO protoChangeImpact;

    private String serviceName;
    private String methodName;

    @Schema(description = "gRPC 服务地址")
    private String serverAddress;

    private String descriptorContent;

    @Schema(description = "主 proto 原文")
    private String protoContent;

    @Schema(description = "主 proto 文件名")
    private String fileName;

    @Schema(description = "主入口 proto 文件名")
    private String mainFileName;

    @Schema(description = "proto bundle 文件列表")
    private List<GrpcProtoFileDTO> protoFiles;

    @Schema(description = "请求 JSON")
    private String requestJson;

    @Schema(description = "Metadata")
    private List<GrpcMetadataItemDTO> metadata = new ArrayList<>();

    @Schema(description = "超时 ms")
    private Integer timeout;

    @Schema(description = "断言配置")
    private GrpcCaseAssertionConfigDTO assertionConfig;

    @Schema(description = "变量提取配置")
    private GrpcCaseExtractConfigDTO extractConfig;

    @Schema(description = "运行设置")
    private GrpcCaseSettingsDTO settings;

    @Schema(description = "前置操作配置")
    private MsProcessorConfig preProcessorConfig;

    @Schema(description = "后置操作配置")
    private MsProcessorConfig postProcessorConfig;
}
