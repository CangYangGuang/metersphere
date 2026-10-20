package io.metersphere.api.service.scenario;

import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.ApiRunModeRequest;
import io.metersphere.api.dto.scenario.ApiScenarioBatchRunRequest;
import io.metersphere.api.dto.scenario.ApiScenarioDetail;
import io.metersphere.api.dto.scenario.ApiScenarioStepDTO;
import io.metersphere.api.grpc.scenario.GrpcScenarioRunAdapter;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.api.service.ApiBatchRunBaseService;
import io.metersphere.sdk.constants.ApiBatchRunMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiScenarioBatchRunServiceTest {

    @Mock
    private ApiScenarioService apiScenarioService;
    @Mock
    private ApiBatchRunBaseService apiBatchRunBaseService;
    @Mock
    private GrpcScenarioRunAdapter grpcScenarioRunAdapter;

    private ApiScenarioBatchRunService service;

    @BeforeEach
    void setUp() {
        service = spy(new ApiScenarioBatchRunService());
        ReflectionTestUtils.setField(service, "apiScenarioService", apiScenarioService);
        ReflectionTestUtils.setField(service, "apiBatchRunBaseService", apiBatchRunBaseService);
        ReflectionTestUtils.setField(service, "grpcScenarioRunAdapter", grpcScenarioRunAdapter);
    }

    @Test
    void shouldRoutePureGrpcBatchToInProcessRunner() {
        ApiScenarioBatchRunRequest request = request(ApiBatchRunMode.PARALLEL.name());
        List<String> ids = List.of("scenario-1", "scenario-2");
        List<ApiScenarioDetail> details = List.of(detail("scenario-1"), detail("scenario-2"));
        when(apiScenarioService.doSelectIds(request, false)).thenReturn(ids);
        doReturn(details).when(service).getScenarioDetails(ids);
        when(grpcScenarioRunAdapter.supportsInProcessRun(details.get(0).getSteps())).thenReturn(true);
        when(grpcScenarioRunAdapter.supportsInProcessRun(details.get(1).getSteps())).thenReturn(true);
        doNothing().when(service).executePureGrpcBatch(request, "user-id", ids, details);

        service.batchRun(request, "user-id");

        verify(service).executePureGrpcBatch(request, "user-id", ids, details);
        verify(apiBatchRunBaseService, never()).isParallel(request.getRunModeConfig().getRunMode());
    }

    @Test
    void shouldKeepNonGrpcBatchOnExistingRunner() {
        ApiScenarioBatchRunRequest request = request(ApiBatchRunMode.PARALLEL.name());
        List<String> ids = List.of("scenario-1");
        List<ApiScenarioDetail> details = List.of(detail("scenario-1"));
        when(apiScenarioService.doSelectIds(request, false)).thenReturn(ids);
        doReturn(details).when(service).getScenarioDetails(ids);
        when(grpcScenarioRunAdapter.supportsInProcessRun(details.getFirst().getSteps())).thenReturn(false);
        when(apiBatchRunBaseService.isParallel(ApiBatchRunMode.PARALLEL.name())).thenReturn(true);
        doNothing().when(service).parallelExecute(request, "user-id");

        service.batchRun(request, "user-id");

        verify(service).parallelExecute(request, "user-id");
        verify(service, never()).executePureGrpcBatch(request, "user-id", ids, details);
    }

    @Test
    void shouldRouteDisabledOnlyGrpcBatchToInProcessRunner() {
        ApiScenarioBatchRunRequest request = request(ApiBatchRunMode.PARALLEL.name());
        List<String> ids = List.of("scenario-1", "scenario-2");
        ApiScenarioDetail enabledDetail = grpcDetail("scenario-1", true);
        ApiScenarioDetail disabledDetail = grpcDetail("scenario-2", false);
        List<ApiScenarioDetail> details = List.of(enabledDetail, disabledDetail);
        when(apiScenarioService.doSelectIds(request, false)).thenReturn(ids);
        doReturn(details).when(service).getScenarioDetails(ids);
        when(grpcScenarioRunAdapter.supportsInProcessRun(enabledDetail.getSteps())).thenReturn(true);
        when(grpcScenarioRunAdapter.supportsInProcessRun(disabledDetail.getSteps())).thenReturn(false);
        doNothing().when(service).executePureGrpcBatch(request, "user-id", ids, details);

        service.batchRun(request, "user-id");

        verify(service).executePureGrpcBatch(request, "user-id", ids, details);
        verify(service, never()).parallelExecute(request, "user-id");
    }

    @Test
    void shouldCreateFailureDetailsForEveryDisabledGrpcStep() {
        ApiScenarioDetail detail = grpcDetail("scenario-1", false);
        ApiScenarioStepDTO secondStep = grpcStep("step-2", false);
        detail.setSteps(List.of(detail.getSteps().getFirst(), secondStep));

        GrpcScenarioRunResultDTO result = ReflectionTestUtils.invokeMethod(service, "disabledGrpcResult", detail);

        assertFalse(result.getSuccess());
        assertEquals(2, result.getStepResults().size());
        assertTrue(result.getStepResults().stream()
                .allMatch(step -> "GRPC_BATCH_NO_ENABLED_STEPS".equals(step.getErrorCode())));
        assertTrue(result.getStepResults().stream()
                .allMatch(step -> step.getErrorMessage().contains("disabled")));
    }

    @Test
    void shouldCreateStepFailureDetailsForBatchInfrastructureError() {
        ApiScenarioDetail detail = detail("scenario-1");
        ApiScenarioStepDTO step = detail.getSteps().getFirst();
        step.setUniqueId("step-1");
        step.setName("GetDevice");
        step.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("protocol", "GRPC");
        step.setConfig(config);

        GrpcScenarioRunResultDTO result = ReflectionTestUtils.invokeMethod(service, "failedGrpcResult",
                detail, "GRPC_BATCH_EXECUTION_ERROR", "connection refused");

        assertFalse(result.getSuccess());
        assertEquals(1, result.getStepResults().size());
        assertEquals("step-1", result.getStepResults().getFirst().getStepId());
        assertEquals("GRPC_BATCH_EXECUTION_ERROR", result.getStepResults().getFirst().getErrorCode());
        assertEquals("connection refused", result.getStepResults().getFirst().getErrorMessage());
    }

    private ApiScenarioBatchRunRequest request(String runMode) {
        ApiScenarioBatchRunRequest request = new ApiScenarioBatchRunRequest();
        request.setProjectId("project-id");
        ApiRunModeRequest runModeRequest = new ApiRunModeRequest();
        runModeRequest.setRunMode(runMode);
        request.setRunModeConfig(runModeRequest);
        return request;
    }

    private ApiScenarioDetail detail(String id) {
        ApiScenarioDetail detail = new ApiScenarioDetail();
        detail.setId(id);
        detail.setProjectId("project-id");
        ApiScenarioStepDTO step = new ApiScenarioStepDTO();
        step.setUniqueId(id + "-step");
        step.setName("gRPC step");
        step.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
        detail.setSteps(List.of(step));
        return detail;
    }

    private ApiScenarioDetail grpcDetail(String id, boolean enabled) {
        ApiScenarioDetail detail = new ApiScenarioDetail();
        detail.setId(id);
        detail.setProjectId("project-id");
        detail.setSteps(List.of(grpcStep(id + "-step", enabled)));
        return detail;
    }

    private ApiScenarioStepDTO grpcStep(String id, boolean enabled) {
        ApiScenarioStepDTO step = new ApiScenarioStepDTO();
        step.setUniqueId(id);
        step.setName("gRPC step");
        step.setStepType(ApiScenarioStepType.API_CASE.name());
        step.setEnable(enabled);
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("protocol", "GRPC");
        step.setConfig(config);
        return step;
    }
}
