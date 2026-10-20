package io.metersphere.plan.service;

import io.metersphere.api.domain.ApiScenario;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.api.service.ApiCommonService;
import io.metersphere.api.service.ApiExecuteService;
import io.metersphere.api.service.scenario.ApiScenarioRunService;
import io.metersphere.api.service.scenario.ApiScenarioService;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.domain.TestPlanApiScenario;
import io.metersphere.plan.mapper.TestPlanApiScenarioMapper;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.dto.api.task.ApiRunModeConfigDTO;
import io.metersphere.sdk.dto.api.task.TaskInfo;
import io.metersphere.sdk.dto.api.task.TaskItem;
import io.metersphere.sdk.dto.api.task.TaskRequestDTO;
import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.system.domain.ExecTask;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.service.BaseTaskHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanApiScenarioDirectGrpcDispatchTest {

    @Mock
    private TestPlanApiScenarioMapper testPlanApiScenarioMapper;
    @Mock
    private ApiScenarioService apiScenarioService;
    @Mock
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Mock
    private TestPlanApiBatchRunBaseService testPlanApiBatchRunBaseService;
    @Mock
    private ApiScenarioRunService apiScenarioRunService;
    @Mock
    private ApiExecuteService apiExecuteService;
    @Mock
    private TestPlanMapper testPlanMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ApiCommonService apiCommonService;
    @Mock
    private BaseTaskHubService baseTaskHubService;
    @Mock
    private TestPlanGrpcApiScenarioRunService grpcRunService;
    @Mock
    private TestPlanService testPlanService;

    private TestPlanApiScenarioService service;

    @BeforeEach
    void setUp() {
        service = new TestPlanApiScenarioService();
        ReflectionTestUtils.setField(service, "testPlanApiScenarioMapper", testPlanApiScenarioMapper);
        ReflectionTestUtils.setField(service, "apiScenarioService", apiScenarioService);
        ReflectionTestUtils.setField(service, "apiBatchRunBaseService", apiBatchRunBaseService);
        ReflectionTestUtils.setField(service, "testPlanApiBatchRunBaseService", testPlanApiBatchRunBaseService);
        ReflectionTestUtils.setField(service, "apiScenarioRunService", apiScenarioRunService);
        ReflectionTestUtils.setField(service, "apiExecuteService", apiExecuteService);
        ReflectionTestUtils.setField(service, "testPlanMapper", testPlanMapper);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "apiCommonService", apiCommonService);
        ReflectionTestUtils.setField(service, "baseTaskHubService", baseTaskHubService);
        ReflectionTestUtils.setField(service, "testPlanGrpcApiScenarioRunService", grpcRunService);
    }

    @Test
    void shouldBypassTaskRunnerForDirectGrpcScenarioRun() {
        TestPlanApiScenario planScenario = new TestPlanApiScenario();
        planScenario.setId("plan-scenario-id");
        planScenario.setApiScenarioId("scenario-id");
        planScenario.setTestPlanId("plan-id");
        planScenario.setTestPlanCollectionId("collection-id");
        planScenario.setEnvironmentId("environment-id");

        ApiScenario apiScenario = new ApiScenario();
        apiScenario.setId("scenario-id");
        apiScenario.setProjectId("project-id");
        apiScenario.setName("gRPC scenario");

        TestPlan testPlan = new TestPlan();
        testPlan.setId("plan-id");
        testPlan.setProjectId("project-id");

        Project project = new Project();
        project.setId("project-id");
        project.setOrganizationId("organization-id");

        ApiRunModeConfigDTO runModeConfig = new ApiRunModeConfigDTO();
        TaskRequestDTO taskRequest = new TaskRequestDTO();
        taskRequest.setTaskInfo(new TaskInfo());
        TaskItem taskItem = new TaskItem();
        taskItem.setReportId("report-id");
        taskRequest.setTaskItem(taskItem);

        ExecTask execTask = new ExecTask();
        execTask.setId("task-id");
        ExecTaskItem execTaskItem = new ExecTaskItem();
        execTaskItem.setId("task-item-id");

        when(testPlanApiScenarioMapper.selectByPrimaryKey("plan-scenario-id")).thenReturn(planScenario);
        when(apiScenarioService.checkResourceExist("scenario-id")).thenReturn(apiScenario);
        when(testPlanApiBatchRunBaseService.getApiRunModeConfig("collection-id")).thenReturn(runModeConfig);
        when(apiBatchRunBaseService.getEnvId(runModeConfig, "environment-id")).thenReturn("environment-id");
        when(apiScenarioRunService.getTaskRequest(eq("report-id"), eq("plan-scenario-id"),
                eq("project-id"), any(String.class))).thenReturn(taskRequest);
        when(testPlanMapper.selectByPrimaryKey("plan-id")).thenReturn(testPlan);
        when(projectMapper.selectByPrimaryKey("project-id")).thenReturn(project);
        when(apiCommonService.newExecTask("project-id", "user-id")).thenReturn(execTask);
        when(apiCommonService.newExecTaskItem("task-id", "project-id", "user-id")).thenReturn(execTaskItem);
        when(grpcRunService.executeTestPlanApiScenarioGrpcTaskIfSupported(taskRequest, planScenario)).thenReturn(true);

        try (MockedStatic<CommonBeanFactory> commonBeanFactory = Mockito.mockStatic(CommonBeanFactory.class)) {
            commonBeanFactory.when(() -> CommonBeanFactory.getBean(TestPlanService.class)).thenReturn(testPlanService);

            TaskRequestDTO result = service.run("plan-scenario-id", "report-id", "user-id");

            org.junit.jupiter.api.Assertions.assertSame(taskRequest, result);
        }

        verify(grpcRunService).executeTestPlanApiScenarioGrpcTaskIfSupported(taskRequest, planScenario);
        verify(apiExecuteService, never()).executePlanResource(any(TaskRequestDTO.class));
    }
}
