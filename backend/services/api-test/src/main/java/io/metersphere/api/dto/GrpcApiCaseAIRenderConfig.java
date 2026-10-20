package io.metersphere.api.dto;

import io.metersphere.system.dto.AIRenderConfig;
import lombok.Data;

@Data
public class GrpcApiCaseAIRenderConfig extends AIRenderConfig {

    private String apiName;
    private String userMessage;
    private String packageName;
    private String serviceName;
    private String methodName;
    private String requestType;
    private String responseType;
    private String requestMessageSchema;
    private String responseMessageSchema;

    private Boolean assertion = true;
    private Boolean normal;
    private Boolean abnormal;
}
