package io.metersphere.api.grpc.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class GrpcProtoMessageSchemaExtractorTest {

    @Test
    void shouldExtractRequestAndResponseMessageSchema() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);

        String requestSchema = GrpcProtoMessageSchemaExtractor.extractMessageDefinition(
                protoContent, "helloworld.HelloRequest");
        String responseSchema = GrpcProtoMessageSchemaExtractor.extractMessageDefinition(
                protoContent, "helloworld.HelloReply");

        Assertions.assertTrue(requestSchema.contains("message HelloRequest"));
        Assertions.assertTrue(requestSchema.contains("string name"));
        Assertions.assertTrue(responseSchema.contains("message HelloReply"));
        Assertions.assertTrue(responseSchema.contains("string message"));
    }

    @Test
    void shouldReturnEmptyWhenMessageNotFound() {
        String protoContent = "syntax = \"proto3\";\nmessage Other { string id = 1; }";
        String schema = GrpcProtoMessageSchemaExtractor.extractMessageDefinition(protoContent, "Missing");
        Assertions.assertEquals("", schema);
    }
}
