package io.metersphere.api.grpc.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcServiceInfoDTO {
    private String name;
    private String fullName;
    private List<GrpcMethodInfoDTO> methods = new ArrayList<>();
}
