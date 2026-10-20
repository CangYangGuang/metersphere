package io.metersphere.api.grpc;

import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.dto.GrpcReflectionDescribeRequest;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.util.GrpcReflectionAddressGuard;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class GrpcReflectionAddressGuardTest {

    @AfterEach
    void tearDown() {
        GrpcReflectionAddressGuard.setAllowPrivateNetworksForTests(false);
    }

    @Test
    void shouldRejectLocalhost() {
        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcReflectionAddressGuard.validateServerAddress("localhost:50051"));
        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("localhost"));
    }

    @Test
    void shouldRejectLoopback127() {
        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcReflectionAddressGuard.validateServerAddress("127.0.0.1:50051"));
        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("loopback"));
    }

    @Test
    void shouldRejectMetadataServiceAddress() {
        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcReflectionAddressGuard.validateServerAddress("169.254.169.254:80"));
        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("metadata"));
    }

    @Test
    void shouldRejectInvalidServerAddressFormat() {
        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcReflectionAddressGuard.validateServerAddress("invalid-address"));
        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectMetadataItemCountLimit() {
        GrpcReflectionDescribeRequest request = new GrpcReflectionDescribeRequest();
        request.setServerAddress("8.8.8.8:50051");
        request.setMetadata(buildMetadataItems(GrpcReflectionAddressGuard.MAX_METADATA_ITEMS + 1));

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcReflectionAddressGuard.validateDescribeRequest(request));
        Assertions.assertEquals(GrpcResultCode.GRPC_INVALID_ARGUMENT, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("metadata item count"));
    }

    @Test
    void shouldRejectTimeoutAboveLimit() {
        GrpcReflectionDescribeRequest request = new GrpcReflectionDescribeRequest();
        request.setServerAddress("8.8.8.8:50051");
        request.setTimeout(GrpcReflectionAddressGuard.MAX_TIMEOUT_MS + 1);

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcReflectionAddressGuard.validateDescribeRequest(request));
        Assertions.assertEquals(GrpcResultCode.GRPC_CALL_FAILED, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("timeout"));
    }

    @Test
    void shouldRejectPrivateNetworkByDefault() {
        MSException exception = Assertions.assertThrows(MSException.class,
                () -> GrpcReflectionAddressGuard.validateServerAddress("192.168.1.10:50051"));
        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("private network"));
    }

    @Test
    void shouldAllowPrivateNetworkWhenConfigured() {
        GrpcReflectionAddressGuard.setAllowPrivateNetworksForTests(true);
        Assertions.assertDoesNotThrow(
                () -> GrpcReflectionAddressGuard.validateServerAddress("192.168.1.10:50051"));
    }

    private List<GrpcMetadataItemDTO> buildMetadataItems(int count) {
        List<GrpcMetadataItemDTO> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            GrpcMetadataItemDTO item = new GrpcMetadataItemDTO();
            item.setKey("key-" + index);
            item.setValue("value");
            items.add(item);
        }
        return items;
    }
}
