package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcProtoAffectedCaseDTO {
    private String apiCaseId;
    private String serviceName;
    private String methodName;
    private String protoId;
}
