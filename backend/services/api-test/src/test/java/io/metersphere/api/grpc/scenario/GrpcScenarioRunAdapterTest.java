package io.metersphere.api.grpc.scenario;

import io.metersphere.api.constants.ApiScenarioStepRefType;
import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunContext;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioStepResultDTO;
import io.metersphere.api.grpc.service.GrpcCaseRunService;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcScenarioRunAdapterTest {

    private static final String CASE_ID_1 = "grpc-case-1";
    private static final String CASE_ID_2 = "grpc-case-2";
    private static final String SERVER_ADDRESS = "127.0.0.1:50051";

    @InjectMocks
    private GrpcScenarioRunAdapter grpcScenarioRunAdapter;

    @Mock
    private GrpcScenarioStepExecutor grpcScenarioStepExecutor;
    @Mock
    private GrpcScenarioDebugNotifier grpcScenarioDebugNotifier;
    @Mock
    private GrpcCaseRunService grpcCaseRunService;
    @Mock
    private GrpcScenarioStepConfigResolver grpcScenarioStepConfigResolver;

    private GrpcScenarioStepExecutor realExecutor;
    private GrpcScenarioVariableResolver variableResolver;

    private final Map<String, ApiGrpcCaseConfigDTO> caseConfigMap = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        caseConfigMap.clear();
        variableResolver = new GrpcScenarioVariableResolver();
        realExecutor = new GrpcScenarioStepExecutor();
        ReflectionTestUtils.setField(realExecutor, "grpcCaseRunService", grpcCaseRunService);
        ReflectionTestUtils.setField(realExecutor, "grpcScenarioStepConfigResolver", grpcScenarioStepConfigResolver);
        ReflectionTestUtils.setField(realExecutor, "grpcScenarioVariableResolver", variableResolver);
        ReflectionTestUtils.setField(grpcScenarioRunAdapter, "grpcScenarioStepExecutor", realExecutor);
        lenient().when(grpcScenarioStepConfigResolver.resolve(any(), any())).thenAnswer(invocation -> {
            ApiScenarioStepCommonDTO step = invocation.getArgument(0);
            if (step == null || step.getResourceId() == null) {
                return null;
            }
            return caseConfigMap.get(step.getResourceId());
        });
    }

    @Test
    void shouldRunSingleGrpcStepSuccessfully() {
        mockCaseConfig(CASE_ID_1, "{\"name\":\"MeterSphere\"}", null);
        when(grpcCaseRunService.run(eq(CASE_ID_1), any())).thenReturn(successResult("{\"message\":\"ok\"}"));

        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(buildContext(List.of(buildGrpcStep(CASE_ID_1, "step-1"))));

        Assertions.assertTrue(result.getSuccess());
        Assertions.assertEquals(1, result.getStepResults().size());
        Assertions.assertTrue(result.getStepResults().getFirst().getSuccess());
    }

    @Test
    void shouldFailScenarioWhenAssertionFailed() {
        mockCaseConfig(CASE_ID_1, "{}", null);
        GrpcCaseRunResultDTO runResult = successResult("{\"message\":\"ok\"}");
        ResponseAssertionResult assertionResult = new ResponseAssertionResult();
        assertionResult.setPass(false);
        runResult.setAssertionResults(List.of(assertionResult));
        runResult.setSuccess(false);
        when(grpcCaseRunService.run(eq(CASE_ID_1), any())).thenReturn(runResult);

        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(buildContext(List.of(buildGrpcStep(CASE_ID_1, "step-1"))));

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertFalse(result.getStepResults().getFirst().getSuccess());
    }

    @Test
    void shouldExtractVariableAndUseInNextStep() {
        mockCaseConfig(CASE_ID_1, "{}", null);
        mockCaseConfig(CASE_ID_2, "{\"name\":\"${userName}\"}", null);

        GrpcCaseRunResultDTO firstResult = successResult("{\"userName\":\"Alice\"}");
        ExtractResult extractResult = new ExtractResult();
        extractResult.setName("userName");
        extractResult.setValue("Alice");
        firstResult.setExtractResults(List.of(extractResult));
        when(grpcCaseRunService.run(eq(CASE_ID_1), any())).thenReturn(firstResult);
        when(grpcCaseRunService.run(eq(CASE_ID_2), any())).thenReturn(successResult("{\"message\":\"Hello Alice\"}"));

        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(buildContext(List.of(
                buildGrpcStep(CASE_ID_1, "step-1"),
                buildGrpcStep(CASE_ID_2, "step-2")
        )));

        Assertions.assertTrue(result.getSuccess());
        Assertions.assertEquals(2, result.getStepResults().size());

        ArgumentCaptor<GrpcCaseRunParamDTO> captor = ArgumentCaptor.forClass(GrpcCaseRunParamDTO.class);
        verify(grpcCaseRunService).run(eq(CASE_ID_2), captor.capture());
        Assertions.assertEquals("{\"name\":\"Alice\"}", captor.getValue().getRequestJson());
    }

    @Test
    void shouldReturnClearErrorWhenCaseConfigMissing() {
        caseConfigMap.clear();

        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(buildContext(List.of(buildGrpcStep(CASE_ID_1, "step-1"))));

        Assertions.assertFalse(result.getSuccess());
        GrpcScenarioStepResultDTO stepResult = result.getStepResults().getFirst();
        Assertions.assertEquals(GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND.name(), stepResult.getErrorCode());
    }

    @Test
    void shouldContinueAfterFailedStepByDefault() {
        mockCaseConfig(CASE_ID_1, "{}", null);
        mockCaseConfig(CASE_ID_2, "{}", null);
        when(grpcCaseRunService.run(eq(CASE_ID_1), any())).thenReturn(errorResult());
        when(grpcCaseRunService.run(eq(CASE_ID_2), any())).thenReturn(successResult("{}"));

        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(buildContext(List.of(
                buildGrpcStep(CASE_ID_1, "step-1"),
                buildGrpcStep(CASE_ID_2, "step-2")
        )));

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(2, result.getStepResults().size());
        Assertions.assertFalse(result.getStepResults().getFirst().getSuccess());
        Assertions.assertTrue(result.getStepResults().getLast().getSuccess());
    }

    @Test
    void shouldStopAfterFailedStepWhenConfigured() {
        mockCaseConfig(CASE_ID_1, "{}", null);
        mockCaseConfig(CASE_ID_2, "{}", null);
        when(grpcCaseRunService.run(eq(CASE_ID_1), any())).thenReturn(errorResult());
        GrpcScenarioRunContext context = buildContext(List.of(
                buildGrpcStep(CASE_ID_1, "step-1"),
                buildGrpcStep(CASE_ID_2, "step-2")
        ));
        context.setStopOnFailure(true);

        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(context);

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(1, result.getStepResults().size());
        verify(grpcCaseRunService, never()).run(eq(CASE_ID_2), any());
    }

    @Test
    void shouldReuseFirstStepServerAddressForFollowingSteps() {
        mockCaseConfig(CASE_ID_1, "{}", null);
        mockCaseConfig(CASE_ID_2, "{}", null);
        caseConfigMap.get(CASE_ID_1).setServerAddress(SERVER_ADDRESS);
        when(grpcCaseRunService.run(eq(CASE_ID_1), any())).thenReturn(successResult("{}"));
        when(grpcCaseRunService.run(eq(CASE_ID_2), any())).thenReturn(successResult("{}"));
        GrpcScenarioRunContext context = buildContext(List.of(
                buildGrpcStep(CASE_ID_1, "step-1"),
                buildGrpcStep(CASE_ID_2, "step-2")
        ));
        context.setDefaultServerAddress(null);

        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(context);

        Assertions.assertTrue(result.getSuccess());
        ArgumentCaptor<GrpcCaseRunParamDTO> captor = ArgumentCaptor.forClass(GrpcCaseRunParamDTO.class);
        verify(grpcCaseRunService).run(eq(CASE_ID_2), captor.capture());
        Assertions.assertEquals(SERVER_ADDRESS, captor.getValue().getServerAddress());
    }

    @Test
    void shouldExecuteGrpcAndWaitStepsInOriginalOrder() {
        mockCaseConfig(CASE_ID_1, "{}", null);
        mockCaseConfig(CASE_ID_2, "{}", null);
        when(grpcCaseRunService.run(eq(CASE_ID_1), any())).thenReturn(successResult("{\"step\":1}"));
        when(grpcCaseRunService.run(eq(CASE_ID_2), any())).thenReturn(successResult("{\"step\":2}"));

        long startedAt = System.nanoTime();
        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(buildContext(List.of(
                buildGrpcStep(CASE_ID_1, "step-1"),
                buildWaitStep("wait-step", 40L, true),
                buildGrpcStep(CASE_ID_2, "step-2")
        )));
        long elapsed = (System.nanoTime() - startedAt) / 1_000_000L;

        Assertions.assertTrue(result.getSuccess());
        Assertions.assertEquals(3, result.getStepResults().size());
        Assertions.assertEquals(ApiScenarioStepType.API_CASE.name(), result.getStepResults().get(0).getStepType());
        Assertions.assertEquals(ApiScenarioStepType.CONSTANT_TIMER.name(), result.getStepResults().get(1).getStepType());
        Assertions.assertEquals(ApiScenarioStepType.API_CASE.name(), result.getStepResults().get(2).getStepType());
        Assertions.assertTrue(result.getStepResults().get(1).getSuccess());
        Assertions.assertTrue(result.getStepResults().get(1).getDuration() >= 35L);
        Assertions.assertTrue(elapsed >= 35L);
        Assertions.assertEquals("{\"delayMs\":40}", result.getStepResults().get(1).getRequestJson());
    }

    @Test
    void shouldSkipDisabledWaitStep() {
        mockCaseConfig(CASE_ID_1, "{}", null);
        when(grpcCaseRunService.run(eq(CASE_ID_1), any())).thenReturn(successResult("{}"));

        GrpcScenarioRunResultDTO result = grpcScenarioRunAdapter.run(buildContext(List.of(
                buildWaitStep("disabled-wait", 100L, false),
                buildGrpcStep(CASE_ID_1, "step-1")
        )));

        Assertions.assertTrue(result.getSuccess());
        Assertions.assertEquals(1, result.getStepResults().size());
        Assertions.assertEquals(ApiScenarioStepType.API_CASE.name(), result.getStepResults().getFirst().getStepType());
    }

    @Test
    void shouldRejectMixedHttpAndGrpcScenario() {
        ApiScenarioStepCommonDTO grpcStep = buildGrpcStep(CASE_ID_1, "grpc-step");
        ApiScenarioStepCommonDTO httpStep = new ApiScenarioStepCommonDTO();
        httpStep.setId("http-step");
        httpStep.setEnable(true);
        httpStep.setStepType(ApiScenarioStepType.API_CASE.name());
        httpStep.setRefType(ApiScenarioStepRefType.REF.name());
        httpStep.setResourceId("http-case");
        httpStep.setProjectId("project-1");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("protocol", "HTTP");
        httpStep.setConfig(config);

        Assertions.assertFalse(grpcScenarioRunAdapter.supportsInProcessRun(List.of(grpcStep, httpStep)));
    }

    private GrpcScenarioRunContext buildContext(List<ApiScenarioStepCommonDTO> steps) {
        GrpcScenarioRunContext context = new GrpcScenarioRunContext();
        context.setDefaultServerAddress(SERVER_ADDRESS);
        context.setSteps(steps);
        return context;
    }

    private ApiScenarioStepCommonDTO buildGrpcStep(String caseId, String uniqueId) {
        ApiScenarioStepCommonDTO step = new ApiScenarioStepCommonDTO();
        step.setId(uniqueId + "-id");
        step.setUniqueId(uniqueId);
        step.setEnable(true);
        step.setName("gRPC step");
        step.setStepType(ApiScenarioStepType.API_CASE.name());
        step.setRefType(ApiScenarioStepRefType.REF.name());
        step.setResourceId(caseId);
        step.setProjectId("project-1");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("protocol", "GRPC");
        step.setConfig(config);
        return step;
    }

    private ApiScenarioStepCommonDTO buildWaitStep(String uniqueId, long delay, boolean enabled) {
        ApiScenarioStepCommonDTO step = new ApiScenarioStepCommonDTO();
        step.setId(uniqueId + "-id");
        step.setUniqueId(uniqueId);
        step.setEnable(enabled);
        step.setName("wait time");
        step.setStepType(ApiScenarioStepType.CONSTANT_TIMER.name());
        step.setProjectId("project-1");
        step.setConfig(Map.of("delay", delay));
        return step;
    }

    private void mockCaseConfig(String caseId, String requestJson, List<io.metersphere.api.grpc.dto.GrpcMetadataItemDTO> metadata) {
        ApiGrpcCaseConfigDTO config = new ApiGrpcCaseConfigDTO();
        config.setApiCaseId(caseId);
        config.setServiceName("helloworld.Greeter");
        config.setMethodName("SayHello");
        config.setDescriptorContent("descriptor");
        config.setRequestJson(requestJson);
        config.setMetadata(metadata);
        caseConfigMap.put(caseId, config);
    }

    private GrpcCaseRunResultDTO successResult(String responseJson) {
        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(true);
        result.setGrpcStatus("OK");
        result.setGrpcCode(0);
        result.setDuration(10L);
        result.setResponseJson(responseJson);
        return result;
    }

    private GrpcCaseRunResultDTO errorResult() {
        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(false);
        result.setErrorCode(GrpcResultCode.GRPC_INVALID_ARGUMENT.name());
        result.setErrorMessage("invalid argument");
        return result;
    }
}
