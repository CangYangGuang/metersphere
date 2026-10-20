package io.metersphere.api.utils;

import io.metersphere.api.dto.GrpcApiCaseAIRenderConfig;
import io.metersphere.api.grpc.util.GrpcProtoMessageSchemaExtractor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class GrpcApiCaseAIRenderTest {

    private GrpcApiCasePromptTemplateCache templateCache;

    @BeforeEach
    void setUp() {
        templateCache = new GrpcApiCasePromptTemplateCache();
    }

    @Test
    void shouldRenderGrpcAiPromptSuccessfully() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);

        GrpcApiCaseAIRenderConfig renderConfig = new GrpcApiCaseAIRenderConfig();
        renderConfig.setApiName("Greeter API");
        renderConfig.setPackageName("helloworld");
        renderConfig.setServiceName("helloworld.Greeter");
        renderConfig.setMethodName("SayHello");
        renderConfig.setRequestType("helloworld.HelloRequest");
        renderConfig.setResponseType("helloworld.HelloReply");
        renderConfig.setRequestMessageSchema(
                GrpcProtoMessageSchemaExtractor.extractMessageDefinition(protoContent, "helloworld.HelloRequest"));
        renderConfig.setResponseMessageSchema(
                GrpcProtoMessageSchemaExtractor.extractMessageDefinition(protoContent, "helloworld.HelloReply"));
        renderConfig.setUserMessage("生成一条正常调用用例");
        renderConfig.setAssertion(true);
        renderConfig.setNormal(true);
        renderConfig.setAbnormal(false);

        String prompt = templateCache.getTemplate(renderConfig);

        Assertions.assertTrue(prompt.contains("helloworld.Greeter"));
        Assertions.assertTrue(prompt.contains("SayHello"));
        Assertions.assertTrue(prompt.contains("message HelloRequest"));
        Assertions.assertTrue(prompt.contains("message HelloReply"));
        Assertions.assertTrue(prompt.contains("生成一条正常调用用例"));
        Assertions.assertFalse(prompt.contains("descriptorContent"));
    }
}
