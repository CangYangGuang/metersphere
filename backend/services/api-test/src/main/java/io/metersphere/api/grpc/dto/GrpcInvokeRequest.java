package io.metersphere.api.grpc.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcInvokeRequest {
    private String serverAddress;
    private String descriptorContent;
    private String protoContent;
    private String serviceName;
    private String methodName;
    private String requestJson;
    private List<GrpcMetadataItemDTO> metadata = new ArrayList<>();
    private Integer timeout;

    // runtime settings
    private Boolean useTls;
    private Boolean verifyServerCertificate;
    private String serverNameOverride;
    private String authority;
    private Boolean includeDefaultValues;
    private Integer maxResponseSizeMb;
    private Integer connectTimeout;
}
