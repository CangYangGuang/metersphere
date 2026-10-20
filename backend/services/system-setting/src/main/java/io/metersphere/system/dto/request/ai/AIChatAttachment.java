package io.metersphere.system.dto.request.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AIChatAttachment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "附件ID")
    @Size(max = 50)
    private String id;

    @Schema(description = "图片文件名")
    @Size(max = 255)
    private String name;

    @Schema(description = "图片 MIME 类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String mimeType;

    @Schema(description = "不包含 data URL 前缀的 Base64 图片数据", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String data;

    @Schema(description = "图片原始字节数")
    private Long size;
}
