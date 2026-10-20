package io.metersphere.api.grpc.demo;

import com.google.protobuf.Descriptors;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class GrpcDynamicInvokeDemoTest {

    private final GrpcDynamicInvokeDemo demo = new GrpcDynamicInvokeDemo();

    @Test
    void shouldInvokeUnarySuccessfully() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            Descriptors.FileDescriptor fileDescriptor =
                    GrpcProtoCompiler.loadDescriptorSetFromClasspath("grpc/helloworld.desc");

            GrpcDynamicInvokeDemo.InvokeResult result = demo.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{\"name\":\"MeterSphere\"}",
                    5000
            );

            Assertions.assertTrue(result.success());
            Assertions.assertNotNull(result.responseJson());
            Assertions.assertTrue(result.responseJson().contains("Hello MeterSphere"));
        }
    }

    @Test
    void shouldFailWhenProtoSyntaxInvalid() throws Exception {
        Path tempDir = Files.createTempDirectory("grpc-proto-invalid-");
        Path protoFile = tempDir.resolve("invalid-syntax.proto");
        Files.copy(Path.of("src/test/proto/invalid-syntax.proto"), protoFile);
        Path outputDesc = tempDir.resolve("invalid.desc");

        GrpcProtoCompileException exception = Assertions.assertThrows(
                GrpcProtoCompileException.class,
                () -> GrpcProtoCompiler.compileProtoToDescriptorSet(protoFile, tempDir, outputDesc)
        );
        Assertions.assertFalse(exception.getMessage().isBlank());
    }

    @Test
    void shouldFailWhenRequestJsonInvalid() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            Descriptors.FileDescriptor fileDescriptor =
                    GrpcProtoCompiler.loadDescriptorSetFromClasspath("grpc/helloworld.desc");

            GrpcDynamicInvokeDemo.InvokeResult result = demo.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{invalid-json}",
                    5000
            );

            Assertions.assertFalse(result.success());
            Assertions.assertTrue(result.errorMessage().contains("Invalid request JSON"));
        }
    }

    @Test
    void shouldFailWhenServiceUnreachable() throws Exception {
        Descriptors.FileDescriptor fileDescriptor =
                GrpcProtoCompiler.loadDescriptorSetFromClasspath("grpc/helloworld.desc");
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", 1)
                .usePlaintext()
                .build();
        try {
            GrpcDynamicInvokeDemo.InvokeResult result = demo.invokeUnary(
                    channel,
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{\"name\":\"MeterSphere\"}",
                    2000
            );
            Assertions.assertFalse(result.success());
            Assertions.assertNotNull(result.errorMessage());
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void shouldFailWhenCallTimesOut() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer(2000L)) {
            server.start();
            Descriptors.FileDescriptor fileDescriptor =
                    GrpcProtoCompiler.loadDescriptorSetFromClasspath("grpc/helloworld.desc");

            GrpcDynamicInvokeDemo.InvokeResult result = demo.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "helloworld.Greeter",
                    "SayHello",
                    "{\"name\":\"MeterSphere\"}",
                    200
            );

            Assertions.assertFalse(result.success());
            Assertions.assertEquals(io.grpc.Status.Code.DEADLINE_EXCEEDED, result.statusCode());
        }
    }

    @Test
    void shouldFailWhenMethodNotFound() throws Exception {
        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            Descriptors.FileDescriptor fileDescriptor =
                    GrpcProtoCompiler.loadDescriptorSetFromClasspath("grpc/helloworld.desc");

            GrpcDynamicInvokeDemo.InvokeResult result = demo.invokeUnary(
                    server.getChannel(),
                    fileDescriptor,
                    "helloworld.Greeter",
                    "NotExists",
                    "{\"name\":\"MeterSphere\"}",
                    5000
            );

            Assertions.assertFalse(result.success());
            Assertions.assertTrue(result.errorMessage().contains("Method not found"));
        }
    }
}
