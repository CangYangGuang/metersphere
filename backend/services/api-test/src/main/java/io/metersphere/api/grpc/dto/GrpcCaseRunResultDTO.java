package io.metersphere.api.grpc.dto;

import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import io.metersphere.sdk.util.BeanUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcCaseRunResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否执行成功")
    private Boolean success;

    @Schema(description = "gRPC 状态名")
    private String grpcStatus;

    @Schema(description = "gRPC 状态码")
    private Integer grpcCode;

    @Schema(description = "耗时 ms")
    private Long duration;

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

    @Schema(description = "用例执行报告 ID，run 落库后返回")
    private String reportId;

    public static GrpcCaseRunResultDTO from(GrpcInvokeResponse response) {
        if (response == null) {
            return error(GrpcResultCode.GRPC_CALL_FAILED, GrpcResultCode.GRPC_CALL_FAILED.name());
        }
        GrpcCaseRunResultDTO dto = new GrpcCaseRunResultDTO();
        BeanUtils.copyBean(dto, response);
        return dto;
    }

    public static GrpcCaseRunResultDTO error(GrpcResultCode resultCode, String message) {
        GrpcCaseRunResultDTO dto = new GrpcCaseRunResultDTO();
        dto.setSuccess(false);
        dto.setErrorCode(resultCode.name());
        dto.setErrorMessage(message != null ? message : resultCode.name());
        dto.setDuration(0L);
        return dto;
    }
}
