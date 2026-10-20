package io.metersphere.plan.service;

import io.metersphere.api.domain.ApiScenario;
import io.metersphere.api.mapper.ApiScenarioMapper;
import io.metersphere.api.service.ApiExecuteService;
import io.metersphere.api.service.scenario.ApiScenarioBatchRunService;
import io.metersphere.plan.domain.TestPlanApiScenario;
import io.metersphere.plan.domain.TestPlanReportApiScenario;
import io.metersphere.plan.mapper.TestPlanApiScenarioMapper;
import io.metersphere.plan.mapper.TestPlanReportApiScenarioMapper;
import io.metersphere.sdk.dto.api.task.ApiRunModeConfigDTO;
import io.metersphere.sdk.dto.api.task.TaskInfo;
import io.metersphere.sdk.dto.api.task.TaskItem;
import io.metersphere.sdk.dto.api.task.TaskRequestDTO;
import io.metersphere.sdk.dto.queue.ExecutionQueue;
import io.metersphere.sdk.dto.queue.ExecutionQueueDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanGrpcScenarioDispatchTest {

    @Mock
    private ApiExecuteService apiExecuteService;
    @Mock
    private ApiScenarioMapper apiScenarioMapper;
    @Mock
    private ApiScenarioBatchRunService apiScenarioBatchRunService;
    @Mock
    private TestPlanApiScenarioMapper testPlanApiScenarioMapper;
    @Mock
    private TestPlanReportApiScenarioMapper testPlanReportApiScenarioMapper;
    @Mock
    private TestPlanApiScenarioBatchRunService testPlanApiScenarioBatchRunService;
    @Mock
    private TestPlanGrpcApiScenarioRunService grpcRunService;

    private TestPlanApiScenarioBatchRunService scenarioBatchRunService;
    private PlanRunTestPlanApiScenarioService planRunScenarioService;

    @BeforeEach
    void setUp() {
        scenarioBatchRunService = new TestPlanApiScenarioBatchRunService();
        ReflectionTestUtils.setField(scenarioBatchRunService, "apiExecuteService", apiExecuteService);
        ReflectionTestUtils.setField(scenarioBatchRunService, "apiScenarioMapper", apiScenarioMapper);
        ReflectionTestUtils.setField(scenarioBatchRunService, "apiScenarioBatchRunService", apiScenarioBatchRunService);
        ReflectionTestUtils.setField(scenarioBatchRunService, "testPlanApiScenarioMapper", testPlanApiScenarioMapper);
        ReflectionTestUtils.setField(scenarioBatchRunService, "testPlanGrpcApiScenarioRunService", grpcRunService);

        planRunScenarioService = new PlanRunTestPlanApiScenarioService();
        ReflectionTestUtils.setField(planRunScenarioService, "apiExecuteService", apiExecuteService);
        ReflectionTestUtils.setField(planRunScenarioService, "apiScenarioMapper", apiScenarioMapper);
        ReflectionTestUtils.setField(planRunScenarioService, "testPlanReportApiScenarioMapper", testPlanReportApiScenarioMapper);
        ReflectionTestUtils.setField(planRunScenarioService, "testPlanApiScenarioBatchRunService", testPlanApiScenarioBatchRunService);
        ReflectionTestUtils.setField(planRunScenarioService, "testPlanGrpcApiScenarioRunService", grpcRunService);
    }

    @Test
    void shouldBypassTaskRunnerForDirectTestPlanGrpcScenario() {
        TestPlanApiScenario planScenario = new TestPlanApiScenario();
        planScenario.setId("plan-scenario-id");
        planScenario.setApiScenarioId("scenario-id");

        ApiScenario apiScenario = new ApiScenario();
        apiScenario.setId("scenario-id");
        apiScenario.setProjectId("project-id");

        ExecutionQueue queue = buildQueue();
        ExecutionQueueDetail detail = buildQueueDetail("plan-scenario-id");

        when(testPlanApiScenarioMapper.selectByPrimaryKey("plan-scenario-id")).thenReturn(planScenario);
        when(apiScenarioMapper.selectByPrimaryKey("scenario-id")).thenReturn(apiScenario);
        when(apiScenarioBatchRunService.getTaskInfo(eq("project-id"), any(ApiRunModeConfigDTO.class)))
                .thenReturn(new TaskInfo());
        when(apiExecuteService.getTaskItem("plan-scenario-id")).thenReturn(new TaskItem());
        when(grpcRunService.executeTestPlanApiScenarioGrpcTaskIfSupported(any(TaskRequestDTO.class), eq(planScenario)))
                .thenReturn(true);

        scenarioBatchRunService.executeNextTask(queue, detail);

        verify(grpcRunService).executeTestPlanApiScenarioGrpcTaskIfSupported(any(TaskRequestDTO.class), eq(planScenario));
        verify(apiExecuteService, never()).execute(any(TaskRequestDTO.class));
    }

    @Test
    void shouldBypassTaskRunnerForPlanRunGrpcScenario() {
        TestPlanReportApiScenario reportScenario = new TestPlanReportApiScenario();
        reportScenario.setId("plan-report-scenario-id");
        reportScenario.setApiScenarioId("scenario-id");

        ApiScenario apiScenario = new ApiScenario();
        apiScenario.setId("scenario-id");
        apiScenario.setProjectId("project-id");

        TaskRequestDTO taskRequest = new TaskRequestDTO();
        taskRequest.setTaskInfo(new TaskInfo());

        ExecutionQueue queue = buildQueue();
        ExecutionQueueDetail detail = buildQueueDetail("plan-report-scenario-id");

        when(testPlanReportApiScenarioMapper.selectByPrimaryKey("plan-report-scenario-id")).thenReturn(reportScenario);
        when(apiScenarioMapper.selectByPrimaryKey("scenario-id")).thenReturn(apiScenario);
        when(testPlanApiScenarioBatchRunService.getTaskRequestDTO(eq("project-id"), any(ApiRunModeConfigDTO.class)))
                .thenReturn(taskRequest);
        when(apiExecuteService.getTaskItem("plan-report-scenario-id")).thenReturn(new TaskItem());
        when(grpcRunService.executePlanRunGrpcTaskIfSupported(any(TaskRequestDTO.class), eq(reportScenario)))
                .thenReturn(true);

        planRunScenarioService.executeNextTask(queue, detail);

        verify(grpcRunService).executePlanRunGrpcTaskIfSupported(any(TaskRequestDTO.class), eq(reportScenario));
        verify(apiExecuteService, never()).execute(any(TaskRequestDTO.class));
    }

    private ExecutionQueue buildQueue() {
        ExecutionQueue queue = new ExecutionQueue();
        queue.setQueueId("queue-id");
        queue.setTaskId("task-id");
        queue.setUserId("user-id");
        queue.setRunModeConfig(new ApiRunModeConfigDTO());
        return queue;
    }

    private ExecutionQueueDetail buildQueueDetail(String resourceId) {
        ExecutionQueueDetail detail = new ExecutionQueueDetail();
        detail.setResourceId(resourceId);
        detail.setTaskItemId("task-item-id");
        return detail;
    }
}
