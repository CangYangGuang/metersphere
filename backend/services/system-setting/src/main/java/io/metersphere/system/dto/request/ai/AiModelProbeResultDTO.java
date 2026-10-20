package io.metersphere.system.dto.request.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiModelProbeResultDTO {
    private boolean connected;
    private String message;
    private String apiUrl;
    private List<String> models;
    private Boolean modelAvailable;
}
