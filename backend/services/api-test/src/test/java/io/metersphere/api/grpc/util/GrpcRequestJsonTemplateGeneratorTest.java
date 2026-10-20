package io.metersphere.api.grpc.util;

import com.google.protobuf.Descriptors;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.sdk.util.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class GrpcRequestJsonTemplateGeneratorTest {

    private GrpcProtoService grpcProtoService;

    @BeforeEach
    void setUp() {
        grpcProtoService = new GrpcProtoService();
    }

    @Test
    void shouldGenerateHelloRequestTemplate() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        Descriptors.Descriptor helloRequest = resolveMessageDescriptor(protoContent, "helloworld.HelloRequest");

        String template = GrpcRequestJsonTemplateGenerator.generate(helloRequest);
        Map<String, Object> parsed = JSON.parseMap(template);

        Assertions.assertEquals("string", parsed.get("name"));
        Assertions.assertTrue(template.contains("\"name\""));
    }

    @Test
    void shouldGenerateComplexMessageTemplate() throws Exception {
        String protoContent = """
                syntax = "proto3";
                package tmpl;
                enum Status { STATUS_UNKNOWN = 0; STATUS_OK = 1; }
                message Nested { string note = 1; bool active = 2; }
                message ComplexRequest {
                  string name = 1;
                  int32 age = 2;
                  bool enabled = 3;
                  repeated string tags = 4;
                  map<string, int32> scores = 5;
                  Nested nested = 6;
                  Status status = 7;
                  bytes payload = 8;
                  double ratio = 9;
                }
                service Svc { rpc Call (ComplexRequest) returns (Nested) {} }
                """;
        Descriptors.Descriptor descriptor = resolveMessageDescriptor(protoContent, "tmpl.ComplexRequest");
        Map<String, Object> template = GrpcRequestJsonTemplateGenerator.generateObject(descriptor, 10);

        Assertions.assertEquals("string", template.get("name"));
        Assertions.assertEquals(0, template.get("age"));
        Assertions.assertEquals(true, template.get("enabled"));
        Assertions.assertEquals(List.of("string"), template.get("tags"));
        Assertions.assertInstanceOf(Map.class, template.get("scores"));
        Map<?, ?> scores = (Map<?, ?>) template.get("scores");
        Assertions.assertTrue(scores.containsKey("key"));
        Assertions.assertEquals(0, scores.get("key"));

        Map<?, ?> nested = (Map<?, ?>) template.get("nested");
        Assertions.assertEquals("string", nested.get("note"));
        Assertions.assertEquals(true, nested.get("active"));
        Assertions.assertEquals("STATUS_UNKNOWN", template.get("status"));
        Assertions.assertEquals("base64", template.get("payload"));
        Assertions.assertEquals(0, template.get("ratio"));
    }

    @Test
    void shouldLimitRecursiveDepthForSelfReference() throws Exception {
        String protoContent = """
                syntax = "proto3";
                package loop;
                message Node { string name = 1; Node child = 2; }
                service Svc { rpc Call (Node) returns (Node) {} }
                """;
        Descriptors.Descriptor descriptor = resolveMessageDescriptor(protoContent, "loop.Node");
        Map<String, Object> template = GrpcRequestJsonTemplateGenerator.generateObject(descriptor, 2);

        Assertions.assertEquals("string", template.get("name"));
        Map<?, ?> child = (Map<?, ?>) template.get("child");
        Assertions.assertNotNull(child);
        Assertions.assertTrue(child.isEmpty());
    }

    private Descriptors.Descriptor resolveMessageDescriptor(String protoContent, String fullName) {
        byte[] descriptorBytes = grpcProtoService.compileProtoToDescriptorSet(protoContent, "template.proto");
        Descriptors.FileDescriptor fileDescriptor = grpcProtoService.buildFileDescriptor(descriptorBytes);
        Descriptors.Descriptor descriptor = fileDescriptor.findMessageTypeByName(fullName.substring(fullName.lastIndexOf('.') + 1));
        if (descriptor == null || !fullName.equals(descriptor.getFullName())) {
            descriptor = findMessageByFullName(fileDescriptor, fullName);
        }
        Assertions.assertNotNull(descriptor, "message not found: " + fullName);
        return descriptor;
    }

    private Descriptors.Descriptor findMessageByFullName(Descriptors.FileDescriptor fileDescriptor, String fullName) {
        for (Descriptors.Descriptor message : fileDescriptor.getMessageTypes()) {
            if (fullName.equals(message.getFullName())) {
                return message;
            }
        }
        return null;
    }
}
