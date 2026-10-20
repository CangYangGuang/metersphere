package io.metersphere.system.service;

import com.sun.net.httpserver.HttpServer;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AiModelSource;
import io.metersphere.system.dto.request.ai.AiModelProbeRequest;
import io.metersphere.system.dto.request.ai.AiModelProbeResultDTO;
import io.metersphere.system.mapper.AiModelSourceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAIConfigServiceProbeTest {

    @Mock
    private AiModelSourceMapper aiModelSourceMapper;

    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private SystemAIConfigService service;
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        service = new SystemAIConfigService();
        ReflectionTestUtils.setField(service, "aiModelSourceMapper", aiModelSourceMapper);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        responseBody.set("{\"data\":[{\"id\":\"model-b\"},{\"id\":\"model-a\"},{\"id\":\"model-a\"}]}");
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void probesModelsAndNormalizesV1OnlyOnce() {
        AiModelProbeRequest request = request(baseUrl + "/v1/", "good-key");
        request.setBaseName("model-a");

        AiModelProbeResultDTO result = service.probeModel(request, "admin");

        assertTrue(result.isConnected());
        assertEquals(baseUrl + "/v1", result.getApiUrl());
        assertEquals("/v1/models", requestPath.get());
        assertEquals("Bearer good-key", authorization.get());
        assertEquals(java.util.List.of("model-a", "model-b"), result.getModels());
        assertTrue(result.getModelAvailable());
    }

    @Test
    void usesSavedSecretWhenBrowserSendsMaskedKey() {
        AiModelSource source = new AiModelSource();
        source.setId("saved-id");
        source.setOwner("admin");
        source.setApiUrl(baseUrl);
        source.setAppKey("saved-secret-key");
        source.setBaseName("model-b");
        when(aiModelSourceMapper.selectByPrimaryKey("saved-id")).thenReturn(source);
        AiModelProbeRequest request = new AiModelProbeRequest();
        request.setId("saved-id");
        request.setAppKey(SystemAIConfigService.maskSkString(source.getAppKey()));

        AiModelProbeResultDTO result = service.probeModel(request, "admin");

        assertEquals("Bearer saved-secret-key", authorization.get());
        assertTrue(result.getModelAvailable());
    }

    @Test
    void rejectsAccessToAnotherUsersSavedCredential() {
        AiModelSource source = new AiModelSource();
        source.setOwner("another-user");
        when(aiModelSourceMapper.selectByPrimaryKey("saved-id")).thenReturn(source);
        AiModelProbeRequest request = new AiModelProbeRequest();
        request.setId("saved-id");

        assertThrows(MSException.class, () -> service.probeModel(request, "admin"));
    }

    @Test
    void reportsRemoteAuthenticationFailureWithoutLeakingTheKey() {
        responseStatus.set(401);
        responseBody.set("{\"error\":{\"message\":\"invalid credentials\"}}");

        MSException error = assertThrows(MSException.class,
                () -> service.probeModel(request(baseUrl, "private-value"), "admin"));

        assertTrue(error.getMessage().contains("HTTP 401"));
        assertTrue(error.getMessage().contains("invalid credentials"));
        assertFalse(error.getMessage().contains("private-value"));
    }

    @Test
    void acceptsAnEmptyRemoteModelList() {
        responseBody.set("{\"data\":[]}");

        AiModelProbeResultDTO result = service.probeModel(request(baseUrl, "good-key"), "admin");

        assertTrue(result.isConnected());
        assertTrue(result.getModels().isEmpty());
        assertTrue(result.getMessage().contains("未返回可用模型"));
    }

    @Test
    void rejectsUnsupportedUrlsBeforeSendingCredentials() {
        MSException error = assertThrows(MSException.class,
                () -> service.probeModel(request("file:///tmp/models", "private-value"), "admin"));
        assertTrue(error.getMessage().contains("http://"));
    }

    private static AiModelProbeRequest request(String apiUrl, String key) {
        AiModelProbeRequest request = new AiModelProbeRequest();
        request.setApiUrl(apiUrl);
        request.setAppKey(key);
        return request;
    }
}
