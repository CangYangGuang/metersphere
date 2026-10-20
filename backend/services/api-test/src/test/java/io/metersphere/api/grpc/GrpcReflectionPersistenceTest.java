package io.metersphere.api.grpc;

import io.metersphere.api.domain.ApiGrpcCaseConfig;
import io.metersphere.api.domain.ApiGrpcDefinition;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.service.GrpcCasePersistenceService;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactAnalyzer;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactService;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.service.GrpcProtoVersionService;
import io.metersphere.api.grpc.util.GrpcProtoSchemaSnapshotBuilder;
import io.metersphere.api.grpc.util.GrpcReflectionProtoStorage;
import io.metersphere.api.mapper.ApiGrpcCaseConfigMapper;
import io.metersphere.api.mapper.ApiGrpcDefinitionMapper;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcReflectionPersistenceTest {

    private static final String PROJECT_ID = "project-id";
    private static final String USER_ID = "admin";
    private static final String API_DEFINITION_ID = "api-def-id";
    private static final String API_CASE_ID = "case-id";
    private static final String SERVER_ADDRESS = "public.example.com:50051";

    @Mock
    private ApiGrpcProtoMapper apiGrpcProtoMapper;
    @Mock
    private ApiGrpcDefinitionMapper apiGrpcDefinitionMapper;
    @Mock
    private ApiGrpcCaseConfigMapper apiGrpcCaseConfigMapper;
    @Mock
    private GrpcProtoVersionService grpcProtoVersionService;
    @Mock
    private GrpcProtoChangeImpactService grpcProtoChangeImpactService;
    @Mock
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;

    private GrpcProtoService grpcProtoService;
    private GrpcDefinitionPersistenceService definitionPersistenceService;
    private GrpcCasePersistenceService casePersistenceService;

    @BeforeEach
    void setUp() {
        grpcProtoService = new GrpcProtoService();
        definitionPersistenceService = new GrpcDefinitionPersistenceService();
        casePersistenceService = new GrpcCasePersistenceService();
        ReflectionTestUtils.setField(definitionPersistenceService, "apiGrpcProtoMapper", apiGrpcProtoMapper);
        ReflectionTestUtils.setField(definitionPersistenceService, "apiGrpcDefinitionMapper", apiGrpcDefinitionMapper);
        ReflectionTestUtils.setField(definitionPersistenceService, "grpcProtoService", grpcProtoService);
        ReflectionTestUtils.setField(definitionPersistenceService, "grpcProtoVersionService", grpcProtoVersionService);
        ReflectionTestUtils.setField(definitionPersistenceService, "grpcProtoChangeImpactService", grpcProtoChangeImpactService);
        ReflectionTestUtils.setField(casePersistenceService, "apiGrpcCaseConfigMapper", apiGrpcCaseConfigMapper);
        ReflectionTestUtils.setField(casePersistenceService, "apiGrpcProtoMapper", apiGrpcProtoMapper);
        ReflectionTestUtils.setField(casePersistenceService, "grpcDefinitionPersistenceService", grpcDefinitionPersistenceService);
        ReflectionTestUtils.setField(casePersistenceService, "grpcProtoVersionService", grpcProtoVersionService);
        ReflectionTestUtils.setField(casePersistenceService, "grpcProtoChangeImpactService", grpcProtoChangeImpactService);

        GrpcProtoChangeImpactDTO defaultImpact = new GrpcProtoChangeImpactDTO();
        defaultImpact.setImpactLevel(GrpcProtoChangeImpactAnalyzer.LEVEL_NONE);
        lenient().when(grpcProtoChangeImpactService.analyzeForDefinition(any(), any())).thenReturn(defaultImpact);
        lenient().when(grpcProtoChangeImpactService.analyzeForCase(any(), any())).thenReturn(defaultImpact);
        GrpcProtoVersionDTO defaultVersion = new GrpcProtoVersionDTO();
        defaultVersion.setVersionLabel("v1");
        lenient().when(grpcProtoVersionService.resolveVersion(any())).thenReturn(defaultVersion);
        lenient().when(grpcProtoVersionService.listVersionHistory(any())).thenReturn(List.of(defaultVersion));
        lenient().when(grpcDefinitionPersistenceService.getActiveProtoId(any())).thenReturn(null);
    }

    @Test
    void shouldSaveReflectionDescriptorOnlyDefinition() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildReflectionAdd("SayHello");
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);

        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(null);
        when(apiGrpcProtoMapper.insertSelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("reflection-proto-id", "reflection-def-id");

            ApiGrpcProto proto = definitionPersistenceService.saveOrGetProto(
                    PROJECT_ID, grpcAdd, parseResponse, USER_ID);
            ApiGrpcDefinition definition = definitionPersistenceService.saveGrpcDefinition(
                    API_DEFINITION_ID, grpcAdd, proto, parseResponse, USER_ID);

            Assertions.assertTrue(GrpcReflectionProtoStorage.isReflectionContent(proto.getContent()));
            Assertions.assertEquals(GrpcReflectionProtoStorage.hashForStorage(grpcAdd), proto.getContentHash());
            Assertions.assertEquals("reflection://public.example.com:50051", proto.getFileName());
            Assertions.assertEquals(parseResponse.getDescriptorContent(), proto.getDescriptorContent());
            Assertions.assertNull(grpcAdd.getProtoContent());
            Assertions.assertEquals("helloworld.Greeter", definition.getServiceName());
            Assertions.assertEquals("SayHello", definition.getMethodName());
            Assertions.assertNotNull(definition.getDescriptorContent());
        }
    }

    @Test
    void shouldSaveReflectionWhenProtoContentEmptyButDescriptorValid() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildReflectionAdd("SayHello");
        grpcAdd.setProtoContent("");
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);

        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(null);
        when(apiGrpcProtoMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("reflection-proto-id");
            ApiGrpcProto proto = definitionPersistenceService.saveOrGetProto(
                    PROJECT_ID, grpcAdd, parseResponse, USER_ID);
            Assertions.assertTrue(GrpcReflectionProtoStorage.isReflectionContent(proto.getContent()));
            Assertions.assertNotEquals("", proto.getDescriptorContent());
        }
    }

    @Test
    void shouldLoadReflectionDefinitionDetailWithServicesAndTemplate() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildReflectionAdd("SayHello");
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);

        ApiGrpcProto proto = buildStoredReflectionProto("reflection-proto-id", grpcAdd, parseResponse);
        ApiGrpcDefinition definition = buildDefinition(proto.getId(), "SayHello");
        definition.setDescriptorContent(parseResponse.getDescriptorContent());

        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(definition));
        when(apiGrpcProtoMapper.selectByPrimaryKey(proto.getId())).thenReturn(proto);

        ApiGrpcDefinitionDTO dto = definitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID);

        Assertions.assertTrue(Boolean.TRUE.equals(dto.getReflection()));
        Assertions.assertEquals(SERVER_ADDRESS, dto.getReflectionServerAddress());
        Assertions.assertNull(dto.getProtoContent());
        Assertions.assertFalse(dto.getServices().isEmpty());
        Assertions.assertNotNull(dto.getServices().get(0).getMethods().get(0).getRequestJsonTemplate());
        Assertions.assertEquals(parseResponse.getDescriptorContent(), dto.getDescriptorContent());
    }

    @Test
    void shouldKeepCaseDescriptorContentForReflectionDefinition() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildReflectionAdd("SayHello");
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);
        ApiGrpcDefinitionDTO definitionDto = buildDefinitionDto(grpcAdd, parseResponse);

        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID)).thenReturn(definitionDto);
        when(apiGrpcCaseConfigMapper.insertSelective(any())).thenReturn(1);

        ApiGrpcCaseConfigAddDTO caseAdd = new ApiGrpcCaseConfigAddDTO();
        caseAdd.setServiceName("helloworld.Greeter");
        caseAdd.setMethodName("SayHello");
        caseAdd.setDescriptorContent(parseResponse.getDescriptorContent());
        caseAdd.setRequestJson("{\"name\":\"case\"}");

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("case-config-id");
            ApiGrpcCaseConfig saved = casePersistenceService.saveCaseConfig(
                    API_CASE_ID, API_DEFINITION_ID, caseAdd, USER_ID);
            Assertions.assertEquals(parseResponse.getDescriptorContent(), saved.getDescriptorContent());
            Assertions.assertEquals(definitionDto.getProtoId(), saved.getProtoId());
        }
    }

    @Test
    void shouldCreateNewReflectionProtoVersionWhenDescriptorChanges() throws Exception {
        ApiGrpcDefinitionAddDTO v1Add = buildReflectionAdd("SayHello");
        ApiGrpcDefinitionAddDTO v2Add = buildReflectionAdd("SayHello");
        v2Add.setDescriptorContent(buildChangedDescriptorContent());

        GrpcParseProtoResponse v1Parse = definitionPersistenceService.parseProto(v1Add);
        GrpcParseProtoResponse v2Parse = definitionPersistenceService.parseProto(v2Add);

        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(eq(PROJECT_ID),
                eq(GrpcReflectionProtoStorage.hashForStorage(v1Add)))).thenReturn(null);
        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(eq(PROJECT_ID),
                eq(GrpcReflectionProtoStorage.hashForStorage(v2Add)))).thenReturn(null);
        when(apiGrpcProtoMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("reflection-v1", "reflection-v2");
            ApiGrpcProto v1 = definitionPersistenceService.saveOrGetProto(PROJECT_ID, v1Add, v1Parse, USER_ID);
            ApiGrpcProto v2 = definitionPersistenceService.saveOrGetProto(PROJECT_ID, v2Add, v2Parse, USER_ID);

            Assertions.assertNotEquals(v1.getContentHash(), v2.getContentHash());
            Assertions.assertEquals(v1.getFileName(), v2.getFileName());
            Assertions.assertNotEquals(v1.getDescriptorContent(), v2.getDescriptorContent());
        }
    }

    @Test
    void shouldAnalyzeReflectionProtoChangeImpact() throws Exception {
        ApiGrpcDefinitionAddDTO v1Add = buildReflectionAdd("SayHello");
        ApiGrpcDefinitionAddDTO v2Add = buildReflectionAdd("SayHello");
        v2Add.setDescriptorContent(buildChangedDescriptorContent());

        GrpcParseProtoResponse v1Parse = definitionPersistenceService.parseProto(v1Add);
        GrpcParseProtoResponse v2Parse = definitionPersistenceService.parseProto(v2Add);

        ApiGrpcProto oldProto = buildStoredReflectionProto("reflection-v1", v1Add, v1Parse);
        ApiGrpcProto newProto = buildStoredReflectionProto("reflection-v2", v2Add, v2Parse);
        ApiGrpcDefinition definition = buildDefinition(newProto.getId(), "SayHello");

        GrpcProtoChangeImpactService impactService = buildRealImpactService();
        when(grpcProtoVersionService.listVersionHistory(newProto)).thenReturn(List.of(
                version("reflection-v1", 1),
                version("reflection-v2", 2)));
        when(apiGrpcProtoMapper.selectByPrimaryKey("reflection-v1")).thenReturn(oldProto);

        GrpcProtoChangeImpactDTO impact = impactService.analyzeForDefinition(definition, newProto);

        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_BREAKING, impact.getImpactLevel());
        Assertions.assertFalse(impact.getChangedMessages().isEmpty());
    }

    @Test
    void shouldNotDuplicateReflectionProtoWhenDescriptorUnchanged() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildReflectionAdd("SayHello");
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);
        ApiGrpcProto existing = buildStoredReflectionProto("existing-id", grpcAdd, parseResponse);

        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(
                PROJECT_ID, GrpcReflectionProtoStorage.hashForStorage(grpcAdd))).thenReturn(existing);

        ApiGrpcProto proto = definitionPersistenceService.saveOrGetProto(
                PROJECT_ID, grpcAdd, parseResponse, USER_ID);

        Assertions.assertEquals(existing.getId(), proto.getId());
        verify(apiGrpcProtoMapper, Mockito.never()).insertSelective(any());
    }

    private GrpcProtoChangeImpactService buildRealImpactService() {
        GrpcProtoChangeImpactService impactService = new GrpcProtoChangeImpactService();
        GrpcProtoSchemaSnapshotBuilder snapshotBuilder = new GrpcProtoSchemaSnapshotBuilder();
        ReflectionTestUtils.setField(snapshotBuilder, "grpcProtoService", grpcProtoService);
        ReflectionTestUtils.setField(impactService, "apiGrpcProtoMapper", apiGrpcProtoMapper);
        ReflectionTestUtils.setField(impactService, "apiGrpcDefinitionMapper", apiGrpcDefinitionMapper);
        ReflectionTestUtils.setField(impactService, "apiGrpcCaseConfigMapper", apiGrpcCaseConfigMapper);
        ReflectionTestUtils.setField(impactService, "schemaSnapshotBuilder", snapshotBuilder);
        ReflectionTestUtils.setField(impactService, "impactAnalyzer", new GrpcProtoChangeImpactAnalyzer());
        ReflectionTestUtils.setField(impactService, "grpcProtoVersionService", grpcProtoVersionService);
        return impactService;
    }

    private ApiGrpcDefinitionAddDTO buildReflectionAdd(String methodName) throws Exception {
        String protoContent = Files.readString(Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        GrpcParseProtoResponse parseResponse = grpcProtoService.parseProto(buildParseRequest(protoContent));
        ApiGrpcDefinitionAddDTO grpcAdd = new ApiGrpcDefinitionAddDTO();
        grpcAdd.setDescriptorContent(parseResponse.getDescriptorContent());
        grpcAdd.setReflection(true);
        grpcAdd.setReflectionSourceType("REFLECTION");
        grpcAdd.setReflectionServerAddress(SERVER_ADDRESS);
        grpcAdd.setReflectionUseTls(false);
        grpcAdd.setReflectionServiceFullName("helloworld.Greeter");
        grpcAdd.setReflectionDiscoveredAt("2026-06-18T10:00:00");
        grpcAdd.setServiceName("helloworld.Greeter");
        grpcAdd.setMethodName(methodName);
        return grpcAdd;
    }

    private String buildChangedDescriptorContent() throws Exception {
        String protoContent = Files.readString(Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        String changed = protoContent.replace("string name = 1;", "int32 name = 1;");
        return grpcProtoService.parseProto(buildParseRequest(changed)).getDescriptorContent();
    }

    private io.metersphere.api.grpc.dto.GrpcParseProtoRequest buildParseRequest(String protoContent) {
        io.metersphere.api.grpc.dto.GrpcParseProtoRequest request =
                new io.metersphere.api.grpc.dto.GrpcParseProtoRequest();
        request.setProtoContent(protoContent);
        request.setFileName("helloworld.proto");
        return request;
    }

    private ApiGrpcProto buildStoredReflectionProto(String id, ApiGrpcDefinitionAddDTO grpcAdd,
                                                    GrpcParseProtoResponse parseResponse) {
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId(id);
        proto.setProjectId(PROJECT_ID);
        proto.setName(GrpcReflectionProtoStorage.resolveStoredFileName(grpcAdd));
        proto.setFileName(GrpcReflectionProtoStorage.resolveStoredFileName(grpcAdd));
        proto.setContent(GrpcReflectionProtoStorage.serializeForStorage(grpcAdd));
        proto.setDescriptorContent(parseResponse.getDescriptorContent());
        proto.setContentHash(GrpcReflectionProtoStorage.hashForStorage(grpcAdd));
        proto.setCreateTime(1L);
        return proto;
    }

    private ApiGrpcDefinition buildDefinition(String protoId, String methodName) {
        ApiGrpcDefinition definition = new ApiGrpcDefinition();
        definition.setId("grpc-def-id");
        definition.setApiDefinitionId(API_DEFINITION_ID);
        definition.setProtoId(protoId);
        definition.setServiceName("helloworld.Greeter");
        definition.setMethodName(methodName);
        definition.setRequestType("helloworld.HelloRequest");
        definition.setResponseType("helloworld.HelloReply");
        definition.setDescriptorContent("placeholder");
        definition.setDeleted(false);
        return definition;
    }

    private ApiGrpcDefinitionDTO buildDefinitionDto(ApiGrpcDefinitionAddDTO grpcAdd,
                                                    GrpcParseProtoResponse parseResponse) {
        ApiGrpcDefinitionDTO dto = new ApiGrpcDefinitionDTO();
        dto.setProtoId("reflection-proto-id");
        dto.setServiceName(grpcAdd.getServiceName());
        dto.setMethodName(grpcAdd.getMethodName());
        dto.setDescriptorContent(parseResponse.getDescriptorContent());
        dto.setReflection(true);
        dto.setReflectionServerAddress(SERVER_ADDRESS);
        return dto;
    }

    private GrpcProtoVersionDTO version(String protoId, int versionNo) {
        GrpcProtoVersionDTO version = new GrpcProtoVersionDTO();
        version.setProtoId(protoId);
        version.setVersionNo(versionNo);
        version.setVersionLabel("v" + versionNo);
        return version;
    }
}
