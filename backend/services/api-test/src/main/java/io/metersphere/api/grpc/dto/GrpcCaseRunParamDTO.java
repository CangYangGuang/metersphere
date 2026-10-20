package io.metersphere.api.grpc.dto;

import io.metersphere.api.dto.request.processors.MsProcessorConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcCaseRunParamDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "gRPC 服务地址，如 127.0.0.1:50051")
    private String serverAddress;

    @Schema(description = "调试时覆盖 serviceName")
    private String serviceName;

    @Schema(description = "调试时覆盖 methodName")
    private String methodName;

    @Schema(description = "调试时覆盖 descriptorContent")
    private String descriptorContent;

    @Schema(description = "调试时覆盖 requestJson")
    private String requestJson;

    @Schema(description = "调试时覆盖 metadata")
    private List<GrpcMetadataItemDTO> metadata = new ArrayList<>();

    @Schema(description = "调试时覆盖 timeout ms")
    private Integer timeout;

    @Schema(description = "调试时覆盖运行设置")
    private GrpcCaseSettingsDTO settings;

    @Schema(description = "调试时覆盖前置操作配置")
    private MsProcessorConfig preProcessorConfig;

    @Schema(description = "调试时覆盖后置操作配置")
    private MsProcessorConfig postProcessorConfig;
}
