package io.metersphere.plan.service;

import io.metersphere.api.dto.scenario.ApiScenarioDetail;
import io.metersphere.api.grpc.dto.GrpcTestPlanScenarioReportContext;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.report.GrpcScenarioReportPersistenceService;
import io.metersphere.api.grpc.scenario.GrpcScenarioRunAdapter;
import io.metersphere.api.grpc.scenario.GrpcScenarioStepSupport;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.api.grpc.service.GrpcTestPlanExecuteNoticeService;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.api.service.ApiCommonService;
import io.metersphere.api.service.queue.ApiExecutionQueueService;
import io.metersphere.api.service.queue.ApiExecutionSetService;
import io.metersphere.api.service.scenario.ApiScenarioRunService;
import io.metersphere.plan.domain.TestPlanApiScenario;
import io.metersphere.plan.domain.TestPlanReportApiScenario;
import io.metersphere.plan.mapper.TestPlanApiScenarioMapper;
import io.metersphere.plan.mapper.TestPlanReportApiScenarioMapper;
import io.metersphere.sdk.constants.ApiExecuteResourceType;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.constants.TaskTriggerMode;
import io.metersphere.sdk.dto.api.notice.ApiNoticeDTO;
import io.metersphere.sdk.dto.api.task.*;
import io.metersphere.sdk.dto.queue.ExecutionQueue;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.mapper.ExecTaskItemMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class TestPlanGrpcApiScenarioRunService {

    @Resource
    private ApiScenarioRunService apiScenarioRunService;
    @Resource
    private GrpcScenarioRunAdapter grpcScenarioRunAdapter;
    @Resource
    private GrpcScenarioReportPersistenceService grpcScenarioReportPersistenceService;
    @Resource
    private GrpcTestPlanExecuteNoticeService grpcTestPlanExecuteNoticeService;
    @Resource
    private TestPlanReportApiScenarioMapper testPlanReportApiScenarioMapper;
    @Resource
    private TestPlanApiScenarioMapper testPlanApiScenarioMapper;
    @Resource
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Resource
    private ApiExecutionSetService apiExecutionSetService;
    @Resource
    private ApiExecutionQueueService apiExecutionQueueService;
    @Resource
    private ApiCommonService apiCommonService;
    @Resource
    private ExecTaskItemMapper execTaskItemMapper;
    @Resource
    private PlanRunTestPlanApiScenarioService planRunTestPlanApiScenarioService;
    @Resource
    private TestPlanApiScenarioService testPlanApiScenarioService;

    public boolean isGrpcScenario(ApiScenarioDetail apiScenarioDetail) {
        return apiScenarioDetail != null && grpcScenarioRunAdapter.supportsInProcessRun(apiScenarioDetail.getSteps());
    }

    public void validateExecutableScenario(ApiScenarioDetail apiScenarioDetail) {
        if (apiScenarioDetail == null) {
            return;
        }
        if (GrpcScenarioStepSupport.isMixedScenario(apiScenarioDetail.getSteps())) {
            throw new MSException(GrpcResultCode.GRPC_MIXED_SCENARIO_NOT_SUPPORTED);
        }
    }

    public GetRunScriptResult finishPlanRunGrpcGetRunScript(GetRunScriptRequest request,
                                                            TestPlanReportApiScenario reportApiScenario,
                                                            ApiScenarioDetail apiScenarioDetail,
                                                            String reportId) {
        validateExecutableScenario(apiScenarioDetail);
        GrpcScenarioRunResultDTO result = runGrpcScenario(apiScenarioDetail, request);
        completePlanRunReport(request, reportApiScenario, apiScenarioDetail, reportId, result);
        publishPlanRunNotice(request, null, reportApiScenario.getId(), reportId, result, apiScenarioDetail, request.getTaskItem().getId());
        return buildEmptyRunScriptResult(reportId, apiScenarioDetail);
    }

    public GetRunScriptResult finishTestPlanApiScenarioGrpcGetRunScript(GetRunScriptRequest request,
                                                                        TestPlanApiScenario testPlanApiScenario,
                                                                        ApiScenarioDetail apiScenarioDetail,
                                                                        String reportId) {
        validateExecutableScenario(apiScenarioDetail);
        GrpcScenarioRunResultDTO result = runGrpcScenario(apiScenarioDetail, request);
        completeTestPlanApiScenarioReport(request, testPlanApiScenario, apiScenarioDetail, reportId, result);
        publishTestPlanApiScenarioNotice(request, null, testPlanApiScenario.getId(), reportId, result, apiScenarioDetail, request.getTaskItem().getId());
        return buildEmptyRunScriptResult(reportId, apiScenarioDetail);
    }

    public void executePlanRunGrpcTask(TaskRequestDTO taskRequest,
                                       TestPlanReportApiScenario reportApiScenario,
                                       ApiScenarioDetail apiScenarioDetail) {
        GetRunScriptRequest request = toGetRunScriptRequest(taskRequest);
        markTaskRunning(request);
        String reportId = planRunTestPlanApiScenarioService.initReport(request, reportApiScenario, apiScenarioDetail);
        apiScenarioRunService.initScenarioReportSteps(apiScenarioDetail.getSteps(), reportId);
        validateExecutableScenario(apiScenarioDetail);
        GrpcScenarioRunResultDTO result = runGrpcScenario(apiScenarioDetail, request);
        completePlanRunReport(request, reportApiScenario, apiScenarioDetail, reportId, result);
        publishPlanRunNotice(request, taskRequest.getTaskInfo(), reportApiScenario.getId(), reportId, result, apiScenarioDetail, taskRequest.getTaskItem().getId());
        markTaskItemCompleted(taskRequest.getTaskItem().getId(), result);
    }

    public boolean executePlanRunGrpcTaskIfSupported(TaskRequestDTO taskRequest,
                                                     TestPlanReportApiScenario reportApiScenario) {
        ApiScenarioDetail apiScenarioDetail = loadScenarioDetail(reportApiScenario.getApiScenarioId(),
                reportApiScenario.getEnvironmentId(), reportApiScenario.getGrouped());
        if (!isGrpcScenario(apiScenarioDetail)) {
            return false;
        }
        executePlanRunGrpcTask(taskRequest, reportApiScenario, apiScenarioDetail);
        return true;
    }

    public void executeTestPlanApiScenarioGrpcTask(TaskRequestDTO taskRequest,
                                                   TestPlanApiScenario testPlanApiScenario,
                                                   ApiScenarioDetail apiScenarioDetail) {
        GetRunScriptRequest request = toGetRunScriptRequest(taskRequest);
        markTaskRunning(request);
        String reportId = testPlanApiScenarioService.initApiScenarioReport(testPlanApiScenario, apiScenarioDetail, request);
        apiScenarioRunService.initScenarioReportSteps(apiScenarioDetail.getSteps(), reportId);
        validateExecutableScenario(apiScenarioDetail);
        GrpcScenarioRunResultDTO result = runGrpcScenario(apiScenarioDetail, request);
        completeTestPlanApiScenarioReport(request, testPlanApiScenario, apiScenarioDetail, reportId, result);
        publishTestPlanApiScenarioNotice(request, taskRequest.getTaskInfo(), testPlanApiScenario.getId(), reportId, result, apiScenarioDetail, taskRequest.getTaskItem().getId());
        markTaskItemCompleted(taskRequest.getTaskItem().getId(), result);
    }

    public boolean executeTestPlanApiScenarioGrpcTaskIfSupported(TaskRequestDTO taskRequest,
                                                                 TestPlanApiScenario testPlanApiScenario) {
        ApiScenarioDetail apiScenarioDetail = loadScenarioDetail(testPlanApiScenario.getApiScenarioId(),
                testPlanApiScenario.getEnvironmentId(), testPlanApiScenario.getGrouped());
        if (!isGrpcScenario(apiScenarioDetail)) {
            return false;
        }
        executeTestPlanApiScenarioGrpcTask(taskRequest, testPlanApiScenario, apiScenarioDetail);
        return true;
    }

    public void runPlanRunGrpcParallelItems(TaskBatchRequestDTO taskRequest, List<TaskItem> grpcTaskItems) {
        if (grpcTaskItems == null || grpcTaskItems.isEmpty()) {
            return;
        }
        TaskInfo taskInfo = taskRequest.getTaskInfo();
        Map<String, TestPlanReportApiScenario> reportScenarioMap = grpcTaskItems.stream()
                .map(item -> testPlanReportApiScenarioMapper.selectByPrimaryKey(item.getResourceId()))
                .filter(item -> item != null)
                .collect(Collectors.toMap(TestPlanReportApiScenario::getId, Function.identity(), (left, right) -> left));

        for (TaskItem taskItem : grpcTaskItems) {
            TestPlanReportApiScenario reportApiScenario = reportScenarioMap.get(taskItem.getResourceId());
            if (reportApiScenario == null) {
                continue;
            }
            try {
                ApiScenarioDetail apiScenarioDetail = loadScenarioDetail(reportApiScenario.getApiScenarioId(),
                        reportApiScenario.getEnvironmentId(), reportApiScenario.getGrouped());
                TaskRequestDTO singleRequest = buildSingleTaskRequest(taskRequest, taskItem);
                executePlanRunGrpcTask(singleRequest, reportApiScenario, apiScenarioDetail);
                if (StringUtils.isNotBlank(taskInfo.getSetId())) {
                    apiExecutionSetService.removeItem(taskInfo.getSetId(), taskItem.getId());
                }
            } catch (Exception exception) {
                LogUtils.error("测试计划 gRPC 场景并行执行失败: {}", taskItem.getResourceId(), exception);
                if (StringUtils.isNotBlank(taskInfo.getSetId())) {
                    apiExecutionSetService.removeItems(taskInfo.getSetId(), List.of(taskItem.getId()));
                }
            }
        }
    }

    public void runTestPlanApiScenarioGrpcParallelItems(TaskBatchRequestDTO taskRequest, List<TaskItem> grpcTaskItems) {
        if (grpcTaskItems == null || grpcTaskItems.isEmpty()) {
            return;
        }
        TaskInfo taskInfo = taskRequest.getTaskInfo();
        for (TaskItem taskItem : grpcTaskItems) {
            TestPlanApiScenario testPlanApiScenario = testPlanApiScenarioMapper.selectByPrimaryKey(taskItem.getResourceId());
            if (testPlanApiScenario == null) {
                continue;
            }
            try {
                ApiScenarioDetail apiScenarioDetail = loadScenarioDetail(testPlanApiScenario.getApiScenarioId(),
                        testPlanApiScenario.getEnvironmentId(), testPlanApiScenario.getGrouped());
                TaskRequestDTO singleRequest = buildSingleTaskRequest(taskRequest, taskItem);
                executeTestPlanApiScenarioGrpcTask(singleRequest, testPlanApiScenario, apiScenarioDetail);
                if (StringUtils.isBlank(taskInfo.getParentSetId()) && StringUtils.isNotBlank(taskInfo.getSetId())) {
                    apiExecutionSetService.removeItem(taskInfo.getSetId(), taskItem.getId());
                }
            } catch (Exception exception) {
                LogUtils.error("测试计划场景 gRPC 并行执行失败: {}", taskItem.getResourceId(), exception);
                if (StringUtils.isBlank(taskInfo.getParentSetId()) && StringUtils.isNotBlank(taskInfo.getSetId())) {
                    apiExecutionSetService.removeItems(taskInfo.getSetId(), List.of(taskItem.getId()));
                }
            }
        }
    }

    public List<TaskItem> filterGrpcScenarioTaskItems(List<TaskItem> taskItems, Function<String, String> scenarioIdResolver) {
        if (taskItems == null || taskItems.isEmpty()) {
            return List.of();
        }
        List<TaskItem> grpcItems = new ArrayList<>();
        for (TaskItem taskItem : taskItems) {
            String scenarioId = scenarioIdResolver.apply(taskItem.getResourceId());
            if (StringUtils.isBlank(scenarioId)) {
                continue;
            }
            ApiScenarioDetail apiScenarioDetail = apiScenarioRunService.getForRun(scenarioId);
            if (isGrpcScenario(apiScenarioDetail)) {
                grpcItems.add(taskItem);
            }
        }
        return grpcItems;
    }

    private ApiScenarioDetail loadScenarioDetail(String apiScenarioId, String environmentId, Boolean grouped) {
        ApiScenarioDetail apiScenarioDetail = apiScenarioRunService.getForRun(apiScenarioId);
        if (apiScenarioDetail != null) {
            apiScenarioDetail.setEnvironmentId(environmentId);
            apiScenarioDetail.setGrouped(grouped);
        }
        return apiScenarioDetail;
    }

    private GrpcScenarioRunResultDTO runGrpcScenario(ApiScenarioDetail apiScenarioDetail, GetRunScriptRequest request) {
        return grpcScenarioRunAdapter.run(grpcScenarioRunAdapter.buildContext(apiScenarioDetail, request));
    }

    private void completePlanRunReport(GetRunScriptRequest request,
                                       TestPlanReportApiScenario reportApiScenario,
                                       ApiScenarioDetail apiScenarioDetail,
                                       String reportId,
                                       GrpcScenarioRunResultDTO result) {
        GrpcTestPlanScenarioReportContext context = buildReportContext(request, reportId,
                reportApiScenario.getTestPlanApiScenarioId(), reportApiScenario.getId(), true,
                reportApiScenario.getEnvironmentId());
        grpcScenarioReportPersistenceService.completeInitializedReport(apiScenarioDetail, result, context);
        updatePlanRunAssociations(reportApiScenario, reportId, result, request.getUserId());
    }

    private void completeTestPlanApiScenarioReport(GetRunScriptRequest request,
                                                   TestPlanApiScenario testPlanApiScenario,
                                                   ApiScenarioDetail apiScenarioDetail,
                                                   String reportId,
                                                   GrpcScenarioRunResultDTO result) {
        GrpcTestPlanScenarioReportContext context = buildReportContext(request, reportId,
                testPlanApiScenario.getId(), null, false, testPlanApiScenario.getEnvironmentId());
        grpcScenarioReportPersistenceService.completeInitializedReport(apiScenarioDetail, result, context);
        updateTestPlanApiScenarioAssociation(testPlanApiScenario, reportId, result, request.getUserId());
    }

    private GrpcTestPlanScenarioReportContext buildReportContext(GetRunScriptRequest request,
                                                               String reportId,
                                                               String testPlanScenarioId,
                                                               String planRunReportScenarioId,
                                                               boolean planRun,
                                                               String caseEnvironmentId) {
        GrpcTestPlanScenarioReportContext context = new GrpcTestPlanScenarioReportContext();
        context.setReportId(reportId);
        context.setTestPlanScenarioId(testPlanScenarioId);
        context.setPlanRunReportScenarioId(planRunReportScenarioId);
        context.setPlanRun(planRun);
        context.setUserId(request.getUserId());
        context.setTriggerMode(request.getTriggerMode());
        context.setRunMode(request.getRunMode());
        context.setPoolId(request.getPoolId());
        context.setEnvironmentId(apiBatchRunBaseService.getEnvId(request.getRunModeConfig(), caseEnvironmentId));
        return context;
    }

    private void updatePlanRunAssociations(TestPlanReportApiScenario reportApiScenario,
                                           String reportId,
                                           GrpcScenarioRunResultDTO result,
                                           String userId) {
        String executeResult = resolveExecuteResult(result);
        TestPlanReportApiScenario updateReportScenario = new TestPlanReportApiScenario();
        updateReportScenario.setId(reportApiScenario.getId());
        updateReportScenario.setApiScenarioExecuteResult(executeResult);
        updateReportScenario.setApiScenarioExecuteReportId(reportId);
        updateReportScenario.setApiScenarioExecuteUser(userId);
        testPlanReportApiScenarioMapper.updateByPrimaryKeySelective(updateReportScenario);

        TestPlanApiScenario testPlanApiScenario = testPlanApiScenarioMapper.selectByPrimaryKey(reportApiScenario.getTestPlanApiScenarioId());
        if (testPlanApiScenario != null) {
            updateTestPlanApiScenarioAssociation(testPlanApiScenario, reportId, result, userId);
        }
    }

    private void updateTestPlanApiScenarioAssociation(TestPlanApiScenario testPlanApiScenario,
                                                      String reportId,
                                                      GrpcScenarioRunResultDTO result,
                                                      String userId) {
        TestPlanApiScenario updateScenario = new TestPlanApiScenario();
        updateScenario.setId(testPlanApiScenario.getId());
        updateScenario.setLastExecResult(resolveExecuteResult(result));
        updateScenario.setLastExecReportId(reportId);
        updateScenario.setExecuteUser(userId);
        updateScenario.setLastExecTime(System.currentTimeMillis());
        testPlanApiScenarioMapper.updateByPrimaryKeySelective(updateScenario);
    }

    private String resolveExecuteResult(GrpcScenarioRunResultDTO result) {
        return BooleanUtils.isTrue(result.getSuccess()) ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name();
    }

    private void publishPlanRunNotice(GetRunScriptRequest request,
                                      TaskInfo taskInfo,
                                      String resourceId,
                                      String reportId,
                                      GrpcScenarioRunResultDTO result,
                                      ApiScenarioDetail apiScenarioDetail,
                                      String taskItemId) {
        ApiNoticeDTO noticeDTO = buildNotice(request, taskInfo, ApiExecuteResourceType.PLAN_RUN_API_SCENARIO.name(),
                resourceId, reportId, result, apiScenarioDetail, taskItemId);
        grpcTestPlanExecuteNoticeService.publish(noticeDTO);
    }

    private void publishTestPlanApiScenarioNotice(GetRunScriptRequest request,
                                                  TaskInfo taskInfo,
                                                  String resourceId,
                                                  String reportId,
                                                  GrpcScenarioRunResultDTO result,
                                                  ApiScenarioDetail apiScenarioDetail,
                                                  String taskItemId) {
        ApiNoticeDTO noticeDTO = buildNotice(request, taskInfo, ApiExecuteResourceType.TEST_PLAN_API_SCENARIO.name(),
                resourceId, reportId, result, apiScenarioDetail, taskItemId);
        grpcTestPlanExecuteNoticeService.publish(noticeDTO);
    }

    private ApiNoticeDTO buildNotice(GetRunScriptRequest request,
                                     TaskInfo taskInfo,
                                     String resourceType,
                                     String resourceId,
                                     String reportId,
                                     GrpcScenarioRunResultDTO result,
                                     ApiScenarioDetail apiScenarioDetail,
                                     String taskItemId) {
        String queueId = taskInfo == null ? null : taskInfo.getQueueId();
        String setId = taskInfo == null ? null : taskInfo.getSetId();
        String parentQueueId = taskInfo == null ? null : taskInfo.getParentQueueId();
        String parentSetId = taskInfo == null ? null : taskInfo.getParentSetId();
        ApiNoticeDTO noticeDTO = grpcTestPlanExecuteNoticeService.buildNotice(
                resourceType,
                resourceId,
                reportId,
                grpcTestPlanExecuteNoticeService.resolveReportStatus(BooleanUtils.isTrue(result.getSuccess())),
                request.getUserId(),
                apiScenarioDetail.getProjectId(),
                request.getTaskId(),
                taskItemId,
                queueId,
                setId,
                parentQueueId,
                parentSetId,
                request.getRunModeConfig(),
                request.getRerun());
        markCollectionExecuteOverIfNeeded(noticeDTO, queueId);
        return noticeDTO;
    }

    private void markCollectionExecuteOverIfNeeded(ApiNoticeDTO noticeDTO, String queueId) {
        if (StringUtils.isBlank(queueId)) {
            return;
        }
        ExecutionQueue queue = apiExecutionQueueService.getQueue(queueId);
        if (queue == null) {
            noticeDTO.setChildCollectionExecuteOver(true);
        }
    }

    private void markTaskRunning(GetRunScriptRequest request) {
        apiCommonService.updateTaskRunningStatus(request.getTaskId());
        apiCommonService.updateTaskItemRunningStatus(request);
    }

    private void markTaskItemCompleted(String taskItemId, GrpcScenarioRunResultDTO result) {
        if (StringUtils.isBlank(taskItemId)) {
            return;
        }
        ExecTaskItem execTaskItem = new ExecTaskItem();
        execTaskItem.setId(taskItemId);
        execTaskItem.setEndTime(System.currentTimeMillis());
        execTaskItem.setStatus(ExecStatus.COMPLETED.name());
        execTaskItem.setResult(resolveExecuteResult(result));
        execTaskItemMapper.updateByPrimaryKeySelective(execTaskItem);
    }

    private GetRunScriptRequest toGetRunScriptRequest(TaskRequestDTO taskRequest) {
        GetRunScriptRequest request = new GetRunScriptRequest();
        TaskInfo taskInfo = taskRequest.getTaskInfo();
        TaskItem taskItem = taskRequest.getTaskItem();
        request.setTaskItem(taskItem);
        request.setUserId(taskInfo.getUserId());
        request.setRunMode(taskInfo.getRunMode());
        request.setRunModeConfig(taskInfo.getRunModeConfig());
        request.setResourceType(taskInfo.getResourceType());
        request.setBatch(taskInfo.getBatch());
        request.setTaskId(taskInfo.getTaskId());
        request.setPoolId(taskInfo.getPoolId());
        request.setTriggerMode(taskInfo.getTriggerMode());
        request.setRerun(taskInfo.getRerun());
        enrichRunScriptRequest(request, taskInfo);
        return request;
    }

    private void enrichRunScriptRequest(GetRunScriptRequest request, TaskInfo taskInfo) {
        if (StringUtils.isBlank(request.getPoolId()) && taskInfo.getRunModeConfig() != null) {
            request.setPoolId(taskInfo.getRunModeConfig().getPoolId());
        }
        if (StringUtils.isBlank(request.getTriggerMode())) {
            request.setTriggerMode(TaskTriggerMode.MANUAL.name());
        }
    }

    private TaskRequestDTO buildSingleTaskRequest(TaskBatchRequestDTO batchRequest, TaskItem taskItem) {
        TaskRequestDTO taskRequest = new TaskRequestDTO();
        taskRequest.setTaskInfo(batchRequest.getTaskInfo());
        taskRequest.setTaskItem(taskItem);
        return taskRequest;
    }

    private GetRunScriptResult buildEmptyRunScriptResult(String reportId, ApiScenarioDetail apiScenarioDetail) {
        GetRunScriptResult result = new GetRunScriptResult();
        result.setReportId(reportId);
        result.setScript(StringUtils.EMPTY);
        result.setRequestCount((long) GrpcScenarioStepSupport.collectEnabledFlatSteps(apiScenarioDetail.getSteps()).size());
        return result;
    }
}
