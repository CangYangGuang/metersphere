package io.metersphere.api.grpc.util;

import io.grpc.Status;
import io.metersphere.api.grpc.exception.GrpcResultCode;

public final class GrpcStatusMapper {

    private GrpcStatusMapper() {
    }

    public static GrpcResultCode mapStatusCode(Status.Code code) {
        if (code == null) {
            return GrpcResultCode.GRPC_CALL_FAILED;
        }
        return switch (code) {
            case OK -> GrpcResultCode.GRPC_CALL_FAILED;
            case UNAVAILABLE -> GrpcResultCode.GRPC_CONNECT_FAILED;
            case DEADLINE_EXCEEDED -> GrpcResultCode.GRPC_TIMEOUT;
            case PERMISSION_DENIED -> GrpcResultCode.GRPC_PERMISSION_DENIED;
            case UNAUTHENTICATED -> GrpcResultCode.GRPC_UNAUTHENTICATED;
            case INVALID_ARGUMENT -> GrpcResultCode.GRPC_INVALID_ARGUMENT;
            default -> GrpcResultCode.GRPC_CALL_FAILED;
        };
    }
}
