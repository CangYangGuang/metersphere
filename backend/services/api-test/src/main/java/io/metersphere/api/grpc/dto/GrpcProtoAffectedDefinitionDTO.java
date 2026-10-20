package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcProtoAffectedDefinitionDTO {
    private String apiDefinitionId;
    private String serviceName;
    private String methodName;
}
