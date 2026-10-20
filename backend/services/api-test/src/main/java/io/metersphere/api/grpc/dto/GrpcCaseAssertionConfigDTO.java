package io.metersphere.api.grpc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcCaseAssertionConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "断言项列表")
    private List<GrpcAssertionItemDTO> items = new ArrayList<>();
}
