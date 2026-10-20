package io.metersphere.api.grpc;

import io.metersphere.api.domain.ApiGrpcCaseConfig;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.service.GrpcCasePersistenceService;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactAnalyzer;
import io.metersphere.api.grpc.service.GrpcProtoChangeImpactService;
import io.metersphere.api.grpc.service.GrpcProtoVersionService;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import io.metersphere.api.mapper.ApiGrpcCaseConfigMapper;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcCasePersistenceServiceTest {

    private static final String USER_ID = "admin";
    private static final String API_CASE_ID = "case-id";
    private static final String API_DEFINITION_ID = "api-def-id";
    private static final String PROTO_ID = "proto-id";
    private static final String DESCRIPTOR = "descriptor-base64";

    @Mock
    private ApiGrpcCaseConfigMapper apiGrpcCaseConfigMapper;
    @Mock
    private ApiGrpcProtoMapper apiGrpcProtoMapper;
    @Mock
    private GrpcProtoVersionService grpcProtoVersionService;
    @Mock
    private GrpcProtoChangeImpactService grpcProtoChangeImpactService;
    @Mock
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;

    private GrpcCasePersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new GrpcCasePersistenceService();
        ReflectionTestUtils.setField(persistenceService, "apiGrpcCaseConfigMapper", apiGrpcCaseConfigMapper);
        ReflectionTestUtils.setField(persistenceService, "grpcDefinitionPersistenceService", grpcDefinitionPersistenceService);
        ReflectionTestUtils.setField(persistenceService, "apiGrpcProtoMapper", apiGrpcProtoMapper);
        ReflectionTestUtils.setField(persistenceService, "grpcProtoVersionService", grpcProtoVersionService);
        ReflectionTestUtils.setField(persistenceService, "grpcProtoChangeImpactService", grpcProtoChangeImpactService);
        GrpcProtoChangeImpactDTO defaultImpact = new GrpcProtoChangeImpactDTO();
        defaultImpact.setImpactLevel(GrpcProtoChangeImpactAnalyzer.LEVEL_NONE);
        lenient().when(grpcProtoChangeImpactService.analyzeForCase(any(), any())).thenReturn(defaultImpact);
        GrpcProtoVersionDTO defaultVersion = new GrpcProtoVersionDTO();
        defaultVersion.setVersionLabel("v1");
        lenient().when(grpcProtoVersionService.resolveVersion(any())).thenReturn(defaultVersion);
        lenient().when(grpcDefinitionPersistenceService.getActiveProtoId(any())).thenReturn(null);
    }

    @Test
    void shouldSaveGrpcCaseConfigSuccessfully() {
        ApiGrpcCaseConfigAddDTO config = buildCaseConfigAdd();
        ApiGrpcDefinitionDTO definition = buildGrpcDefinition();

        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID)).thenReturn(definition);
        when(apiGrpcCaseConfigMapper.insertSelective(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("generated-case-config-id");

            ApiGrpcCaseConfig saved = persistenceService.saveCaseConfig(
                    API_CASE_ID, API_DEFINITION_ID, config, USER_ID);

            Assertions.assertEquals("generated-case-config-id", saved.getId());
            Assertions.assertEquals(API_CASE_ID, saved.getApiCaseId());
            Assertions.assertEquals("helloworld.Greeter", saved.getServiceName());
            Assertions.assertEquals("SayHello", saved.getMethodName());
            Assertions.assertEquals("127.0.0.1:50051", saved.getServerAddress());
            Assertions.assertEquals("{\"name\":\"world\"}", saved.getRequestJson());
            Assertions.assertEquals(30000, saved.getTimeout());
        }

        ArgumentCaptor<ApiGrpcCaseConfig> captor = ArgumentCaptor.forClass(ApiGrpcCaseConfig.class);
        verify(apiGrpcCaseConfigMapper).insertSelective(captor.capture());
        ApiGrpcCaseConfig inserted = captor.getValue();
        Assertions.assertFalse(inserted.getDeleted());
        Assertions.assertTrue(inserted.getMetadata().contains("trace-id"));
    }

    @Test
    void shouldLoadGrpcCaseConfigDtoSuccessfully() {
        ApiGrpcCaseConfig stored = buildStoredConfig();
        when(apiGrpcCaseConfigMapper.selectByApiCaseId(API_CASE_ID)).thenReturn(stored);

        ApiGrpcCaseConfigDTO dto = persistenceService.loadDtoByCaseId(API_CASE_ID);

        Assertions.assertNotNull(dto);
        Assertions.assertEquals("{\"name\":\"world\"}", dto.getRequestJson());
        Assertions.assertEquals(30000, dto.getTimeout());
        Assertions.assertEquals(1, dto.getMetadata().size());
        Assertions.assertEquals("trace-id", dto.getMetadata().getFirst().getKey());
    }

    @Test
    void shouldUpdateGrpcCaseConfigSuccessfully() {
        ApiGrpcCaseConfig existing = buildStoredConfig();
        ApiGrpcCaseConfigAddDTO config = buildCaseConfigAdd();
        config.setRequestJson("{\"name\":\"updated\"}");
        config.setTimeout(45000);
        ApiGrpcDefinitionDTO definition = buildGrpcDefinition();

        when(apiGrpcCaseConfigMapper.selectByApiCaseId(API_CASE_ID)).thenReturn(existing);
        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID)).thenReturn(definition);
        when(apiGrpcCaseConfigMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        ApiGrpcCaseConfig updated = persistenceService.updateCaseConfig(
                API_CASE_ID, API_DEFINITION_ID, config, USER_ID);

        Assertions.assertEquals("{\"name\":\"updated\"}", updated.getRequestJson());
        Assertions.assertEquals(45000, updated.getTimeout());
        verify(apiGrpcCaseConfigMapper).updateByPrimaryKeySelective(existing);
    }

    @Test
    void shouldSoftDeleteGrpcCaseConfig() {
        ApiGrpcCaseConfig existing = buildStoredConfig();
        when(apiGrpcCaseConfigMapper.selectByApiCaseId(API_CASE_ID)).thenReturn(existing);
        when(apiGrpcCaseConfigMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        persistenceService.softDeleteByCaseIds(List.of(API_CASE_ID), USER_ID);

        ArgumentCaptor<ApiGrpcCaseConfig> captor = ArgumentCaptor.forClass(ApiGrpcCaseConfig.class);
        verify(apiGrpcCaseConfigMapper).updateByPrimaryKeySelective(captor.capture());
        Assertions.assertTrue(captor.getValue().getDeleted());
    }

    @Test
    void shouldHardDeleteGrpcCaseConfig() {
        persistenceService.deleteByCaseIds(List.of(API_CASE_ID));
        verify(apiGrpcCaseConfigMapper).deleteByApiCaseIds(List.of(API_CASE_ID));
    }

    @Test
    void shouldReturnNullWhenCaseConfigNotFoundOnLoad() {
        when(apiGrpcCaseConfigMapper.selectByApiCaseId(API_CASE_ID)).thenReturn(null);
        Assertions.assertNull(persistenceService.loadDtoByCaseId(API_CASE_ID));
    }

    @Test
    void shouldFailWhenConfigIsNull() {
        MSException exception = Assertions.assertThrows(MSException.class,
                () -> persistenceService.saveCaseConfig(API_CASE_ID, API_DEFINITION_ID, null, USER_ID));
        Assertions.assertEquals(GrpcResultCode.GRPC_CASE_CONFIG_REQUIRED, exception.getErrorCode());
        verify(apiGrpcCaseConfigMapper, never()).insertSelective(any());
    }

    @Test
    void shouldFailWhenGrpcDefinitionNotFound() {
        ApiGrpcCaseConfigAddDTO config = buildCaseConfigAdd();
        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(API_DEFINITION_ID)).thenReturn(null);

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> persistenceService.saveCaseConfig(API_CASE_ID, API_DEFINITION_ID, config, USER_ID));
        Assertions.assertEquals(GrpcResultCode.GRPC_DEFINITION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldFailWhenUpdatingMissingCaseConfig() {
        ApiGrpcCaseConfigAddDTO config = buildCaseConfigAdd();
        when(apiGrpcCaseConfigMapper.selectByApiCaseId(API_CASE_ID)).thenReturn(null);

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> persistenceService.updateCaseConfig(API_CASE_ID, API_DEFINITION_ID, config, USER_ID));
        Assertions.assertEquals(GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldRecoverSoftDeletedCaseConfig() {
        ApiGrpcCaseConfig deleted = buildStoredConfig();
        deleted.setDeleted(true);
        when(apiGrpcCaseConfigMapper.selectDeletedByApiCaseId(API_CASE_ID)).thenReturn(List.of(deleted));
        when(apiGrpcCaseConfigMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        persistenceService.recoverByCaseIds(List.of(API_CASE_ID), USER_ID);

        ArgumentCaptor<ApiGrpcCaseConfig> captor = ArgumentCaptor.forClass(ApiGrpcCaseConfig.class);
        verify(apiGrpcCaseConfigMapper).updateByPrimaryKeySelective(captor.capture());
        Assertions.assertFalse(captor.getValue().getDeleted());
    }

    private ApiGrpcCaseConfigAddDTO buildCaseConfigAdd() {
        ApiGrpcCaseConfigAddDTO config = new ApiGrpcCaseConfigAddDTO();
        config.setServiceName("helloworld.Greeter");
        config.setMethodName("SayHello");
        config.setServerAddress("127.0.0.1:50051");
        config.setDescriptorContent(DESCRIPTOR);
        config.setRequestJson("{\"name\":\"world\"}");
        GrpcMetadataItemDTO metadataItem = new GrpcMetadataItemDTO();
        metadataItem.setKey("trace-id");
        metadataItem.setValue("abc");
        config.setMetadata(List.of(metadataItem));
        config.setTimeout(30000);
        return config;
    }

    private ApiGrpcDefinitionDTO buildGrpcDefinition() {
        ApiGrpcDefinitionDTO definition = new ApiGrpcDefinitionDTO();
        definition.setProtoId(PROTO_ID);
        definition.setServiceName("helloworld.Greeter");
        definition.setMethodName("SayHello");
        definition.setDescriptorContent(DESCRIPTOR);
        return definition;
    }

    private ApiGrpcCaseConfig buildStoredConfig() {
        ApiGrpcCaseConfig config = new ApiGrpcCaseConfig();
        config.setId("config-id");
        config.setApiCaseId(API_CASE_ID);
        config.setApiDefinitionId(API_DEFINITION_ID);
        config.setProtoId(PROTO_ID);
        config.setServiceName("helloworld.Greeter");
        config.setMethodName("SayHello");
        config.setServerAddress("127.0.0.1:50051");
        config.setDescriptorContent(DESCRIPTOR);
        config.setRequestJson("{\"name\":\"world\"}");
        config.setMetadata(JSON.toJSONString(buildCaseConfigAdd().getMetadata()));
        config.setTimeout(30000);
        config.setDeleted(false);
        return config;
    }
}
