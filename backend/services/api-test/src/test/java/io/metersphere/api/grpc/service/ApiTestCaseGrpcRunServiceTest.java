package io.metersphere.api.grpc.service;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.dto.definition.ApiCaseRunRequest;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.grpc.dto.GrpcCaseDebugPayload;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.report.GrpcCaseReportPersistenceService;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.service.definition.ApiTestCaseService;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTestCaseGrpcRunServiceTest {

    private static final String GRPC_CASE_ID = "grpc-case-id";
    private static final String HTTP_CASE_ID = "http-case-id";
    private static final String GRPC_API_ID = "grpc-api-id";
    private static final String HTTP_API_ID = "http-api-id";
    private static final String REPORT_ID = "report-id-1";

    @InjectMocks
    private ApiTestCaseGrpcRunService apiTestCaseGrpcRunService;

    @Mock
    private ApiTestCaseService apiTestCaseService;
    @Mock
    private ApiDefinitionMapper apiDefinitionMapper;
    @Mock
    private GrpcCaseRunService grpcCaseRunService;

    @Mock
    private GrpcCaseReportPersistenceService grpcCaseReportPersistenceService;

    @Test
    void shouldDetectGrpcCase() {
        mockCaseProtocol(GRPC_CASE_ID, GRPC_API_ID, ApiConstants.GRPC_PROTOCOL);
        Assertions.assertTrue(apiTestCaseGrpcRunService.isGrpcCase(GRPC_CASE_ID));
    }

    @Test
    void shouldDetectHttpCase() {
        mockCaseProtocol(HTTP_CASE_ID, HTTP_API_ID, ApiConstants.HTTP_PROTOCOL);
        Assertions.assertFalse(apiTestCaseGrpcRunService.isGrpcCase(HTTP_CASE_ID));
    }

    @Test
    void shouldExtractDebugPayloadFromRunRequest() {
        ApiCaseRunRequest request = new ApiCaseRunRequest();
        request.setId(GRPC_CASE_ID);
        GrpcCaseDebugPayload payload = new GrpcCaseDebugPayload();
        payload.setServerAddress("127.0.0.1:50051");
        ApiGrpcCaseConfigAddDTO config = new ApiGrpcCaseConfigAddDTO();
        config.setRequestJson("{\"name\":\"debug\"}");
        payload.setGrpcCaseConfig(config);
        request.setRequest(payload);

        GrpcCaseRunParamDTO param = apiTestCaseGrpcRunService.extractParam(request);

        Assertions.assertEquals("127.0.0.1:50051", param.getServerAddress());
        Assertions.assertEquals("{\"name\":\"debug\"}", param.getRequestJson());
    }

    @Test
    void shouldPersistReportWhenRunByCaseId() {
        ApiTestCase apiTestCase = mockGrpcCase();
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress("127.0.0.1:50051");
        GrpcCaseRunResultDTO runResult = buildSuccessResult();
        when(grpcCaseRunService.run(GRPC_CASE_ID, param)).thenReturn(runResult);
        when(grpcCaseReportPersistenceService.saveReport(apiTestCase, param, runResult, null)).thenReturn(REPORT_ID);

        GrpcCaseRunResultDTO result = apiTestCaseGrpcRunService.run(GRPC_CASE_ID, param);

        Assertions.assertTrue(result.getSuccess());
        Assertions.assertEquals(REPORT_ID, result.getReportId());
        verify(grpcCaseReportPersistenceService).saveReport(apiTestCase, param, runResult, null);
    }

    @Test
    void shouldPersistReportWithAssertionAndExtractWhenRunByRequest() {
        ApiTestCase apiTestCase = mockGrpcCase();
        ApiCaseRunRequest request = new ApiCaseRunRequest();
        request.setId(GRPC_CASE_ID);
        request.setReportId(REPORT_ID);
        GrpcCaseDebugPayload payload = new GrpcCaseDebugPayload();
        payload.setServerAddress("grpcb.in:9000");
        request.setRequest(payload);

        GrpcCaseRunResultDTO runResult = buildSuccessResult();
        when(grpcCaseRunService.run(eq(GRPC_CASE_ID), any())).thenReturn(runResult);
        when(grpcCaseReportPersistenceService.saveReport(eq(apiTestCase), any(), eq(runResult), eq(REPORT_ID)))
                .thenReturn(REPORT_ID);

        GrpcCaseRunResultDTO result = apiTestCaseGrpcRunService.run(request);

        Assertions.assertEquals(REPORT_ID, result.getReportId());
        ArgumentCaptor<GrpcCaseRunResultDTO> resultCaptor = ArgumentCaptor.forClass(GrpcCaseRunResultDTO.class);
        verify(grpcCaseReportPersistenceService).saveReport(eq(apiTestCase), any(), resultCaptor.capture(), eq(REPORT_ID));
        Assertions.assertEquals(1, resultCaptor.getValue().getAssertionResults().size());
        Assertions.assertEquals(1, resultCaptor.getValue().getExtractResults().size());
    }

    @Test
    void shouldPersistFailedReportWhenRunFails() {
        ApiTestCase apiTestCase = mockGrpcCase();
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        GrpcCaseRunResultDTO runResult = new GrpcCaseRunResultDTO();
        runResult.setSuccess(false);
        runResult.setErrorCode("GRPC_ADDRESS_INVALID");
        when(grpcCaseRunService.run(GRPC_CASE_ID, param)).thenReturn(runResult);
        when(grpcCaseReportPersistenceService.saveReport(apiTestCase, param, runResult, null)).thenReturn(REPORT_ID);

        GrpcCaseRunResultDTO result = apiTestCaseGrpcRunService.run(GRPC_CASE_ID, param);

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(REPORT_ID, result.getReportId());
        verify(grpcCaseReportPersistenceService).saveReport(apiTestCase, param, runResult, null);
    }

    @Test
    void shouldNotPersistReportWhenDebug() {
        ApiCaseRunRequest request = new ApiCaseRunRequest();
        request.setId(GRPC_CASE_ID);
        GrpcCaseDebugPayload payload = new GrpcCaseDebugPayload();
        payload.setServerAddress("127.0.0.1:50051");
        request.setRequest(payload);
        GrpcCaseRunResultDTO runResult = buildSuccessResult();
        when(grpcCaseRunService.run(eq(GRPC_CASE_ID), any())).thenReturn(runResult);

        GrpcCaseRunResultDTO result = apiTestCaseGrpcRunService.debug(request);

        Assertions.assertTrue(result.getSuccess());
        Assertions.assertNull(result.getReportId());
        verify(grpcCaseReportPersistenceService, never()).saveReport(any(), any(), any(), any());
    }

    @Test
    void shouldKeepHttpRunPathIndependent() {
        mockCaseProtocol(HTTP_CASE_ID, HTTP_API_ID, ApiConstants.HTTP_PROTOCOL);
        Assertions.assertFalse(apiTestCaseGrpcRunService.isGrpcCase(HTTP_CASE_ID));
        verify(grpcCaseRunService, never()).run(any(), any());
        verify(grpcCaseReportPersistenceService, never()).saveReport(any(), any(), any(), isNull());
    }

    private ApiTestCase mockGrpcCase() {
        ApiTestCase apiTestCase = new ApiTestCase();
        apiTestCase.setId(GRPC_CASE_ID);
        apiTestCase.setName("gRPC Case");
        apiTestCase.setProjectId("project-id");
        apiTestCase.setApiDefinitionId(GRPC_API_ID);
        when(apiTestCaseService.checkResourceExist(GRPC_CASE_ID)).thenReturn(apiTestCase);
        return apiTestCase;
    }

    private GrpcCaseRunResultDTO buildSuccessResult() {
        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(true);
        result.setGrpcStatus("OK");
        result.setGrpcCode(0);
        result.setResponseJson("{\"reply\":\"hello MeterSphere\"}");
        ResponseAssertionResult assertion = new ResponseAssertionResult();
        assertion.setName("status-ok");
        assertion.setPass(true);
        result.setAssertionResults(List.of(assertion));
        ExtractResult extract = new ExtractResult();
        extract.setName("grpcReply");
        extract.setValue("hello MeterSphere");
        result.setExtractResults(List.of(extract));
        return result;
    }

    private void mockCaseProtocol(String caseId, String apiId, String protocol) {
        ApiTestCase testCase = new ApiTestCase();
        testCase.setId(caseId);
        testCase.setApiDefinitionId(apiId);
        when(apiTestCaseService.checkResourceExist(caseId)).thenReturn(testCase);

        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId(apiId);
        apiDefinition.setProtocol(protocol);
        when(apiDefinitionMapper.selectByPrimaryKey(apiId)).thenReturn(apiDefinition);
    }
}
