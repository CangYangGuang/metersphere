package io.metersphere.api.service.definition;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiDefinitionBlob;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.domain.ApiTestCaseBlob;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.definition.ApiTestCaseAddRequest;
import io.metersphere.api.dto.definition.ApiTestCaseDTO;
import io.metersphere.api.dto.definition.ApiTestCaseUpdateRequest;
import io.metersphere.api.controller.MsHTTPElementTest;
import io.metersphere.api.dto.request.http.MsHTTPElement;
import io.metersphere.api.utils.ApiDataUtils;
import io.metersphere.api.grpc.service.GrpcCasePersistenceService;
import io.metersphere.api.mapper.ApiDefinitionBlobMapper;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.mapper.ApiTestCaseBlobMapper;
import io.metersphere.api.mapper.ApiTestCaseFollowerMapper;
import io.metersphere.api.mapper.ApiTestCaseMapper;
import io.metersphere.api.mapper.ExtApiTestCaseMapper;
import io.metersphere.api.service.ApiCommonService;
import io.metersphere.api.service.ApiFileResourceService;
import io.metersphere.functional.mapper.FunctionalCaseTestMapper;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.ApplicationNumScope;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.uid.NumGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiTestCaseGrpcServiceTest {

    private static final String USER_ID = "admin";
    private static final String CASE_ID = "case-id";
    private static final String GRPC_API_ID = "grpc-api-id";
    private static final String HTTP_API_ID = "http-api-id";
    private static final String PROJECT_ID = "project-id";

    @Spy
    @InjectMocks
    private ApiTestCaseService apiTestCaseService;

    @Mock
    private GrpcCasePersistenceService grpcCasePersistenceService;
    @Mock
    private ApiTestCaseMapper apiTestCaseMapper;
    @Mock
    private ApiTestCaseBlobMapper apiTestCaseBlobMapper;
    @Mock
    private ApiDefinitionMapper apiDefinitionMapper;
    @Mock
    private ApiDefinitionBlobMapper apiDefinitionBlobMapper;
    @Mock
    private ApiTestCaseFollowerMapper apiTestCaseFollowerMapper;
    @Mock
    private ApiCommonService apiCommonService;
    @Mock
    private ExtApiTestCaseMapper extApiTestCaseMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ApiFileResourceService apiFileResourceService;
    @Mock
    private ApiTestCaseLogService apiTestCaseLogService;
    @Mock
    private FunctionalCaseTestMapper functionalCaseTestMapper;

    @Test
    void shouldGetGrpcCaseDetailSuccessfully() {
        ApiTestCase testCase = buildTestCase(CASE_ID, GRPC_API_ID);
        ApiTestCaseBlob caseBlob = new ApiTestCaseBlob();
        caseBlob.setId(CASE_ID);
        caseBlob.setRequest("{}".getBytes(StandardCharsets.UTF_8));

        ApiDefinition apiDefinition = buildGrpcDefinition(GRPC_API_ID);
        ApiGrpcCaseConfigDTO grpcCaseConfig = new ApiGrpcCaseConfigDTO();
        grpcCaseConfig.setRequestJson("{\"name\":\"world\"}");
        grpcCaseConfig.setTimeout(30000);

        when(apiTestCaseMapper.selectByExample(any())).thenReturn(List.of(testCase));
        when(apiTestCaseBlobMapper.selectByPrimaryKey(CASE_ID)).thenReturn(caseBlob);
        doReturn(apiDefinition).when(apiTestCaseService).getApiDefinition(GRPC_API_ID);
        when(apiTestCaseFollowerMapper.selectByExample(any())).thenReturn(Collections.emptyList());
        when(grpcCasePersistenceService.loadDtoByCaseId(CASE_ID)).thenReturn(grpcCaseConfig);

        ApiTestCaseDTO detail = apiTestCaseService.get(CASE_ID, USER_ID);

        Assertions.assertEquals(ApiConstants.GRPC_PROTOCOL, detail.getProtocol());
        Assertions.assertNotNull(detail.getGrpcCaseConfig());
        Assertions.assertEquals("{\"name\":\"world\"}", detail.getGrpcCaseConfig().getRequestJson());
        Assertions.assertNull(detail.getRequest());
        Assertions.assertFalse(detail.getInconsistentWithApi());
        verify(grpcCasePersistenceService).loadDtoByCaseId(CASE_ID);
        verify(apiDefinitionBlobMapper, never()).selectByPrimaryKey(any());
    }

    @Test
    void shouldKeepHttpCaseDetailWorking() {
        ApiTestCase testCase = buildTestCase(CASE_ID, HTTP_API_ID);
        ApiTestCaseBlob caseBlob = new ApiTestCaseBlob();
        caseBlob.setId(CASE_ID);
        caseBlob.setRequest(buildHttpElementBytes());

        ApiDefinition apiDefinition = buildHttpDefinition(HTTP_API_ID);
        ApiDefinitionBlob definitionBlob = new ApiDefinitionBlob();
        definitionBlob.setId(HTTP_API_ID);
        definitionBlob.setRequest(buildHttpElementBytes());

        when(apiTestCaseMapper.selectByExample(any())).thenReturn(List.of(testCase));
        when(apiTestCaseBlobMapper.selectByPrimaryKey(CASE_ID)).thenReturn(caseBlob);
        doReturn(apiDefinition).when(apiTestCaseService).getApiDefinition(HTTP_API_ID);
        when(apiTestCaseFollowerMapper.selectByExample(any())).thenReturn(Collections.emptyList());
        when(apiDefinitionBlobMapper.selectByPrimaryKey(HTTP_API_ID)).thenReturn(definitionBlob);

        ApiTestCaseDTO detail = apiTestCaseService.get(CASE_ID, USER_ID);

        Assertions.assertEquals(ApiConstants.HTTP_PROTOCOL, detail.getProtocol());
        Assertions.assertNull(detail.getGrpcCaseConfig());
        Assertions.assertNotNull(detail.getRequest());
        verify(grpcCasePersistenceService, never()).loadDtoByCaseId(any());
    }

    @Test
    void shouldTreatBlankProtocolAsHttpCaseDetail() {
        ApiTestCase testCase = buildTestCase(CASE_ID, HTTP_API_ID);
        ApiTestCaseBlob caseBlob = new ApiTestCaseBlob();
        caseBlob.setId(CASE_ID);
        caseBlob.setRequest(buildHttpElementBytes());

        ApiDefinition apiDefinition = buildHttpDefinition(HTTP_API_ID);
        apiDefinition.setProtocol(null);
        ApiDefinitionBlob definitionBlob = new ApiDefinitionBlob();
        definitionBlob.setId(HTTP_API_ID);
        definitionBlob.setRequest(buildHttpElementBytes());

        when(apiTestCaseMapper.selectByExample(any())).thenReturn(List.of(testCase));
        when(apiTestCaseBlobMapper.selectByPrimaryKey(CASE_ID)).thenReturn(caseBlob);
        doReturn(apiDefinition).when(apiTestCaseService).getApiDefinition(HTTP_API_ID);
        when(apiTestCaseFollowerMapper.selectByExample(any())).thenReturn(Collections.emptyList());
        when(apiDefinitionBlobMapper.selectByPrimaryKey(HTTP_API_ID)).thenReturn(definitionBlob);

        ApiTestCaseDTO detail = apiTestCaseService.get(CASE_ID, USER_ID);

        Assertions.assertNull(detail.getProtocol());
        Assertions.assertNull(detail.getGrpcCaseConfig());
        verify(grpcCasePersistenceService, never()).loadDtoByCaseId(any());
    }

    @Test
    void shouldSaveGrpcCaseConfigWhenAddingGrpcCase() {
        ApiTestCaseAddRequest request = new ApiTestCaseAddRequest();
        request.setName("grpc-case");
        request.setProjectId(PROJECT_ID);
        request.setApiDefinitionId(GRPC_API_ID);
        request.setPriority("P0");
        request.setStatus("UNDERWAY");
        request.setGrpcCaseConfig(new ApiGrpcCaseConfigAddDTO());

        ApiDefinition apiDefinition = buildGrpcDefinition(GRPC_API_ID);
        doReturn(apiDefinition).when(apiTestCaseService).getApiDefinition(GRPC_API_ID);
        when(projectMapper.selectByPrimaryKey(PROJECT_ID)).thenReturn(new io.metersphere.project.domain.Project());
        when(apiTestCaseMapper.countByExample(any())).thenReturn(0L);
        doReturn(1000L).when(apiTestCaseService).getNextOrder(PROJECT_ID);
        when(apiTestCaseMapper.insertSelective(any())).thenReturn(1);
        when(apiTestCaseBlobMapper.insert(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class);
             MockedStatic<NumGenerator> numGenerator = Mockito.mockStatic(NumGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn(CASE_ID);
            numGenerator.when(() -> NumGenerator.nextNum(any(), eq(ApplicationNumScope.API_TEST_CASE))).thenReturn(10001L);

            apiTestCaseService.addCase(request, USER_ID);
        }

        verify(grpcCasePersistenceService).saveCaseConfig(eq(CASE_ID), eq(GRPC_API_ID), any(), eq(USER_ID));
        ArgumentCaptor<ApiTestCaseBlob> blobCaptor = ArgumentCaptor.forClass(ApiTestCaseBlob.class);
        verify(apiTestCaseBlobMapper).insert(blobCaptor.capture());
        Assertions.assertEquals("{}", new String(blobCaptor.getValue().getRequest(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldKeepHttpAddCaseWorking() {
        ApiTestCaseAddRequest request = new ApiTestCaseAddRequest();
        request.setName("http-case");
        request.setProjectId(PROJECT_ID);
        request.setApiDefinitionId(HTTP_API_ID);
        request.setPriority("P0");
        request.setStatus("UNDERWAY");
        request.setRequest(new MsHTTPElement());

        ApiDefinition apiDefinition = buildHttpDefinition(HTTP_API_ID);
        doReturn(apiDefinition).when(apiTestCaseService).getApiDefinition(HTTP_API_ID);
        when(projectMapper.selectByPrimaryKey(PROJECT_ID)).thenReturn(new io.metersphere.project.domain.Project());
        when(apiTestCaseMapper.countByExample(any())).thenReturn(0L);
        doReturn(1000L).when(apiTestCaseService).getNextOrder(PROJECT_ID);
        doReturn("{}").when(apiTestCaseService).getMsTestElementStr(any());
        when(apiTestCaseMapper.insertSelective(any())).thenReturn(1);
        when(apiTestCaseBlobMapper.insert(any())).thenReturn(1);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class);
             MockedStatic<NumGenerator> numGenerator = Mockito.mockStatic(NumGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn(CASE_ID);
            numGenerator.when(() -> NumGenerator.nextNum(any(), eq(ApplicationNumScope.API_TEST_CASE))).thenReturn(10001L);

            apiTestCaseService.addCase(request, USER_ID);
        }

        verify(grpcCasePersistenceService, never()).saveCaseConfig(any(), any(), any(), any());
    }

    @Test
    void shouldUpdateGrpcCaseConfigWhenUpdatingGrpcCase() {
        ApiTestCaseUpdateRequest request = new ApiTestCaseUpdateRequest();
        request.setId(CASE_ID);
        request.setName("grpc-case-updated");
        request.setPriority("P0");
        request.setStatus("UNDERWAY");
        request.setGrpcCaseConfig(new ApiGrpcCaseConfigAddDTO());

        ApiTestCase testCase = buildTestCase(CASE_ID, GRPC_API_ID);
        ApiDefinition apiDefinition = buildGrpcDefinition(GRPC_API_ID);

        when(apiTestCaseMapper.selectByPrimaryKey(CASE_ID)).thenReturn(testCase);
        doReturn(apiDefinition).when(apiTestCaseService).getApiDefinition(GRPC_API_ID);
        when(apiTestCaseMapper.countByExample(any())).thenReturn(0L);
        when(apiTestCaseMapper.updateByPrimaryKey(any())).thenReturn(1);
        when(apiTestCaseBlobMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        apiTestCaseService.update(request, USER_ID);

        verify(grpcCasePersistenceService).updateCaseConfig(eq(CASE_ID), eq(GRPC_API_ID), any(), eq(USER_ID));
    }

    @Test
    void shouldSoftDeleteGrpcCaseConfigWhenMovingToGc() {
        ApiTestCase testCase = buildTestCase(CASE_ID, GRPC_API_ID);
        when(apiTestCaseMapper.selectByPrimaryKey(CASE_ID)).thenReturn(testCase);
        when(apiTestCaseMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        apiTestCaseService.deleteToGc(CASE_ID, USER_ID);

        verify(grpcCasePersistenceService).softDeleteByCaseIds(List.of(CASE_ID), USER_ID);
    }

    @Test
    void shouldHardDeleteGrpcCaseConfigWhenDeletingResource() {
        ApiTestCase testCase = buildTestCase(CASE_ID, GRPC_API_ID);
        when(extApiTestCaseMapper.getCaseInfoByIds(List.of(CASE_ID), true)).thenReturn(List.of(testCase));

        apiTestCaseService.deleteResourceByIds(List.of(CASE_ID), PROJECT_ID, USER_ID);

        verify(grpcCasePersistenceService).deleteByCaseIds(List.of(CASE_ID));
    }

    private ApiTestCase buildTestCase(String caseId, String apiDefinitionId) {
        ApiTestCase testCase = new ApiTestCase();
        testCase.setId(caseId);
        testCase.setApiDefinitionId(apiDefinitionId);
        testCase.setProjectId(PROJECT_ID);
        testCase.setName("case-name");
        testCase.setDeleted(false);
        return testCase;
    }

    private ApiDefinition buildGrpcDefinition(String apiDefinitionId) {
        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId(apiDefinitionId);
        apiDefinition.setProjectId(PROJECT_ID);
        apiDefinition.setProtocol(ApiConstants.GRPC_PROTOCOL);
        apiDefinition.setNum(1001L);
        apiDefinition.setVersionId("version-id");
        apiDefinition.setName("grpc-api");
        return apiDefinition;
    }

    private ApiDefinition buildHttpDefinition(String apiDefinitionId) {
        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId(apiDefinitionId);
        apiDefinition.setProjectId(PROJECT_ID);
        apiDefinition.setProtocol(ApiConstants.HTTP_PROTOCOL);
        apiDefinition.setNum(1002L);
        apiDefinition.setVersionId("version-id");
        apiDefinition.setName("http-api");
        apiDefinition.setMethod("GET");
        apiDefinition.setPath("/api/test");
        return apiDefinition;
    }

    private byte[] buildHttpElementBytes() {
        return ApiDataUtils.toJSONString(MsHTTPElementTest.getMsHttpElement()).getBytes(StandardCharsets.UTF_8);
    }
}
