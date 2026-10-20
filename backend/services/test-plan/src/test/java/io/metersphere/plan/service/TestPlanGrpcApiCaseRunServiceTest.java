package io.metersphere.plan.service;

import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.report.GrpcCaseReportPersistenceService;
import io.metersphere.api.grpc.service.ApiTestCaseGrpcRunService;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.api.grpc.service.GrpcCaseRunService;
import io.metersphere.api.grpc.service.GrpcTestPlanExecuteNoticeService;
import io.metersphere.api.grpc.service.GrpcTestPlanServerAddressResolver;
import io.metersphere.api.mapper.ApiTestCaseMapper;
import io.metersphere.plan.domain.TestPlanReportApiCase;
import io.metersphere.plan.mapper.TestPlanApiCaseMapper;
import io.metersphere.plan.mapper.TestPlanReportApiCaseMapper;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.dto.api.task.GetRunScriptRequest;
import io.metersphere.sdk.dto.api.task.TaskInfo;
import io.metersphere.sdk.dto.api.task.TaskRequestDTO;
import io.metersphere.api.service.ApiCommonService;
import io.metersphere.api.service.queue.ApiExecutionQueueService;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.mapper.ExecTaskItemMapper;
import org.mockito.ArgumentCaptor;
import io.metersphere.sdk.dto.api.task.TaskItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanGrpcApiCaseRunServiceTest {

    private static final String CASE_ID = "grpc-case-id";
    private static final String REPORT_ID = "report-id";

    @Mock
    private ApiTestCaseMapper apiTestCaseMapper;
    @Mock
    private ApiTestCaseGrpcRunService apiTestCaseGrpcRunService;
    @Mock
    private GrpcCaseRunService grpcCaseRunService;
    @Mock
    private GrpcCaseReportPersistenceService grpcCaseReportPersistenceService;
    @Mock
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Mock
    private GrpcTestPlanServerAddressResolver grpcTestPlanServerAddressResolver;
    @Mock
    private GrpcTestPlanExecuteNoticeService grpcTestPlanExecuteNoticeService;
    @Mock
    private TestPlanReportApiCaseMapper testPlanReportApiCaseMapper;
    @Mock
    private TestPlanApiCaseMapper testPlanApiCaseMapper;
    @Mock
    private ApiCommonService apiCommonService;
    @Mock
    private ExecTaskItemMapper execTaskItemMapper;
    @Mock
    private PlanRunTestPlanApiCaseService planRunTestPlanApiCaseService;
    @Mock
    private ApiExecutionQueueService apiExecutionQueueService;

    private TestPlanGrpcApiCaseRunService service;

    @BeforeEach
    void setUp() {
        service = new TestPlanGrpcApiCaseRunService();
        ReflectionTestUtils.setField(service, "apiTestCaseMapper", apiTestCaseMapper);
        ReflectionTestUtils.setField(service, "apiTestCaseGrpcRunService", apiTestCaseGrpcRunService);
        ReflectionTestUtils.setField(service, "grpcCaseRunService", grpcCaseRunService);
        ReflectionTestUtils.setField(service, "grpcCaseReportPersistenceService", grpcCaseReportPersistenceService);
        ReflectionTestUtils.setField(service, "apiBatchRunBaseService", apiBatchRunBaseService);
        ReflectionTestUtils.setField(service, "grpcTestPlanServerAddressResolver", grpcTestPlanServerAddressResolver);
        ReflectionTestUtils.setField(service, "grpcTestPlanExecuteNoticeService", grpcTestPlanExecuteNoticeService);
        ReflectionTestUtils.setField(service, "testPlanReportApiCaseMapper", testPlanReportApiCaseMapper);
        ReflectionTestUtils.setField(service, "testPlanApiCaseMapper", testPlanApiCaseMapper);
        ReflectionTestUtils.setField(service, "apiCommonService", apiCommonService);
        ReflectionTestUtils.setField(service, "execTaskItemMapper", execTaskItemMapper);
        ReflectionTestUtils.setField(service, "planRunTestPlanApiCaseService", planRunTestPlanApiCaseService);
        ReflectionTestUtils.setField(service, "apiExecutionQueueService", apiExecutionQueueService);
    }

    @Test
    void shouldWriteSuccessResultToPlanReportApiCase() {
        ApiTestCase apiTestCase = buildCase();
        TestPlanReportApiCase reportApiCase = buildReportApiCase();
        GetRunScriptRequest request = buildRequest();
        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(true);

        when(apiBatchRunBaseService.getEnvId(any(), any())).thenReturn("env-id");
        when(grpcTestPlanServerAddressResolver.resolve(any())).thenReturn("127.0.0.1:50051");
        when(grpcCaseRunService.run(eq(CASE_ID), any())).thenReturn(result);
        when(grpcTestPlanExecuteNoticeService.resolveReportStatus(true)).thenReturn(ResultStatus.SUCCESS.name());
        when(grpcTestPlanExecuteNoticeService.buildNotice(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new io.metersphere.sdk.dto.api.notice.ApiNoticeDTO());
        when(testPlanApiCaseMapper.selectByPrimaryKey("test-plan-api-case-id")).thenReturn(null);

        service.finishPlanRunGrpcGetRunScript(request, reportApiCase, apiTestCase, REPORT_ID);

        verify(testPlanReportApiCaseMapper).updateByPrimaryKeySelective(any(TestPlanReportApiCase.class));
        verify(grpcTestPlanExecuteNoticeService).publish(any());
    }

    private ApiTestCase buildCase() {
        ApiTestCase apiTestCase = new ApiTestCase();
        apiTestCase.setId(CASE_ID);
        apiTestCase.setName("gRPC Case");
        apiTestCase.setProjectId("project-id");
        return apiTestCase;
    }

    private TestPlanReportApiCase buildReportApiCase() {
        TestPlanReportApiCase reportApiCase = new TestPlanReportApiCase();
        reportApiCase.setId("report-api-case-id");
        reportApiCase.setTestPlanApiCaseId("test-plan-api-case-id");
        reportApiCase.setApiCaseId(CASE_ID);
        reportApiCase.setEnvironmentId("env-id");
        return reportApiCase;
    }


    @Test
    void shouldExecutePlanRunInProcessWithoutTaskRunner() {
        ApiTestCase apiTestCase = buildCase();
        TestPlanReportApiCase reportApiCase = buildReportApiCase();
        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(true);

        TaskRequestDTO taskRequest = new TaskRequestDTO();
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setUserId("user-id");
        taskInfo.setTaskId("task-id");
        taskInfo.setQueueId("queue-id");
        taskRequest.setTaskInfo(taskInfo);
        TaskItem taskItem = new TaskItem();
        taskItem.setId("task-item-id");
        taskRequest.setTaskItem(taskItem);

        when(apiBatchRunBaseService.getEnvId(any(), any())).thenReturn("env-id");
        when(grpcTestPlanServerAddressResolver.resolve(any())).thenReturn("127.0.0.1:50051");
        when(grpcCaseRunService.run(eq(CASE_ID), any())).thenReturn(result);
        when(planRunTestPlanApiCaseService.initApiReport(any(), eq(reportApiCase), eq(apiTestCase))).thenReturn(REPORT_ID);
        when(grpcTestPlanExecuteNoticeService.resolveReportStatus(true)).thenReturn(ResultStatus.SUCCESS.name());
        when(grpcTestPlanExecuteNoticeService.buildNotice(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new io.metersphere.sdk.dto.api.notice.ApiNoticeDTO());
        when(testPlanApiCaseMapper.selectByPrimaryKey("test-plan-api-case-id")).thenReturn(null);
        when(apiExecutionQueueService.getQueue("queue-id")).thenReturn(null);

        service.executePlanRunGrpcTask(taskRequest, reportApiCase, apiTestCase);

        verify(apiCommonService).updateTaskItemRunningStatus(any());
        verify(planRunTestPlanApiCaseService).initApiReport(any(), eq(reportApiCase), eq(apiTestCase));
        verify(grpcCaseReportPersistenceService).completeInitializedReport(eq(apiTestCase), any(), eq(result), any());
        verify(testPlanReportApiCaseMapper).updateByPrimaryKeySelective(any(TestPlanReportApiCase.class));
        verify(grpcTestPlanExecuteNoticeService).publish(any());

        ArgumentCaptor<ExecTaskItem> captor = ArgumentCaptor.forClass(ExecTaskItem.class);
        verify(execTaskItemMapper).updateByPrimaryKeySelective(captor.capture());
        Assertions.assertEquals(ResultStatus.SUCCESS.name(), captor.getValue().getResult());
        Assertions.assertEquals("task-item-id", captor.getValue().getId());
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
