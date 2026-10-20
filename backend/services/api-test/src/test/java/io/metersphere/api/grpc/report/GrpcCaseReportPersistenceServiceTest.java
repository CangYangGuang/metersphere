package io.metersphere.api.grpc.report;

import io.metersphere.api.domain.ApiReport;
import io.metersphere.api.domain.ApiReportDetail;
import io.metersphere.api.domain.ApiReportRelateTask;
import io.metersphere.api.domain.ApiReportStep;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.domain.ApiTestCaseRecord;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseReportContentDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.dto.GrpcTestPlanReportContext;
import io.metersphere.api.grpc.service.GrpcCasePersistenceService;
import io.metersphere.api.mapper.ApiReportDetailMapper;
import io.metersphere.api.mapper.ApiReportMapper;
import io.metersphere.api.mapper.ApiReportRelateTaskMapper;
import io.metersphere.api.mapper.ApiReportStepMapper;
import io.metersphere.api.mapper.ApiTestCaseRecordMapper;
import io.metersphere.api.service.ApiCommonService;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.api.service.definition.ApiTestCaseRunService;
import io.metersphere.api.domain.ApiScenarioReport;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.ExecTask;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.mapper.ExecTaskItemMapper;
import io.metersphere.system.mapper.ExecTaskMapper;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcCaseReportPersistenceServiceTest {

    private static final String CASE_ID = "grpc-case-id";
    private static final String REPORT_ID = "report-id-1";

    @Mock
    private ApiReportMapper apiReportMapper;
    @Mock
    private ApiReportDetailMapper apiReportDetailMapper;
    @Mock
    private ApiReportStepMapper apiReportStepMapper;
    @Mock
    private ApiTestCaseRecordMapper apiTestCaseRecordMapper;
    @Mock
    private ApiReportRelateTaskMapper apiReportRelateTaskMapper;
    @Mock
    private ApiTestCaseRunService apiTestCaseRunService;
    @Mock
    private ApiCommonService apiCommonService;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ExecTaskMapper execTaskMapper;
    @Mock
    private ExecTaskItemMapper execTaskItemMapper;
    @Mock
    private GrpcCasePersistenceService grpcCasePersistenceService;
    @Mock
    private GrpcCaseReportConverter grpcCaseReportConverter;
    @Mock
    private ApiBatchRunBaseService apiBatchRunBaseService;

    private GrpcCaseReportPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new GrpcCaseReportPersistenceService();
        ReflectionTestUtils.setField(persistenceService, "apiReportMapper", apiReportMapper);
        ReflectionTestUtils.setField(persistenceService, "apiReportDetailMapper", apiReportDetailMapper);
        ReflectionTestUtils.setField(persistenceService, "apiReportStepMapper", apiReportStepMapper);
        ReflectionTestUtils.setField(persistenceService, "apiTestCaseRecordMapper", apiTestCaseRecordMapper);
        ReflectionTestUtils.setField(persistenceService, "apiReportRelateTaskMapper", apiReportRelateTaskMapper);
        ReflectionTestUtils.setField(persistenceService, "apiTestCaseRunService", apiTestCaseRunService);
        ReflectionTestUtils.setField(persistenceService, "apiCommonService", apiCommonService);
        ReflectionTestUtils.setField(persistenceService, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(persistenceService, "execTaskMapper", execTaskMapper);
        ReflectionTestUtils.setField(persistenceService, "execTaskItemMapper", execTaskItemMapper);
        ReflectionTestUtils.setField(persistenceService, "grpcCasePersistenceService", grpcCasePersistenceService);
        ReflectionTestUtils.setField(persistenceService, "grpcCaseReportConverter", grpcCaseReportConverter);
        ReflectionTestUtils.setField(persistenceService, "apiBatchRunBaseService", apiBatchRunBaseService);
    }

    @Test
    void shouldPersistSuccessReportWithGrpcDetailContent() {
        ApiTestCase apiTestCase = buildCase();
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress("127.0.0.1:50051");
        GrpcCaseRunResultDTO result = buildSuccessResult();
        ApiGrpcCaseConfigDTO caseConfig = new ApiGrpcCaseConfigDTO();
        caseConfig.setServiceName("helloworld.Greeter");
        caseConfig.setMethodName("SayHello");
        caseConfig.setRequestJson("{\"name\":\"ms\"}");
        GrpcCaseReportContentDTO content = buildReportContent();

        when(grpcCasePersistenceService.loadDtoByCaseId(CASE_ID)).thenReturn(caseConfig);
        when(grpcCaseReportConverter.toReportContent(caseConfig, param, result, CASE_ID, "gRPC Case"))
                .thenReturn(content);
        when(apiTestCaseRunService.getApiReport(any())).thenReturn(new ApiReport());
        when(apiTestCaseRunService.getApiReportStep(eq(apiTestCase), eq(REPORT_ID), eq(1L)))
                .thenReturn(new ApiReportStep());
        when(apiTestCaseRunService.getApiTestCaseRecord(apiTestCase, REPORT_ID))
                .thenReturn(new ApiTestCaseRecord());
        when(apiBatchRunBaseService.computeRequestRate(any(), eq(1L))).thenAnswer(invocation -> {
            ApiScenarioReport report = invocation.getArgument(0);
            report.setRequestPassRate("100.00");
            report.setRequestErrorRate("0.00");
            report.setRequestPendingRate("0.00");
            report.setRequestFakeErrorRate("0.00");
            return report;
        });
        mockExecutionHistory(apiTestCase);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("detail-id");

            String savedReportId = persistenceService.saveReport(apiTestCase, param, result, REPORT_ID);

            Assertions.assertEquals(REPORT_ID, savedReportId);
        }

        ArgumentCaptor<ApiReport> reportCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(apiReportMapper).insertSelective(reportCaptor.capture());
        Assertions.assertEquals(REPORT_ID, reportCaptor.getValue().getId());
        Assertions.assertEquals("SUCCESS", reportCaptor.getValue().getStatus());
        Assertions.assertEquals("COMPLETED", reportCaptor.getValue().getExecStatus());

        ArgumentCaptor<ApiReportDetail> detailCaptor = ArgumentCaptor.forClass(ApiReportDetail.class);
        verify(apiReportDetailMapper).insertSelective(detailCaptor.capture());
        ApiReportDetail detail = detailCaptor.getValue();
        Assertions.assertEquals(REPORT_ID, detail.getReportId());
        Assertions.assertEquals(CASE_ID, detail.getStepId());
        Assertions.assertEquals("SUCCESS", detail.getStatus());
        Assertions.assertEquals("0", detail.getCode());

        GrpcCaseReportContentDTO stored = JSON.parseObject(new String(detail.getContent()), GrpcCaseReportContentDTO.class);
        Assertions.assertEquals("GRPC", stored.getMethod());
        Assertions.assertEquals("OK", stored.getGrpcStatus());
        Assertions.assertEquals("{\"message\":\"hello\"}", stored.getResponseJson());
        Assertions.assertEquals(1, stored.getAssertionResults().size());
        Assertions.assertEquals(1, stored.getExtractResults().size());

        assertExecutionHistory("SUCCESS");
    }

    @Test
    void shouldCompleteInitializedTestPlanReport() {
        ApiTestCase apiTestCase = buildCase();
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress("127.0.0.1:50051");
        GrpcCaseRunResultDTO result = buildSuccessResult();
        ApiGrpcCaseConfigDTO caseConfig = new ApiGrpcCaseConfigDTO();
        GrpcCaseReportContentDTO content = buildReportContent();
        GrpcTestPlanReportContext context = new GrpcTestPlanReportContext();
        context.setReportId(REPORT_ID);
        context.setTestPlanCaseId("test-plan-case-id");
        context.setReportDetailStepId("report-step-id");
        context.setPlan(true);
        context.setEnvironmentId("env-id");
        context.setUserId("user-id");

        when(grpcCasePersistenceService.loadDtoByCaseId(CASE_ID)).thenReturn(caseConfig);
        when(grpcCaseReportConverter.toReportContent(caseConfig, param, result, CASE_ID, "gRPC Case"))
                .thenReturn(content);
        when(apiTestCaseRunService.getApiReport("user-id")).thenReturn(new ApiReport());
        when(apiBatchRunBaseService.computeRequestRate(any(), eq(1L))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("detail-id");
            persistenceService.completeInitializedReport(apiTestCase, param, result, context);
        }

        ArgumentCaptor<ApiReport> reportCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(apiReportMapper).updateByPrimaryKeySelective(reportCaptor.capture());
        Assertions.assertEquals(REPORT_ID, reportCaptor.getValue().getId());
        Assertions.assertEquals("test-plan-case-id", reportCaptor.getValue().getTestPlanCaseId());
        Assertions.assertTrue(reportCaptor.getValue().getPlan());
        Assertions.assertEquals("SUCCESS", reportCaptor.getValue().getStatus());

        ArgumentCaptor<ApiReportDetail> detailCaptor = ArgumentCaptor.forClass(ApiReportDetail.class);
        verify(apiReportDetailMapper).insertSelective(detailCaptor.capture());
        Assertions.assertEquals("report-step-id", detailCaptor.getValue().getStepId());
    }

    @Test
    void shouldPersistFailedReport() {
        ApiTestCase apiTestCase = buildCase();
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(false);
        result.setGrpcStatus("UNAVAILABLE");
        result.setGrpcCode(14);
        result.setDuration(30L);
        result.setErrorCode("GRPC_CALL_FAILED");
        result.setErrorMessage("connection refused");

        GrpcCaseReportContentDTO content = new GrpcCaseReportContentDTO();
        content.setMethod("GRPC");
        content.setSuccess(false);
        content.setGrpcStatus("UNAVAILABLE");
        content.setGrpcCode(14);
        content.setErrorCode("GRPC_CALL_FAILED");
        content.setErrorMessage("connection refused");

        when(grpcCasePersistenceService.loadDtoByCaseId(CASE_ID)).thenReturn(null);
        when(grpcCaseReportConverter.toReportContent(null, param, result, CASE_ID, "gRPC Case"))
                .thenReturn(content);
        when(apiTestCaseRunService.getApiReport(any())).thenReturn(new ApiReport());
        when(apiTestCaseRunService.getApiReportStep(eq(apiTestCase), any(), eq(1L)))
                .thenReturn(new ApiReportStep());
        when(apiTestCaseRunService.getApiTestCaseRecord(eq(apiTestCase), any()))
                .thenReturn(new ApiTestCaseRecord());
        when(apiBatchRunBaseService.computeRequestRate(any(), eq(1L))).thenAnswer(invocation -> invocation.getArgument(0));
        mockExecutionHistory(apiTestCase);

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("detail-id");
            persistenceService.saveReport(apiTestCase, param, result, null);
        }

        ArgumentCaptor<ApiReport> reportCaptor = ArgumentCaptor.forClass(ApiReport.class);
        verify(apiReportMapper).insertSelective(reportCaptor.capture());
        Assertions.assertEquals("ERROR", reportCaptor.getValue().getStatus());

        ArgumentCaptor<ApiReportDetail> detailCaptor = ArgumentCaptor.forClass(ApiReportDetail.class);
        verify(apiReportDetailMapper).insertSelective(detailCaptor.capture());
        Assertions.assertEquals("ERROR", detailCaptor.getValue().getStatus());
        Assertions.assertEquals("14", detailCaptor.getValue().getCode());

        assertExecutionHistory("ERROR");
    }

    private void mockExecutionHistory(ApiTestCase apiTestCase) {
        Project project = new Project();
        project.setOrganizationId("org-id");
        when(projectMapper.selectByPrimaryKey(apiTestCase.getProjectId())).thenReturn(project);

        ExecTask task = new ExecTask();
        task.setId("task-id");
        when(apiCommonService.newExecTask(apiTestCase.getProjectId(), "system")).thenReturn(task);

        ExecTaskItem item = new ExecTaskItem();
        item.setId("task-item-id");
        when(apiCommonService.newExecTaskItem("task-id", apiTestCase.getProjectId(), "system")).thenReturn(item);

        when(apiCommonService.getApiReportRelateTask(eq("task-item-id"), any())).thenAnswer(invocation -> {
            ApiReportRelateTask relation = new ApiReportRelateTask();
            relation.setTaskResourceId("task-item-id");
            relation.setReportId(invocation.getArgument(1));
            return relation;
        });
    }

    private void assertExecutionHistory(String expectedResult) {
        ArgumentCaptor<ExecTask> taskCaptor = ArgumentCaptor.forClass(ExecTask.class);
        verify(execTaskMapper).insertSelective(taskCaptor.capture());
        Assertions.assertEquals("COMPLETED", taskCaptor.getValue().getStatus());
        Assertions.assertEquals(expectedResult, taskCaptor.getValue().getResult());

        ArgumentCaptor<ExecTaskItem> itemCaptor = ArgumentCaptor.forClass(ExecTaskItem.class);
        verify(execTaskItemMapper).insertSelective(itemCaptor.capture());
        Assertions.assertEquals(CASE_ID, itemCaptor.getValue().getCaseId());
        Assertions.assertEquals("COMPLETED", itemCaptor.getValue().getStatus());
        Assertions.assertEquals(expectedResult, itemCaptor.getValue().getResult());
        verify(apiReportRelateTaskMapper).insertSelective(any(ApiReportRelateTask.class));
    }

    private ApiTestCase buildCase() {
        ApiTestCase apiTestCase = new ApiTestCase();
        apiTestCase.setId(CASE_ID);
        apiTestCase.setName("gRPC Case");
        apiTestCase.setProjectId("project-id");
        apiTestCase.setEnvironmentId("env-id");
        return apiTestCase;
    }

    private GrpcCaseRunResultDTO buildSuccessResult() {
        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(true);
        result.setGrpcStatus("OK");
        result.setGrpcCode(0);
        result.setDuration(100L);
        result.setResponseJson("{\"message\":\"hello\"}");
        ResponseAssertionResult assertion = new ResponseAssertionResult();
        assertion.setName("status");
        assertion.setPass(true);
        result.setAssertionResults(List.of(assertion));
        io.metersphere.sdk.dto.api.result.ExtractResult extract =
                new io.metersphere.sdk.dto.api.result.ExtractResult();
        extract.setName("msg");
        extract.setValue("hello");
        result.setExtractResults(List.of(extract));
        return result;
    }

    private GrpcCaseReportContentDTO buildReportContent() {
        GrpcCaseReportContentDTO content = new GrpcCaseReportContentDTO();
        content.setMethod("GRPC");
        content.setProtocol("GRPC");
        content.setGrpcStatus("OK");
        content.setGrpcCode(0);
        content.setSuccess(true);
        content.setResponseJson("{\"message\":\"hello\"}");
        ResponseAssertionResult assertion = new ResponseAssertionResult();
        assertion.setName("status");
        assertion.setPass(true);
        content.setAssertionResults(List.of(assertion));
        io.metersphere.sdk.dto.api.result.ExtractResult extract =
                new io.metersphere.sdk.dto.api.result.ExtractResult();
        extract.setName("msg");
        extract.setValue("hello");
        content.setExtractResults(List.of(extract));
        return content;
    }
}
