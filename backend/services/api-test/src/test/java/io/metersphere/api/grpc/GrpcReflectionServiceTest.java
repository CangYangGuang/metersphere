package io.metersphere.api.grpc;

import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcReflectionDescribeRequest;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.service.GrpcReflectionService;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrpcReflectionServiceTest {

    private GrpcReflectionService reflectionService;

    @BeforeEach
    void setUp() {
        reflectionService = new GrpcReflectionService(new GrpcProtoService());
    }

    @Test
    void shouldDiscoverServicesViaReflection() throws Exception {
        try (ReflectionHelloWorldInProcessServer server = new ReflectionHelloWorldInProcessServer()) {
            server.start();

            GrpcParseProtoResponse response = reflectionService.describeOverChannel(server.getChannel(), 5000);

            Assertions.assertEquals("helloworld", response.getPackageName());
            Assertions.assertFalse(response.getServices().isEmpty());
            Assertions.assertEquals("Greeter", response.getServices().get(0).getName());
            Assertions.assertEquals("SayHello", response.getServices().get(0).getMethods().get(0).getName());
            Assertions.assertNotNull(response.getDescriptorContent());
        }
    }

    @Test
    void shouldReturnEmptyServicesWhenReflectionHasNoServices() throws Exception {
        EmptyReflectionInProcessServer server = new EmptyReflectionInProcessServer();
        try {
            GrpcParseProtoResponse response = reflectionService.describeOverChannel(server.start(), 5000);

            Assertions.assertTrue(response.getServices().isEmpty());
            Assertions.assertFalse(response.getWarnings().isEmpty());
        } finally {
            server.close();
        }
    }

    @Test
    void shouldFailWhenServerAddressInvalid() {
        GrpcReflectionDescribeRequest request = new GrpcReflectionDescribeRequest();
        request.setServerAddress("invalid-address");

        MSException exception = Assertions.assertThrows(MSException.class, () -> reflectionService.describe(request));
        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldFailWhenReflectionTargetIsLoopback() {
        GrpcReflectionDescribeRequest request = new GrpcReflectionDescribeRequest();
        request.setServerAddress("127.0.0.1:1");
        request.setTimeout(2000);

        MSException exception = Assertions.assertThrows(MSException.class, () -> reflectionService.describe(request));
        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("loopback"));
    }
}
