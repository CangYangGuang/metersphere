package io.metersphere.api.grpc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class GrpcExtractItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "变量名")
    private String variableName;

    @Schema(description = "JSONPath 表达式")
    private String expression;

    @Schema(description = "是否启用")
    private Boolean enable = true;
}
