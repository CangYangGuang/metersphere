package io.metersphere.api.grpc.dto;

import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class GrpcCaseDebugRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "gRPC 服务地址 host:port")
    private String serverAddress;

    @Schema(description = "gRPC 调试配置")
    @NotNull
    @Valid
    private ApiGrpcCaseConfigAddDTO grpcCaseConfig;
}
