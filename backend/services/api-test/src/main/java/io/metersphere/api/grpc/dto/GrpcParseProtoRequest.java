package io.metersphere.api.grpc.dto;

import lombok.Data;

import java.util.List;

@Data
public class GrpcParseProtoRequest {
    /** @deprecated use protoFiles for multi-file; kept for backward compatibility */
    private String protoContent;
    private String fileName;
    private List<GrpcProtoFileDTO> protoFiles;
    private String mainFileName;
}
