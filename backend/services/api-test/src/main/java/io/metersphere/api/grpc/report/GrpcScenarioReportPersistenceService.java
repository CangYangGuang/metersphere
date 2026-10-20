package io.metersphere.api.grpc.report;

import io.metersphere.api.domain.ApiScenarioReport;
import io.metersphere.api.domain.ApiScenario;
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
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.domain.ExecTask;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.mapper.ExecTaskItemMapper;
import io.metersphere.system.mapper.ExecTaskMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GrpcScenarioReportPersistenceService {

    @Resource
    private ApiScenarioReportMapper apiScenarioReportMapper;
    @Resource
    private ApiScenarioMapper apiScenarioMapper;
    @Resource
    private ApiScenarioReportStepMapper apiScenarioReportStepMapper;
    @Resource
    private ApiScenarioReportDetailMapper apiScenarioReportDetailMapper;
    @Resource
    private ApiScenarioReportDetailBlobMapper apiScenarioReportDetailBlobMapper;
    @Resource
    private GrpcScenarioResultConverter grpcScenarioResultConverter;
    @Resource
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Resource
    private ExecTaskMapper execTaskMapper;
    @Resource
    private ExecTaskItemMapper execTaskItemMapper;

    /**
     * 独立场景（非测试计划）gRPC 进程内执行完成后，落库一份可通过
     * /api/report/scenario/get/{id} 查询的报告：报告头 + 步骤树 + 每步详情。
     * 与 JMeter 执行路径（saveResult=true）不同，gRPC 场景需要在此显式持久化。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void persistCompletedReport(GrpcScenarioRunResultDTO runResult,
                                       GrpcTestPlanScenarioReportContext context) {
        if (context == null || StringUtils.isBlank(context.getReportId())) {
            LogUtils.warn("gRPC 场景报告持久化缺少 reportId，跳过");
            return;
        }
        if (apiScenarioReportMapper.selectByPrimaryKey(context.getReportId()) != null) {
            completeInitializedReport(null, safeRunResult(runResult), context);
            // 已存在（例如测试计划预初始化报告），走更新逻辑
            return;
        }
        long now = System.currentTimeMillis();
        boolean success = runResult != null && BooleanUtils.isTrue(runResult.getSuccess());
        List<GrpcScenarioStepResultDTO> stepResults = (runResult == null || runResult.getStepResults() == null)
                ? List.of() : runResult.getStepResults();

        ApiScenarioReport report = buildCompletedReport(null, context, runResult == null ? new GrpcScenarioRunResultDTO() : runResult,
                success, now, stepResults.size());
        report.setName(StringUtils.defaultIfBlank(context.getReportName(), "gRPC Scenario"));
        report.setProjectId(context.getProjectId());
        report.setEnvironmentId(context.getEnvironmentId());
        report.setCreateUser(context.getUserId());
        report.setUpdateUser(context.getUserId());
        report.setDeleted(false);
        report.setIntegrated(false);
        report.setTriggerMode(StringUtils.defaultIfBlank(context.getTriggerMode(), "MANUAL"));
        report.setRunMode(StringUtils.defaultIfBlank(context.getRunMode(), "RUN"));
        report.setPoolId(StringUtils.defaultIfBlank(context.getPoolId(), "NONE"));
        report.setStartTime(context.getStartTime() != null ? context.getStartTime() : now);
        apiScenarioReportMapper.insertSelective(report);
        updateScenarioExecutionSummary(context.getScenarioId(), context.getReportId(), report, context.getUserId());

        long sort = 1L;
        for (GrpcScenarioStepResultDTO stepResult : stepResults) {
            ApiScenarioReportStep reportStep = new ApiScenarioReportStep();
            reportStep.setReportId(context.getReportId());
            reportStep.setStepId(stepResult.getStepId());
            reportStep.setParentId("NONE");
            reportStep.setSort(sort);
            reportStep.setName(stepResult.getStepName());
            reportStep.setStepType(StringUtils.defaultIfBlank(stepResult.getStepType(), "API_CASE"));
            apiScenarioReportStepMapper.insertSelective(reportStep);
            persistStepDetail(context.getReportId(), stepResult, sort);
            sort++;
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void completeInitializedReport(ApiScenarioDetail apiScenarioDetail,
                                         GrpcScenarioRunResultDTO runResult,
                                         GrpcTestPlanScenarioReportContext context) {
        if (context == null || StringUtils.isBlank(context.getReportId())) {
            throw new IllegalArgumentException("report context is required");
        }
        runResult = safeRunResult(runResult);
        long now = System.currentTimeMillis();
        boolean success = BooleanUtils.isTrue(runResult.getSuccess());
        List<GrpcScenarioStepResultDTO> stepResults = runResult.getStepResults() == null ? List.of() : runResult.getStepResults();

        ApiScenarioReport report = buildCompletedReport(apiScenarioDetail, context, runResult, success, now, stepResults.size());
        apiScenarioReportMapper.updateByPrimaryKeySelective(report);
        String scenarioId = apiScenarioDetail == null ? context.getScenarioId() : apiScenarioDetail.getId();
        updateScenarioExecutionSummary(scenarioId, context.getReportId(), report, context.getUserId());

        long sort = 1L;
        for (GrpcScenarioStepResultDTO stepResult : stepResults) {
            persistStepDetail(context.getReportId(), stepResult, sort++);
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void completeExecutionTask(GrpcScenarioRunResultDTO runResult,
                                      GrpcTestPlanScenarioReportContext context) {
        if (context == null || StringUtils.isBlank(context.getTaskId())
                || StringUtils.isBlank(context.getTaskItemId())) {
            return;
        }
        long now = System.currentTimeMillis();
        String result = BooleanUtils.isTrue(safeRunResult(runResult).getSuccess())
                ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name();

        ExecTaskItem taskItem = new ExecTaskItem();
        taskItem.setId(context.getTaskItemId());
        taskItem.setStatus(ExecStatus.COMPLETED.name());
        taskItem.setResult(result);
        taskItem.setEndTime(now);
        execTaskItemMapper.updateByPrimaryKeySelective(taskItem);

        ExecTask task = new ExecTask();
        task.setId(context.getTaskId());
        task.setStatus(ExecStatus.COMPLETED.name());
        task.setResult(result);
        task.setEndTime(now);
        execTaskMapper.updateByPrimaryKeySelective(task);
    }

    private GrpcScenarioRunResultDTO safeRunResult(GrpcScenarioRunResultDTO runResult) {
        if (runResult != null) {
            return runResult;
        }
        GrpcScenarioRunResultDTO failedResult = new GrpcScenarioRunResultDTO();
        failedResult.setSuccess(false);
        failedResult.setStepResults(List.of());
        return failedResult;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void updateScenarioExecutionSummary(String scenarioId,
                                               String reportId,
                                               GrpcScenarioRunResultDTO runResult,
                                               String userId) {
        if (StringUtils.isBlank(scenarioId) || StringUtils.isBlank(reportId)) {
            return;
        }
        GrpcScenarioRunResultDTO safeResult = safeRunResult(runResult);
        List<GrpcScenarioStepResultDTO> stepResults = safeResult.getStepResults() == null
                ? List.of() : safeResult.getStepResults();
        long successCount = stepResults.stream()
                .filter(step -> step != null && Boolean.TRUE.equals(step.getSuccess()))
                .count();
        String passRate = stepResults.isEmpty()
                ? (BooleanUtils.isTrue(safeResult.getSuccess()) ? "100.00" : "0.00")
                : formatRate(successCount * 100.0 / stepResults.size());

        ApiScenario scenario = new ApiScenario();
        scenario.setId(scenarioId);
        scenario.setLastReportId(reportId);
        scenario.setLastReportStatus(BooleanUtils.isTrue(safeResult.getSuccess())
                ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        scenario.setRequestPassRate(passRate);
        scenario.setUpdateUser(userId);
        apiScenarioMapper.updateByPrimaryKeySelective(scenario);
    }

    private void updateScenarioExecutionSummary(String scenarioId,
                                                String reportId,
                                                ApiScenarioReport report,
                                                String userId) {
        if (StringUtils.isBlank(scenarioId) || report == null) {
            return;
        }
        ApiScenario scenario = new ApiScenario();
        scenario.setId(scenarioId);
        scenario.setLastReportId(reportId);
        scenario.setLastReportStatus(report.getStatus());
        scenario.setRequestPassRate(report.getRequestPassRate());
        scenario.setUpdateUser(userId);
        apiScenarioMapper.updateByPrimaryKeySelective(scenario);
    }

    private ApiScenarioReport buildCompletedReport(ApiScenarioDetail apiScenarioDetail,
                                                   GrpcTestPlanScenarioReportContext context,
                                                   GrpcScenarioRunResultDTO runResult,
                                                   boolean success,
                                                   long endTime,
                                                   int stepCount) {
        ApiScenarioReport report = new ApiScenarioReport();
        report.setId(context.getReportId());
        report.setEndTime(endTime);
        report.setUpdateTime(endTime);
        report.setUpdateUser(context.getUserId());
        report.setExecStatus(ExecStatus.COMPLETED.name());
        report.setStatus(success ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());

        long totalDuration = stepResultsDuration(runResult.getStepResults());
        report.setRequestDuration(totalDuration);

        long successCount = 0L;
        long errorCount = 0L;
        long assertionTotal = 0L;
        long assertionSuccess = 0L;
        if (CollectionUtils.isNotEmpty(runResult.getStepResults())) {
            for (GrpcScenarioStepResultDTO stepResult : runResult.getStepResults()) {
                if (Boolean.TRUE.equals(stepResult.getSuccess())) {
                    successCount++;
                } else {
                    errorCount++;
                }
                if (CollectionUtils.isNotEmpty(stepResult.getAssertionResults())) {
                    assertionTotal += stepResult.getAssertionResults().size();
                    assertionSuccess += stepResult.getAssertionResults().stream()
                            .filter(item -> item != null && item.isPass())
                            .count();
                }
            }
        }
        if (stepCount > 0 && successCount == 0 && errorCount == 0) {
            errorCount = success ? 0L : (long) stepCount;
            successCount = success ? (long) stepCount : 0L;
        }
        report.setSuccessCount(successCount);
        report.setErrorCount(errorCount);
        long pendingCount = context.getPendingCount() == null ? 0L : context.getPendingCount();
        report.setPendingCount(pendingCount);
        report.setFakeErrorCount(0L);
        report.setAssertionCount(assertionTotal);
        report.setAssertionSuccessCount(assertionSuccess);
        report.setAssertionPassRate(assertionTotal == 0 ? "100.00" : formatRate(assertionSuccess * 100.0 / assertionTotal));

        ApiScenarioReport rateHolder = new ApiScenarioReport();
        rateHolder.setSuccessCount(report.getSuccessCount());
        rateHolder.setErrorCount(report.getErrorCount());
        rateHolder.setPendingCount(report.getPendingCount());
        rateHolder.setFakeErrorCount(report.getFakeErrorCount());
        long requestTotal = Math.max(stepCount + pendingCount, successCount + errorCount + pendingCount);
        rateHolder = apiBatchRunBaseService.computeRequestRate(rateHolder, requestTotal);
        report.setRequestPassRate(rateHolder.getRequestPassRate());
        report.setRequestErrorRate(rateHolder.getRequestErrorRate());
        report.setRequestPendingRate(rateHolder.getRequestPendingRate());
        report.setRequestFakeErrorRate(rateHolder.getRequestFakeErrorRate());
        return report;
    }

    private void persistStepDetail(String reportId, GrpcScenarioStepResultDTO stepResult, long sort) {
        RequestResult requestResult = grpcScenarioResultConverter.toRequestResult(stepResult);
        boolean stepSuccess = Boolean.TRUE.equals(stepResult.getSuccess());

        ApiScenarioReportDetail detail = new ApiScenarioReportDetail();
        detail.setId(IDGenerator.nextStr());
        detail.setReportId(reportId);
        detail.setStepId(stepResult.getStepId());
        detail.setStatus(stepSuccess ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        detail.setRequestName(stepResult.getStepName());
        detail.setRequestTime(stepResult.getDuration() != null ? stepResult.getDuration() : 0L);
        detail.setCode(stepResult.getGrpcCode() == null ? null : String.valueOf(stepResult.getGrpcCode()));
        String responseBody = stepResult.getResponseJson();
        detail.setResponseSize(StringUtils.isBlank(responseBody) ? 0L : responseBody.getBytes().length);
        detail.setSort(sort);
        apiScenarioReportDetailMapper.insertSelective(detail);

        ApiScenarioReportDetailBlob blob = new ApiScenarioReportDetailBlob();
        blob.setId(detail.getId());
        blob.setReportId(reportId);
        blob.setContent(JSON.toJSONBytes(requestResult));
        // 使用 batchInsert 写入，绕过 MybatisInterceptor 对 ApiScenarioReportDetailBlob.content 的 zip 压缩，
        // 与 JMeter 报告详情写入方式保持一致；否则读取端 ResultTypeHandler 直接按明文 JSON 解析压缩字节会抛
        // JsonParseException: Unrecognized token 'PK'（zip 魔数）。
        apiScenarioReportDetailBlobMapper.batchInsert(List.of(blob));
    }

    private long stepResultsDuration(List<GrpcScenarioStepResultDTO> stepResults) {
        if (CollectionUtils.isEmpty(stepResults)) {
            return 0L;
        }
        return stepResults.stream()
                .map(GrpcScenarioStepResultDTO::getDuration)
                .filter(duration -> duration != null)
                .mapToLong(Long::longValue)
                .sum();
    }

    private String formatRate(double rate) {
        return String.format("%.2f", rate);
    }
}
