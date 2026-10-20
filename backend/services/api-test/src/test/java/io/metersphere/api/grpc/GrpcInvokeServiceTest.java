package io.metersphere.api.grpc;

import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.metersphere.api.grpc.dto.GrpcInvokeRequest;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.service.GrpcInvokeService;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.util.GrpcProtoCompiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

class GrpcInvokeServiceTest {

    private GrpcProtoService grpcProtoService;
    private GrpcInvokeService grpcInvokeService;

    @BeforeEach
    void setUp() {
        grpcProtoService = new GrpcProtoService();
        grpcInvokeService = new GrpcInvokeService(grpcProtoService);
    }

    @Test
    void shouldInvokeUnarySuccessfully() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            var fileDescriptor = GrpcProtoCompiler.buildFileDescriptor(
                    Base64.getDecoder().decode(loadDescriptorContent()));

            GrpcInvokeResponse response = grpcInvokeService.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{\"name\":\"MeterSphere\"}",
                    5000
            );

            Assertions.assertTrue(response.getSuccess());
            Assertions.assertNotNull(response.getResponseJson());
            Assertions.assertTrue(response.getResponseJson().contains("Hello MeterSphere"));
            Assertions.assertEquals(Status.Code.OK.name(), response.getGrpcStatus());
        }
    }

    @Test
    void shouldFailWhenRequestJsonInvalid() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            var fileDescriptor = GrpcProtoCompiler.buildFileDescriptor(
                    Base64.getDecoder().decode(loadDescriptorContent()));

            GrpcInvokeResponse response = grpcInvokeService.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{invalid-json}",
                    5000
            );

            Assertions.assertFalse(response.getSuccess());
            Assertions.assertEquals(GrpcResultCode.GRPC_JSON_CONVERT_FAILED.name(), response.getErrorCode());
        }
    }

    @Test
    void shouldFailWhenMethodNotFound() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            var fileDescriptor = GrpcProtoCompiler.buildFileDescriptor(
                    Base64.getDecoder().decode(loadDescriptorContent()));

            GrpcInvokeResponse response = grpcInvokeService.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "helloworld.Greeter",
                    "NotExists",
                    "{\"name\":\"MeterSphere\"}",
                    5000
            );

            Assertions.assertFalse(response.getSuccess());
            Assertions.assertEquals(GrpcResultCode.GRPC_METHOD_NOT_FOUND.name(), response.getErrorCode());
        }
    }

    @Test
    void shouldFailWhenServiceUnreachable() throws Exception {
        var fileDescriptor = GrpcProtoCompiler.buildFileDescriptor(
                Base64.getDecoder().decode(loadDescriptorContent()));
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", 1)
                .usePlaintext()
                .build();
        try {
            GrpcInvokeResponse response = grpcInvokeService.invokeUnary(
                    channel,
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{\"name\":\"MeterSphere\"}",
                    2000
            );
            Assertions.assertFalse(response.getSuccess());
            Assertions.assertEquals(GrpcResultCode.GRPC_CONNECT_FAILED.name(), response.getErrorCode());
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void shouldFailWhenCallTimesOut() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer(2000L)) {
            server.start();
            var fileDescriptor = GrpcProtoCompiler.buildFileDescriptor(
                    Base64.getDecoder().decode(loadDescriptorContent()));

            GrpcInvokeResponse response = grpcInvokeService.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{\"name\":\"MeterSphere\"}",
                    200
            );

            Assertions.assertFalse(response.getSuccess());
            Assertions.assertEquals(GrpcResultCode.GRPC_TIMEOUT.name(), response.getErrorCode());
            Assertions.assertEquals(Status.Code.DEADLINE_EXCEEDED.name(), response.getGrpcStatus());
        }
    }

    @Test
    void shouldPassMetadataToServer() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            var fileDescriptor = GrpcProtoCompiler.buildFileDescriptor(
                    Base64.getDecoder().decode(loadDescriptorContent()));

            Metadata metadata = new Metadata();
            metadata.put(HelloWorldInProcessServer.TEST_METADATA_KEY, "test-value");
            ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
            io.grpc.Channel channelWithMetadata = ClientInterceptors.intercept(server.getChannel(), interceptor);

            GrpcInvokeResponse response = grpcInvokeService.invokeUnary(
                    channelWithMetadata,
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{\"name\":\"MeterSphere\"}",
                    5000
            );

            Assertions.assertTrue(response.getSuccess());
            Assertions.assertEquals("test-value", server.getLastMetadataValue());
        }
    }

    @Test
    void shouldFailWhenRequestJsonTypeInvalid() throws Exception {
        String proto = """
                syntax = "proto3";
                package typetest;
                service TypeSvc {
                  rpc Call (Req) returns (Resp) {}
                }
                message Req { int32 age = 1; }
                message Resp { string message = 1; }
                """;
        byte[] descriptorBytes = grpcProtoService.compileProtoToDescriptorSet(proto, "typetest.proto");
        var fileDescriptor = grpcProtoService.buildFileDescriptor(descriptorBytes);

        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            GrpcInvokeResponse response = grpcInvokeService.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "typetest.TypeSvc",
                    "Call",
                    "{\"age\":\"not-a-number\"}",
                    5000
            );
            Assertions.assertFalse(response.getSuccess());
            Assertions.assertEquals(GrpcResultCode.GRPC_JSON_CONVERT_FAILED.name(), response.getErrorCode());
        }
    }

    @Test
    void shouldBuildMetadataViaInvokeRequest() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            String protoContent = Files.readString(
                    Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
            byte[] descriptorBytes = grpcProtoService.compileProtoToDescriptorSet(protoContent, "helloworld.proto");

            GrpcMetadataItemDTO metadataItem = new GrpcMetadataItemDTO();
            metadataItem.setKey("x-test-key");
            metadataItem.setValue("invoke-metadata");
            metadataItem.setEnabled(true);

            GrpcInvokeRequest request = new GrpcInvokeRequest();
            request.setServerAddress("inprocess:ignored");
            request.setDescriptorContent(Base64.getEncoder().encodeToString(descriptorBytes));
            request.setServiceName("helloworld.Greeter");
            request.setMethodName("SayHello");
            request.setRequestJson("{\"name\":\"MeterSphere\"}");
            request.setMetadata(List.of(metadataItem));
            request.setTimeout(5000);

            GrpcInvokeResponse response = grpcInvokeService.invokeUnary(
                    attachMetadataChannel(server.getChannel(), request),
                    grpcProtoService.buildFileDescriptor(descriptorBytes),
                    request.getServiceName(),
                    request.getMethodName(),
                    request.getRequestJson(),
                    request.getTimeout()
            );

            Assertions.assertTrue(response.getSuccess());
            Assertions.assertEquals("invoke-metadata", server.getLastMetadataValue());
        }
    }

    private io.grpc.Channel attachMetadataChannel(ManagedChannel channel, GrpcInvokeRequest request) {
        Metadata metadata = io.metersphere.api.grpc.util.GrpcMetadataBuilder.buildMetadata(request.getMetadata());
        if (metadata.keys().isEmpty()) {
            return channel;
        }
        return ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private String loadDescriptorContent() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        byte[] descriptorBytes = grpcProtoService.compileProtoToDescriptorSet(protoContent, "helloworld.proto");
        return Base64.getEncoder().encodeToString(descriptorBytes);
    }
}
