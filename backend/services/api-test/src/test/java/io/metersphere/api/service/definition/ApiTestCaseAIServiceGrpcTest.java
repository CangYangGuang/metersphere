package io.metersphere.api.service.definition;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.constants.ApiDefinitionStatus;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiDefinitionBlob;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.dto.definition.ApiCaseAiTransformDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.dto.definition.ApiTestCaseAddRequest;
import io.metersphere.api.dto.definition.ApiTestCaseDTO;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.mapper.ApiTestCaseMapper;
import io.metersphere.system.mapper.AiUserPromptConfigMapper;
import io.metersphere.system.service.AiChatBaseService;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import io.metersphere.api.grpc.util.GrpcProtocolUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTestCaseAIServiceGrpcTest {

    private static final String API_DEFINITION_ID = "grpc-api-id";
    private static final String PROJECT_ID = "project-id";
    private static final String USER_ID = "admin";

    @InjectMocks
    private ApiTestCaseAIService apiTestCaseAIService;

    @Mock
    private ApiDefinitionMapper apiDefinitionMapper;
    @Mock
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;
    @Mock
    private ApiTestCaseService apiTestCaseService;
    @Mock
    private ApiTestCaseMapper apiTestCaseMapper;
    @Mock
    private AiChatBaseService aiChatBaseService;
    @Mock
    private AiUserPromptConfigMapper aiUserPromptConfigMapper;

    private ApiGrpcDefinitionDTO grpcDefinition;

    @BeforeEach
    void setUp() throws Exception {
        grpcDefinition = new ApiGrpcDefinitionDTO();
        grpcDefinition.setPackageName("helloworld");
        grpcDefinition.setServiceName("helloworld.Greeter");
        grpcDefinition.setMethodName("SayHello");
        grpcDefinition.setRequestType("helloworld.HelloRequest");
        grpcDefinition.setResponseType("helloworld.HelloReply");
        grpcDefinition.setProtoId("proto-id");
        grpcDefinition.setFileName("helloworld.proto");
        grpcDefinition.setMainFileName("helloworld.proto");
        grpcDefinition.setServerAddress("host.docker.internal:50052");
        grpcDefinition.setDescriptorContent("descriptor-base64");
        grpcDefinition.setProtoContent(Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8));
        GrpcProtoFileDTO protoFile = new GrpcProtoFileDTO();
        protoFile.setFileName("helloworld.proto");
        protoFile.setContent(grpcDefinition.getProtoContent());
        protoFile.setMain(true);
        grpcDefinition.setProtoFiles(List.of(protoFile));
    }

    @Test
    void shouldTransformGrpcAiContentToDto() {
        ApiDefinition apiDefinition = buildGrpcDefinition();
        when(apiDefinitionMapper.selectByPrimaryKey(API_DEFINITION_ID)).thenReturn(apiDefinition);
        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID)).thenReturn(grpcDefinition);

        ApiCaseAiTransformDTO request = new ApiCaseAiTransformDTO();
        request.setApiDefinitionId(API_DEFINITION_ID);
        request.setPrompt(buildValidAiContent());

        ApiTestCaseDTO result = apiTestCaseAIService.transformToDTO(request);

        Assertions.assertEquals("greeter-hello-success", result.getName());
        Assertions.assertEquals(ApiConstants.GRPC_PROTOCOL, result.getProtocol());
        Assertions.assertNotNull(result.getGrpcCaseConfig());
        Assertions.assertEquals("{\"name\":\"MeterSphere\"}", result.getGrpcCaseConfig().getRequestJson());
        Assertions.assertEquals("host.docker.internal:50052", result.getGrpcCaseConfig().getServerAddress());
        Assertions.assertEquals("proto-id", result.getGrpcCaseConfig().getProtoId());
        Assertions.assertEquals("helloworld.proto", result.getGrpcCaseConfig().getMainFileName());
        Assertions.assertEquals(1, result.getGrpcCaseConfig().getProtoFiles().size());
    }

    @Test
    void shouldBatchSaveGrpcAiCaseViaAddCase() {
        ApiDefinition apiDefinition = buildGrpcDefinition();
        when(apiTestCaseService.getApiDefinition(API_DEFINITION_ID)).thenReturn(apiDefinition);
        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID)).thenReturn(grpcDefinition);
        when(apiTestCaseMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        ApiTestCase savedCase = new ApiTestCase();
        savedCase.setId("saved-case-id");
        when(apiTestCaseService.addCase(any(), eq(USER_ID))).thenReturn(savedCase);

        ApiCaseAiTransformDTO request = new ApiCaseAiTransformDTO();
        request.setApiDefinitionId(API_DEFINITION_ID);
        request.setPrompt(buildValidAiContent());

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("temp-case-id");
            apiTestCaseAIService.batchSave(request, USER_ID);
        }

        ArgumentCaptor<ApiTestCaseAddRequest> captor = ArgumentCaptor.forClass(ApiTestCaseAddRequest.class);
        verify(apiTestCaseService).addCase(captor.capture(), eq(USER_ID));
        ApiTestCaseAddRequest addRequest = captor.getValue();
        Assertions.assertEquals("greeter-hello-success", addRequest.getName());
        Assertions.assertEquals(PROJECT_ID, addRequest.getProjectId());
        Assertions.assertEquals(ApiDefinitionStatus.PROCESSING.name(), addRequest.getStatus());
        Assertions.assertTrue(addRequest.getAiCreate());
        Assertions.assertNotNull(addRequest.getGrpcCaseConfig());
        Assertions.assertEquals("{\"name\":\"MeterSphere\"}", addRequest.getGrpcCaseConfig().getRequestJson());
        Assertions.assertEquals("helloworld.Greeter", addRequest.getGrpcCaseConfig().getServiceName());
        Assertions.assertEquals("SayHello", addRequest.getGrpcCaseConfig().getMethodName());
        Assertions.assertEquals("host.docker.internal:50052", addRequest.getGrpcCaseConfig().getServerAddress());
        Assertions.assertEquals("proto-id", addRequest.getGrpcCaseConfig().getProtoId());
        Assertions.assertEquals("helloworld.proto", addRequest.getGrpcCaseConfig().getFileName());
        Assertions.assertEquals("helloworld.proto", addRequest.getGrpcCaseConfig().getMainFileName());
        Assertions.assertEquals(grpcDefinition.getProtoContent(), addRequest.getGrpcCaseConfig().getProtoContent());
        Assertions.assertEquals(1, addRequest.getGrpcCaseConfig().getProtoFiles().size());
    }

    @Test
    void shouldNotLoadGrpcDefinitionForHttpProtocolTransform() {
        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId("http-api-id");
        apiDefinition.setProtocol(ApiConstants.HTTP_PROTOCOL);
        when(apiDefinitionMapper.selectByPrimaryKey("http-api-id")).thenReturn(apiDefinition);

        ApiCaseAiTransformDTO request = new ApiCaseAiTransformDTO();
        request.setApiDefinitionId("http-api-id");
        request.setPrompt("apiCaseStart\n## 用例名称: http-case\napiCaseEnd");

        Assertions.assertThrows(Exception.class, () -> apiTestCaseAIService.transformToDTO(request));
        verify(grpcDefinitionPersistenceService, never()).loadDtoByApiDefinitionId(any());
        Assertions.assertFalse(GrpcProtocolUtils.isGrpcProtocol(apiDefinition.getProtocol()));
    }

    private ApiDefinition buildGrpcDefinition() {
        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId(API_DEFINITION_ID);
        apiDefinition.setProjectId(PROJECT_ID);
        apiDefinition.setProtocol(ApiConstants.GRPC_PROTOCOL);
        apiDefinition.setName("Greeter API");
        return apiDefinition;
    }

    private String buildValidAiContent() {
        return """
                apiCaseStart
                ## 用例名称: greeter-hello-success
                ## requestJson
                ```json
                {"name":"MeterSphere"}
                ```
                ## assertionConfig
                ```json
                {"items":[{"type":"JSON_PATH","name":"message","expression":"$.message","condition":"EQUALS","expectedValue":"Hello MeterSphere","enable":true}]}
                ```
                ## extractConfig
                ```json
                {"items":[{"variableName":"msg","expression":"$.message","enable":true}]}
                ```
                apiCaseEnd
                """;
    }
}
