package io.metersphere.api.grpc;

import com.google.protobuf.Descriptors;
import io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcReflectionDescribeRequest;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.service.GrpcReflectionService;
import io.metersphere.api.grpc.util.GrpcReflectionDescriptorConverter;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class GrpcReflectionDescriptorConverterTest {

    private GrpcProtoService grpcProtoService;

    @BeforeEach
    void setUp() {
        grpcProtoService = new GrpcProtoService();
    }

    @Test
    void shouldConvertDescriptorSetToParseResponseWithRequestJsonTemplate() throws Exception {
        String protoContent = Files.readString(
                Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        byte[] descriptorBytes = grpcProtoService.compileProtoToDescriptorSet(protoContent, "helloworld.proto");
        Descriptors.FileDescriptor fileDescriptor = grpcProtoService.buildFileDescriptor(descriptorBytes);

        byte[] converted = GrpcReflectionDescriptorConverter.toDescriptorSetBytes(List.of(fileDescriptor));
        GrpcParseProtoResponse response = grpcProtoService.parseAllServicesFromDescriptorSet(converted);

        Assertions.assertEquals("helloworld", response.getPackageName());
        Assertions.assertFalse(response.getServices().isEmpty());
        GrpcMethodInfoDTO method = response.getServices().get(0).getMethods().get(0);
        Assertions.assertEquals("SayHello", method.getName());
        Assertions.assertNotNull(method.getRequestJsonTemplate());
        Assertions.assertTrue(method.getRequestJsonTemplate().contains("\"name\""));
    }
}
