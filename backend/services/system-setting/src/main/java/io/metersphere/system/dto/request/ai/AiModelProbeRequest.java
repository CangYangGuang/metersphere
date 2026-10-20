package io.metersphere.system.dto.request.ai;

import lombok.Data;

@Data
public class AiModelProbeRequest {
    private String id;
    private String providerName;
    private String apiUrl;
    private String appKey;
    private String baseName;
}
