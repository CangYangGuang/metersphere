package io.metersphere.api.service.scenario;

import io.metersphere.api.domain.*;
import io.metersphere.api.dto.ApiResourceBatchRunInfo;
import io.metersphere.api.dto.scenario.ApiScenarioBatchRunRequest;
import io.metersphere.api.dto.scenario.ApiScenarioDetail;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.dto.GrpcTestPlanScenarioReportContext;
import io.metersphere.api.grpc.report.GrpcScenarioReportPersistenceService;
import io.metersphere.api.grpc.scenario.GrpcScenarioRunAdapter;
import io.metersphere.api.grpc.scenario.GrpcScenarioStepSupport;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioStepResultDTO;
import io.metersphere.api.mapper.ApiScenarioMapper;
import io.metersphere.api.mapper.ApiScenarioReportMapper;
import io.metersphere.api.mapper.ApiScenarioReportStepMapper;
import io.metersphere.api.mapper.ExtApiScenarioMapper;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.api.service.ApiCommonService;
import io.metersphere.api.service.ApiExecuteService;
import io.metersphere.api.service.definition.ApiTestCaseBatchRunService;
import io.metersphere.api.service.queue.ApiExecutionQueueService;
import io.metersphere.api.service.queue.ApiExecutionSetService;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.*;
import io.metersphere.sdk.dto.api.task.*;
import io.metersphere.sdk.dto.queue.ExecutionQueue;
import io.metersphere.sdk.dto.queue.ExecutionQueueDetail;
import io.metersphere.sdk.util.*;
import io.metersphere.system.domain.ExecTask;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.mapper.ExtExecTaskItemMapper;
import io.metersphere.system.mapper.ExecTaskItemMapper;
import io.metersphere.system.mapper.ExecTaskMapper;
import io.metersphere.system.service.BaseTaskHubService;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class ApiScenarioBatchRunService {
    @Resource
    private ApiScenarioService apiScenarioService;
    @Resource
    private ApiExecuteService apiExecuteService;
    @Resource
    private ApiExecutionQueueService apiExecutionQueueService;
    @Resource
    private ApiExecutionSetService apiExecutionSetService;
    @Resource
    private ApiScenarioReportService apiScenarioReportService;
    @Resource
    private ApiScenarioReportMapper apiScenarioReportMapper;
    @Resource
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Resource
    private ExtApiScenarioMapper extApiScenarioMapper;
    @Resource
    private ApiScenarioMapper apiScenarioMapper;
    @Resource
    private ApiScenarioRunService apiScenarioRunService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ApiCommonService apiCommonService;
    @Resource
    private BaseTaskHubService baseTaskHubService;
    @Resource
    private ApiTestCaseBatchRunService apiTestCaseBatchRunService;
    @Resource
    private ExtExecTaskItemMapper extExecTaskItemMapper;
    @Resource
    private ApiScenarioReportStepMapper apiScenarioReportStepMapper;
    @Resource
    private GrpcScenarioRunAdapter grpcScenarioRunAdapter;
    @Resource
    private GrpcScenarioReportPersistenceService grpcScenarioReportPersistenceService;
    @Resource
    private ExecTaskMapper execTaskMapper;
    @Resource
    private ExecTaskItemMapper execTaskItemMapper;

    public static final int TASK_BATCH_SIZE = 600;

    /**
     * 批量执行
     *
     * @param request
     * @param userId
     */
    public void batchRun(ApiScenarioBatchRunRequest request, String userId) {
        try {
            List<String> ids = apiScenarioService.doSelectIds(request, false);
            List<ApiScenarioDetail> scenarioDetails = getScenarioDetails(ids);
            if (isPureGrpcBatch(scenarioDetails, ids.size())) {
                executePureGrpcBatch(request, userId, ids, scenarioDetails);
                return;
            }
            if (apiBatchRunBaseService.isParallel(request.getRunModeConfig().getRunMode())) {
                parallelExecute(request, userId);
            } else {
                serialExecute(request, userId);
            }
        } catch (Exception e) {
            LogUtils.error("批量执行用例失败: ", e);
        }
    }

    List<ApiScenarioDetail> getScenarioDetails(List<String> ids) {
        List<ApiScenarioDetail> details = new ArrayList<>(ids.size());
        for (String id : ids) {
            // Keep disabled steps for routing and report initialization. The in-process
            // runner still filters them before execution, but an all-disabled gRPC
            // scenario must produce an explicit result instead of falling back to the
            // task-runner and being reported as a successful empty execution.
            ApiScenarioDetail detail = apiScenarioService.get(id);
            if (detail != null) {
                details.add(detail);
            }
        }
        return details;
    }

    boolean isPureGrpcBatch(List<ApiScenarioDetail> details, int expectedSize) {
        return expectedSize > 0
                && details.size() == expectedSize
                && details.stream().allMatch(this::supportsGrpcBatchRun);
    }

    private boolean supportsGrpcBatchRun(ApiScenarioDetail detail) {
        if (detail == null || CollectionUtils.isEmpty(detail.getSteps())) {
            return false;
        }
        if (grpcScenarioRunAdapter.supportsInProcessRun(detail.getSteps())) {
            return !GrpcScenarioStepSupport.isMixedScenario(detail.getSteps());
        }
        List<ApiScenarioStepCommonDTO> enabledSteps = GrpcScenarioStepSupport.collectEnabledFlatSteps(detail.getSteps());
        if (CollectionUtils.isNotEmpty(enabledSteps)) {
            return false;
        }
        List<ApiScenarioStepCommonDTO> definedSteps = collectFlatSteps(detail.getSteps());
        return CollectionUtils.isNotEmpty(definedSteps)
                && definedSteps.stream().allMatch(GrpcScenarioStepSupport::isInProcessExecutableStep);
    }

    void executePureGrpcBatch(ApiScenarioBatchRunRequest request,
                              String userId,
                              List<String> ids,
                              List<ApiScenarioDetail> scenarioDetails) {
        ApiRunModeConfigDTO runModeConfig = getRunModeConfig(request);
        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());
        ExecTask execTask = initExecTask(ids, runModeConfig, project, userId);
        long startTime = System.currentTimeMillis();
        markTaskRunning(execTask.getId(), startTime);

        if (runModeConfig.isIntegratedReport()) {
            initIntegratedReport(execTask.getId(), runModeConfig, userId, request.getProjectId());
        }

        List<ApiResourceBatchRunInfo> apiScenarios = getOrderScenarios(ids);
        List<ExecTaskItem> taskItems = initExecTaskItem(apiScenarios, userId, project, execTask);
        initInitApiScenarioReportSteps(runModeConfig, apiScenarios);
        Map<String, ExecTaskItem> taskItemMap = taskItems.stream()
                .collect(Collectors.toMap(ExecTaskItem::getResourceId, Function.identity()));
        Map<String, ApiScenarioDetail> detailMap = scenarioDetails.stream()
                .collect(Collectors.toMap(ApiScenarioDetail::getId, Function.identity()));

        runAsyncAfterCommit(() -> completePureGrpcBatch(ids, detailMap, taskItemMap,
                execTask, runModeConfig, userId, startTime));
    }

    private void completePureGrpcBatch(List<String> ids,
                                       Map<String, ApiScenarioDetail> detailMap,
                                       Map<String, ExecTaskItem> taskItemMap,
                                       ExecTask execTask,
                                       ApiRunModeConfigDTO runModeConfig,
                                       String userId,
                                       long startTime) {
        List<GrpcScenarioRunResultDTO> results = new ArrayList<>(ids.size());
        try {
            if (apiBatchRunBaseService.isParallel(runModeConfig.getRunMode())) {
                try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    List<CompletableFuture<GrpcScenarioRunResultDTO>> futures = ids.stream()
                            .map(id -> CompletableFuture.supplyAsync(() -> executePureGrpcScenario(
                                    detailMap.get(id), taskItemMap.get(id), execTask, runModeConfig, userId), executor))
                            .toList();
                    futures.stream().map(CompletableFuture::join).forEach(results::add);
                }
            } else {
                for (String id : ids) {
                    GrpcScenarioRunResultDTO result = executePureGrpcScenario(
                            detailMap.get(id), taskItemMap.get(id), execTask, runModeConfig, userId);
                    results.add(result);
                    if (BooleanUtils.isTrue(runModeConfig.getStopOnFailure()) && !BooleanUtils.isTrue(result.getSuccess())) {
                        break;
                    }
                }
            }
        } catch (Exception exception) {
            LogUtils.error("Pure gRPC batch execution failed: {}", execTask.getId(), exception);
        } finally {
            if (runModeConfig.isIntegratedReport()) {
                try {
                    completeIntegratedGrpcReport(ids, detailMap, execTask, runModeConfig, userId, startTime, results);
                } catch (Exception exception) {
                    LogUtils.error("Pure gRPC integrated report completion failed: {}", execTask.getId(), exception);
                }
            }
            completeBatchTask(execTask, taskItemMap, results, ids, System.currentTimeMillis());
        }
    }

    private void completeIntegratedGrpcReport(List<String> ids,
                                              Map<String, ApiScenarioDetail> detailMap,
                                              ExecTask execTask,
                                              ApiRunModeConfigDTO runModeConfig,
                                              String userId,
                                              long startTime,
                                              List<GrpcScenarioRunResultDTO> results) {
        long pendingCount = 0L;
        if (results.size() < ids.size()) {
            for (String id : ids.subList(results.size(), ids.size())) {
                ApiScenarioDetail detail = detailMap.get(id);
                if (detail != null) {
                    apiScenarioRunService.initScenarioReportSteps(detail.getId(), detail.getSteps(),
                            runModeConfig.getCollectionReport().getReportId());
                    pendingCount += apiScenarioRunService.getRequestCount(detail.getSteps());
                }
            }
        }
        GrpcScenarioRunResultDTO aggregate = aggregateGrpcResults(results);
        GrpcTestPlanScenarioReportContext context = buildBatchReportContext(
                runModeConfig.getCollectionReport().getReportId(), execTask, null,
                runModeConfig, userId, startTime);
        context.setPendingCount(pendingCount);
        try {
            grpcScenarioReportPersistenceService.completeInitializedReport(null, aggregate, context);
        } catch (Exception exception) {
            LogUtils.error("Pure gRPC integrated report completion failed: {}", context.getReportId(), exception);
        }
    }

    private GrpcScenarioRunResultDTO executePureGrpcScenario(ApiScenarioDetail detail,
                                                             ExecTaskItem taskItem,
                                                             ExecTask execTask,
                                                             ApiRunModeConfigDTO runModeConfig,
                                                             String userId) {
        if (detail == null || taskItem == null) {
            return failedGrpcResult(detail, "GRPC_BATCH_CONTEXT_MISSING", "Scenario or task item is missing");
        }
        long startTime = System.currentTimeMillis();
        markTaskItemRunning(taskItem.getId(), startTime);
        String reportId = runModeConfig.isIntegratedReport()
                ? runModeConfig.getCollectionReport().getReportId() : IDGenerator.nextStr();
        try {
            if (runModeConfig.isIntegratedReport()) {
                apiScenarioRunService.initScenarioReportSteps(detail.getId(), detail.getSteps(), reportId);
            } else {
                initScenarioReport(taskItem.getId(), reportId, runModeConfig, detail, userId);
                apiScenarioRunService.initScenarioReportSteps(detail.getSteps(), reportId);
            }

            GetRunScriptRequest scriptRequest = new GetRunScriptRequest();
            TaskItem sdkTaskItem = new TaskItem();
            sdkTaskItem.setId(taskItem.getId());
            sdkTaskItem.setResourceId(detail.getId());
            sdkTaskItem.setReportId(reportId);
            scriptRequest.setTaskItem(sdkTaskItem);
            scriptRequest.setRunMode(ApiExecuteRunMode.RUN.name());
            scriptRequest.setRunModeConfig(runModeConfig);
            scriptRequest.setUserId(userId);
            scriptRequest.setBatch(true);

            GrpcScenarioRunResultDTO result;
            List<ApiScenarioStepCommonDTO> enabledExecutableSteps = GrpcScenarioStepSupport.collectEnabledFlatSteps(detail.getSteps())
                    .stream()
                    .filter(GrpcScenarioStepSupport::isInProcessExecutableStep)
                    .toList();
            if (enabledExecutableSteps.isEmpty()) {
                result = disabledGrpcResult(detail);
            } else {
                result = grpcScenarioRunAdapter.run(grpcScenarioRunAdapter.buildContext(detail, scriptRequest));
            }
            if (!runModeConfig.isIntegratedReport()) {
                GrpcTestPlanScenarioReportContext context = buildBatchReportContext(
                        reportId, execTask, taskItem, runModeConfig, userId, startTime);
                grpcScenarioReportPersistenceService.completeInitializedReport(detail, result, context);
            } else {
                grpcScenarioReportPersistenceService.updateScenarioExecutionSummary(
                        detail.getId(), reportId, result, userId);
            }
            completeTaskItem(taskItem.getId(), result, System.currentTimeMillis());
            return result;
        } catch (Exception exception) {
            LogUtils.error("批量执行纯 gRPC 场景失败: {}", detail.getId(), exception);
            GrpcScenarioRunResultDTO failed = failedGrpcResult(detail, "GRPC_BATCH_EXECUTION_ERROR",
                    StringUtils.defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName()));
            if (!runModeConfig.isIntegratedReport()) {
                GrpcTestPlanScenarioReportContext context = buildBatchReportContext(
                        reportId, execTask, taskItem, runModeConfig, userId, startTime);
                try {
                    grpcScenarioReportPersistenceService.completeInitializedReport(detail, failed, context);
                } catch (Exception persistException) {
                    LogUtils.error("Pure gRPC scenario failure report completion failed: {}", reportId, persistException);
                }
            } else {
                grpcScenarioReportPersistenceService.updateScenarioExecutionSummary(
                        detail.getId(), reportId, failed, userId);
            }
            completeTaskItem(taskItem.getId(), failed, System.currentTimeMillis());
            return failed;
        }
    }

    private GrpcTestPlanScenarioReportContext buildBatchReportContext(String reportId,
                                                                      ExecTask execTask,
                                                                      ExecTaskItem taskItem,
                                                                      ApiRunModeConfigDTO runModeConfig,
                                                                      String userId,
                                                                      long startTime) {
        GrpcTestPlanScenarioReportContext context = new GrpcTestPlanScenarioReportContext();
        context.setReportId(reportId);
        context.setProjectId(execTask.getProjectId());
        context.setEnvironmentId(runModeConfig.getEnvironmentId());
        context.setUserId(userId);
        context.setTriggerMode(TaskTriggerMode.BATCH.name());
        context.setRunMode(runModeConfig.getRunMode());
        context.setPoolId(runModeConfig.getPoolId());
        context.setStartTime(startTime);
        context.setTaskId(execTask.getId());
        if (taskItem != null) {
            context.setTaskItemId(taskItem.getId());
        }
        return context;
    }

    private GrpcScenarioRunResultDTO aggregateGrpcResults(List<GrpcScenarioRunResultDTO> results) {
        GrpcScenarioRunResultDTO aggregate = new GrpcScenarioRunResultDTO();
        aggregate.setSuccess(!results.isEmpty() && results.stream().allMatch(result -> BooleanUtils.isTrue(result.getSuccess())));
        aggregate.setStepResults(results.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(result -> CollectionUtils.emptyIfNull(result.getStepResults()).stream())
                .toList());
        return aggregate;
    }

    private GrpcScenarioRunResultDTO failedGrpcResult(ApiScenarioDetail detail, String errorCode, String errorMessage) {
        GrpcScenarioRunResultDTO result = new GrpcScenarioRunResultDTO();
        result.setSuccess(false);
        if (detail == null) {
            result.setStepResults(Collections.emptyList());
            return result;
        }
        List<GrpcScenarioStepResultDTO> stepResults = GrpcScenarioStepSupport.collectEnabledFlatSteps(detail.getSteps()).stream()
                .filter(GrpcScenarioStepSupport::isInProcessExecutableStep)
                .map(step -> failedGrpcStepResult(step, errorCode, errorMessage))
                .toList();
        result.setStepResults(stepResults);
        return result;
    }

    private GrpcScenarioRunResultDTO disabledGrpcResult(ApiScenarioDetail detail) {
        GrpcScenarioRunResultDTO result = new GrpcScenarioRunResultDTO();
        result.setSuccess(false);
        result.setStepResults(collectFlatSteps(detail.getSteps()).stream()
                .filter(GrpcScenarioStepSupport::isInProcessExecutableStep)
                .map(step -> failedGrpcStepResult(step, "GRPC_BATCH_NO_ENABLED_STEPS",
                        "Scenario step is disabled and was not executed"))
                .toList());
        return result;
    }

    private List<ApiScenarioStepCommonDTO> collectFlatSteps(
            List<? extends ApiScenarioStepCommonDTO> steps) {
        List<ApiScenarioStepCommonDTO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(steps)) {
            return result;
        }
        for (ApiScenarioStepCommonDTO step : steps) {
            if (step == null) {
                continue;
            }
            result.add(step);
            result.addAll(collectFlatSteps(step.getChildren()));
        }
        return result;
    }

    private GrpcScenarioStepResultDTO failedGrpcStepResult(ApiScenarioStepCommonDTO step,
                                                           String errorCode,
                                                           String errorMessage) {
        GrpcScenarioStepResultDTO result = new GrpcScenarioStepResultDTO();
        result.setStepId(step.getUniqueId());
        result.setStepName(step.getName());
        result.setStepType(step.getStepType());
        result.setResourceId(step.getResourceId());
        result.setProjectId(step.getProjectId());
        result.setSuccess(false);
        result.setDuration(0L);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }

    private void markTaskRunning(String taskId, long startTime) {
        ExecTask update = new ExecTask();
        update.setId(taskId);
        update.setStatus(ExecStatus.RUNNING.name());
        update.setStartTime(startTime);
        execTaskMapper.updateByPrimaryKeySelective(update);
    }

    private void markTaskItemRunning(String taskItemId, long startTime) {
        ExecTaskItem update = new ExecTaskItem();
        update.setId(taskItemId);
        update.setStatus(ExecStatus.RUNNING.name());
        update.setStartTime(startTime);
        execTaskItemMapper.updateByPrimaryKeySelective(update);
    }

    private void completeTaskItem(String taskItemId, GrpcScenarioRunResultDTO result, long endTime) {
        ExecTaskItem update = new ExecTaskItem();
        update.setId(taskItemId);
        update.setStatus(ExecStatus.COMPLETED.name());
        update.setResult(BooleanUtils.isTrue(result.getSuccess()) ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        update.setEndTime(endTime);
        execTaskItemMapper.updateByPrimaryKeySelective(update);
    }

    private void completeBatchTask(ExecTask execTask,
                                   Map<String, ExecTaskItem> taskItemMap,
                                   List<GrpcScenarioRunResultDTO> results,
                                   List<String> ids,
                                   long endTime) {
        if (results.size() < ids.size()) {
            ids.subList(results.size(), ids.size()).stream()
                    .map(taskItemMap::get)
                    .filter(java.util.Objects::nonNull)
                    .forEach(item -> stopTaskItem(item.getId(), endTime));
        }
        boolean success = results.size() == ids.size()
                && results.stream().allMatch(result -> BooleanUtils.isTrue(result.getSuccess()));
        ExecTask update = new ExecTask();
        update.setId(execTask.getId());
        update.setStatus(ExecStatus.COMPLETED.name());
        update.setResult(success ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        update.setEndTime(endTime);
        execTaskMapper.updateByPrimaryKeySelective(update);
    }

    private void stopTaskItem(String taskItemId, long endTime) {
        ExecTaskItem update = new ExecTaskItem();
        update.setId(taskItemId);
        update.setStatus(ExecStatus.STOPPED.name());
        update.setEndTime(endTime);
        execTaskItemMapper.updateByPrimaryKeySelective(update);
    }

    private void runAsyncAfterCommit(Runnable task) {
        Runnable startTask = () -> Thread.startVirtualThread(task);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            startTask.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                startTask.run();
            }
        });
    }

    /**
     * 串行批量执行
     *
     * @param request
     */
    public void serialExecute(ApiScenarioBatchRunRequest request, String userId) {
        List<String> ids = apiScenarioService.doSelectIds(request, false);
        ApiRunModeConfigDTO runModeConfig = getRunModeConfig(request);

        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());

        // 初始化任务
        ExecTask execTask = initExecTask(ids, runModeConfig, project, userId);

        // 初始化集成报告
        if (runModeConfig.isIntegratedReport()) {
            initIntegratedReport(execTask.getId(), runModeConfig, userId, request.getProjectId());
        }

        // 先初始化集成报告，设置好报告ID，再初始化执行队列
        ExecutionQueue queue = apiBatchRunBaseService.initExecutionQueue(execTask.getId(), runModeConfig,
                ApiExecuteResourceType.API_SCENARIO.name(), null, userId);

        // 分批查询
        SubListUtils.dealForSubList(ids, TASK_BATCH_SIZE, subIds -> {
            List<ApiResourceBatchRunInfo> apiScenarios = getOrderScenarios(subIds);

            // 初始化任务项
            List<ExecTaskItem> execTaskItems = initExecTaskItem(apiScenarios, userId, project, execTask);

            // 初始化队列项
            apiBatchRunBaseService.initExecutionQueueDetails(queue.getQueueId(), execTaskItems);

            // 集合报告初始化一级步骤
            initInitApiScenarioReportSteps(runModeConfig, apiScenarios);
        });

        Thread.startVirtualThread(() -> {
            // 执行第一个任务
            ExecutionQueueDetail nextDetail = apiExecutionQueueService.getNextDetail(queue.getQueueId());
            executeNextTask(queue, nextDetail);
        });
    }

    /**
     * 并行批量执行
     *
     * @param request
     */
    public void parallelExecute(ApiScenarioBatchRunRequest request, String userId) {
        List<String> ids = apiScenarioService.doSelectIds(request, false);

        ApiRunModeConfigDTO runModeConfig = getRunModeConfig(request);

        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());

        // 初始化任务
        ExecTask execTask = initExecTask(ids, runModeConfig, project, userId);

        if (runModeConfig.isIntegratedReport()) {
            // 初始化集成报告
            initIntegratedReport(execTask.getId(), runModeConfig, userId, request.getProjectId());
        }

        // 记录用例和任务的映射
        Map<String, String> resourceExecTaskItemMap = new TreeMap<>();
        // 分批处理，初始化任务项
        SubListUtils.dealForSubList(ids, TASK_BATCH_SIZE, subIds -> {
            List<ApiResourceBatchRunInfo> apiScenarios = getOrderScenarios(subIds);
            // 初始化任务项
            List<ExecTaskItem> execTaskItems = initExecTaskItem(apiScenarios, userId, project, execTask);
            // 记录任务
            execTaskItems.forEach(item -> resourceExecTaskItemMap.put(item.getResourceId(), item.getId()));
            // 初始化集合报告步骤
            initInitApiScenarioReportSteps(runModeConfig, apiScenarios);
        });

        TaskBatchRequestDTO taskRequest = getTaskBatchRequestDTO(request.getProjectId(), runModeConfig);
        taskRequest.getTaskInfo().setTaskId(execTask.getId());
        taskRequest.getTaskInfo().setSetId(execTask.getId());
        taskRequest.getTaskInfo().setUserId(userId);

        Thread.startVirtualThread(() -> {
            // 记录任务项，用于统计整体执行情况
            apiExecutionSetService.initSet(execTask.getId(), new ArrayList<>(resourceExecTaskItemMap.values()));
            apiBatchRunBaseService.parallelBatchExecute(taskRequest, runModeConfig, resourceExecTaskItemMap);
        });
    }

    private void initInitApiScenarioReportSteps(ApiRunModeConfigDTO runModeConfig, List<ApiResourceBatchRunInfo> apiScenarios) {
        // 先初始化所有报告
        if (runModeConfig.isIntegratedReport()) {
            // 获取集成报告ID
            String reportId = runModeConfig.getCollectionReport().getReportId();
            // 初始化集合报告和场景的关联关系
            initIntegratedReportCaseRecord(reportId, apiScenarios);
            // 集合报告初始化一级步骤
            initApiScenarioReportStep(apiScenarios, reportId);
        }
    }

    /**
     * 获取有序的用例
     *
     * @param ids
     * @return
     */
    private List<ApiResourceBatchRunInfo> getOrderScenarios(List<String> ids) {
        List<ApiResourceBatchRunInfo> apiScenarios = new ArrayList<>(TASK_BATCH_SIZE);
        // 分批查询
        List<ApiResourceBatchRunInfo> finalApiScenarios = apiScenarios;
        SubListUtils.dealForSubList(ids, ApiBatchRunBaseService.SELECT_BATCH_SIZE, subIds -> finalApiScenarios.addAll(extApiScenarioMapper.getScenarioExecuteInfoByIds(subIds)));
        Map<String, ApiResourceBatchRunInfo> apiScenarioMap = apiScenarios.stream()
                .collect(Collectors.toMap(ApiResourceBatchRunInfo::getId, Function.identity()));

        apiScenarios = new ArrayList<>(ids.size());

        for (String id : ids) {
            // 按照ID顺序排序
            ApiResourceBatchRunInfo apiScenario = apiScenarioMap.get(id);
            if (apiScenario == null) {
                LogUtils.info("当前执行任务的用例已删除 {}", id);
                break;
            }
            apiScenarios.add(apiScenario);
        }
        return apiScenarios;
    }

    private ExecTask initExecTask(List<String> ids, ApiRunModeConfigDTO runModeConfig, Project project, String userId) {
        ExecTask execTask = apiCommonService.newExecTask(project.getId(), userId);
        execTask.setCaseCount(Long.valueOf(ids.size()));
        if (runModeConfig.isIntegratedReport()) {
            execTask.setTaskName(runModeConfig.getCollectionReport().getReportName());
        } else {
            execTask.setTaskName(Translator.get("api_scenario_batch_task_name"));
        }
        execTask.setPoolId(runModeConfig.getPoolId());
        execTask.setParallel(StringUtils.equals(runModeConfig.getRunMode(), ApiBatchRunMode.PARALLEL.name()));
        execTask.setEnvGrouped(runModeConfig.getGrouped());
        execTask.setEnvironmentId(runModeConfig.getEnvironmentId());
        execTask.setOrganizationId(project.getOrganizationId());
        execTask.setTriggerMode(TaskTriggerMode.BATCH.name());
        execTask.setTaskType(ExecTaskType.API_SCENARIO_BATCH.name());
        execTask.setIntegrated(runModeConfig.isIntegratedReport());
        baseTaskHubService.insertExecTask(execTask);
        return execTask;
    }

    private List<ExecTaskItem> initExecTaskItem(List<ApiResourceBatchRunInfo> apiScenarios, String userId, Project project, ExecTask execTask) {
        List<ExecTaskItem> execTaskItems = new ArrayList<>(apiScenarios.size());
        for (ApiResourceBatchRunInfo apiScenario : apiScenarios) {
            ExecTaskItem execTaskItem = apiCommonService.newExecTaskItem(execTask.getId(), project.getId(), userId);
            execTaskItem.setOrganizationId(project.getOrganizationId());
            execTaskItem.setResourceType(ApiExecuteResourceType.API_SCENARIO.name());
            execTaskItem.setResourceId(apiScenario.getId());
            execTaskItem.setCaseId(apiScenario.getId());
            execTaskItem.setResourceName(apiScenario.getName());
            execTaskItems.add(execTaskItem);
        }
        baseTaskHubService.insertExecTaskDetail(execTaskItems);
        return execTaskItems;
    }

    public void initApiScenarioReportStep(List<ApiResourceBatchRunInfo> apiScenarios, String reportId) {
        AtomicLong sort = new AtomicLong(1);
        List<ApiScenarioReportStep> apiScenarioReportSteps = new ArrayList<>(apiScenarios.size());
        for (ApiResourceBatchRunInfo apiScenario : apiScenarios) {
            ApiScenarioReportStep apiReportStep = new ApiScenarioReportStep();
            apiReportStep.setReportId(reportId);
            apiReportStep.setStepId(apiScenario.getId());
            apiReportStep.setSort(sort.getAndIncrement());
            apiReportStep.setName(apiScenario.getName());
            apiReportStep.setStepType(ApiExecuteResourceType.API_SCENARIO.name());
            apiScenarioReportSteps.add(apiReportStep);
        }
        if (CollectionUtils.isNotEmpty(apiScenarioReportSteps)) {
            apiScenarioReportService.insertApiScenarioReportStep(apiScenarioReportSteps);
        }
    }

    private ApiRunModeConfigDTO getRunModeConfig(ApiScenarioBatchRunRequest request) {
        ApiRunModeConfigDTO runModeConfig = BeanUtils.copyBean(new ApiRunModeConfigDTO(), request.getRunModeConfig());
        if (StringUtils.isNotBlank(request.getRunModeConfig().getIntegratedReportName()) && runModeConfig.isIntegratedReport()) {
            runModeConfig.setCollectionReport(new CollectionReportDTO());
            runModeConfig.getCollectionReport().setReportName(request.getRunModeConfig().getIntegratedReportName());
        }
        return runModeConfig;
    }

    /**
     * 预生成用例的执行报告
     *
     * @param runModeConfig
     * @return
     */
    private ApiScenarioReport initIntegratedReport(String taskId, ApiRunModeConfigDTO runModeConfig, String userId, String projectId) {
        ApiScenarioReport apiScenarioReport = getScenarioReport(runModeConfig, userId);
        apiScenarioReport.setName(runModeConfig.getCollectionReport().getReportName() + "_" + DateUtils.getTimeString(System.currentTimeMillis()));
        apiScenarioReport.setIntegrated(true);
        apiScenarioReport.setProjectId(projectId);

        // 创建报告和任务的关联关系
        ApiReportRelateTask apiReportRelateTask = new ApiReportRelateTask();
        apiReportRelateTask.setReportId(apiScenarioReport.getId());
        apiReportRelateTask.setTaskResourceId(taskId);

        apiScenarioReportService.insertApiScenarioReport(apiScenarioReport, apiReportRelateTask);
        // 设置集成报告执行参数
        runModeConfig.getCollectionReport().setReportId(apiScenarioReport.getId());
        return apiScenarioReport;
    }

    private void initIntegratedReportCaseRecord(String reportId, List<ApiResourceBatchRunInfo> apiScenarios) {
        // 初始化集成报告与用例的关联关系
        List<ApiScenarioRecord> records = apiScenarios.stream().map(apiScenario -> {
            ApiScenarioRecord scenarioRecord = new ApiScenarioRecord();
            scenarioRecord.setApiScenarioReportId(reportId);
            scenarioRecord.setApiScenarioId(apiScenario.getId());
            return scenarioRecord;
        }).toList();
        apiScenarioReportService.insertApiScenarioReport(List.of(), records);
    }

    /**
     * 执行串行的下一个任务
     *
     * @param queue
     * @param queueDetail
     */
    public void executeNextTask(ExecutionQueue queue, ExecutionQueueDetail queueDetail) {
        ApiRunModeConfigDTO runModeConfig = queue.getRunModeConfig();
        ApiScenario apiScenario = apiScenarioMapper.selectByPrimaryKey(queueDetail.getResourceId());

        ApiScenarioDetail apiScenarioDetail = apiScenarioRunService.getForRun(queueDetail.getResourceId());
        if (apiScenarioDetail == null) {
            LogUtils.info("当前执行任务的用例已删除 {}", queueDetail.getResourceId());
            return;
        }

        String reportId = null;
        if (runModeConfig.isIntegratedReport()) {
            reportId = runModeConfig.getCollectionReport().getReportId();
        }

        TaskRequestDTO taskRequest = getTaskRequestDTO(apiScenario.getProjectId(), queue.getRunModeConfig());
        TaskItem taskItem = apiExecuteService.getTaskItem(reportId, queueDetail.getResourceId());
        taskItem.setId(queueDetail.getTaskItemId());
        taskRequest.setTaskItem(taskItem);
        taskRequest.getTaskInfo().setQueueId(queue.getQueueId());
        taskRequest.getTaskInfo().setUserId(queue.getUserId());
        taskRequest.getTaskInfo().setTaskId(queue.getTaskId());
        taskRequest.getTaskInfo().setRerun(queue.getRerun());

        try {
            apiExecuteService.execute(taskRequest);
        } catch (Exception e) {
            // 执行失败，删除队列
            apiExecutionQueueService.deleteQueue(queue.getQueueId());
        }
    }

    private TaskRequestDTO getTaskRequestDTO(String projectId, ApiRunModeConfigDTO runModeConfig) {
        TaskRequestDTO taskRequest = new TaskRequestDTO();
        TaskInfo taskInfo = getTaskInfo(projectId, runModeConfig);
        taskRequest.setTaskInfo(taskInfo);
        return taskRequest;
    }

    private TaskBatchRequestDTO getTaskBatchRequestDTO(String projectId, ApiRunModeConfigDTO runModeConfig) {
        TaskBatchRequestDTO taskRequest = new TaskBatchRequestDTO();
        TaskInfo taskInfo = getTaskInfo(projectId, runModeConfig);
        taskRequest.setTaskInfo(taskInfo);
        return taskRequest;
    }

    public TaskInfo getTaskInfo(String projectId, ApiRunModeConfigDTO runModeConfig) {
        TaskInfo taskInfo = apiScenarioRunService.getTaskInfo(projectId, ApiExecuteRunMode.RUN.name());
        taskInfo.setBatch(true);
        return apiBatchRunBaseService.setBatchRunTaskInfoParam(runModeConfig, taskInfo);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public String initScenarioReport(String taskItemId, String reportId, ApiRunModeConfigDTO runModeConfig,
                                                 ApiScenario apiScenario, String userId) {
        // 初始化报告
        ApiScenarioReport apiScenarioReport = getScenarioReport(runModeConfig, apiScenario, userId);
        apiScenarioReport.setId(reportId);
        apiScenarioReportService.insertApiScenarioReport(apiScenarioReport);
        return apiScenarioRunService.initApiScenarioReportDetail(taskItemId, apiScenario.getId(), apiScenarioReport.getId());
    }

    private ApiScenarioReport getScenarioReport(ApiRunModeConfigDTO runModeConfig, ApiScenario apiScenario, String userId) {
        ApiScenarioReport apiScenarioReport = getScenarioReport(runModeConfig, userId);
        apiScenarioReport.setName(apiScenario.getName() + "_" + DateUtils.getTimeString(System.currentTimeMillis()));
        apiScenarioReport.setEnvironmentId(apiScenarioRunService.getEnvId(runModeConfig, apiScenario.getEnvironmentId()));
        apiScenarioReport.setProjectId(apiScenario.getProjectId());
        apiScenarioReport.setTriggerMode(TaskTriggerMode.BATCH.name());
        return apiScenarioReport;
    }

    public ApiScenarioReport getScenarioReport(ApiRunModeConfigDTO runModeConfig, String userId) {
        ApiScenarioReport apiScenarioReport = apiScenarioRunService.getScenarioReport(userId);
        apiScenarioReport.setEnvironmentId(runModeConfig.getEnvironmentId());
        apiScenarioReport.setRunMode(runModeConfig.getRunMode());
        apiScenarioReport.setPoolId(runModeConfig.getPoolId());
        apiScenarioReport.setTriggerMode(TaskTriggerMode.BATCH.name());
        return apiScenarioReport;
    }


    public void updateStopOnFailureReport(ExecutionQueue queue) {
        ApiRunModeConfigDTO runModeConfig = queue.getRunModeConfig();
        if (BooleanUtils.isFalse(runModeConfig.isIntegratedReport())) {
            return;
        }
        try {
            ExecutionQueueDetail queueDetail = apiExecutionQueueService.getNextDetail(queue.getQueueId());
            if (queueDetail == null) {
                return;
            }
            long requestCount = 0L;
            while (queueDetail != null) {
                ApiScenarioDetail apiScenarioDetail = apiScenarioRunService.getForRun(queueDetail.getResourceId());
                if (apiScenarioDetail == null) {
                    LogUtils.info("当前场景已删除 {}", queueDetail.getResourceId());
                    continue;
                }

                Long requestCountItem = apiScenarioRunService.getRequestCount(apiScenarioDetail.getSteps());
                requestCount += requestCountItem;

                // 初始化报告步骤
                if (runModeConfig.isIntegratedReport()) {
                    apiScenarioRunService.initScenarioReportSteps(apiScenarioDetail.getId(), apiScenarioDetail.getSteps(), runModeConfig.getCollectionReport().getReportId());
                }

                queueDetail = apiExecutionQueueService.getNextDetail(queue.getQueueId());
            }

            // 获取未执行的请求数，更新统计指标
            String reportId = runModeConfig.getCollectionReport().getReportId();
            ApiScenarioReport report = apiScenarioReportMapper.selectByPrimaryKey(reportId);
            Long pendingCount = requestCount + report.getPendingCount();
            report.setPendingCount(pendingCount);
            // 计算各种通过率
            long total = apiScenarioReportService.getRequestTotal(report);
            report = apiBatchRunBaseService.computeRequestRate(report, total);
            report.setStatus(ResultStatus.ERROR.name());
            report.setExecStatus(ExecStatus.COMPLETED.name());
            apiScenarioReportMapper.updateByPrimaryKeySelective(report);
        } catch (Exception e) {
            LogUtils.error("失败停止，补充报告步骤失败：", e);
        }
    }

    public void rerun(ExecTask execTask, String userId) {
        if (BooleanUtils.isTrue(execTask.getParallel())) {
            parallelRerunExecute(execTask, userId);
        } else {
            serialRerunExecute(execTask, userId);
        }
    }

    private void serialRerunExecute(ExecTask execTask, String userId) {
        ApiRunModeConfigDTO runModeConfig = apiTestCaseBatchRunService.getRunModeConfig(execTask);

        List<ExecTaskItem> execTaskItems = extExecTaskItemMapper.selectRerunIdAndResourceIdByTaskId(execTask.getId());

        // 删除重跑的步骤
        deleteRerunIntegratedStepResult(execTask, execTaskItems, runModeConfig);

        // 初始化执行队列
        ExecutionQueue queue = apiBatchRunBaseService.getExecutionQueue(runModeConfig, ApiExecuteResourceType.API_SCENARIO.name(), execTask.getId(), userId);
        queue.setQueueId(execTask.getId());
        queue.setRerun(true);
        apiExecutionQueueService.insertQueue(queue);

        // 初始化队列项
        apiBatchRunBaseService.initExecutionQueueDetails(queue.getQueueId(), execTaskItems);

        // 执行第一个任务
        ExecutionQueueDetail nextDetail = apiExecutionQueueService.getNextDetail(queue.getQueueId());
        executeNextTask(queue, nextDetail);
    }

    /**
     * 并行重跑
     *
     */
    public void parallelRerunExecute(ExecTask execTask, String userId) {
        String projectId = execTask.getProjectId();
        List<ExecTaskItem> execTaskItems = extExecTaskItemMapper.selectRerunIdAndResourceIdByTaskId(execTask.getId());
        ApiRunModeConfigDTO runModeConfig = apiTestCaseBatchRunService.getRunModeConfig(execTask);

        // 删除重跑的步骤
        deleteRerunIntegratedStepResult(execTask, execTaskItems, runModeConfig);

        // 记录用例和任务的映射
        Map<String, String> resourceExecTaskItemMap = new TreeMap<>();
        execTaskItems.forEach(item -> resourceExecTaskItemMap.put(item.getResourceId(), item.getId()));

        TaskBatchRequestDTO taskRequest = getTaskBatchRequestDTO(projectId, runModeConfig);
        taskRequest.getTaskInfo().setTaskId(execTask.getId());
        taskRequest.getTaskInfo().setSetId(execTask.getId());
        taskRequest.getTaskInfo().setUserId(userId);
        taskRequest.getTaskInfo().setRerun(true);

        // 记录任务项，用于统计整体执行情况
        apiExecutionSetService.initSet(execTask.getId(), new ArrayList<>(resourceExecTaskItemMap.values()));
        apiBatchRunBaseService.parallelBatchExecute(taskRequest, runModeConfig, resourceExecTaskItemMap);
    }

    private void deleteRerunIntegratedStepResult(ExecTask execTask, List<ExecTaskItem> execTaskItems, ApiRunModeConfigDTO runModeConfig) {
        if (BooleanUtils.isTrue(execTask.getIntegrated())) {
            SubListUtils.dealForSubList(execTaskItems, TASK_BATCH_SIZE, subItems -> {
                // 删除子步骤,重新执行
                ApiScenarioReportStepExample stepExample = new ApiScenarioReportStepExample();
                stepExample.createCriteria()
                        .andReportIdEqualTo(runModeConfig.getCollectionReport().getReportId())
                        .andParentIdIn(subItems.stream().map(ExecTaskItem::getResourceId).toList());
                apiScenarioReportStepMapper.deleteByExample(stepExample);
            });
        }
    }
}
