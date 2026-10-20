package io.metersphere.api.grpc.dto;

import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.dto.api.result.RequestResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * gRPC 用例报告详情内容，序列化存入 api_report_detail.content。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GrpcCaseReportContentDTO extends RequestResult {

    @Schema(description = "协议")
    private String protocol = "GRPC";

    @Schema(description = "服务名")
    private String serviceName;

    @Schema(description = "方法名")
    private String methodName;

    @Schema(description = "gRPC server address")
    private String serverAddress;

    @Schema(description = "gRPC 状态名")
    private String grpcStatus;

    @Schema(description = "gRPC 状态码")
    private Integer grpcCode;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "耗时 ms")
    private Long duration;

    @Schema(description = "请求 JSON")
    private String requestJson;

    @Schema(description = "响应 JSON")
    private String responseJson;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "断言结果")
    private List<ResponseAssertionResult> assertionResults = new ArrayList<>();

    @Schema(description = "变量提取结果")
    private List<ExtractResult> extractResults = new ArrayList<>();

    @Schema(description = "前置操作执行结果")
    private List<GrpcProcessorResultDTO> preProcessorResults = new ArrayList<>();

    @Schema(description = "后置操作执行结果")
    private List<GrpcProcessorResultDTO> postProcessorResults = new ArrayList<>();
}
