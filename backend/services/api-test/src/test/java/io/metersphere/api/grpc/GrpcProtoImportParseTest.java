package io.metersphere.api.grpc;

import io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;
import io.metersphere.api.grpc.dto.GrpcParseProtoRequest;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class GrpcProtoImportParseTest {

    private GrpcProtoService grpcProtoService;

    @BeforeEach
    void setUp() {
        grpcProtoService = new GrpcProtoService();
    }

    @Test
    void shouldKeepSingleProtoParseCompatible() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent(protoContent);
        request.setFileName("helloworld.proto");

        GrpcParseProtoResponse response = grpcProtoService.parseProto(request);

        Assertions.assertEquals("helloworld", response.getPackageName());
        Assertions.assertFalse(response.getServices().isEmpty());
        GrpcMethodInfoDTO method = response.getServices().get(0).getMethods().get(0);
        Assertions.assertNotNull(method.getRequestJsonTemplate());
        Assertions.assertTrue(method.getRequestJsonTemplate().contains("\"name\""));
    }

    @Test
    void shouldParseMultiProtoWithImportMessage() throws Exception {
        GrpcParseProtoRequest request = buildImportBundleRequest();

        GrpcParseProtoResponse response = grpcProtoService.parseProto(request);

        Assertions.assertEquals("api", response.getPackageName());
        Assertions.assertEquals("Greeter", response.getServices().get(0).getName());
        GrpcMethodInfoDTO method = response.getServices().get(0).getMethods().get(0);
        Assertions.assertEquals("types.CommonRequest", method.getInputType());
        Assertions.assertNotNull(method.getRequestJsonTemplate());
        Assertions.assertTrue(method.getRequestJsonTemplate().contains("\"name\""));
        Assertions.assertTrue(method.getRequestJsonTemplate().contains("\"status\""));
    }

    @Test
    void shouldGenerateTemplateWithImportedEnum() throws Exception {
        GrpcParseProtoResponse response = grpcProtoService.parseProto(buildImportBundleRequest());
        String template = response.getServices().get(0).getMethods().get(0).getRequestJsonTemplate();
        Assertions.assertTrue(template.contains("STATUS_UNKNOWN") || template.contains("STATUS_OK"));
    }

    @Test
    void shouldAlignFlatUploadedDependencyNameWithImportPath() {
        GrpcProtoFileDTO annotations = new GrpcProtoFileDTO();
        annotations.setFileName("annotations.proto");
        annotations.setContent("""
                syntax = "proto3";
                package google.api;
                import "google/api/http.proto";
                message AnnotationMarker {
                  HttpRule rule = 1;
                }
                """);

        GrpcProtoFileDTO http = new GrpcProtoFileDTO();
        http.setFileName("http.proto");
        http.setContent("""
                syntax = "proto3";
                package google.api;
                message Http {
                  repeated HttpRule rules = 1;
                }
                message HttpRule {
                  string get = 2;
                }
                """);

        GrpcProtoFileDTO main = new GrpcProtoFileDTO();
        main.setFileName("polar_control.proto");
        main.setMain(true);
        main.setContent("""
                syntax = "proto3";
                package Polar.v1;
                import "google/api/annotations.proto";
                service DeviceService {
                  rpc ListDevices (ListDevicesRequest) returns (ListDevicesResponse);
                }
                message ListDevicesRequest {
                  string type_filter = 1;
                }
                message ListDevicesResponse {
                  repeated string devices = 1;
                }
                """);

        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoFiles(List.of(annotations, http, main));
        request.setMainFileName("polar_control.proto");

        GrpcParseProtoResponse response = grpcProtoService.parseProto(request);

        Assertions.assertEquals("Polar.v1", response.getPackageName());
        Assertions.assertEquals("DeviceService", response.getServices().get(0).getName());
    }

    @Test
    void shouldFailWhenImportMissingInBundle() throws Exception {
        GrpcParseProtoRequest request = buildImportBundleRequest();
        request.setProtoFiles(List.of(request.getProtoFiles().get(2)));

        MSException exception = Assertions.assertThrows(MSException.class, () -> grpcProtoService.parseProto(request));
        Assertions.assertEquals(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("missing import proto file"));
    }

    @Test
    void shouldFailWhenMainFileNameNotFound() throws Exception {
        GrpcParseProtoRequest request = buildImportBundleRequest();
        request.setMainFileName("missing/main.proto");

        MSException exception = Assertions.assertThrows(MSException.class, () -> grpcProtoService.parseProto(request));
        Assertions.assertEquals(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getErrorCode());
        Assertions.assertTrue(exception.getMessage().contains("main proto file not found"));
    }

    @Test
    void shouldFailWhenLegacySingleProtoHasUnsupportedImport() throws Exception {
        String importProto = Files.readString(
                Path.of("src/test/proto/import_error.proto"), StandardCharsets.UTF_8);
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent(importProto);

        MSException exception = Assertions.assertThrows(MSException.class, () -> grpcProtoService.parseProto(request));
        Assertions.assertEquals(GrpcResultCode.GRPC_IMPORT_NOT_SUPPORTED, exception.getErrorCode());
    }

    private GrpcParseProtoRequest buildImportBundleRequest() throws Exception {
        GrpcProtoFileDTO status = new GrpcProtoFileDTO();
        status.setFileName("types/status.proto");
        status.setContent(Files.readString(
                Path.of("src/test/proto/import/types/status.proto"), StandardCharsets.UTF_8));

        GrpcProtoFileDTO common = new GrpcProtoFileDTO();
        common.setFileName("types/common.proto");
        common.setContent(Files.readString(
                Path.of("src/test/proto/import/types/common.proto"), StandardCharsets.UTF_8));

        GrpcProtoFileDTO greeter = new GrpcProtoFileDTO();
        greeter.setFileName("api/greeter.proto");
        greeter.setMain(true);
        greeter.setContent(Files.readString(
                Path.of("src/test/proto/import/api/greeter.proto"), StandardCharsets.UTF_8));

        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoFiles(List.of(status, common, greeter));
        request.setMainFileName("api/greeter.proto");
        return request;
    }
}
