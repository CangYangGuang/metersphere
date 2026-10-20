package io.metersphere.api.grpc.report;

import io.metersphere.api.domain.ApiScenarioReport;
import io.metersphere.api.domain.ApiScenarioReportDetail;
import io.metersphere.api.domain.ApiScenarioReportDetailBlob;
import io.metersphere.api.domain.ApiScenarioReportStep;
import io.metersphere.api.dto.scenario.ApiScenarioDetail;
import io.metersphere.api.grpc.dto.GrpcTestPlanScenarioReportContext;
import io.metersphere.api.grpc.scenario.GrpcScenarioResultConverter;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioStepResultDTO;
import io.metersphere.api.mapper.ApiScenarioReportDetailBlobMapper;
import io.metersphere.api.mapper.ApiScenarioReportDetailMapper;
import io.metersphere.api.mapper.ApiScenarioReportMapper;
import io.metersphere.api.mapper.ApiScenarioReportStepMapper;
import io.metersphere.api.mapper.ApiScenarioMapper;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.dto.api.result.RequestResult;
import io.metersphere.system.domain.ExecTask;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.mapper.ExecTaskItemMapper;
import io.metersphere.system.mapper.ExecTaskMapper;
import io.metersphere.system.uid.IDGenerator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcScenarioReportPersistenceServiceTest {

    @Mock
    private ApiScenarioReportMapper apiScenarioReportMapper;
    @Mock
    private ApiScenarioMapper apiScenarioMapper;
    @Mock
    private ApiScenarioReportStepMapper apiScenarioReportStepMapper;
    @Mock
    private ApiScenarioReportDetailMapper apiScenarioReportDetailMapper;
    @Mock
    private ApiScenarioReportDetailBlobMapper apiScenarioReportDetailBlobMapper;
    @Mock
    private GrpcScenarioResultConverter grpcScenarioResultConverter;
    @Mock
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Mock
    private ExecTaskMapper execTaskMapper;
    @Mock
    private ExecTaskItemMapper execTaskItemMapper;

    private GrpcScenarioReportPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new GrpcScenarioReportPersistenceService();
        ReflectionTestUtils.setField(service, "apiScenarioReportMapper", apiScenarioReportMapper);
        ReflectionTestUtils.setField(service, "apiScenarioMapper", apiScenarioMapper);
        ReflectionTestUtils.setField(service, "apiScenarioReportStepMapper", apiScenarioReportStepMapper);
        ReflectionTestUtils.setField(service, "apiScenarioReportDetailMapper", apiScenarioReportDetailMapper);
        ReflectionTestUtils.setField(service, "apiScenarioReportDetailBlobMapper", apiScenarioReportDetailBlobMapper);
        ReflectionTestUtils.setField(service, "grpcScenarioResultConverter", grpcScenarioResultConverter);
        ReflectionTestUtils.setField(service, "apiBatchRunBaseService", apiBatchRunBaseService);
        ReflectionTestUtils.setField(service, "execTaskMapper", execTaskMapper);
        ReflectionTestUtils.setField(service, "execTaskItemMapper", execTaskItemMapper);
    }

    @Test
    void shouldPersistScenarioReportAndStepDetail() {
        ApiScenarioDetail apiScenarioDetail = new ApiScenarioDetail();
        apiScenarioDetail.setId("scenario-id");
        apiScenarioDetail.setProjectId("project-id");

        GrpcScenarioRunResultDTO runResult = new GrpcScenarioRunResultDTO();
        runResult.setSuccess(true);
        runResult.setStepResults(List.of(buildStepResult()));

        GrpcTestPlanScenarioReportContext context = new GrpcTestPlanScenarioReportContext();
        context.setReportId("report-id");
        context.setUserId("user-id");

        RequestResult requestResult = new RequestResult();
        requestResult.setMethod("GRPC");
        when(grpcScenarioResultConverter.toRequestResult(any(GrpcScenarioStepResultDTO.class))).thenReturn(requestResult);
        when(apiBatchRunBaseService.computeRequestRate(any(ApiScenarioReport.class), eq(1L))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("detail-id");
            service.completeInitializedReport(apiScenarioDetail, runResult, context);
        }

        ArgumentCaptor<ApiScenarioReport> reportCaptor = ArgumentCaptor.forClass(ApiScenarioReport.class);
        verify(apiScenarioReportMapper).updateByPrimaryKeySelective(reportCaptor.capture());
        assertEquals(ResultStatus.SUCCESS.name(), reportCaptor.getValue().getStatus());

        verify(apiScenarioReportDetailMapper).insertSelective(any(ApiScenarioReportDetail.class));
        verify(apiScenarioReportDetailBlobMapper).batchInsert(anyList());

        ArgumentCaptor<io.metersphere.api.domain.ApiScenario> scenarioCaptor =
                ArgumentCaptor.forClass(io.metersphere.api.domain.ApiScenario.class);
        verify(apiScenarioMapper).updateByPrimaryKeySelective(scenarioCaptor.capture());
        assertEquals("scenario-id", scenarioCaptor.getValue().getId());
        assertEquals("report-id", scenarioCaptor.getValue().getLastReportId());
        assertEquals(ResultStatus.SUCCESS.name(), scenarioCaptor.getValue().getLastReportStatus());
    }

    @Test
    void shouldPersistCompletedReportHeaderStepAndDetail() {
        GrpcScenarioRunResultDTO runResult = new GrpcScenarioRunResultDTO();
        runResult.setSuccess(true);
        runResult.setStepResults(List.of(buildStepResult()));

        GrpcTestPlanScenarioReportContext context = new GrpcTestPlanScenarioReportContext();
        context.setReportId("report-id");
        context.setUserId("user-id");
        context.setProjectId("project-id");
        context.setReportName("grpc-scenario_now");
        context.setScenarioId("scenario-id");
        context.setTriggerMode("MANUAL");
        context.setRunMode("RUN");

        RequestResult requestResult = new RequestResult();
        requestResult.setMethod("GRPC");
        when(apiScenarioReportMapper.selectByPrimaryKey("report-id")).thenReturn(null);
        when(grpcScenarioResultConverter.toRequestResult(any(GrpcScenarioStepResultDTO.class))).thenReturn(requestResult);
        when(apiBatchRunBaseService.computeRequestRate(any(ApiScenarioReport.class), eq(1L))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("detail-id");
            service.persistCompletedReport(runResult, context);
        }

        ArgumentCaptor<ApiScenarioReport> reportCaptor = ArgumentCaptor.forClass(ApiScenarioReport.class);
        verify(apiScenarioReportMapper).insertSelective(reportCaptor.capture());
        assertEquals("report-id", reportCaptor.getValue().getId());
        assertEquals(ResultStatus.SUCCESS.name(), reportCaptor.getValue().getStatus());
        assertEquals(ExecStatus.COMPLETED.name(), reportCaptor.getValue().getExecStatus());
        assertEquals("project-id", reportCaptor.getValue().getProjectId());

        ArgumentCaptor<ApiScenarioReportStep> stepCaptor = ArgumentCaptor.forClass(ApiScenarioReportStep.class);
        verify(apiScenarioReportStepMapper).insertSelective(stepCaptor.capture());
        assertEquals("step-1", stepCaptor.getValue().getStepId());
        assertEquals("NONE", stepCaptor.getValue().getParentId());

        verify(apiScenarioReportDetailMapper).insertSelective(any(ApiScenarioReportDetail.class));
        verify(apiScenarioReportDetailBlobMapper).batchInsert(anyList());
        verify(apiScenarioMapper).updateByPrimaryKeySelective(any(io.metersphere.api.domain.ApiScenario.class));
    }

    @Test
    void shouldUpdateScenarioSummaryWhenReportWasInitialized() {
        GrpcScenarioRunResultDTO runResult = new GrpcScenarioRunResultDTO();
        runResult.setSuccess(true);
        runResult.setStepResults(List.of(buildStepResult()));

        GrpcTestPlanScenarioReportContext context = new GrpcTestPlanScenarioReportContext();
        context.setReportId("report-id");
        context.setScenarioId("scenario-id");
        context.setUserId("user-id");

        when(apiScenarioReportMapper.selectByPrimaryKey("report-id")).thenReturn(new ApiScenarioReport());
        when(grpcScenarioResultConverter.toRequestResult(any(GrpcScenarioStepResultDTO.class)))
                .thenReturn(new RequestResult());
        when(apiBatchRunBaseService.computeRequestRate(any(ApiScenarioReport.class), eq(1L)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("detail-id");
            service.persistCompletedReport(runResult, context);
        }

        ArgumentCaptor<io.metersphere.api.domain.ApiScenario> scenarioCaptor =
                ArgumentCaptor.forClass(io.metersphere.api.domain.ApiScenario.class);
        verify(apiScenarioMapper).updateByPrimaryKeySelective(scenarioCaptor.capture());
        assertEquals("scenario-id", scenarioCaptor.getValue().getId());
        assertEquals("report-id", scenarioCaptor.getValue().getLastReportId());
        assertEquals(ResultStatus.SUCCESS.name(), scenarioCaptor.getValue().getLastReportStatus());
    }

    @Test
    void shouldUpdateScenarioSummaryForFailedIntegratedRun() {
        GrpcScenarioRunResultDTO runResult = new GrpcScenarioRunResultDTO();
        runResult.setSuccess(false);
        GrpcScenarioStepResultDTO failedStep = buildStepResult();
        failedStep.setSuccess(false);
        runResult.setStepResults(List.of(failedStep));

        service.updateScenarioExecutionSummary("scenario-id", "report-id", runResult, "user-id");

        ArgumentCaptor<io.metersphere.api.domain.ApiScenario> scenarioCaptor =
                ArgumentCaptor.forClass(io.metersphere.api.domain.ApiScenario.class);
        verify(apiScenarioMapper).updateByPrimaryKeySelective(scenarioCaptor.capture());
        assertEquals(ResultStatus.ERROR.name(), scenarioCaptor.getValue().getLastReportStatus());
        assertEquals("report-id", scenarioCaptor.getValue().getLastReportId());
        assertEquals("0.00", scenarioCaptor.getValue().getRequestPassRate());
    }

    @Test
    void shouldCompleteExecutionTaskAndItem() {
        GrpcScenarioRunResultDTO runResult = new GrpcScenarioRunResultDTO();
        runResult.setSuccess(false);

        GrpcTestPlanScenarioReportContext context = new GrpcTestPlanScenarioReportContext();
        context.setTaskId("task-id");
        context.setTaskItemId("task-item-id");

        service.completeExecutionTask(runResult, context);

        ArgumentCaptor<ExecTask> taskCaptor = ArgumentCaptor.forClass(ExecTask.class);
        verify(execTaskMapper).updateByPrimaryKeySelective(taskCaptor.capture());
        assertEquals("task-id", taskCaptor.getValue().getId());
        assertEquals(ExecStatus.COMPLETED.name(), taskCaptor.getValue().getStatus());
        assertEquals(ResultStatus.ERROR.name(), taskCaptor.getValue().getResult());

        ArgumentCaptor<ExecTaskItem> itemCaptor = ArgumentCaptor.forClass(ExecTaskItem.class);
        verify(execTaskItemMapper).updateByPrimaryKeySelective(itemCaptor.capture());
        assertEquals("task-item-id", itemCaptor.getValue().getId());
        assertEquals(ExecStatus.COMPLETED.name(), itemCaptor.getValue().getStatus());
        assertEquals(ResultStatus.ERROR.name(), itemCaptor.getValue().getResult());
    }

    @Test
    void shouldPreservePendingRequestCountInIntegratedReport() {
        GrpcScenarioRunResultDTO runResult = new GrpcScenarioRunResultDTO();
        runResult.setSuccess(false);
        runResult.setStepResults(List.of(buildStepResult()));

        GrpcTestPlanScenarioReportContext context = new GrpcTestPlanScenarioReportContext();
        context.setReportId("report-id");
        context.setUserId("user-id");
        context.setPendingCount(2L);

        RequestResult requestResult = new RequestResult();
        when(grpcScenarioResultConverter.toRequestResult(any(GrpcScenarioStepResultDTO.class))).thenReturn(requestResult);
        when(apiBatchRunBaseService.computeRequestRate(any(ApiScenarioReport.class), eq(3L)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("detail-id");
            service.completeInitializedReport(null, runResult, context);
        }

        ArgumentCaptor<ApiScenarioReport> reportCaptor = ArgumentCaptor.forClass(ApiScenarioReport.class);
        verify(apiScenarioReportMapper).updateByPrimaryKeySelective(reportCaptor.capture());
        assertEquals(2L, reportCaptor.getValue().getPendingCount());
    }

    private GrpcScenarioStepResultDTO buildStepResult() {
        GrpcScenarioStepResultDTO stepResult = new GrpcScenarioStepResultDTO();
        stepResult.setStepId("step-1");
        stepResult.setStepName("gRPC step");
        stepResult.setStepType("API_CASE");
        stepResult.setSuccess(true);
        stepResult.setDuration(12L);
        stepResult.setGrpcCode(0);
        stepResult.setResponseJson("{\"ok\":true}");
        stepResult.setServiceName("helloworld.Greeter");
        stepResult.setMethodName("SayHello");
        return stepResult;
    }
}
