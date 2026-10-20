package io.metersphere.api.grpc.dto;

import lombok.Data;

@Data
public class GrpcTestPlanReportContext {
    private String reportId;
    private String reportDetailStepId;
    private String testPlanCaseId;
    private String environmentId;
    private String userId;
    private String triggerMode;
    private String runMode;
    private String poolId;
    private boolean plan;
}
