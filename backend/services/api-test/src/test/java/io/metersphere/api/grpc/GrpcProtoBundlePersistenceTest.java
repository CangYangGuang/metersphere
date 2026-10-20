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
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.service.GrpcCasePersistenceService;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactAnalyzer;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactService;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.service.GrpcProtoVersionService;
import io.metersphere.api.grpc.util.GrpcProtoBundleStorage;
import io.metersphere.api.grpc.util.GrpcProtoSchemaSnapshotBuilder;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcProtoBundlePersistenceTest {

    private static final String PROJECT_ID = "project-id";
    private static final String USER_ID = "admin";
    private static final String API_DEFINITION_ID = "api-def-id";
    private static final String API_CASE_ID = "case-id";

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
    void shouldKeepSingleProtoStorageCompatible() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildHelloWorldAdd();
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);

        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(null);
        when(apiGrpcProtoMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("proto-single-id");
            ApiGrpcProto proto = definitionPersistenceService.saveOrGetProto(
                    PROJECT_ID, grpcAdd, parseResponse, USER_ID);

            Assertions.assertFalse(GrpcProtoBundleStorage.isBundleContent(proto.getContent()));
            Assertions.assertEquals(grpcAdd.getProtoContent(), proto.getContent());
            Assertions.assertEquals(GrpcProtoBundleStorage.hashForStorage(grpcAdd), proto.getContentHash());
        }
    }

    @Test
    void shouldPersistMultiProtoBundleAsJsonContent() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildImportBundleAdd();
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);

        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(null);
        when(apiGrpcProtoMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("proto-bundle-id");
            ApiGrpcProto proto = definitionPersistenceService.saveOrGetProto(
                    PROJECT_ID, grpcAdd, parseResponse, USER_ID);

            ArgumentCaptor<ApiGrpcProto> protoCaptor = ArgumentCaptor.forClass(ApiGrpcProto.class);
            verify(apiGrpcProtoMapper).insertSelective(protoCaptor.capture());
            ApiGrpcProto stored = protoCaptor.getValue();

            Assertions.assertTrue(GrpcProtoBundleStorage.isBundleContent(stored.getContent()));
            Assertions.assertEquals("api/greeter.proto", stored.getFileName());
            Assertions.assertEquals(3, GrpcProtoBundleStorage.parseStored(stored).protoFiles().size());
            Assertions.assertEquals(GrpcProtoBundleStorage.hashForStorage(grpcAdd), stored.getContentHash());
        }
    }

    @Test
    void shouldLoadBundleDefinitionDetailWithMainFileNameAndProtoFiles() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildImportBundleAdd();
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);
        String storedContent = GrpcProtoBundleStorage.serializeForStorage(grpcAdd);

        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("bundle-proto-id");
        proto.setFileName("api/greeter.proto");
        proto.setContent(storedContent);

        ApiGrpcDefinition grpcDefinition = new ApiGrpcDefinition();
        grpcDefinition.setProtoId(proto.getId());
        grpcDefinition.setPackageName("api");
        grpcDefinition.setServiceName("api.Greeter");
        grpcDefinition.setMethodName("SayHello");
        grpcDefinition.setFullMethodName("api.Greeter/SayHello");
        grpcDefinition.setMethodType("UNARY");
        grpcDefinition.setRequestType("types.CommonRequest");
        grpcDefinition.setResponseType("types.CommonResponse");
        grpcDefinition.setDescriptorContent(parseResponse.getDescriptorContent());

        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(grpcDefinition));
        when(apiGrpcProtoMapper.selectByPrimaryKey(proto.getId())).thenReturn(proto);

        ApiGrpcDefinitionDTO dto = definitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID);

        Assertions.assertNotNull(dto);
        Assertions.assertEquals("api/greeter.proto", dto.getMainFileName());
        Assertions.assertEquals("api/greeter.proto", dto.getFileName());
        Assertions.assertEquals(3, dto.getProtoFiles().size());
        Assertions.assertFalse(dto.getServices().isEmpty());
        Assertions.assertTrue(dto.getProtoContent().contains("service Greeter"));
    }

    @Test
    void shouldEchoBundleOnCaseDetailWithoutLosingImports() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildImportBundleAdd();
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);
        String storedContent = GrpcProtoBundleStorage.serializeForStorage(grpcAdd);

        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("bundle-proto-id");
        proto.setFileName("api/greeter.proto");
        proto.setContent(storedContent);
        proto.setDescriptorContent(parseResponse.getDescriptorContent());

        ApiGrpcCaseConfig caseConfig = new ApiGrpcCaseConfig();
        caseConfig.setId("case-config-id");
        caseConfig.setApiCaseId(API_CASE_ID);
        caseConfig.setApiDefinitionId(API_DEFINITION_ID);
        caseConfig.setProtoId(proto.getId());
        caseConfig.setServiceName("api.Greeter");
        caseConfig.setMethodName("SayHello");
        caseConfig.setDescriptorContent(parseResponse.getDescriptorContent());
        caseConfig.setRequestJson("{\"name\":\"world\",\"status\":\"STATUS_OK\"}");
        caseConfig.setMetadata("[]");
        caseConfig.setTimeout(60000);

        when(apiGrpcCaseConfigMapper.selectByApiCaseId(API_CASE_ID)).thenReturn(caseConfig);
        when(apiGrpcProtoMapper.selectByPrimaryKey(proto.getId())).thenReturn(proto);
        when(grpcDefinitionPersistenceService.getActiveProtoId(API_DEFINITION_ID)).thenReturn(proto.getId());

        ApiGrpcCaseConfigDTO dto = casePersistenceService.loadDtoByCaseId(API_CASE_ID);

        Assertions.assertEquals("api/greeter.proto", dto.getMainFileName());
        Assertions.assertEquals(3, dto.getProtoFiles().size());
        Assertions.assertTrue(dto.getProtoFiles().stream()
                .anyMatch(file -> "types/common.proto".equals(file.getFileName())));
    }

    @Test
    void shouldDetectBundleContentHashChangeForProtoVersion() throws Exception {
        ApiGrpcDefinitionAddDTO baseAdd = buildImportBundleAdd();
        ApiGrpcDefinitionAddDTO changedAdd = buildImportBundleAdd();
        changedAdd.getProtoFiles().stream()
                .filter(file -> "types/common.proto".equals(file.getFileName()))
                .findFirst()
                .ifPresent(file -> file.setContent(file.getContent().replace("string name = 1;", "int32 name = 1;")));

        String baseHash = GrpcProtoBundleStorage.hashForStorage(baseAdd);
        String changedHash = GrpcProtoBundleStorage.hashForStorage(changedAdd);

        Assertions.assertNotEquals(baseHash, changedHash);
    }

    @Test
    void shouldDetectImportMessageChangeForProtoChangeImpact() throws Exception {
        ApiGrpcDefinitionAddDTO baseAdd = buildImportBundleAdd();
        ApiGrpcDefinitionAddDTO changedAdd = buildImportBundleAdd();
        changedAdd.getProtoFiles().stream()
                .filter(file -> "types/common.proto".equals(file.getFileName()))
                .findFirst()
                .ifPresent(file -> file.setContent(file.getContent().replace("string name = 1;", "int32 name = 1;")));

        ApiGrpcProto oldProto = buildStoredBundleProto("proto-old", baseAdd);
        ApiGrpcProto newProto = buildStoredBundleProto("proto-new", changedAdd);
        ApiGrpcDefinition definition = new ApiGrpcDefinition();
        definition.setApiDefinitionId(API_DEFINITION_ID);
        definition.setProtoId(newProto.getId());
        definition.setServiceName("api.Greeter");
        definition.setMethodName("SayHello");

        GrpcProtoChangeImpactService impactService = buildRealImpactService();
        when(grpcProtoVersionService.listVersionHistory(newProto)).thenReturn(List.of(
                version("proto-old", 1),
                version("proto-new", 2)));
        when(apiGrpcProtoMapper.selectByPrimaryKey("proto-old")).thenReturn(oldProto);

        GrpcProtoChangeImpactDTO impact = impactService.analyzeForDefinition(definition, newProto);

        Assertions.assertEquals(GrpcProtoChangeImpactAnalyzer.LEVEL_BREAKING, impact.getImpactLevel());
        Assertions.assertFalse(impact.getChangedMessages().isEmpty());
    }

    @Test
    void shouldSaveCaseConfigBoundToBundleProto() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildImportBundleAdd();
        GrpcParseProtoResponse parseResponse = definitionPersistenceService.parseProto(grpcAdd);

        ApiGrpcDefinitionDTO definition = new ApiGrpcDefinitionDTO();
        definition.setProtoId("bundle-proto-id");
        definition.setServiceName("api.Greeter");
        definition.setMethodName("SayHello");
        definition.setDescriptorContent(parseResponse.getDescriptorContent());

        ApiGrpcCaseConfigAddDTO config = new ApiGrpcCaseConfigAddDTO();
        config.setServiceName("api.Greeter");
        config.setMethodName("SayHello");
        config.setDescriptorContent(parseResponse.getDescriptorContent());
        config.setRequestJson("{\"name\":\"world\",\"status\":\"STATUS_OK\"}");

        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID)).thenReturn(definition);
        when(apiGrpcCaseConfigMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("case-config-id");
            ApiGrpcCaseConfig saved = casePersistenceService.saveCaseConfig(
                    API_CASE_ID, API_DEFINITION_ID, config, USER_ID);
            Assertions.assertEquals("bundle-proto-id", saved.getProtoId());
            Assertions.assertEquals(parseResponse.getDescriptorContent(), saved.getDescriptorContent());
        }
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

    private ApiGrpcProto buildStoredBundleProto(String id, ApiGrpcDefinitionAddDTO grpcAdd) {
        GrpcParseProtoResponse response = definitionPersistenceService.parseProto(grpcAdd);
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId(id);
        proto.setProjectId(PROJECT_ID);
        proto.setFileName("api/greeter.proto");
        proto.setContent(GrpcProtoBundleStorage.serializeForStorage(grpcAdd));
        proto.setDescriptorContent(response.getDescriptorContent());
        proto.setContentHash(GrpcProtoBundleStorage.hashForStorage(grpcAdd));
        return proto;
    }

    private GrpcProtoVersionDTO version(String protoId, int versionNo) {
        GrpcProtoVersionDTO version = new GrpcProtoVersionDTO();
        version.setProtoId(protoId);
        version.setVersionNo(versionNo);
        version.setVersionLabel("v" + versionNo);
        return version;
    }

    private ApiGrpcDefinitionAddDTO buildHelloWorldAdd() throws Exception {
        String protoContent = Files.readString(Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        ApiGrpcDefinitionAddDTO grpcAdd = new ApiGrpcDefinitionAddDTO();
        grpcAdd.setProtoContent(protoContent);
        grpcAdd.setFileName("helloworld.proto");
        grpcAdd.setServiceName("helloworld.Greeter");
        grpcAdd.setMethodName("SayHello");
        return grpcAdd;
    }

    private ApiGrpcDefinitionAddDTO buildImportBundleAdd() throws Exception {
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

        ApiGrpcDefinitionAddDTO grpcAdd = new ApiGrpcDefinitionAddDTO();
        grpcAdd.setProtoContent(greeter.getContent());
        grpcAdd.setFileName("api/greeter.proto");
        grpcAdd.setMainFileName("api/greeter.proto");
        grpcAdd.setProtoFiles(List.of(status, common, greeter));
        grpcAdd.setServiceName("api.Greeter");
        grpcAdd.setMethodName("SayHello");
        return grpcAdd;
    }
}
