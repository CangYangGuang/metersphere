package io.metersphere.api.grpc.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcParseProtoResponse {
    private String packageName;
    private String descriptorContent;
    private List<GrpcServiceInfoDTO> services = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
