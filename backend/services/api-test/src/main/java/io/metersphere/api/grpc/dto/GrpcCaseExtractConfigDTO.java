package io.metersphere.api.grpc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcCaseExtractConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "提取项列表")
    private List<GrpcExtractItemDTO> items = new ArrayList<>();
}
