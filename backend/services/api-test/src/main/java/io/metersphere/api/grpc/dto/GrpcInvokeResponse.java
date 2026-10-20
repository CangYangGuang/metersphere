package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcInvokeResponse {
    private Boolean success;
    private String grpcStatus;
    private Integer grpcCode;
    private Long duration;
    private String responseJson;
    private String errorCode;
    private String errorMessage;
}
