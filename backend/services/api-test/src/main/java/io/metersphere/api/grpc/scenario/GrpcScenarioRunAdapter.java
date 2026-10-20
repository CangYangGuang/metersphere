package io.metersphere.api.grpc.scenario;

import io.metersphere.api.dto.scenario.ApiScenarioDebugRequest;
import io.metersphere.api.dto.scenario.ApiScenarioDetail;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.dto.scenario.ScenarioConfig;
import io.metersphere.api.dto.scenario.ScenarioOtherConfig;
import io.metersphere.api.dto.scenario.ScenarioVariable;
import io.metersphere.sdk.dto.api.task.GetRunScriptRequest;
import io.metersphere.api.grpc.dto.GrpcTestPlanScenarioReportContext;
import io.metersphere.api.grpc.report.GrpcScenarioReportPersistenceService;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunContext;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioStepResultDTO;
import io.metersphere.project.dto.environment.EnvironmentConfig;
import io.metersphere.project.dto.environment.variables.CommonVariables;
import io.metersphere.project.dto.environment.EnvironmentInfoDTO;
import io.metersphere.project.service.EnvironmentService;
import io.metersphere.sdk.util.LogUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class GrpcScenarioRunAdapter {

    static final long MIN_WAIT_DELAY_MS = 1L;
    static final long MAX_WAIT_DELAY_MS = 600_000L;

    @Resource
    private GrpcScenarioStepExecutor grpcScenarioStepExecutor;
    @Resource
    private GrpcScenarioDebugNotifier grpcScenarioDebugNotifier;
    @Resource
    private EnvironmentService environmentService;
    @Resource
    private GrpcScenarioReportPersistenceService grpcScenarioReportPersistenceService;

    public boolean supportsInProcessRun(List<? extends ApiScenarioStepCommonDTO> steps) {
        return GrpcScenarioStepSupport.supportsInProcessRun(steps);
    }

    public void debugRun(ApiScenarioDebugRequest request) {
        debugRun(request, null);
    }

    /**
     * 进程内执行 gRPC 场景。执行结果通过 websocket 实时回传；若提供 reportContext，
     * 则在执行结束后落库一份可查询的场景报告（报告头 + 步骤树 + 步骤详情）。
     */
    public void debugRun(ApiScenarioDebugRequest request, GrpcTestPlanScenarioReportContext reportContext) {
        CompletableFuture.runAsync(() -> {
            GrpcScenarioRunResultDTO runResult = null;
            try {
                runResult = run(buildContext(request));
                grpcScenarioDebugNotifier.notifyResult(request.getReportId(), runResult);
            } catch (Exception exception) {
                LogUtils.error("gRPC 场景调试执行失败: {}", request.getReportId(), exception);
            } finally {
                if (reportContext != null) {
                    try {
                        grpcScenarioReportPersistenceService.persistCompletedReport(runResult, reportContext);
                    } catch (Exception persistException) {
                        LogUtils.error("gRPC 场景报告持久化失败: {}", request.getReportId(), persistException);
                    }
                    try {
                        grpcScenarioReportPersistenceService.completeExecutionTask(runResult, reportContext);
                    } catch (Exception taskException) {
                        LogUtils.error("gRPC scenario task completion failed: {}", request.getReportId(), taskException);
                    }
                }
                grpcScenarioDebugNotifier.notifyEnd(request.getReportId());
            }
        });
    }

    public GrpcScenarioRunResultDTO run(GrpcScenarioRunContext context) {
        GrpcScenarioRunResultDTO runResult = new GrpcScenarioRunResultDTO();
        runResult.setSuccess(true);
        if (context == null || CollectionUtils.isEmpty(context.getSteps())) {
            runResult.setSuccess(false);
            return runResult;
        }

        List<GrpcScenarioStepResultDTO> stepResults = new ArrayList<>();
        for (ApiScenarioStepCommonDTO step : context.getSteps()) {
            if (step == null || BooleanUtils.isFalse(step.getEnable())) {
                continue;
            }
            GrpcScenarioStepResultDTO stepResult = GrpcScenarioStepSupport.isWaitStep(step)
                    ? executeWaitStep(step)
                    : grpcScenarioStepExecutor.execute(step, context);
            stepResults.add(stepResult);
            if (!Boolean.TRUE.equals(stepResult.getSuccess())) {
                runResult.setSuccess(false);
                if (context.isStopOnFailure()) {
                    break;
                }
            }
        }
        runResult.setStepResults(stepResults);
        if (stepResults.isEmpty()) {
            runResult.setSuccess(false);
        }
        return runResult;
    }

    private GrpcScenarioStepResultDTO executeWaitStep(ApiScenarioStepCommonDTO step) {
        GrpcScenarioStepResultDTO result = new GrpcScenarioStepResultDTO();
        result.setStepId(StringUtils.defaultIfBlank(step.getUniqueId(), step.getId()));
        result.setStepName(step.getName());
        result.setStepType(step.getStepType());
        result.setResourceId(step.getResourceId());
        result.setProjectId(step.getProjectId());

        Long delay = GrpcScenarioStepSupport.extractWaitDelay(step.getConfig());
        if (delay == null || delay < MIN_WAIT_DELAY_MS || delay > MAX_WAIT_DELAY_MS) {
            result.setSuccess(false);
            result.setDuration(0L);
            result.setErrorCode("GRPC_WAIT_DELAY_INVALID");
            result.setErrorMessage("Wait delay must be between 1 and 600000 ms");
            return result;
        }

        result.setRequestJson("{\"delayMs\":" + delay + "}");
        long startedAt = System.nanoTime();
        try {
            Thread.sleep(delay);
            long duration = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
            result.setSuccess(true);
            result.setDuration(duration);
            result.setResponseJson("{\"message\":\"Wait completed\",\"actualDurationMs\":" + duration + "}");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setDuration(Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L));
            result.setErrorCode("GRPC_WAIT_INTERRUPTED");
            result.setErrorMessage("Wait step was interrupted");
        }
        return result;
    }

    public GrpcScenarioRunContext buildContext(ApiScenarioDebugRequest request) {
        GrpcScenarioRunContext context = new GrpcScenarioRunContext();
        context.setReportId(request.getReportId());
        context.setProjectId(request.getProjectId());
        context.setEnvironmentId(request.getEnvironmentId());
        initScenarioVariables(context, request.getScenarioConfig());
        initFailureStrategy(context, request.getScenarioConfig());
        initEnvironmentVariables(context, request.getEnvironmentId(), request.getGrouped());
        context.setSteps(GrpcScenarioStepSupport.collectEnabledFlatSteps(request.getSteps()));
        context.setStepDetails(normalizeStepDetails(request.getStepDetails()));
        return context;
    }

    public GrpcScenarioRunContext buildContext(ApiScenarioDetail apiScenarioDetail, GetRunScriptRequest request) {
        GrpcScenarioRunContext context = new GrpcScenarioRunContext();
        context.setReportId(request.getTaskItem().getReportId());
        context.setProjectId(apiScenarioDetail.getProjectId());
        context.setEnvironmentId(resolveEnvironmentId(request, apiScenarioDetail.getEnvironmentId()));
        initScenarioVariables(context, apiScenarioDetail.getScenarioConfig());
        initFailureStrategy(context, apiScenarioDetail.getScenarioConfig());
        initEnvironmentVariables(context, context.getEnvironmentId(), resolveGrouped(request, apiScenarioDetail.getGrouped()));
        context.setSteps(GrpcScenarioStepSupport.collectEnabledFlatSteps(apiScenarioDetail.getSteps()));
        return context;
    }

    private Map<String, Object> normalizeStepDetails(Map<String, Object> stepDetails) {
        if (stepDetails == null || stepDetails.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        stepDetails.forEach((key, value) -> {
            if (value instanceof byte[] bytes) {
                normalized.put(key, new String(bytes));
            } else {
                normalized.put(key, value);
            }
        });
        return normalized;
    }

    private String resolveEnvironmentId(GetRunScriptRequest request, String caseEnvironmentId) {
        if (request == null || request.getRunModeConfig() == null) {
            return caseEnvironmentId;
        }
        String envId = request.getRunModeConfig().getEnvironmentId();
        if (StringUtils.isBlank(envId) || StringUtils.equals(envId, io.metersphere.sdk.constants.CommonConstants.DEFAULT_NULL_VALUE)) {
            return caseEnvironmentId;
        }
        return envId;
    }

    private boolean resolveGrouped(GetRunScriptRequest request, Boolean grouped) {
        if (request == null || request.getRunModeConfig() == null) {
            return BooleanUtils.isTrue(grouped);
        }
        String envId = request.getRunModeConfig().getEnvironmentId();
        if (StringUtils.isBlank(envId) || StringUtils.equals(envId, io.metersphere.sdk.constants.CommonConstants.DEFAULT_NULL_VALUE)) {
            return BooleanUtils.isTrue(grouped);
        }
        return BooleanUtils.isTrue(request.getRunModeConfig().getGrouped());
    }

    private void initScenarioVariables(GrpcScenarioRunContext context, ScenarioConfig scenarioConfig) {
        if (scenarioConfig == null || scenarioConfig.getVariable() == null) {
            return;
        }
        ScenarioVariable scenarioVariable = scenarioConfig.getVariable();
        if (CollectionUtils.isNotEmpty(scenarioVariable.getCommonVariables())) {
            for (CommonVariables variable : scenarioVariable.getCommonVariables()) {
                if (variable != null && BooleanUtils.isNotFalse(variable.getEnable())) {
                    context.getVariableContext().put(variable.getKey(), variable.getValue());
                }
            }
        }
    }

    private void initFailureStrategy(GrpcScenarioRunContext context, ScenarioConfig scenarioConfig) {
        context.setStopOnFailure(scenarioConfig != null
                && scenarioConfig.getOtherConfig() != null
                && StringUtils.equals(scenarioConfig.getOtherConfig().getFailureStrategy(),
                ScenarioOtherConfig.FailureStrategy.STOP.name()));
    }

    private void initEnvironmentVariables(GrpcScenarioRunContext context, ApiScenarioDebugRequest request) {
        initEnvironmentVariables(context, request.getEnvironmentId(), request.getGrouped());
    }

    private void initEnvironmentVariables(GrpcScenarioRunContext context, String environmentId, Boolean grouped) {
        if (StringUtils.isBlank(environmentId) || BooleanUtils.isTrue(grouped)) {
            return;
        }
        try {
            EnvironmentInfoDTO environmentInfo = environmentService.get(environmentId);
            if (environmentInfo == null || environmentInfo.getConfig() == null) {
                return;
            }
            EnvironmentConfig environmentConfig = environmentInfo.getConfig();
            if (CollectionUtils.isEmpty(environmentConfig.getCommonVariables())) {
                return;
            }
            String serverAddress = null;
            for (CommonVariables variable : environmentConfig.getCommonVariables()) {
                if (variable == null || BooleanUtils.isFalse(variable.getEnable()) || StringUtils.isBlank(variable.getKey())) {
                    continue;
                }
                context.getVariableContext().put(variable.getKey(), variable.getValue());
                if (StringUtils.equalsAnyIgnoreCase(variable.getKey(), "grpcServerAddress", "serverAddress")) {
                    serverAddress = variable.getValue();
                }
            }
            context.setDefaultServerAddress(serverAddress);
        } catch (Exception exception) {
            LogUtils.warn("读取环境变量失败: {}", environmentId, exception);
        }
    }
}
