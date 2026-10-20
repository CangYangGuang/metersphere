package io.metersphere.api.grpc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcReflectionDescribeRequest {

    @NotBlank
    private String serverAddress;

    /** Defaults to plaintext when null or false. */
    private Boolean useTls;

    private String authority;

    private List<GrpcMetadataItemDTO> metadata = new ArrayList<>();

    private Integer timeout;
}
