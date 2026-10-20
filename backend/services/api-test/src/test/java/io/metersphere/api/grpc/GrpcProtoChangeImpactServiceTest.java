package io.metersphere.api.grpc;

import io.metersphere.api.domain.ApiGrpcCaseConfig;
import io.metersphere.api.domain.ApiGrpcDefinition;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.grpc.dto.GrpcParseProtoRequest;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactAnalyzer;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactService;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.service.GrpcProtoVersionService;
import io.metersphere.api.grpc.util.GrpcProtoSchemaSnapshotBuilder;
import io.metersphere.api.mapper.ApiGrpcCaseConfigMapper;
import io.metersphere.api.mapper.ApiGrpcDefinitionMapper;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcProtoChangeImpactServiceTest {

    private static final String OLD_PROTO_ID = "proto-old";
    private static final String NEW_PROTO_ID = "proto-new";

    @Mock
    private ApiGrpcProtoMapper apiGrpcProtoMapper;
    @Mock
    private ApiGrpcDefinitionMapper apiGrpcDefinitionMapper;
    @Mock
    private ApiGrpcCaseConfigMapper apiGrpcCaseConfigMapper;
    @Mock
    private GrpcProtoVersionService grpcProtoVersionService;

    private GrpcProtoChangeImpactService impactService;

    @BeforeEach
    void setUp() {
        impactService = new GrpcProtoChangeImpactService();
        GrpcProtoService grpcProtoService = new GrpcProtoService();
        GrpcProtoSchemaSnapshotBuilder snapshotBuilder = new GrpcProtoSchemaSnapshotBuilder();
        ReflectionTestUtils.setField(snapshotBuilder, "grpcProtoService", grpcProtoService);
        ReflectionTestUtils.setField(impactService, "apiGrpcProtoMapper", apiGrpcProtoMapper);
        ReflectionTestUtils.setField(impactService, "apiGrpcDefinitionMapper", apiGrpcDefinitionMapper);
        ReflectionTestUtils.setField(impactService, "apiGrpcCaseConfigMapper", apiGrpcCaseConfigMapper);
        ReflectionTestUtils.setField(impactService, "schemaSnapshotBuilder", snapshotBuilder);
        ReflectionTestUtils.setField(impactService, "impactAnalyzer", new GrpcProtoChangeImpactAnalyzer());
        ReflectionTestUtils.setField(impactService, "grpcProtoVersionService", grpcProtoVersionService);
    }

    @Test
    void shouldAnalyzeDefinitionImpactAgainstPreviousVersion() throws Exception {
        String baseProto = readHelloWorldProto();
        String changedProto = baseProto.replace("string name = 1;", "int32 name = 1;");

        ApiGrpcProto oldProto = buildProto(OLD_PROTO_ID, baseProto);
        ApiGrpcProto newProto = buildProto(NEW_PROTO_ID, changedProto);
        ApiGrpcDefinition definition = buildDefinition(newProto.getId());

        when(grpcProtoVersionService.listVersionHistory(newProto)).thenReturn(List.of(
                version(OLD_PROTO_ID, 1),
                version(NEW_PROTO_ID, 2)));
        when(apiGrpcProtoMapper.selectByPrimaryKey(OLD_PROTO_ID)).thenReturn(oldProto);

        GrpcProtoChangeImpactDTO impact = impactService.analyzeForDefinition(definition, newProto);

        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_BREAKING, impact.getImpactLevel());
        Assertions.assertFalse(impact.getChangedMessages().isEmpty());
    }

    @Test
    void shouldMarkCaseUsingOldProtoAsInfoWhenStructureUnchanged() throws Exception {
        String baseProto = readHelloWorldProto();
        ApiGrpcProto oldProto = buildProto(OLD_PROTO_ID, baseProto);
        ApiGrpcProto newProto = buildProto(NEW_PROTO_ID, baseProto + "\n// version bump");

        ApiGrpcCaseConfig caseConfig = new ApiGrpcCaseConfig();
        caseConfig.setApiCaseId("case-1");
        caseConfig.setProtoId(OLD_PROTO_ID);
        caseConfig.setServiceName("helloworld.Greeter");
        caseConfig.setMethodName("SayHello");

        when(apiGrpcProtoMapper.selectByPrimaryKey(OLD_PROTO_ID)).thenReturn(oldProto);
        when(apiGrpcProtoMapper.selectByPrimaryKey(NEW_PROTO_ID)).thenReturn(newProto);

        GrpcProtoChangeImpactDTO impact = impactService.analyzeForCase(caseConfig, NEW_PROTO_ID);

        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_INFO, impact.getImpactLevel());
        Assertions.assertEquals(1, impact.getAffectedCases().size());
        Assertions.assertEquals("case-1", impact.getAffectedCases().getFirst().getApiCaseId());
    }

    @Test
    void shouldKeepDefinitionDetailAvailableWhenPreviousProtoCannotBeParsed() throws Exception {
        ApiGrpcProto oldProto = new ApiGrpcProto();
        oldProto.setId(OLD_PROTO_ID);
        oldProto.setProjectId("project-1");
        oldProto.setFileName("helloworld.proto");
        oldProto.setContent("syntax = \"proto3\"; import \"missing.proto\";");
        ApiGrpcProto newProto = buildProto(NEW_PROTO_ID, readHelloWorldProto());
        ApiGrpcDefinition definition = buildDefinition(newProto.getId());

        when(grpcProtoVersionService.listVersionHistory(newProto)).thenReturn(List.of(
                version(OLD_PROTO_ID, 1),
                version(NEW_PROTO_ID, 2)));
        when(apiGrpcProtoMapper.selectByPrimaryKey(OLD_PROTO_ID)).thenReturn(oldProto);

        GrpcProtoChangeImpactDTO impact = impactService.analyzeForDefinition(definition, newProto);

        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_INFO, impact.getImpactLevel());
        Assertions.assertTrue(impact.getImpactSummary().contains("unavailable"));
    }

    private ApiGrpcProto buildProto(String id, String content) {
        GrpcProtoService grpcProtoService = new GrpcProtoService();
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent(content);
        request.setFileName("helloworld.proto");
        GrpcParseProtoResponse response = grpcProtoService.parseProto(request);
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId(id);
        proto.setProjectId("project-1");
        proto.setFileName("helloworld.proto");
        proto.setContent(content);
        proto.setDescriptorContent(response.getDescriptorContent());
        return proto;
    }

    private ApiGrpcDefinition buildDefinition(String protoId) {
        ApiGrpcDefinition definition = new ApiGrpcDefinition();
        definition.setApiDefinitionId("api-def-1");
        definition.setProtoId(protoId);
        definition.setServiceName("helloworld.Greeter");
        definition.setMethodName("SayHello");
        definition.setRequestType("helloworld.HelloRequest");
        definition.setResponseType("helloworld.HelloReply");
        return definition;
    }

    private GrpcProtoVersionDTO version(String protoId, int versionNo) {
        GrpcProtoVersionDTO dto = new GrpcProtoVersionDTO();
        dto.setProtoId(protoId);
        dto.setVersionNo(versionNo);
        dto.setVersionLabel("v" + versionNo);
        return dto;
    }

    private String readHelloWorldProto() throws Exception {
        return Files.readString(Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
    }
}
