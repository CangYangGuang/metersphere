package io.metersphere.system.utils;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.request.ai.AIChatAttachment;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIChatAttachmentUtilsTest {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
    };

    @Test
    void shouldNormalizeSerializeAndRestoreImage() {
        AIChatAttachment source = attachment("image/jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01});
        source.setName("C:\\screenshots\\failure.jpg");

        List<AIChatAttachment> normalized = AIChatAttachmentUtils.normalizeAndValidate(List.of(source));
        AIChatAttachment image = normalized.getFirst();

        assertNotNull(image.getId());
        assertEquals("failure.jpg", image.getName());
        assertEquals("image/jpeg", image.getMimeType());
        assertEquals(4L, image.getSize());

        String stored = AIChatAttachmentUtils.serialize(normalized);
        assertFalse(stored.isBlank());
        assertEquals(normalized, AIChatAttachmentUtils.parse(stored));

        Media[] media = AIChatAttachmentUtils.toMedia(normalized);
        assertEquals(1, media.length);
        assertEquals("image/jpeg", media[0].getMimeType().toString());
    }

    @Test
    void shouldRejectMimeTypeSpoofing() {
        AIChatAttachment source = attachment("image/png", "not-a-png".getBytes());

        MSException error = assertThrows(MSException.class,
                () -> AIChatAttachmentUtils.normalizeAndValidate(List.of(source)));

        assertTrue(error.getMessage().contains("不匹配"));
    }

    @Test
    void shouldRejectMoreThanFourImages() {
        AIChatAttachment source = attachment("image/png", PNG_BYTES);

        MSException error = assertThrows(MSException.class,
                () -> AIChatAttachmentUtils.normalizeAndValidate(Collections.nCopies(5, source)));

        assertTrue(error.getMessage().contains("最多添加"));
    }

    @Test
    void shouldIgnoreInvalidStoredAttachments() {
        assertTrue(AIChatAttachmentUtils.parse("not-json").isEmpty());
    }

    private AIChatAttachment attachment(String mimeType, byte[] bytes) {
        AIChatAttachment attachment = new AIChatAttachment();
        attachment.setMimeType(mimeType);
        attachment.setData(Base64.getEncoder().encodeToString(bytes));
        return attachment;
    }
}
