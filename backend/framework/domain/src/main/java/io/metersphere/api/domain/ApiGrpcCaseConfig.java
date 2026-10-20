package io.metersphere.api.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class ApiGrpcCaseConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String apiCaseId;
    private String apiDefinitionId;
    private String protoId;
    private String serviceName;
    private String methodName;
    private String serverAddress;
    private String descriptorContent;
    private String requestJson;
    private String metadata;
    private Integer timeout;
    private String assertionJson;
    private String extractJson;
    private String settingsJson;
    private String preProcessorJson;
    private String postProcessorJson;
    private String createUser;
    private Long createTime;
    private String updateUser;
    private Long updateTime;
    private Boolean deleted;
}
