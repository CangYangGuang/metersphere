package io.metersphere.system.utils;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.dto.request.ai.AIChatAttachment;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class AIChatAttachmentUtils {

    public static final int MAX_IMAGE_COUNT = 4;
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_TOTAL_IMAGE_BYTES = 10 * 1024 * 1024;

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif"
    );

    private AIChatAttachmentUtils() {
    }

    public static List<AIChatAttachment> normalizeAndValidate(List<AIChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        if (attachments.size() > MAX_IMAGE_COUNT) {
            throw new MSException("每条消息最多添加 " + MAX_IMAGE_COUNT + " 张图片");
        }

        List<AIChatAttachment> normalized = new ArrayList<>(attachments.size());
        long totalBytes = 0;
        for (int index = 0; index < attachments.size(); index++) {
            AIChatAttachment attachment = attachments.get(index);
            if (attachment == null) {
                throw new MSException("图片附件不能为空");
            }
            String mimeType = StringUtils.trimToEmpty(attachment.getMimeType()).toLowerCase(Locale.ROOT);
            if ("image/jpg".equals(mimeType)) {
                mimeType = "image/jpeg";
            }
            if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
                throw new MSException("仅支持 PNG、JPEG、WebP 和 GIF 图片");
            }

            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(StringUtils.trimToEmpty(attachment.getData()));
            } catch (IllegalArgumentException e) {
                throw new MSException("图片数据格式无效", e);
            }
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw new MSException("单张图片不能超过 5 MB");
            }
            if (!matchesSignature(mimeType, bytes)) {
                throw new MSException("图片内容与文件类型不匹配");
            }
            totalBytes += bytes.length;
            if (totalBytes > MAX_TOTAL_IMAGE_BYTES) {
                throw new MSException("图片总大小不能超过 10 MB");
            }

            AIChatAttachment item = new AIChatAttachment();
            item.setId(StringUtils.defaultIfBlank(attachment.getId(), UUID.randomUUID().toString()));
            item.setName(sanitizeName(attachment.getName(), index));
            item.setMimeType(mimeType);
            item.setData(Base64.getEncoder().encodeToString(bytes));
            item.setSize((long) bytes.length);
            normalized.add(item);
        }
        return normalized;
    }

    public static Media[] toMedia(List<AIChatAttachment> attachments) {
        return normalizeAndValidate(attachments).stream()
                .map(attachment -> Media.builder()
                        .id(attachment.getId())
                        .name(attachment.getName())
                        .mimeType(MimeTypeUtils.parseMimeType(attachment.getMimeType()))
                        .data(Base64.getDecoder().decode(attachment.getData()))
                        .build())
                .toArray(Media[]::new);
    }

    public static String serialize(List<AIChatAttachment> attachments) {
        List<AIChatAttachment> normalized = normalizeAndValidate(attachments);
        return normalized.isEmpty() ? null : JSON.toJSONString(normalized);
    }

    public static List<AIChatAttachment> parse(String attachments) {
        if (StringUtils.isBlank(attachments)) {
            return List.of();
        }
        try {
            return normalizeAndValidate(JSON.parseArray(attachments, AIChatAttachment.class));
        } catch (Exception e) {
            LogUtils.warn("忽略无效的 AI 图片附件记录: {}", e.getMessage());
            return List.of();
        }
    }

    private static String sanitizeName(String name, int index) {
        String normalized = StringUtils.defaultIfBlank(name, "image-" + (index + 1))
                .replace('\\', '/');
        normalized = StringUtils.substringAfterLast("/" + normalized, "/");
        return StringUtils.substring(normalized, 0, 255);
    }

    private static boolean matchesSignature(String mimeType, byte[] bytes) {
        return switch (mimeType) {
            case "image/png" -> startsWith(bytes, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/jpeg" -> startsWith(bytes, new int[]{0xFF, 0xD8, 0xFF});
            case "image/gif" -> startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && bytes.length >= 12
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
