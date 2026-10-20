package io.metersphere.api.dto.debug;

import io.metersphere.api.domain.ApiDebug;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ApiDebugDTO extends ApiDebug {
    @Schema(description = "请求内容；HTTP 为 MsTestElement，GRPC 为 GrpcCaseDebugRequest")
    private Object request;

    @Schema(description = "响应内容")
    private String response;
}
