package io.metersphere.api.grpc.exception;

public class GrpcProtoCompileException extends Exception {

    public GrpcProtoCompileException(String message) {
        super(message);
    }

    public GrpcProtoCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
