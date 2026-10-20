package io.metersphere.api.grpc;

import io.metersphere.api.domain.ApiGrpcDefinition;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactAnalyzer;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactService;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.service.GrpcProtoVersionService;
import io.metersphere.api.grpc.util.GrpcProtoBundleStorage;
import io.metersphere.api.mapper.ApiGrpcDefinitionMapper;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import io.metersphere.sdk.exception.MSException;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcDefinitionPersistenceServiceTest {

    private static final String PROJECT_ID = "project-id";
    private static final String USER_ID = "admin";
    private static final String API_DEFINITION_ID = "api-def-id";

    @Mock
    private ApiGrpcProtoMapper apiGrpcProtoMapper;
    @Mock
    private ApiGrpcDefinitionMapper apiGrpcDefinitionMapper;
    @Mock
    private GrpcProtoVersionService grpcProtoVersionService;
    @Mock
    private GrpcProtoChangeImpactService grpcProtoChangeImpactService;

    private GrpcProtoService grpcProtoService;
    private GrpcDefinitionPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        grpcProtoService = new GrpcProtoService();
        persistenceService = new GrpcDefinitionPersistenceService();
        ReflectionTestUtils.setField(persistenceService, "apiGrpcProtoMapper", apiGrpcProtoMapper);
        ReflectionTestUtils.setField(persistenceService, "apiGrpcDefinitionMapper", apiGrpcDefinitionMapper);
        ReflectionTestUtils.setField(persistenceService, "grpcProtoService", grpcProtoService);
        ReflectionTestUtils.setField(persistenceService, "grpcProtoVersionService", grpcProtoVersionService);
        ReflectionTestUtils.setField(persistenceService, "grpcProtoChangeImpactService", grpcProtoChangeImpactService);
        GrpcProtoChangeImpactDTO defaultImpact = new GrpcProtoChangeImpactDTO();
        defaultImpact.setImpactLevel(GrpcProtoChangeImpactAnalyzer.LEVEL_NONE);
        lenient().when(grpcProtoChangeImpactService.analyzeForDefinition(any(), any())).thenReturn(defaultImpact);
        GrpcProtoVersionDTO defaultVersion = new GrpcProtoVersionDTO();
        defaultVersion.setVersionLabel("v1");
        lenient().when(grpcProtoVersionService.resolveVersion(any())).thenReturn(defaultVersion);
        lenient().when(grpcProtoVersionService.listVersionHistory(any())).thenReturn(List.of(defaultVersion));
    }

    @Test
    void shouldSaveGrpcDefinitionSuccessfully() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildHelloWorldAdd("SayHello");
        GrpcParseProtoResponse parseResponse = persistenceService.parseProto(grpcAdd);

        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(null);
        when(apiGrpcProtoMapper.insertSelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("generated-id-1", "generated-id-2");

            ApiGrpcProto proto = persistenceService.saveOrGetProto(PROJECT_ID, grpcAdd, parseResponse, USER_ID);
            ApiGrpcDefinition grpcDefinition = persistenceService.saveGrpcDefinition(
                    API_DEFINITION_ID, grpcAdd, proto, parseResponse, USER_ID);

            Assertions.assertEquals("generated-id-1", proto.getId());
            Assertions.assertNotNull(proto.getDescriptorContent());
            Assertions.assertEquals("helloworld.Greeter", grpcDefinition.getServiceName());
            Assertions.assertEquals("SayHello", grpcDefinition.getMethodName());
            Assertions.assertEquals("UNARY", grpcDefinition.getMethodType());

            ArgumentCaptor<ApiGrpcProto> protoCaptor = ArgumentCaptor.forClass(ApiGrpcProto.class);
            verify(apiGrpcProtoMapper).insertSelective(protoCaptor.capture());
            Assertions.assertEquals(grpcAdd.getProtoContent(), protoCaptor.getValue().getContent());

            ArgumentCaptor<ApiGrpcDefinition> definitionCaptor = ArgumentCaptor.forClass(ApiGrpcDefinition.class);
            verify(apiGrpcDefinitionMapper).insertSelective(definitionCaptor.capture());
            Assertions.assertEquals(API_DEFINITION_ID, definitionCaptor.getValue().getApiDefinitionId());
            Assertions.assertEquals(proto.getId(), definitionCaptor.getValue().getProtoId());
        }
    }

    @Test
    void shouldLoadGrpcDefinitionDetailSuccessfully() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildHelloWorldAdd("SayHello");
        GrpcParseProtoResponse parseResponse = persistenceService.parseProto(grpcAdd);

        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("proto-id");
        proto.setFileName("helloworld.proto");
        proto.setContent(grpcAdd.getProtoContent());

        ApiGrpcDefinition grpcDefinition = new ApiGrpcDefinition();
        grpcDefinition.setProtoId(proto.getId());
        grpcDefinition.setPackageName("helloworld");
        grpcDefinition.setServiceName("helloworld.Greeter");
        grpcDefinition.setMethodName("SayHello");
        grpcDefinition.setFullMethodName("helloworld.Greeter/SayHello");
        grpcDefinition.setMethodType("UNARY");
        grpcDefinition.setRequestType("helloworld.HelloRequest");
        grpcDefinition.setResponseType("helloworld.HelloReply");
        grpcDefinition.setDescriptorContent(parseResponse.getDescriptorContent());

        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(grpcDefinition));
        when(apiGrpcProtoMapper.selectByPrimaryKey(proto.getId())).thenReturn(proto);

        ApiGrpcDefinitionDTO dto = persistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID);

        Assertions.assertNotNull(dto);
        Assertions.assertEquals("SayHello", dto.getMethodName());
        Assertions.assertEquals("UNARY", dto.getMethodType());
        Assertions.assertTrue(dto.getInvokable());
        Assertions.assertEquals(grpcAdd.getProtoContent(), dto.getProtoContent());
        Assertions.assertFalse(dto.getServices().isEmpty());
    }

    @Test
    void shouldLoadServicesFromStoredDescriptorWhenStoredProtoNoLongerCompiles() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildHelloWorldAdd("SayHello");
        GrpcParseProtoResponse parseResponse = persistenceService.parseProto(grpcAdd);

        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("proto-id");
        proto.setFileName("helloworld.proto");
        proto.setContent("syntax = \"proto3\"; message Broken {");

        ApiGrpcDefinition grpcDefinition = buildExistingDefinition(proto.getId(), "SayHello");
        grpcDefinition.setDescriptorContent(parseResponse.getDescriptorContent());

        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(grpcDefinition));
        when(apiGrpcProtoMapper.selectByPrimaryKey(proto.getId())).thenReturn(proto);

        ApiGrpcDefinitionDTO dto = persistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID);

        Assertions.assertNotNull(dto);
        Assertions.assertFalse(dto.getServices().isEmpty());
        Assertions.assertEquals("helloworld.Greeter", dto.getServices().getFirst().getFullName());
    }

    @Test
    void shouldFailWhenProtoInvalid() {
        ApiGrpcDefinitionAddDTO grpcAdd = new ApiGrpcDefinitionAddDTO();
        grpcAdd.setProtoContent("syntax = \"proto3\"\npackage broken\nmessage X { string name = 1");
        grpcAdd.setFileName("broken.proto");
        grpcAdd.setServiceName("broken.Service");
        grpcAdd.setMethodName("Call");

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> persistenceService.parseProto(grpcAdd));
        Assertions.assertEquals(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getErrorCode());
    }

    @Test
    void shouldSaveStreamingMethodWithInvokableFalse() {
        String streamingProto = "syntax = \"proto3\";\npackage streamtest;\n"
                + "service StreamSvc { rpc ServerStream (Req) returns (stream Resp) {} }\n"
                + "message Req { string name = 1; }\nmessage Resp { string message = 1; }\n";
        ApiGrpcDefinitionAddDTO grpcAdd = new ApiGrpcDefinitionAddDTO();
        grpcAdd.setProtoContent(streamingProto);
        grpcAdd.setFileName("stream.proto");
        grpcAdd.setServiceName("streamtest.StreamSvc");
        grpcAdd.setMethodName("ServerStream");

        GrpcParseProtoResponse parseResponse = persistenceService.parseProto(grpcAdd);
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("proto-stream-id");

        when(apiGrpcDefinitionMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("generated-stream-id");

            ApiGrpcDefinition grpcDefinition = persistenceService.saveGrpcDefinition(
                    API_DEFINITION_ID, grpcAdd, proto, parseResponse, USER_ID);

            Assertions.assertEquals("SERVER_STREAMING", grpcDefinition.getMethodType());
        }

        ApiGrpcDefinition stored = new ApiGrpcDefinition();
        stored.setProtoId(proto.getId());
        stored.setPackageName("streamtest");
        stored.setServiceName("streamtest.StreamSvc");
        stored.setMethodName("ServerStream");
        stored.setFullMethodName("streamtest.StreamSvc/ServerStream");
        stored.setMethodType("SERVER_STREAMING");
        stored.setDescriptorContent(parseResponse.getDescriptorContent());

        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(stored));
        when(apiGrpcProtoMapper.selectByPrimaryKey(proto.getId())).thenReturn(proto);

        ApiGrpcDefinitionDTO dto = persistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID);
        Assertions.assertEquals("SERVER_STREAMING", dto.getMethodType());
        Assertions.assertFalse(dto.getInvokable());
    }

    @Test
    void shouldReuseExistingProtoByContentHash() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildHelloWorldAdd("SayHello");
        GrpcParseProtoResponse parseResponse = persistenceService.parseProto(grpcAdd);

        ApiGrpcProto existing = new ApiGrpcProto();
        existing.setId("existing-proto-id");
        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(existing);

        ApiGrpcProto proto = persistenceService.saveOrGetProto(PROJECT_ID, grpcAdd, parseResponse, USER_ID);

        Assertions.assertEquals("existing-proto-id", proto.getId());
        verify(apiGrpcProtoMapper, never()).insertSelective(any());
    }

    @Test
    void shouldUpdateGrpcDefinitionSuccessfully() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildHelloWorldAdd("SayHello");
        ApiGrpcDefinition existing = buildExistingDefinition("old-proto-id", "SayHello");

        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(existing));
        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(null);
        when(apiGrpcProtoMapper.insertSelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.countByProtoId("old-proto-id")).thenReturn(0L);
        ApiGrpcProto oldProto = new ApiGrpcProto();
        oldProto.setId("old-proto-id");
        oldProto.setDeleted(false);
        when(apiGrpcProtoMapper.selectByPrimaryKey("old-proto-id")).thenReturn(oldProto);
        when(apiGrpcProtoMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("new-proto-id");
            ApiGrpcDefinition updated = persistenceService.updateGrpcDefinition(
                    API_DEFINITION_ID, PROJECT_ID, grpcAdd, USER_ID);
            Assertions.assertEquals("SayHello", updated.getMethodName());
            Assertions.assertEquals("new-proto-id", updated.getProtoId());
            verify(apiGrpcDefinitionMapper).updateByPrimaryKeySelective(existing);
            verify(apiGrpcProtoMapper).updateByPrimaryKeySelective(argThat(proto -> "old-proto-id".equals(proto.getId())
                    && Boolean.TRUE.equals(proto.getDeleted())));
        }
    }

    @Test
    void shouldSyncServiceMethodDescriptorAfterProtoUpdate() {
        String updatedProto = "syntax = \"proto3\";\npackage greetv2;\n"
                + "service GreeterV2 { rpc SayGoodbye (ByeRequest) returns (ByeReply) {} }\n"
                + "message ByeRequest { string name = 1; }\nmessage ByeReply { string message = 1; }\n";
        ApiGrpcDefinitionAddDTO grpcAdd = new ApiGrpcDefinitionAddDTO();
        grpcAdd.setProtoContent(updatedProto);
        grpcAdd.setFileName("greetv2.proto");
        grpcAdd.setServiceName("greetv2.GreeterV2");
        grpcAdd.setMethodName("SayGoodbye");

        ApiGrpcDefinition existing = buildExistingDefinition("old-proto-id", "SayHello");
        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(existing));
        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(null);
        when(apiGrpcProtoMapper.insertSelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.countByProtoId("old-proto-id")).thenReturn(0L);
        ApiGrpcProto oldProto = new ApiGrpcProto();
        oldProto.setId("old-proto-id");
        oldProto.setDeleted(false);
        when(apiGrpcProtoMapper.selectByPrimaryKey("old-proto-id")).thenReturn(oldProto);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("new-proto-id");
            ApiGrpcDefinition updated = persistenceService.updateGrpcDefinition(
                    API_DEFINITION_ID, PROJECT_ID, grpcAdd, USER_ID);
            Assertions.assertEquals("greetv2.GreeterV2", updated.getServiceName());
            Assertions.assertEquals("SayGoodbye", updated.getMethodName());
            Assertions.assertEquals("greetv2.GreeterV2/SayGoodbye", updated.getFullMethodName());
            Assertions.assertNotNull(updated.getDescriptorContent());
        }
    }

    @Test
    void shouldRestoreStoredDependenciesWhenUpdatingBundleFromMainProtoOnly() {
        String mainContent = "syntax = \"proto3\";\npackage bundle;\nimport \"common.proto\";\n"
                + "service BundleService { rpc Call (Request) returns (Response) {} }\n";
        String dependencyContent = "syntax = \"proto3\";\npackage bundle;\n"
                + "message Request { string value = 1; }\nmessage Response { string value = 1; }\n";
        ApiGrpcDefinitionAddDTO storedAdd = new ApiGrpcDefinitionAddDTO();
        storedAdd.setMainFileName("main.proto");
        storedAdd.setProtoFiles(List.of(
                protoFile("main.proto", mainContent, true),
                protoFile("common.proto", dependencyContent, false)));

        ApiGrpcProto storedProto = new ApiGrpcProto();
        storedProto.setId("bundle-proto-id");
        storedProto.setFileName("main.proto");
        storedProto.setContent(GrpcProtoBundleStorage.serializeForStorage(storedAdd));
        storedProto.setDeleted(false);

        ApiGrpcDefinition existing = buildExistingDefinition(storedProto.getId(), "Call");
        existing.setServiceName("bundle.BundleService");
        existing.setFullMethodName("bundle.BundleService/Call");

        ApiGrpcDefinitionAddDTO update = new ApiGrpcDefinitionAddDTO();
        update.setProtoContent(mainContent);
        update.setFileName("main.proto");
        update.setServiceName("bundle.BundleService");
        update.setMethodName("Call");

        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(existing));
        when(apiGrpcProtoMapper.selectByPrimaryKey(storedProto.getId())).thenReturn(storedProto);
        when(apiGrpcProtoMapper.selectByProjectIdAndContentHash(any(), any())).thenReturn(storedProto);
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        ApiGrpcDefinition updated = persistenceService.updateGrpcDefinition(
                API_DEFINITION_ID, PROJECT_ID, update, USER_ID);

        Assertions.assertEquals("bundle.BundleService/Call", updated.getFullMethodName());
        Assertions.assertEquals(2, update.getProtoFiles().size());
        Assertions.assertEquals("main.proto", update.getMainFileName());
        verify(apiGrpcProtoMapper, never()).insertSelective(any());
    }

    @Test
    void shouldSoftDeleteGrpcDefinitionByApiDefinitionId() {
        ApiGrpcDefinition existing = buildExistingDefinition("proto-id", "SayHello");
        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(existing));
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.countByProtoId("proto-id")).thenReturn(0L);
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("proto-id");
        proto.setDeleted(false);
        when(apiGrpcProtoMapper.selectByPrimaryKey("proto-id")).thenReturn(proto);

        persistenceService.deleteByApiDefinitionIds(List.of(API_DEFINITION_ID), USER_ID);

        verify(apiGrpcDefinitionMapper).updateByPrimaryKeySelective(argThat(definition ->
                "grpc-def-id".equals(definition.getId()) && Boolean.TRUE.equals(definition.getDeleted())));
        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(Collections.emptyList());
        Assertions.assertNull(persistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID));
    }

    @Test
    void shouldNotDeleteProtoWhenStillReferenced() {
        ApiGrpcDefinition existing = buildExistingDefinition("shared-proto-id", "SayHello");
        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(existing));
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.countByProtoId("shared-proto-id")).thenReturn(1L);

        persistenceService.deleteByApiDefinitionIds(List.of(API_DEFINITION_ID), USER_ID);

        verify(apiGrpcProtoMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void shouldSoftDeleteProtoWhenNoReferences() {
        ApiGrpcDefinition existing = buildExistingDefinition("unused-proto-id", "SayHello");
        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(existing));
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.countByProtoId("unused-proto-id")).thenReturn(0L);
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("unused-proto-id");
        proto.setDeleted(false);
        when(apiGrpcProtoMapper.selectByPrimaryKey("unused-proto-id")).thenReturn(proto);

        persistenceService.deleteByApiDefinitionIds(List.of(API_DEFINITION_ID), USER_ID);

        verify(apiGrpcProtoMapper).updateByPrimaryKeySelective(argThat(item ->
                "unused-proto-id".equals(item.getId()) && Boolean.TRUE.equals(item.getDeleted())));
    }

    @Test
    void shouldRecoverGrpcDefinitionAfterTrash() throws Exception {
        ApiGrpcDefinitionAddDTO grpcAdd = buildHelloWorldAdd("SayHello");
        ApiGrpcDefinition deleted = buildExistingDefinition("proto-id", "SayHello");
        deleted.setDeleted(true);

        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("proto-id");
        proto.setFileName("helloworld.proto");
        proto.setContent(grpcAdd.getProtoContent());
        proto.setDeleted(true);

        when(apiGrpcDefinitionMapper.selectDeletedByApiDefinitionId(API_DEFINITION_ID))
                .thenReturn(List.of(deleted));
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiGrpcProtoMapper.selectByPrimaryKey("proto-id")).thenReturn(proto);
        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(deleted));
        when(apiGrpcProtoMapper.selectByPrimaryKey("proto-id")).thenReturn(proto);

        persistenceService.recoverByApiDefinitionIds(List.of(API_DEFINITION_ID), USER_ID);

        verify(apiGrpcDefinitionMapper).updateByPrimaryKeySelective(argThat(definition ->
                "grpc-def-id".equals(definition.getId()) && Boolean.FALSE.equals(definition.getDeleted())));
        verify(apiGrpcProtoMapper).updateByPrimaryKeySelective(argThat(item ->
                "proto-id".equals(item.getId()) && Boolean.FALSE.equals(item.getDeleted())));

        deleted.setDeleted(false);
        proto.setDeleted(false);
        ApiGrpcDefinitionDTO dto = persistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("SayHello", dto.getMethodName());
    }

    @Test
    void shouldNotRestoreProtoWhenAlreadyActive() {
        ApiGrpcDefinition deleted = buildExistingDefinition("shared-proto-id", "SayHello");
        deleted.setDeleted(true);

        ApiGrpcProto activeProto = new ApiGrpcProto();
        activeProto.setId("shared-proto-id");
        activeProto.setDeleted(false);

        when(apiGrpcDefinitionMapper.selectDeletedByApiDefinitionId(API_DEFINITION_ID))
                .thenReturn(List.of(deleted));
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiGrpcProtoMapper.selectByPrimaryKey("shared-proto-id")).thenReturn(activeProto);

        persistenceService.recoverByApiDefinitionIds(List.of(API_DEFINITION_ID), USER_ID);

        verify(apiGrpcDefinitionMapper).updateByPrimaryKeySelective(argThat(definition ->
                Boolean.FALSE.equals(definition.getDeleted())));
        verify(apiGrpcProtoMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void shouldEnsureInactiveBeforePermanentDelete() {
        ApiGrpcDefinition active = buildExistingDefinition("proto-id", "SayHello");
        when(apiGrpcDefinitionMapper.selectByApiDefinitionId(API_DEFINITION_ID)).thenReturn(List.of(active));
        when(apiGrpcDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiGrpcDefinitionMapper.countByProtoId("proto-id")).thenReturn(0L);
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("proto-id");
        proto.setDeleted(false);
        when(apiGrpcProtoMapper.selectByPrimaryKey("proto-id")).thenReturn(proto);

        persistenceService.ensureInactiveByApiDefinitionIds(List.of(API_DEFINITION_ID), USER_ID);

        verify(apiGrpcDefinitionMapper).updateByPrimaryKeySelective(argThat(definition ->
                Boolean.TRUE.equals(definition.getDeleted())));
    }

    private ApiGrpcDefinition buildExistingDefinition(String protoId, String methodName) {
        ApiGrpcDefinition existing = new ApiGrpcDefinition();
        existing.setId("grpc-def-id");
        existing.setApiDefinitionId(API_DEFINITION_ID);
        existing.setProtoId(protoId);
        existing.setServiceName("helloworld.Greeter");
        existing.setMethodName(methodName);
        existing.setFullMethodName("helloworld.Greeter/" + methodName);
        existing.setMethodType("UNARY");
        existing.setDeleted(false);
        return existing;
    }

    private ApiGrpcDefinitionAddDTO buildHelloWorldAdd(String methodName) throws Exception {
        String protoContent = Files.readString(Path.of("src/test/proto/helloworld.proto"), StandardCharsets.UTF_8);
        ApiGrpcDefinitionAddDTO grpcAdd = new ApiGrpcDefinitionAddDTO();
        grpcAdd.setProtoContent(protoContent);
        grpcAdd.setFileName("helloworld.proto");
        grpcAdd.setServiceName("helloworld.Greeter");
        grpcAdd.setMethodName(methodName);
        return grpcAdd;
    }

    private GrpcProtoFileDTO protoFile(String fileName, String content, boolean main) {
        GrpcProtoFileDTO file = new GrpcProtoFileDTO();
        file.setFileName(fileName);
        file.setContent(content);
        file.setMain(main);
        return file;
    }
}
