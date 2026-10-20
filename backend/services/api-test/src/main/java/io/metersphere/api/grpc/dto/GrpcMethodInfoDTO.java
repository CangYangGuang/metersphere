package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcMethodInfoDTO {
    private String name;
    private String fullName;
    private String methodType;
    private Boolean invokable;
    private String inputType;
    private String outputType;
    /** Example JSON request body generated from the input message descriptor. */
    private String requestJsonTemplate;
}
