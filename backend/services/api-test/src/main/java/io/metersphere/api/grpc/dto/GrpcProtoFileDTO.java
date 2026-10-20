package io.metersphere.api.grpc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class GrpcProtoFileDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "proto 文件名，可含相对路径，如 types/common.proto")
    private String fileName;

    @Schema(description = "proto 原文")
    private String content;

    @Schema(description = "是否为主入口 proto")
    private Boolean main;
}
