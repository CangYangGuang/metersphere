package io.metersphere.api.grpc.report;

import io.metersphere.api.domain.*;
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
import io.metersphere.sdk.constants.ApiExecuteResourceType;
import io.metersphere.sdk.constants.CommonConstants;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.constants.ExecTaskType;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.constants.TaskTriggerMode;
import io.metersphere.sdk.util.DateUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.ExecTask;
import io.metersphere.system.domain.ExecTaskItem;
import io.metersphere.system.mapper.ExecTaskItemMapper;
import io.metersphere.system.mapper.ExecTaskMapper;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrpcCaseReportPersistenceService {

    @Resource
    private ApiReportMapper apiReportMapper;
    @Resource
    private ApiReportDetailMapper apiReportDetailMapper;
    @Resource
    private ApiReportStepMapper apiReportStepMapper;
    @Resource
    private ApiTestCaseRecordMapper apiTestCaseRecordMapper;
    @Resource
    private ApiReportRelateTaskMapper apiReportRelateTaskMapper;
    @Resource
    private ApiTestCaseRunService apiTestCaseRunService;
    @Resource
    private ApiCommonService apiCommonService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ExecTaskMapper execTaskMapper;
    @Resource
    private ExecTaskItemMapper execTaskItemMapper;
    @Resource
    private GrpcCasePersistenceService grpcCasePersistenceService;
    @Resource
    private GrpcCaseReportConverter grpcCaseReportConverter;
    @Resource
    private ApiBatchRunBaseService apiBatchRunBaseService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public String saveReport(ApiTestCase apiTestCase,
                             GrpcCaseRunParamDTO param,
                             GrpcCaseRunResultDTO result,
                             String reportId) {
        String effectiveReportId = StringUtils.defaultIfBlank(reportId, IDGenerator.nextStr());
        String userId = resolveUserId();
        long now = System.currentTimeMillis();
        boolean success = BooleanUtils.isTrue(result.getSuccess());

        ApiGrpcCaseConfigDTO caseConfig = grpcCasePersistenceService.loadDtoByCaseId(apiTestCase.getId());
        GrpcCaseReportContentDTO content = grpcCaseReportConverter.toReportContent(
                caseConfig, param, result, apiTestCase.getId(), apiTestCase.getName());

        ApiReport apiReport = buildCompletedReport(apiTestCase, effectiveReportId, userId, result, success, now);
        apiReportMapper.insertSelective(apiReport);

        ApiReportStep apiReportStep = apiTestCaseRunService.getApiReportStep(apiTestCase, effectiveReportId, 1L);
        apiReportStepMapper.insertSelective(apiReportStep);

        ApiTestCaseRecord apiTestCaseRecord = apiTestCaseRunService.getApiTestCaseRecord(apiTestCase, effectiveReportId);
        apiTestCaseRecordMapper.insertSelective(apiTestCaseRecord);

        ApiReportDetail apiReportDetail = buildReportDetail(apiTestCase, effectiveReportId, result, content, success, now);
        apiReportDetailMapper.insertSelective(apiReportDetail);

        persistExecutionHistory(apiTestCase, effectiveReportId, userId, result, success, now);

        return effectiveReportId;
    }

    private void persistExecutionHistory(ApiTestCase apiTestCase,
                                         String reportId,
                                         String userId,
                                         GrpcCaseRunResultDTO result,
                                         boolean success,
                                         long endTime) {
        long startTime = endTime - (result.getDuration() != null ? result.getDuration() : 0L);
        Project project = projectMapper.selectByPrimaryKey(apiTestCase.getProjectId());

        ExecTask execTask = apiCommonService.newExecTask(apiTestCase.getProjectId(), userId);
        execTask.setCaseCount(1L);
        execTask.setTaskName(apiTestCase.getName());
        execTask.setOrganizationId(project == null ? null : project.getOrganizationId());
        execTask.setTriggerMode(TaskTriggerMode.MANUAL.name());
        execTask.setTaskType(ExecTaskType.API_CASE.name());
        execTask.setIntegrated(false);
        execTask.setStatus(ExecStatus.COMPLETED.name());
        execTask.setResult(success ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        execTask.setStartTime(startTime);
        execTask.setEndTime(endTime);
        execTaskMapper.insertSelective(execTask);

        ExecTaskItem execTaskItem = apiCommonService.newExecTaskItem(
                execTask.getId(), apiTestCase.getProjectId(), userId);
        execTaskItem.setOrganizationId(project == null ? null : project.getOrganizationId());
        execTaskItem.setResourceType(ApiExecuteResourceType.API_CASE.name());
        execTaskItem.setResourceId(apiTestCase.getId());
        execTaskItem.setCaseId(apiTestCase.getId());
        execTaskItem.setResourceName(apiTestCase.getName());
        execTaskItem.setDeleted(false);
        execTaskItem.setStatus(ExecStatus.COMPLETED.name());
        execTaskItem.setResult(success ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        execTaskItem.setStartTime(startTime);
        execTaskItem.setEndTime(endTime);
        execTaskItemMapper.insertSelective(execTaskItem);

        ApiReportRelateTask relation = apiCommonService.getApiReportRelateTask(execTaskItem.getId(), reportId);
        apiReportRelateTaskMapper.insertSelective(relation);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void completeInitializedReport(ApiTestCase apiTestCase,
                                         GrpcCaseRunParamDTO param,
                                         GrpcCaseRunResultDTO result,
                                         GrpcTestPlanReportContext context) {
        if (context == null || StringUtils.isBlank(context.getReportId())) {
            throw new IllegalArgumentException("report context is required");
        }
        String reportId = context.getReportId();
        String userId = StringUtils.defaultIfBlank(context.getUserId(), resolveUserId());
        long now = System.currentTimeMillis();
        boolean success = BooleanUtils.isTrue(result.getSuccess());

        ApiGrpcCaseConfigDTO caseConfig = grpcCasePersistenceService.loadDtoByCaseId(apiTestCase.getId());
        GrpcCaseReportContentDTO content = grpcCaseReportConverter.toReportContent(
                caseConfig, param, result, apiTestCase.getId(), apiTestCase.getName());

        ApiReport apiReport = buildCompletedReport(apiTestCase, reportId, userId, result, success, now);
        apiReport.setTestPlanCaseId(StringUtils.defaultIfBlank(context.getTestPlanCaseId(), CommonConstants.DEFAULT_NULL_VALUE));
        apiReport.setEnvironmentId(context.getEnvironmentId());
        apiReport.setTriggerMode(StringUtils.defaultIfBlank(context.getTriggerMode(), TaskTriggerMode.MANUAL.name()));
        apiReport.setRunMode(StringUtils.defaultIfBlank(context.getRunMode(), "RUN"));
        apiReport.setPoolId(StringUtils.defaultIfBlank(context.getPoolId(), CommonConstants.DEFAULT_NULL_VALUE));
        apiReport.setPlan(context.isPlan());
        apiReportMapper.updateByPrimaryKeySelective(apiReport);

        String detailStepId = StringUtils.defaultIfBlank(context.getReportDetailStepId(), apiTestCase.getId());
        ApiReportDetail apiReportDetail = buildReportDetail(apiTestCase, reportId, result, content, success, now);
        apiReportDetail.setStepId(detailStepId);
        apiReportDetailMapper.insertSelective(apiReportDetail);
    }

    private ApiReport buildCompletedReport(ApiTestCase apiTestCase,
                                           String reportId,
                                           String userId,
                                           GrpcCaseRunResultDTO result,
                                           boolean success,
                                           long endTime) {
        ApiReport apiReport = apiTestCaseRunService.getApiReport(userId);
        apiReport.setId(reportId);
        apiReport.setName(apiTestCase.getName() + "_" + DateUtils.getTimeString(endTime));
        apiReport.setProjectId(apiTestCase.getProjectId());
        apiReport.setEnvironmentId(apiTestCase.getEnvironmentId());
        apiReport.setTestPlanCaseId(CommonConstants.DEFAULT_NULL_VALUE);
        apiReport.setTriggerMode(TaskTriggerMode.MANUAL.name());
        apiReport.setRunMode("RUN");
        apiReport.setPoolId(CommonConstants.DEFAULT_NULL_VALUE);
        apiReport.setIntegrated(false);
        apiReport.setPlan(false);
        apiReport.setDeleted(false);
        apiReport.setStartTime(endTime - (result.getDuration() != null ? result.getDuration() : 0L));
        apiReport.setEndTime(endTime);
        apiReport.setRequestDuration(result.getDuration() != null ? result.getDuration() : 0L);
        apiReport.setStatus(success ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        apiReport.setExecStatus(ExecStatus.COMPLETED.name());

        long assertionTotal = result.getAssertionResults() == null ? 0 : result.getAssertionResults().size();
        long assertionSuccess = result.getAssertionResults() == null ? 0 : result.getAssertionResults().stream()
                .filter(item -> item != null && item.isPass())
                .count();
        apiReport.setSuccessCount(success ? 1L : 0L);
        apiReport.setErrorCount(success ? 0L : 1L);
        apiReport.setPendingCount(0L);
        apiReport.setFakeErrorCount(0L);
        apiReport.setAssertionCount(assertionTotal);
        apiReport.setAssertionSuccessCount(assertionSuccess);
        apiReport.setAssertionPassRate(assertionTotal == 0 ? "100.00" : formatRate(assertionSuccess * 100.0 / assertionTotal));

        ApiScenarioReport rateHolder = new ApiScenarioReport();
        rateHolder.setSuccessCount(apiReport.getSuccessCount());
        rateHolder.setErrorCount(apiReport.getErrorCount());
        rateHolder.setPendingCount(apiReport.getPendingCount());
        rateHolder.setFakeErrorCount(apiReport.getFakeErrorCount());
        rateHolder = apiBatchRunBaseService.computeRequestRate(rateHolder, 1L);
        apiReport.setRequestPassRate(rateHolder.getRequestPassRate());
        apiReport.setRequestErrorRate(rateHolder.getRequestErrorRate());
        apiReport.setRequestPendingRate(rateHolder.getRequestPendingRate());
        apiReport.setRequestFakeErrorRate(rateHolder.getRequestFakeErrorRate());
        return apiReport;
    }

    private ApiReportDetail buildReportDetail(ApiTestCase apiTestCase,
                                              String reportId,
                                              GrpcCaseRunResultDTO result,
                                              GrpcCaseReportContentDTO content,
                                              boolean success,
                                              long endTime) {
        ApiReportDetail detail = new ApiReportDetail();
        detail.setId(IDGenerator.nextStr());
        detail.setReportId(reportId);
        detail.setStepId(apiTestCase.getId());
        detail.setStatus(success ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        detail.setRequestName(apiTestCase.getName());
        detail.setRequestTime(result.getDuration() != null ? result.getDuration() : 0L);
        detail.setCode(result.getGrpcCode() == null ? null : String.valueOf(result.getGrpcCode()));
        detail.setResponseSize(StringUtils.isBlank(result.getResponseJson()) ? 0L : result.getResponseJson().getBytes().length);
        detail.setContent(JSON.toJSONBytes(content));
        return detail;
    }

    private String resolveUserId() {
        String userId = SessionUtils.getUserId();
        return StringUtils.defaultIfBlank(userId, "system");
    }

    private String formatRate(double rate) {
        return String.format("%.2f", rate);
    }
}
