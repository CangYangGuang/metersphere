package io.metersphere.api.grpc.exception;

import io.metersphere.sdk.exception.IResultCode;

public enum GrpcResultCode implements IResultCode {

    GRPC_ADDRESS_INVALID(104100, "grpc_address_invalid"),
    GRPC_PROTO_COMPILE_FAILED(104101, "grpc_proto_compile_failed"),
    GRPC_IMPORT_NOT_SUPPORTED(104102, "grpc_import_not_supported"),
    GRPC_SERVICE_NOT_FOUND(104103, "grpc_service_not_found"),
    GRPC_METHOD_NOT_FOUND(104104, "grpc_method_not_found"),
    GRPC_STREAM_NOT_SUPPORTED(104105, "grpc_stream_not_supported"),
    GRPC_JSON_CONVERT_FAILED(104106, "grpc_json_convert_failed"),
    GRPC_CONNECT_FAILED(104107, "grpc_connect_failed"),
    GRPC_TIMEOUT(104108, "grpc_timeout"),
    GRPC_PERMISSION_DENIED(104109, "grpc_permission_denied"),
    GRPC_UNAUTHENTICATED(104110, "grpc_unauthenticated"),
    GRPC_CALL_FAILED(104111, "grpc_call_failed"),
    GRPC_INVALID_ARGUMENT(104112, "grpc_invalid_argument"),
    GRPC_DEFINITION_NOT_FOUND(104113, "grpc_definition_not_found"),
    GRPC_CASE_CONFIG_REQUIRED(104114, "grpc_case_config_required"),
    GRPC_CASE_CONFIG_NOT_FOUND(104115, "grpc_case_config_not_found"),
    GRPC_MIXED_SCENARIO_NOT_SUPPORTED(104116, "grpc_mixed_scenario_not_supported"),
    GRPC_REFLECTION_FAILED(104117, "grpc_reflection_failed");

    private final int code;
    private final String message;

    GrpcResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return getTranslationMessage(this.message);
    }
}
