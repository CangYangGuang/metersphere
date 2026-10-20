package io.metersphere.ai.engine.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AIOpenAIChatClientTest {

    @Test
    void shouldNormalizeOpenAiCompatibleBaseUrls() {
        assertEquals("http://192.168.110.124:3000", AIOpenAIChatClient.normalizeBaseUrl("http://192.168.110.124:3000/v1"));
        assertEquals("http://192.168.110.124:3000", AIOpenAIChatClient.normalizeBaseUrl("http://192.168.110.124:3000/v1/"));
        assertEquals("http://192.168.110.124:3000", AIOpenAIChatClient.normalizeBaseUrl(" http://192.168.110.124:3000/ "));
        assertEquals("http://internal-api/custom", AIOpenAIChatClient.normalizeBaseUrl("http://internal-api/custom/v1"));
        assertEquals("http://internal-api/v1beta", AIOpenAIChatClient.normalizeBaseUrl("http://internal-api/v1beta"));
        assertNull(AIOpenAIChatClient.normalizeBaseUrl(null));
    }
}
