package io.metersphere.api.grpc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class GrpcAssertionItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "断言类型：JSON_PATH / GRPC_STATUS / GRPC_CODE")
    private String type;

    @Schema(description = "断言名称")
    private String name;

    @Schema(description = "JSONPath 表达式，仅 JSON_PATH 类型使用")
    private String expression;

    @Schema(description = "匹配条件，参考 MsAssertionCondition")
    private String condition;

    @Schema(description = "期望值")
    private String expectedValue;

    @Schema(description = "是否启用")
    private Boolean enable = true;
}
