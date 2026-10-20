package io.metersphere.api.grpc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * gRPC 运行设置。字段名与前端 GrpcRequestState 中的设置项保持一致，
 * 通过 settings_json 列持久化，并在调试 / 用例执行时进入服务端执行参数。
 */
@Data
public class GrpcCaseSettingsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否启用 TLS")
    private Boolean useTls;

    @Schema(description = "TLS 是否校验服务端证书")
    private Boolean verifyServerCertificate;

    @Schema(description = "TLS 服务名覆盖（SNI / authority override）")
    private String serverNameOverride;

    @Schema(description = "authority 覆盖")
    private String authority;

    @Schema(description = "响应 JSON 是否包含默认值字段")
    private Boolean includeDefaultValues;

    @Schema(description = "最大响应大小 MB")
    private Integer maxResponseSizeMb;

    @Schema(description = "连接超时 ms")
    private Integer connectTimeout;
}
