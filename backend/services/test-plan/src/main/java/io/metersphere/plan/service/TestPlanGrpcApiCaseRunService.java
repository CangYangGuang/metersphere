package io.metersphere.plan.service;

import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.mapper.ApiTestCaseMapper;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.dto.GrpcTestPlanReportContext;
import io.metersphere.api.grpc.report.GrpcCaseReportPersistenceService;
import io.metersphere.api.grpc.service.ApiTestCaseGrpcRunService;
import io.metersphere.api.grpc.service.GrpcCaseRunService;
import io.metersphere.api.grpc.service.GrpcTestPlanExecuteNoticeService;
import io.metersphere.api.grpc.service.GrpcTestPlanServerAddressResolver;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.api.service.ApiCommonService;
import io.metersphere.api.service.queue.ApiExecutionQueueService;
import io.metersphere.api.service.queue.ApiExecutionSetService;
import io.metersphere.plan.domain.TestPlanApiCase;
import io.metersphere.plan.domain.TestPlanReportApiCase;
import io.metersphere.plan.mapper.TestPlanApiCaseMapper;
import io.metersphere.plan.mapper.TestPlanReportApiCaseMapper;
import io.metersphere.sdk.constants.ApiExecuteResourceType;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.constants.TaskTriggerMode;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.dto.api.notice.ApiNoticeDTO;
import io.metersphere.sdk.dto.api.task.*;
import io.metersphere.sdk.dto.queue.ExecutionQueue;
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
public class TestPlanGrpcApiCaseRunService {

    @Resource
    private ApiTestCaseMapper apiTestCaseMapper;
    @Resource
    private ApiTestCaseGrpcRunService apiTestCaseGrpcRunService;
    @Resource
    private GrpcCaseRunService grpcCaseRunService;
    @Resource
    private GrpcCaseReportPersistenceService grpcCaseReportPersistenceService;
    @Resource
    private GrpcTestPlanServerAddressResolver grpcTestPlanServerAddressResolver;
    @Resource
    private GrpcTestPlanExecuteNoticeService grpcTestPlanExecuteNoticeService;
    @Resource
    private TestPlanReportApiCaseMapper testPlanReportApiCaseMapper;
    @Resource
    private TestPlanApiCaseMapper testPlanApiCaseMapper;
    @Resource
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Resource
    private ApiCommonService apiCommonService;
    @Resource
    private ExecTaskItemMapper execTaskItemMapper;
    @Resource
    private ApiExecutionSetService apiExecutionSetService;
    @Resource
    private ApiExecutionQueueService apiExecutionQueueService;
    @Resource
    private PlanRunTestPlanApiCaseService planRunTestPlanApiCaseService;
    @Resource
    private TestPlanApiCaseService testPlanApiCaseService;

    public boolean isGrpcCase(String caseId) {
        return apiTestCaseGrpcRunService.isGrpcCase(caseId);
    }

    public GetRunScriptResult finishPlanRunGrpcGetRunScript(GetRunScriptRequest request,
                                                            TestPlanReportApiCase reportApiCase,
                                                            ApiTestCase apiTestCase,
                                                            String reportId) {
        GrpcCaseRunResultDTO result = runGrpcCase(apiTestCase, resolveEnvironmentId(request, reportApiCase.getEnvironmentId()));
        completePlanRunReport(request, reportApiCase, apiTestCase, reportId, result);
        publishPlanRunNotice(request, null, reportApiCase.getId(), reportId, result, apiTestCase, request.getTaskItem().getId());
        return buildEmptyRunScriptResult(reportId);
    }

    public GetRunScriptResult finishTestPlanApiCaseGrpcGetRunScript(GetRunScriptRequest request,
                                                                    TestPlanApiCase testPlanApiCase,
                                                                    ApiTestCase apiTestCase,
                                                                    String reportId) {
        GrpcCaseRunResultDTO result = runGrpcCase(apiTestCase, resolveEnvironmentId(request, testPlanApiCase.getEnvironmentId()));
        completeTestPlanApiCaseReport(request, testPlanApiCase, apiTestCase, reportId, result);
        publishTestPlanApiCaseNotice(request, null, testPlanApiCase.getId(), reportId, result, apiTestCase, request.getTaskItem().getId());
        return buildEmptyRunScriptResult(reportId);
    }

    public void executePlanRunGrpcTask(TaskRequestDTO taskRequest,
                                       TestPlanReportApiCase reportApiCase,
                                       ApiTestCase apiTestCase) {
        GetRunScriptRequest request = toGetRunScriptRequest(taskRequest);
        apiCommonService.updateTaskRunningStatus(request.getTaskId());
        apiCommonService.updateTaskItemRunningStatus(request);
        String reportId = planRunTestPlanApiCaseService.initApiReport(request, reportApiCase, apiTestCase);
        GrpcCaseRunResultDTO result = runGrpcCase(apiTestCase, resolveEnvironmentId(request, reportApiCase.getEnvironmentId()));
        completePlanRunReport(request, reportApiCase, apiTestCase, reportId, result);
        publishPlanRunNotice(request, taskRequest.getTaskInfo(), reportApiCase.getId(), reportId, result, apiTestCase, taskRequest.getTaskItem().getId());
        markTaskItemCompleted(taskRequest.getTaskItem().getId(), result);
    }

    public void executeTestPlanApiCaseGrpcTask(TaskRequestDTO taskRequest,
                                               TestPlanApiCase testPlanApiCase,
                                               ApiTestCase apiTestCase) {
        GetRunScriptRequest request = toGetRunScriptRequest(taskRequest);
        apiCommonService.updateTaskRunningStatus(request.getTaskId());
        apiCommonService.updateTaskItemRunningStatus(request);
        String reportId = testPlanApiCaseService.initApiReport(apiTestCase, testPlanApiCase, request);
        GrpcCaseRunResultDTO result = runGrpcCase(apiTestCase, resolveEnvironmentId(request, testPlanApiCase.getEnvironmentId()));
        completeTestPlanApiCaseReport(request, testPlanApiCase, apiTestCase, reportId, result);
        publishTestPlanApiCaseNotice(request, taskRequest.getTaskInfo(), testPlanApiCase.getId(), reportId, result, apiTestCase, taskRequest.getTaskItem().getId());
        markTaskItemCompleted(taskRequest.getTaskItem().getId(), result);
    }

    public void runPlanRunGrpcParallelItems(TaskBatchRequestDTO taskRequest, List<TaskItem> grpcTaskItems) {
        if (grpcTaskItems == null || grpcTaskItems.isEmpty()) {
            return;
        }
        TaskInfo taskInfo = taskRequest.getTaskInfo();
        Map<String, TestPlanReportApiCase> reportCaseMap = grpcTaskItems.stream()
                .map(item -> testPlanReportApiCaseMapper.selectByPrimaryKey(item.getResourceId()))
                .filter(item -> item != null)
                .collect(Collectors.toMap(TestPlanReportApiCase::getId, Function.identity(), (left, right) -> left));

        for (TaskItem taskItem : grpcTaskItems) {
            TestPlanReportApiCase reportApiCase = reportCaseMap.get(taskItem.getResourceId());
            if (reportApiCase == null) {
                continue;
            }
            try {
                TaskRequestDTO singleRequest = buildSingleTaskRequest(taskRequest, taskItem);
                executePlanRunGrpcTask(singleRequest, reportApiCase,
                        loadApiTestCase(reportApiCase.getApiCaseId()));
                if (StringUtils.isNotBlank(taskInfo.getSetId())) {
                    apiExecutionSetService.removeItem(taskInfo.getSetId(), taskItem.getId());
                }
            } catch (Exception exception) {
                LogUtils.error("测试计划 gRPC 并行执行失败: {}", taskItem.getResourceId(), exception);
                if (StringUtils.isNotBlank(taskInfo.getSetId())) {
                    apiExecutionSetService.removeItems(taskInfo.getSetId(), List.of(taskItem.getId()));
                }
            }
        }
    }

    public void runTestPlanApiCaseGrpcParallelItems(TaskBatchRequestDTO taskRequest, List<TaskItem> grpcTaskItems) {
        if (grpcTaskItems == null || grpcTaskItems.isEmpty()) {
            return;
        }
        TaskInfo taskInfo = taskRequest.getTaskInfo();
        for (TaskItem taskItem : grpcTaskItems) {
            TestPlanApiCase testPlanApiCase = testPlanApiCaseMapper.selectByPrimaryKey(taskItem.getResourceId());
            if (testPlanApiCase == null) {
                continue;
            }
            try {
                TaskRequestDTO singleRequest = buildSingleTaskRequest(taskRequest, taskItem);
                executeTestPlanApiCaseGrpcTask(singleRequest, testPlanApiCase,
                        loadApiTestCase(testPlanApiCase.getApiCaseId()));
                if (StringUtils.isBlank(taskInfo.getParentSetId()) && StringUtils.isNotBlank(taskInfo.getSetId())) {
                    apiExecutionSetService.removeItem(taskInfo.getSetId(), taskItem.getId());
                }
            } catch (Exception exception) {
                LogUtils.error("测试计划接口用例 gRPC 并行执行失败: {}", taskItem.getResourceId(), exception);
                if (StringUtils.isBlank(taskInfo.getParentSetId()) && StringUtils.isNotBlank(taskInfo.getSetId())) {
                    apiExecutionSetService.removeItems(taskInfo.getSetId(), List.of(taskItem.getId()));
                }
            }
        }
    }

    public List<TaskItem> filterGrpcTaskItems(List<TaskItem> taskItems, Function<String, String> caseIdResolver) {
        if (taskItems == null || taskItems.isEmpty()) {
            return List.of();
        }
        List<TaskItem> grpcItems = new ArrayList<>();
        for (TaskItem taskItem : taskItems) {
            String caseId = caseIdResolver.apply(taskItem.getResourceId());
            if (StringUtils.isNotBlank(caseId) && isGrpcCase(caseId)) {
                grpcItems.add(taskItem);
            }
        }
        return grpcItems;
    }

    private ApiTestCase loadApiTestCase(String caseId) {
        return apiTestCaseMapper.selectByPrimaryKey(caseId);
    }

    private GrpcCaseRunResultDTO runGrpcCase(ApiTestCase apiTestCase, String environmentId) {
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress(grpcTestPlanServerAddressResolver.resolve(environmentId));
        return grpcCaseRunService.run(apiTestCase.getId(), param);
    }

    private void completePlanRunReport(GetRunScriptRequest request,
                                       TestPlanReportApiCase reportApiCase,
                                       ApiTestCase apiTestCase,
                                       String reportId,
                                       GrpcCaseRunResultDTO result) {
        GrpcTestPlanReportContext context = buildReportContext(request, reportId,
                reportApiCase.getTestPlanApiCaseId(), reportApiCase.getId(), true,
                resolveEnvironmentId(request, reportApiCase.getEnvironmentId()));
        grpcCaseReportPersistenceService.completeInitializedReport(apiTestCase,
                buildRunParam(resolveEnvironmentId(request, reportApiCase.getEnvironmentId())), result, context);
        updatePlanRunAssociations(reportApiCase, reportId, result, request.getUserId());
    }

    private void completeTestPlanApiCaseReport(GetRunScriptRequest request,
                                               TestPlanApiCase testPlanApiCase,
                                               ApiTestCase apiTestCase,
                                               String reportId,
                                               GrpcCaseRunResultDTO result) {
        GrpcTestPlanReportContext context = buildReportContext(request, reportId,
                testPlanApiCase.getId(), testPlanApiCase.getId(), false,
                resolveEnvironmentId(request, testPlanApiCase.getEnvironmentId()));
        grpcCaseReportPersistenceService.completeInitializedReport(apiTestCase,
                buildRunParam(resolveEnvironmentId(request, testPlanApiCase.getEnvironmentId())), result, context);
        updateTestPlanApiCaseAssociation(testPlanApiCase, reportId, result, request.getUserId());
    }

    private GrpcTestPlanReportContext buildReportContext(GetRunScriptRequest request,
                                                         String reportId,
                                                         String testPlanCaseId,
                                                         String reportDetailStepId,
                                                         boolean plan,
                                                         String environmentId) {
        GrpcTestPlanReportContext context = new GrpcTestPlanReportContext();
        context.setReportId(reportId);
        context.setTestPlanCaseId(testPlanCaseId);
        context.setReportDetailStepId(reportDetailStepId);
        context.setPlan(plan);
        context.setUserId(request.getUserId());
        context.setTriggerMode(request.getTriggerMode());
        context.setRunMode(request.getRunMode());
        context.setPoolId(request.getPoolId());
        context.setEnvironmentId(environmentId);
        return context;
    }

    private GrpcCaseRunParamDTO buildRunParam(String environmentId) {
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress(grpcTestPlanServerAddressResolver.resolve(environmentId));
        return param;
    }

    private String resolveEnvironmentId(GetRunScriptRequest request, String caseEnvironmentId) {
        return apiBatchRunBaseService.getEnvId(request.getRunModeConfig(), caseEnvironmentId);
    }

    private void updatePlanRunAssociations(TestPlanReportApiCase reportApiCase,
                                           String reportId,
                                           GrpcCaseRunResultDTO result,
                                           String userId) {
        String executeResult = resolveExecuteResult(result);
        TestPlanReportApiCase updateReportCase = new TestPlanReportApiCase();
        updateReportCase.setId(reportApiCase.getId());
        updateReportCase.setApiCaseExecuteResult(executeResult);
        updateReportCase.setApiCaseExecuteReportId(reportId);
        updateReportCase.setApiCaseExecuteUser(userId);
        testPlanReportApiCaseMapper.updateByPrimaryKeySelective(updateReportCase);

        TestPlanApiCase testPlanApiCase = testPlanApiCaseMapper.selectByPrimaryKey(reportApiCase.getTestPlanApiCaseId());
        if (testPlanApiCase != null) {
            updateTestPlanApiCaseAssociation(testPlanApiCase, reportId, result, userId);
        }
    }

    private void updateTestPlanApiCaseAssociation(TestPlanApiCase testPlanApiCase,
                                                  String reportId,
                                                  GrpcCaseRunResultDTO result,
                                                  String userId) {
        TestPlanApiCase updateCase = new TestPlanApiCase();
        updateCase.setId(testPlanApiCase.getId());
        updateCase.setLastExecResult(resolveExecuteResult(result));
        updateCase.setLastExecReportId(reportId);
        updateCase.setExecuteUser(userId);
        updateCase.setLastExecTime(System.currentTimeMillis());
        testPlanApiCaseMapper.updateByPrimaryKeySelective(updateCase);
    }

    private String resolveExecuteResult(GrpcCaseRunResultDTO result) {
        return BooleanUtils.isTrue(result.getSuccess()) ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name();
    }

    private void publishPlanRunNotice(GetRunScriptRequest request,
                                      TaskInfo taskInfo,
                                      String resourceId,
                                      String reportId,
                                      GrpcCaseRunResultDTO result,
                                      ApiTestCase apiTestCase,
                                      String taskItemId) {
        ApiNoticeDTO noticeDTO = buildNotice(request, taskInfo, ApiExecuteResourceType.PLAN_RUN_API_CASE.name(),
                resourceId, reportId, result, apiTestCase, taskItemId);
        grpcTestPlanExecuteNoticeService.publish(noticeDTO);
    }

    private void publishTestPlanApiCaseNotice(GetRunScriptRequest request,
                                              TaskInfo taskInfo,
                                              String resourceId,
                                              String reportId,
                                              GrpcCaseRunResultDTO result,
                                              ApiTestCase apiTestCase,
                                              String taskItemId) {
        ApiNoticeDTO noticeDTO = buildNotice(request, taskInfo, ApiExecuteResourceType.TEST_PLAN_API_CASE.name(),
                resourceId, reportId, result, apiTestCase, taskItemId);
        grpcTestPlanExecuteNoticeService.publish(noticeDTO);
    }

    private ApiNoticeDTO buildNotice(GetRunScriptRequest request,
                                     TaskInfo taskInfo,
                                     String resourceType,
                                     String resourceId,
                                     String reportId,
                                     GrpcCaseRunResultDTO result,
                                     ApiTestCase apiTestCase,
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
                apiTestCase.getProjectId(),
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

    private GetRunScriptResult buildEmptyRunScriptResult(String reportId) {
        GetRunScriptResult result = new GetRunScriptResult();
        result.setReportId(reportId);
        result.setScript(StringUtils.EMPTY);
        result.setRequestCount(0L);
        return result;
    }

    private void markTaskItemCompleted(String taskItemId, GrpcCaseRunResultDTO result) {
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
}
