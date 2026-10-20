package io.metersphere.api.grpc.dto;

import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class GrpcCaseDebugPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "gRPC 服务地址")
    private String serverAddress;

    @Schema(description = "调试时覆盖的 gRPC 用例配置")
    private ApiGrpcCaseConfigAddDTO grpcCaseConfig;
}
