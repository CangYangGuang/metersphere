package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcMetadataItemDTO {
    private String key;
    private String value;
    private Boolean enabled = true;
}
