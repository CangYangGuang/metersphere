package io.metersphere.api.grpc;

import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactAnalyzer;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.util.GrpcProtoSchemaSnapshotBuilder;
import io.metersphere.api.grpc.util.GrpcProtoSchemaSnapshotBuilder.GrpcProtoSchemaSnapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GrpcProtoChangeImpactAnalyzerTest {

    private static final String BASE_PROTO = """
            syntax = "proto3";
            package helloworld;
            service Greeter {
              rpc SayHello (HelloRequest) returns (HelloReply) {}
            }
            message HelloRequest {
              string name = 1;
            }
            message HelloReply {
              string message = 1;
            }
            """;

    private GrpcProtoChangeImpactAnalyzer analyzer;
    private GrpcProtoSchemaSnapshotBuilder snapshotBuilder;

    @BeforeEach
    void setUp() {
        analyzer = new GrpcProtoChangeImpactAnalyzer();
        GrpcProtoService grpcProtoService = new GrpcProtoService();
        snapshotBuilder = new GrpcProtoSchemaSnapshotBuilder();
        ReflectionTestUtils.setField(snapshotBuilder, "grpcProtoService", grpcProtoService);
    }

    @Test
    void shouldDetectMethodRemovalAsBreaking() {
        String newProto = BASE_PROTO.replace("rpc SayHello (HelloRequest) returns (HelloReply) {}", "");
        GrpcProtoChangeImpactDTO impact = analyze(BASE_PROTO, newProto);
        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_BREAKING, impact.getImpactLevel());
        Assertions.assertTrue(impact.getChangedMethods().stream().anyMatch(item -> item.contains("removed")));
    }

    @Test
    void shouldDetectFieldRemovalAsBreaking() {
        String newProto = BASE_PROTO.replace("string name = 1;", "");
        GrpcProtoChangeImpactDTO impact = analyze(BASE_PROTO, newProto);
        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_BREAKING, impact.getImpactLevel());
        Assertions.assertTrue(impact.getChangedMessages().stream()
                .anyMatch(item -> "FIELD_REMOVED".equals(item.getChangeType())));
    }

    @Test
    void shouldDetectFieldTypeChangeAsBreaking() {
        String newProto = BASE_PROTO.replace("string name = 1;", "int32 name = 1;");
        GrpcProtoChangeImpactDTO impact = analyze(BASE_PROTO, newProto);
        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_BREAKING, impact.getImpactLevel());
        Assertions.assertTrue(impact.getChangedMessages().stream()
                .anyMatch(item -> "FIELD_TYPE_CHANGED".equals(item.getChangeType())));
    }

    @Test
    void shouldDetectFieldAdditionAsWarning() {
        String newProto = BASE_PROTO.replace("string name = 1;", "string name = 1;\n  string extra = 2;");
        GrpcProtoChangeImpactDTO impact = analyze(BASE_PROTO, newProto);
        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_WARNING, impact.getImpactLevel());
        Assertions.assertTrue(impact.getChangedMessages().stream()
                .anyMatch(item -> "FIELD_ADDED".equals(item.getChangeType())));
    }

    @Test
    void shouldReturnNoneWhenStructureUnchanged() {
        GrpcProtoChangeImpactDTO impact = analyze(BASE_PROTO, BASE_PROTO);
        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_NONE, impact.getImpactLevel());
    }

    private GrpcProtoChangeImpactDTO analyze(String oldProto, String newProto) {
        GrpcProtoSchemaSnapshot oldSnapshot = snapshot(oldProto);
        GrpcProtoSchemaSnapshot newSnapshot = snapshot(newProto);
        return analyzer.analyze(oldSnapshot, newSnapshot, "helloworld.Greeter", "SayHello", null, null);
    }

    private GrpcProtoSchemaSnapshot snapshot(String content) {
        return snapshotBuilder.buildFromContent(content, "helloworld.proto");
    }
}
