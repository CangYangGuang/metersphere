package io.metersphere.api.grpc.dto;

import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * gRPC 前置 / 后置操作单条执行结果，用于报告展示。
 */
@Data
public class GrpcProcessorResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "操作名称")
    private String name;

    @Schema(description = "处理器类型：SCRIPT / EXTRACT / TIME_WAITING")
    private String processorType;

    @Schema(description = "执行阶段：PRE / POST")
    private String phase;

    @Schema(description = "是否执行成功")
    private Boolean success = true;

    @Schema(description = "执行信息 / 错误信息 / 控制台输出")
    private String message;

    @Schema(description = "耗时 ms")
    private Long duration;

    @Schema(description = "该操作产生的变量提取结果")
    private List<ExtractResult> extractResults = new ArrayList<>();
}
