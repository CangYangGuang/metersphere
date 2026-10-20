package io.metersphere.api.service.debug;

import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.grpc.dto.GrpcCaseDebugRequestDTO;
import io.metersphere.sdk.util.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

class ApiDebugGrpcRequestSupportTest {

    @Test
    void shouldSerializeGrpcDebugRequestWithoutPolymorphicName() {
        Map<String, Object> request = buildGrpcDebugRequestMap();

        byte[] bytes = ApiDebugGrpcRequestSupport.serializeRequest("GRPC", request);
        String json = new String(bytes, StandardCharsets.UTF_8);

        Assertions.assertFalse(json.contains("polymorphicName"));
        Assertions.assertTrue(json.contains("serverAddress"));
        Assertions.assertTrue(json.contains("grpcCaseConfig"));
        Assertions.assertTrue(json.contains("hello.HelloService"));
    }

    @Test
    void shouldDeserializeGrpcDebugRequestForReopen() {
        byte[] bytes = ApiDebugGrpcRequestSupport.serializeRequest("GRPC", buildGrpcDebugRequestMap());

        Object request = ApiDebugGrpcRequestSupport.deserializeRequest("GRPC", bytes);

        Assertions.assertInstanceOf(GrpcCaseDebugRequestDTO.class, request);
        GrpcCaseDebugRequestDTO dto = (GrpcCaseDebugRequestDTO) request;
        Assertions.assertEquals("grpcb.in:9000", dto.getServerAddress());
        Assertions.assertEquals("hello.HelloService", dto.getGrpcCaseConfig().getServiceName());
        Assertions.assertEquals("SayHello", dto.getGrpcCaseConfig().getMethodName());
        Assertions.assertEquals("{\"greeting\":\"save regression\"}", dto.getGrpcCaseConfig().getRequestJson());
    }

    private Map<String, Object> buildGrpcDebugRequestMap() {
        ApiGrpcCaseConfigAddDTO config = new ApiGrpcCaseConfigAddDTO();
        config.setServiceName("hello.HelloService");
        config.setMethodName("SayHello");
        config.setDescriptorContent("dGVzdA==");
        config.setRequestJson("{\"greeting\":\"save regression\"}");
        config.setTimeout(60000);

        GrpcCaseDebugRequestDTO dto = new GrpcCaseDebugRequestDTO();
        dto.setServerAddress("grpcb.in:9000");
        dto.setGrpcCaseConfig(config);

        return JSON.parseObject(JSON.toJSONString(dto), LinkedHashMap.class);
    }
}
