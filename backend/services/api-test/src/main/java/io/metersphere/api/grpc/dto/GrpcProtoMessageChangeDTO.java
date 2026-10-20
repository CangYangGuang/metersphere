package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcProtoMessageChangeDTO {
    private String messageName;
    /** FIELD_REMOVED / FIELD_ADDED / FIELD_TYPE_CHANGED / ENUM_VALUE_ADDED */
    private String changeType;
    private String fieldName;
    private String detail;
    /** BREAKING / WARNING */
    private String impactLevel;
}
