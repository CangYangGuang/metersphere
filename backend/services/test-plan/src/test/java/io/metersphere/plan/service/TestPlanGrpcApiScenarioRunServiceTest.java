package io.metersphere.plan.service;

import io.metersphere.api.constants.ApiScenarioStepRefType;
import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.scenario.ApiScenarioDetail;
import io.metersphere.api.dto.scenario.ApiScenarioStepDTO;
import io.metersphere.api.grpc.report.GrpcScenarioReportPersistenceService;
import io.metersphere.api.grpc.scenario.GrpcScenarioRunAdapter;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioStepResultDTO;
import io.metersphere.api.grpc.service.GrpcTestPlanExecuteNoticeService;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.api.service.ApiCommonService;
import io.metersphere.api.service.queue.ApiExecutionQueueService;
import io.metersphere.api.service.scenario.ApiScenarioRunService;
import io.metersphere.plan.domain.TestPlanReportApiScenario;
import io.metersphere.plan.mapper.TestPlanApiScenarioMapper;
import io.metersphere.plan.mapper.TestPlanReportApiScenarioMapper;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.constants.TaskTriggerMode;
import io.metersphere.sdk.dto.api.notice.ApiNoticeDTO;
import io.metersphere.sdk.dto.api.task.ApiRunModeConfigDTO;
import io.metersphere.sdk.dto.api.task.GetRunScriptRequest;
import io.metersphere.sdk.dto.api.task.TaskInfo;
import io.metersphere.sdk.dto.api.task.TaskItem;
import io.metersphere.sdk.dto.api.task.TaskRequestDTO;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.mapper.ExecTaskItemMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanGrpcApiScenarioRunServiceTest {

    private static final String SCENARIO_ID = "scenario-id";
    private static final String REPORT_ID = "report-id";

    @Mock
    private GrpcScenarioRunAdapter grpcScenarioRunAdapter;
    @Mock
    private GrpcScenarioReportPersistenceService grpcScenarioReportPersistenceService;
    @Mock
    private GrpcTestPlanExecuteNoticeService grpcTestPlanExecuteNoticeService;
    @Mock
    private TestPlanReportApiScenarioMapper testPlanReportApiScenarioMapper;
    @Mock
    private TestPlanApiScenarioMapper testPlanApiScenarioMapper;
    @Mock
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Mock
    private ApiExecutionQueueService apiExecutionQueueService;
    @Mock
    private ApiCommonService apiCommonService;
    @Mock
    private ExecTaskItemMapper execTaskItemMapper;
    @Mock
    private ApiScenarioRunService apiScenarioRunService;
    @Mock
    private PlanRunTestPlanApiScenarioService planRunTestPlanApiScenarioService;

    private TestPlanGrpcApiScenarioRunService service;

    @BeforeEach
    void setUp() {
        service = new TestPlanGrpcApiScenarioRunService();
        ReflectionTestUtils.setField(service, "grpcScenarioRunAdapter", grpcScenarioRunAdapter);
        ReflectionTestUtils.setField(service, "grpcScenarioReportPersistenceService", grpcScenarioReportPersistenceService);
        ReflectionTestUtils.setField(service, "grpcTestPlanExecuteNoticeService", grpcTestPlanExecuteNoticeService);
        ReflectionTestUtils.setField(service, "testPlanReportApiScenarioMapper", testPlanReportApiScenarioMapper);
        ReflectionTestUtils.setField(service, "testPlanApiScenarioMapper", testPlanApiScenarioMapper);
        ReflectionTestUtils.setField(service, "apiBatchRunBaseService", apiBatchRunBaseService);
        ReflectionTestUtils.setField(service, "apiExecutionQueueService", apiExecutionQueueService);
        ReflectionTestUtils.setField(service, "apiCommonService", apiCommonService);
        ReflectionTestUtils.setField(service, "execTaskItemMapper", execTaskItemMapper);
        ReflectionTestUtils.setField(service, "apiScenarioRunService", apiScenarioRunService);
        ReflectionTestUtils.setField(service, "planRunTestPlanApiScenarioService", planRunTestPlanApiScenarioService);
    }

    @Test
    void shouldWriteSuccessResultToPlanReportApiScenario() {
        ApiScenarioDetail apiScenarioDetail = buildScenarioDetail();
        TestPlanReportApiScenario reportApiScenario = buildReportApiScenario();
        GetRunScriptRequest request = buildRequest();
        GrpcScenarioRunResultDTO result = new GrpcScenarioRunResultDTO();
        result.setSuccess(true);
        GrpcScenarioStepResultDTO stepResult = new GrpcScenarioStepResultDTO();
        stepResult.setSuccess(true);
        result.setStepResults(List.of(stepResult));

        when(apiBatchRunBaseService.getEnvId(any(), any())).thenReturn("env-id");
        when(grpcScenarioRunAdapter.buildContext(any(), any())).thenReturn(null);
        when(grpcScenarioRunAdapter.run(any())).thenReturn(result);
        when(grpcTestPlanExecuteNoticeService.resolveReportStatus(true)).thenReturn(ResultStatus.SUCCESS.name());
        when(grpcTestPlanExecuteNoticeService.buildNotice(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new io.metersphere.sdk.dto.api.notice.ApiNoticeDTO());
        when(testPlanApiScenarioMapper.selectByPrimaryKey("test-plan-api-scenario-id")).thenReturn(null);

        service.finishPlanRunGrpcGetRunScript(request, reportApiScenario, apiScenarioDetail, REPORT_ID);

        verify(testPlanReportApiScenarioMapper).updateByPrimaryKeySelective(any(TestPlanReportApiScenario.class));
        verify(grpcTestPlanExecuteNoticeService).publish(any());
    }

    @Test
    void shouldAllowGrpcScenarioWithWaitStep() {
        ApiScenarioDetail detail = buildScenarioDetail();
        detail.setSteps(List.of(buildGrpcStep(), buildWaitStep()));

        Assertions.assertDoesNotThrow(() -> service.validateExecutableScenario(detail));
    }

    @Test
    void shouldRejectGrpcScenarioWithHttpStep() {
        ApiScenarioDetail detail = buildScenarioDetail();
        ApiScenarioStepDTO httpStep = new ApiScenarioStepDTO();
        httpStep.setId("http-step");
        httpStep.setUniqueId("http-step");
        httpStep.setEnable(true);
        httpStep.setStepType(ApiScenarioStepType.API_CASE.name());
        httpStep.setConfig(Map.of("protocol", "HTTP"));
        detail.setSteps(List.of(buildGrpcStep(), httpStep));

        Assertions.assertThrows(MSException.class, () -> service.validateExecutableScenario(detail));
    }

    @Test
    void shouldUseRunModePoolAndManualTriggerForDirectExecution() {
        ApiRunModeConfigDTO runModeConfig = new ApiRunModeConfigDTO();
        runModeConfig.setPoolId("default-pool-id");
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setRunModeConfig(runModeConfig);
        TaskRequestDTO taskRequest = new TaskRequestDTO();
        taskRequest.setTaskInfo(taskInfo);
        taskRequest.setTaskItem(new TaskItem());

        GetRunScriptRequest request = ReflectionTestUtils.invokeMethod(service, "toGetRunScriptRequest", taskRequest);

        Assertions.assertNotNull(request);
        Assertions.assertEquals("default-pool-id", request.getPoolId());
        Assertions.assertEquals(TaskTriggerMode.MANUAL.name(), request.getTriggerMode());
    }

    @Test
    void shouldMarkFinishedSerialCollectionWhenQueueIsExhausted() {
        ApiNoticeDTO notice = new ApiNoticeDTO();
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setQueueId("queue-id");
        GetRunScriptRequest request = buildRequest();
        GrpcScenarioRunResultDTO result = new GrpcScenarioRunResultDTO();
        result.setSuccess(true);

        when(grpcTestPlanExecuteNoticeService.buildNotice(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(notice);
        when(apiExecutionQueueService.getQueue("queue-id")).thenReturn(null);

        ApiNoticeDTO actual = ReflectionTestUtils.invokeMethod(service, "buildNotice", request, taskInfo,
                "PLAN_RUN_API_SCENARIO", "resource-id", REPORT_ID, result, buildScenarioDetail(), "task-item-id");

        Assertions.assertNotNull(actual);
        Assertions.assertTrue(actual.getChildCollectionExecuteOver());
    }

    @Test
    void shouldCompleteTaskLifecycleForDirectPlanRun() {
        ApiScenarioDetail detail = buildScenarioDetail();
        TestPlanReportApiScenario reportApiScenario = buildReportApiScenario();
        GrpcScenarioRunResultDTO result = new GrpcScenarioRunResultDTO();
        result.setSuccess(true);
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskId("task-id");
        taskInfo.setUserId("user-id");
        TaskItem taskItem = new TaskItem();
        taskItem.setId("task-item-id");
        TaskRequestDTO taskRequest = new TaskRequestDTO();
        taskRequest.setTaskInfo(taskInfo);
        taskRequest.setTaskItem(taskItem);

        when(planRunTestPlanApiScenarioService.initReport(any(), any(), any())).thenReturn(REPORT_ID);
        when(grpcScenarioRunAdapter.buildContext(any(), any())).thenReturn(null);
        when(grpcScenarioRunAdapter.run(any())).thenReturn(result);
        when(apiBatchRunBaseService.getEnvId(any(), any())).thenReturn("env-id");
        when(grpcTestPlanExecuteNoticeService.resolveReportStatus(true)).thenReturn(ResultStatus.SUCCESS.name());
        when(grpcTestPlanExecuteNoticeService.buildNotice(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(new ApiNoticeDTO());
        when(testPlanApiScenarioMapper.selectByPrimaryKey("test-plan-api-scenario-id")).thenReturn(null);

        service.executePlanRunGrpcTask(taskRequest, reportApiScenario, detail);

        verify(apiCommonService).updateTaskRunningStatus("task-id");
        verify(apiCommonService).updateTaskItemRunningStatus(any(GetRunScriptRequest.class));
        ArgumentCaptor<ExecTaskItem> taskItemCaptor = ArgumentCaptor.forClass(ExecTaskItem.class);
        verify(execTaskItemMapper).updateByPrimaryKeySelective(taskItemCaptor.capture());
        Assertions.assertEquals("task-item-id", taskItemCaptor.getValue().getId());
        Assertions.assertEquals(ExecStatus.COMPLETED.name(), taskItemCaptor.getValue().getStatus());
        Assertions.assertEquals(ResultStatus.SUCCESS.name(), taskItemCaptor.getValue().getResult());
    }

    private ApiScenarioDetail buildScenarioDetail() {
        ApiScenarioDetail detail = new ApiScenarioDetail();
        detail.setId(SCENARIO_ID);
        detail.setProjectId("project-id");
        detail.setName("gRPC Scenario");
        detail.setSteps(List.of(buildGrpcStep()));
        return detail;
    }

    private ApiScenarioStepDTO buildGrpcStep() {
        ApiScenarioStepDTO step = new ApiScenarioStepDTO();
        step.setId("step-id");
        step.setUniqueId("step-unique");
        step.setEnable(true);
        step.setName("gRPC step");
        step.setStepType(ApiScenarioStepType.API_CASE.name());
        step.setRefType(ApiScenarioStepRefType.REF.name());
        step.setResourceId("grpc-case-id");
        step.setProjectId("project-id");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("protocol", "GRPC");
        step.setConfig(config);
        return step;
    }

    private ApiScenarioStepDTO buildWaitStep() {
        ApiScenarioStepDTO step = new ApiScenarioStepDTO();
        step.setId("wait-step");
        step.setUniqueId("wait-step");
        step.setEnable(true);
        step.setName("wait time");
        step.setStepType(ApiScenarioStepType.CONSTANT_TIMER.name());
        step.setProjectId("project-id");
        step.setConfig(Map.of("delay", 3000));
        return step;
    }

    private TestPlanReportApiScenario buildReportApiScenario() {
        TestPlanReportApiScenario reportApiScenario = new TestPlanReportApiScenario();
        reportApiScenario.setId("report-api-scenario-id");
        reportApiScenario.setTestPlanApiScenarioId("test-plan-api-scenario-id");
        reportApiScenario.setApiScenarioId(SCENARIO_ID);
        reportApiScenario.setEnvironmentId("env-id");
        return reportApiScenario;
    }

    private GetRunScriptRequest buildRequest() {
        GetRunScriptRequest request = new GetRunScriptRequest();
        request.setUserId("user-id");
        request.setTaskId("task-id");
        TaskItem taskItem = new TaskItem();
        taskItem.setId("task-item-id");
        request.setTaskItem(taskItem);
        return request;
    }
}
