package io.metersphere.api.utils;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.dto.definition.ApiTestCaseDTO;
import io.metersphere.api.grpc.assertion.GrpcAssertionType;
import io.metersphere.api.grpc.dto.GrpcCaseSettingsDTO;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.sdk.constants.MsAssertionCondition;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class GrpcApiCaseDTOParserTest {

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
    void shouldParseGrpcAiCaseSuccessfully() {
        ApiTestCaseDTO caseDTO = GrpcApiCaseDTOParser.parse("api-def-id", buildValidAiContent(), grpcDefinition);

        Assertions.assertEquals("greeter-hello-success", caseDTO.getName());
        Assertions.assertEquals(ApiConstants.GRPC_PROTOCOL, caseDTO.getProtocol());
        Assertions.assertNotNull(caseDTO.getGrpcCaseConfig());
        Assertions.assertEquals("{\"name\":\"MeterSphere\"}", caseDTO.getGrpcCaseConfig().getRequestJson());
        Assertions.assertEquals("helloworld.Greeter", caseDTO.getGrpcCaseConfig().getServiceName());
        Assertions.assertEquals("SayHello", caseDTO.getGrpcCaseConfig().getMethodName());
        Assertions.assertEquals("host.docker.internal:50052", caseDTO.getGrpcCaseConfig().getServerAddress());
        Assertions.assertEquals("proto-id", caseDTO.getGrpcCaseConfig().getProtoId());
        Assertions.assertEquals("helloworld.proto", caseDTO.getGrpcCaseConfig().getFileName());
        Assertions.assertEquals("helloworld.proto", caseDTO.getGrpcCaseConfig().getMainFileName());
        Assertions.assertEquals(grpcDefinition.getProtoContent(), caseDTO.getGrpcCaseConfig().getProtoContent());
        Assertions.assertEquals(1, caseDTO.getGrpcCaseConfig().getProtoFiles().size());
        Assertions.assertEquals(1, caseDTO.getGrpcCaseConfig().getAssertionConfig().getItems().size());
        Assertions.assertEquals(GrpcAssertionType.JSON_PATH.name(),
                caseDTO.getGrpcCaseConfig().getAssertionConfig().getItems().getFirst().getType());
        Assertions.assertEquals(1, caseDTO.getGrpcCaseConfig().getExtractConfig().getItems().size());
        Assertions.assertEquals("msg",
                caseDTO.getGrpcCaseConfig().getExtractConfig().getItems().getFirst().getVariableName());

        GrpcCaseSettingsDTO settings = new GrpcCaseSettingsDTO();
        settings.setConnectTimeout(8_000);
        caseDTO.getGrpcCaseConfig().setSettings(settings);
        ApiGrpcCaseConfigAddDTO addDTO = GrpcApiCaseDTOParser.toAddDTO(caseDTO.getGrpcCaseConfig());
        Assertions.assertEquals("{\"name\":\"MeterSphere\"}", addDTO.getRequestJson());
        Assertions.assertEquals("host.docker.internal:50052", addDTO.getServerAddress());
        Assertions.assertEquals("proto-id", addDTO.getProtoId());
        Assertions.assertEquals("helloworld.proto", addDTO.getFileName());
        Assertions.assertEquals("helloworld.proto", addDTO.getMainFileName());
        Assertions.assertEquals(grpcDefinition.getProtoContent(), addDTO.getProtoContent());
        Assertions.assertEquals(1, addDTO.getProtoFiles().size());
        Assertions.assertSame(settings, addDTO.getSettings());
    }

    @Test
    void shouldUseReflectionAddressWhenDefinitionAddressIsBlank() {
        grpcDefinition.setServerAddress(" ");
        grpcDefinition.setReflectionServerAddress("reflection.internal:50051");

        ApiTestCaseDTO caseDTO = GrpcApiCaseDTOParser.parse("api-def-id", buildValidAiContent(), grpcDefinition);

        Assertions.assertEquals("reflection.internal:50051", caseDTO.getGrpcCaseConfig().getServerAddress());
    }

    @Test
    void shouldFailWhenServiceNameMismatch() {
        String content = buildValidAiContent()
                .replace("## requestJson", "## serviceName: wrong.Service\n## requestJson");

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcApiCaseDTOParser.parse("api-def-id", content, grpcDefinition));
        Assertions.assertTrue(exception.getMessage().contains("serviceName"));
    }

    @Test
    void shouldFailWhenMethodNameMismatch() {
        String content = buildValidAiContent()
                .replace("## requestJson", "## methodName: WrongMethod\n## requestJson");

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcApiCaseDTOParser.parse("api-def-id", content, grpcDefinition));
        Assertions.assertTrue(exception.getMessage().contains("methodName"));
    }

    @Test
    void shouldFailWhenRequestJsonIsInvalid() {
        String content = buildValidAiContent()
                .replace("{\"name\":\"MeterSphere\"}", "not-json");

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcApiCaseDTOParser.parse("api-def-id", content, grpcDefinition));
        Assertions.assertTrue(exception.getMessage().contains("requestJson"));
    }

    @Test
    void shouldFailWhenRequestJsonIsArray() {
        String content = buildValidAiContent()
                .replace("{\"name\":\"MeterSphere\"}", "[1,2,3]");

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcApiCaseDTOParser.parse("api-def-id", content, grpcDefinition));
        Assertions.assertTrue(exception.getMessage().contains("JSON 对象"));
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
                {"items":[{"type":"%s","name":"message","expression":"$.message","condition":"%s","expectedValue":"Hello MeterSphere","enable":true}]}
                ```
                ## extractConfig
                ```json
                {"items":[{"variableName":"msg","expression":"$.message","enable":true}]}
                ```
                apiCaseEnd
                """.formatted(GrpcAssertionType.JSON_PATH.name(), MsAssertionCondition.EQUALS.name());
    }
}
