package io.metersphere.api.grpc;

import io.metersphere.api.grpc.dto.GrpcParseProtoRequest;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;
import io.metersphere.api.grpc.dto.GrpcServiceInfoDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class GrpcProtoServiceTest {

    private GrpcProtoService grpcProtoService;

    @BeforeEach
    void setUp() {
        grpcProtoService = new GrpcProtoService();
    }

    @Test
    void shouldParseHelloWorldProtoSuccessfully() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent(protoContent);
        request.setFileName("helloworld.proto");

        GrpcParseProtoResponse response = grpcProtoService.parseProto(request);

        Assertions.assertEquals("helloworld", response.getPackageName());
        Assertions.assertNotNull(response.getDescriptorContent());
        Assertions.assertFalse(response.getServices().isEmpty());
        GrpcServiceInfoDTO service = response.getServices().get(0);
        Assertions.assertEquals("Greeter", service.getName());
        Assertions.assertEquals("helloworld.Greeter", service.getFullName());
        Assertions.assertFalse(service.getMethods().isEmpty());
        GrpcMethodInfoDTO method = service.getMethods().get(0);
        Assertions.assertEquals("SayHello", method.getName());
        Assertions.assertEquals("UNARY", method.getMethodType());
        Assertions.assertTrue(method.getInvokable());
        Assertions.assertEquals("helloworld.HelloRequest", method.getInputType());
        Assertions.assertEquals("helloworld.HelloReply", method.getOutputType());
        Assertions.assertNotNull(method.getRequestJsonTemplate());
        Assertions.assertTrue(method.getRequestJsonTemplate().contains("\"name\""));
        Assertions.assertTrue(method.getRequestJsonTemplate().contains("string"));
    }

    @Test
    void shouldMarkStreamingMethodAsNotInvokable() {
        String streamingProto = """
                syntax = "proto3";
                package streamtest;
                service StreamSvc {
                  rpc Unary (Req) returns (Resp) {}
                  rpc ServerStream (Req) returns (stream Resp) {}
                }
                message Req { string name = 1; }
                message Resp { string message = 1; }
                """;
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent(streamingProto);

        GrpcParseProtoResponse response = grpcProtoService.parseProto(request);

        GrpcMethodInfoDTO unary = response.getServices().get(0).getMethods().stream()
                .filter(method -> "Unary".equals(method.getName()))
                .findFirst()
                .orElseThrow();
        GrpcMethodInfoDTO serverStream = response.getServices().get(0).getMethods().stream()
                .filter(method -> "ServerStream".equals(method.getName()))
                .findFirst()
                .orElseThrow();
        Assertions.assertTrue(unary.getInvokable());
        Assertions.assertEquals("UNARY", unary.getMethodType());
        Assertions.assertFalse(serverStream.getInvokable());
        Assertions.assertEquals("SERVER_STREAMING", serverStream.getMethodType());
        Assertions.assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    void shouldFailWhenProtoSyntaxInvalid() throws Exception {
        Path tempDir = Files.createTempDirectory("grpc-proto-invalid-");
        Path protoFile = tempDir.resolve("invalid-syntax.proto");
        Files.copy(Path.of("src/test/proto/invalid-syntax.proto"), protoFile);

        MSException exception = Assertions.assertThrows(
                MSException.class,
                () -> grpcProtoService.compileProtoToDescriptorSet(
                        Files.readString(protoFile, StandardCharsets.UTF_8), "invalid-syntax.proto")
        );
        Assertions.assertEquals(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getErrorCode());
        Assertions.assertFalse(exception.getMessage().isBlank());
    }

    @Test
    void shouldFailWhenImportIsNotSupported() throws Exception {
        String importProto = Files.readString(
                Path.of("src/test/proto/import_error.proto"), StandardCharsets.UTF_8);
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent(importProto);

        MSException exception = Assertions.assertThrows(
                MSException.class,
                () -> grpcProtoService.parseProto(request)
        );
        Assertions.assertEquals(GrpcResultCode.GRPC_IMPORT_NOT_SUPPORTED, exception.getErrorCode());
    }
}
