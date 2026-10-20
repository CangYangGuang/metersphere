package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcProtoVersionDTO {
    private String protoId;
    private Integer versionNo;
    private String versionLabel;
    private String fileName;
    private String contentHash;
    private String contentHashShort;
    private Long createTime;
}
