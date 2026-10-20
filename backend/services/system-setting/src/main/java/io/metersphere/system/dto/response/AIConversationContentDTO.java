package io.metersphere.system.dto.response;

import io.metersphere.system.dto.request.ai.AIChatAttachment;
import lombok.Data;

import java.util.List;

@Data
public class AIConversationContentDTO {

    private String id;

    private String conversationId;

    private String type;

    private Long createTime;

    private String content;

    private List<AIChatAttachment> attachments;
}
