package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcTestPlanScenarioReportContext {
    private String reportId;
    private String scenarioId;
    private String testPlanScenarioId;
    private String planRunReportScenarioId;
    private String environmentId;
    private String userId;
    private String triggerMode;
    private String runMode;
    private String poolId;
    private boolean planRun;
    /**
     * 独立场景（非测试计划）执行时用于初始化报告头
     */
    private String reportName;
    private String projectId;
    private Long startTime;
    private String taskId;
    private String taskItemId;
    private Long pendingCount;
}
