package io.metersphere.api.grpc;

import io.metersphere.api.grpc.controller.GrpcDebugController;
import io.metersphere.api.grpc.dto.GrpcInvokeRequest;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.api.grpc.dto.GrpcParseProtoRequest;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcReflectionDescribeRequest;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.service.GrpcInvokeService;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.service.GrpcReflectionService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.controller.handler.RestControllerExceptionHandler;
import io.metersphere.system.controller.handler.ResultResponseBodyAdvice;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GrpcDebugControllerTest {

    private static final String PARSE_URL = "/api/grpc/debug/parse";
    private static final String SEND_URL = "/api/grpc/debug/send";
    private static final String REFLECTION_URL = "/api/grpc/debug/reflection/describe";

    @Mock
    private GrpcProtoService grpcProtoService;
    @Mock
    private GrpcInvokeService grpcInvokeService;
    @Mock
    private GrpcReflectionService grpcReflectionService;

    private GrpcDebugController grpcDebugController;

    private MockMvc mockMvc;
    private GrpcDebugController realController;
    private GrpcProtoService realProtoService;
    private GrpcInvokeService realInvokeService;

    @BeforeEach
    void setUp() {
        grpcDebugController = new GrpcDebugController();
        ReflectionTestUtils.setField(grpcDebugController, "grpcProtoService", grpcProtoService);
        ReflectionTestUtils.setField(grpcDebugController, "grpcInvokeService", grpcInvokeService);
        ReflectionTestUtils.setField(grpcDebugController, "grpcReflectionService", grpcReflectionService);
        mockMvc = MockMvcBuilders.standaloneSetup(grpcDebugController)
                .setControllerAdvice(new ResultResponseBodyAdvice(), new RestControllerExceptionHandler())
                .build();
        realProtoService = new GrpcProtoService();
        realInvokeService = new GrpcInvokeService(realProtoService);
        realController = new GrpcDebugController();
        ReflectionTestUtils.setField(realController, "grpcProtoService", realProtoService);
        ReflectionTestUtils.setField(realController, "grpcInvokeService", realInvokeService);
        ReflectionTestUtils.setField(realController, "grpcReflectionService",
                new GrpcReflectionService(realProtoService));
    }

    @Test
    void shouldDescribeViaReflectionController() throws Exception {
        try (ReflectionHelloWorldInProcessServer server = new ReflectionHelloWorldInProcessServer()) {
            server.start();
            GrpcReflectionDescribeRequest request = new GrpcReflectionDescribeRequest();
            request.setServerAddress("127.0.0.1:65530");
            request.setTimeout(5000);

            GrpcReflectionService realReflectionService = new GrpcReflectionService(realProtoService);
            GrpcParseProtoResponse response = realReflectionService.describeOverChannel(server.getChannel(), 5000);

            Assertions.assertFalse(response.getServices().isEmpty());
            Assertions.assertEquals("Greeter", response.getServices().get(0).getName());
        }
    }

    @Test
    void shouldReturnReflectionResultViaHttp() throws Exception {
        GrpcParseProtoResponse parseResponse = new GrpcParseProtoResponse();
        parseResponse.setPackageName("helloworld");
        when(grpcReflectionService.describe(any())).thenReturn(parseResponse);

        GrpcReflectionDescribeRequest request = new GrpcReflectionDescribeRequest();
        request.setServerAddress("127.0.0.1:50051");
        String body = JSON.toJSONString(request);
        String content = mockMvc.perform(post(REFLECTION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertTrue(content.contains("helloworld"));
    }

    @Test
    void shouldParseViaController() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent(protoContent);
        request.setFileName("helloworld.proto");

        GrpcParseProtoResponse response = realController.parse(request);

        Assertions.assertEquals("helloworld", response.getPackageName());
        Assertions.assertFalse(response.getServices().isEmpty());
        Assertions.assertEquals("Greeter", response.getServices().get(0).getName());
        Assertions.assertEquals("SayHello", response.getServices().get(0).getMethods().get(0).getName());
    }

    @Test
    void shouldReturnParseResultViaHttp() throws Exception {
        GrpcParseProtoResponse parseResponse = new GrpcParseProtoResponse();
        parseResponse.setPackageName("helloworld");
        when(grpcProtoService.parseProto(any())).thenReturn(parseResponse);

        String body = JSON.toJSONString(new GrpcParseProtoRequest());
        String content = mockMvc.perform(post(PARSE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertTrue(content.contains("helloworld"));
    }

    @Test
    void shouldReturnErrorWhenProtoInvalid() {
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent("syntax = \"proto3\"\npackage broken\nmessage X { string name = 1");

        MSException exception = Assertions.assertThrows(
                MSException.class,
                () -> realController.parse(request)
        );
        Assertions.assertEquals(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getErrorCode());
    }

    @Test
    void shouldPropagateProtoCompileErrorFromService() {
        when(grpcProtoService.parseProto(any())).thenThrow(new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "protoc failed"));

        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent("invalid");

        MSException exception = Assertions.assertThrows(
                MSException.class,
                () -> grpcDebugController.parse(request)
        );
        Assertions.assertEquals(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getErrorCode());
    }

    @Test
    void shouldSendViaControllerWithMockedService() {
        GrpcInvokeResponse invokeResponse = new GrpcInvokeResponse();
        invokeResponse.setSuccess(true);
        invokeResponse.setResponseJson("{\"message\":\"Hello MeterSphere\"}");
        when(grpcInvokeService.invoke(any())).thenReturn(invokeResponse);

        GrpcInvokeRequest request = new GrpcInvokeRequest();
        request.setServerAddress("127.0.0.1:50051");
        request.setServiceName("helloworld.Greeter");
        request.setMethodName("SayHello");
        request.setRequestJson("{\"name\":\"MeterSphere\"}");

        GrpcInvokeResponse response = grpcDebugController.send(request);

        Assertions.assertTrue(response.getSuccess());
        Assertions.assertTrue(response.getResponseJson().contains("Hello MeterSphere"));
    }

    @Test
    void shouldReturnMethodNotFoundViaSend() throws Exception {
        GrpcInvokeResponse invokeResponse = new GrpcInvokeResponse();
        invokeResponse.setSuccess(false);
        invokeResponse.setErrorCode(GrpcResultCode.GRPC_METHOD_NOT_FOUND.name());
        when(grpcInvokeService.invoke(any())).thenReturn(invokeResponse);

        String body = JSON.toJSONString(buildSendRequest("NotExists"));
        String content = mockMvc.perform(post(SEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertTrue(content.contains(GrpcResultCode.GRPC_METHOD_NOT_FOUND.name()));
    }

    @Test
    void shouldInvokeUnaryThroughRealServiceForMethodNotFound() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        byte[] descriptorBytes = realProtoService.compileProtoToDescriptorSet(protoContent, "helloworld.proto");

        try (HelloWorldInProcessServer server = new HelloWorldInProcessServer()) {
            server.start();
            GrpcInvokeResponse response = realInvokeService.invokeUnary(
                    server.getChannel(),
                    realProtoService.buildFileDescriptor(descriptorBytes),
                    "helloworld.Greeter",
                    "NotExists",
                    "{\"name\":\"MeterSphere\"}",
                    2000
            );

            Assertions.assertFalse(response.getSuccess());
            Assertions.assertEquals(GrpcResultCode.GRPC_METHOD_NOT_FOUND.name(), response.getErrorCode());
        }
    }

    private GrpcInvokeRequest buildSendRequest(String methodName) {
        GrpcInvokeRequest request = new GrpcInvokeRequest();
        request.setServerAddress("127.0.0.1:50051");
        request.setServiceName("helloworld.Greeter");
        request.setMethodName(methodName);
        request.setRequestJson("{\"name\":\"MeterSphere\"}");
        request.setTimeout(5000);
        return request;
    }
}
