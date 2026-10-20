package io.metersphere.api.grpc.scenario;

import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class GrpcScenarioStepSupportTest {

    @Test
    void shouldRecognizeCustomRequestGrpcStep() {
        ApiScenarioStepCommonDTO step = new ApiScenarioStepCommonDTO();
        step.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("protocol", "GRPC");
        config.put("method", "GRPC");
        step.setConfig(config);

        Assertions.assertTrue(GrpcScenarioStepSupport.isGrpcScenarioStep(step));
    }

    @Test
    void shouldResolveInlineGrpcCaseConfigFromStepDetails() {
        GrpcScenarioStepConfigResolver resolver = new GrpcScenarioStepConfigResolver();
        ApiScenarioStepCommonDTO step = new ApiScenarioStepCommonDTO();
        step.setId("step-1");
        step.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
        step.setConfig(Map.of("protocol", "GRPC", "method", "GRPC"));

        ApiGrpcCaseConfigDTO grpcCaseConfig = new ApiGrpcCaseConfigDTO();
        grpcCaseConfig.setServiceName("hello.HelloService");
        grpcCaseConfig.setMethodName("SayHello");
        grpcCaseConfig.setDescriptorContent("descriptor");
        grpcCaseConfig.setRequestJson("{\"greeting\":\"scene custom\"}");
        grpcCaseConfig.setServerAddress("host.docker.internal:50051");

        GrpcScenarioRunContext context = new GrpcScenarioRunContext();
        context.getStepDetails().put("step-1", Map.of("grpcCaseConfig", grpcCaseConfig));

        ApiGrpcCaseConfigDTO resolved = resolver.resolve(step, context);
        Assertions.assertNotNull(resolved);
        Assertions.assertEquals("hello.HelloService", resolved.getServiceName());
        Assertions.assertEquals("SayHello", resolved.getMethodName());
    }

    @Test
    void shouldSupportGrpcScenarioWithWaitSteps() {
        ApiScenarioStepCommonDTO grpcStep = new ApiScenarioStepCommonDTO();
        grpcStep.setStepType(ApiScenarioStepType.API_CASE.name());
        grpcStep.setConfig(Map.of("protocol", "GRPC"));

        ApiScenarioStepCommonDTO waitStep = new ApiScenarioStepCommonDTO();
        waitStep.setStepType(ApiScenarioStepType.CONSTANT_TIMER.name());
        waitStep.setConfig(Map.of("delay", 3000));

        Assertions.assertTrue(GrpcScenarioStepSupport.isWaitStep(waitStep));
        Assertions.assertEquals(3000L, GrpcScenarioStepSupport.extractWaitDelay(waitStep.getConfig()));
        Assertions.assertTrue(GrpcScenarioStepSupport.supportsInProcessRun(List.of(grpcStep, waitStep)));
        Assertions.assertFalse(GrpcScenarioStepSupport.isMixedScenario(List.of(grpcStep, waitStep)));
    }

    @Test
    void shouldStillRejectGrpcScenarioMixedWithHttp() {
        ApiScenarioStepCommonDTO grpcStep = new ApiScenarioStepCommonDTO();
        grpcStep.setStepType(ApiScenarioStepType.API_CASE.name());
        grpcStep.setConfig(Map.of("protocol", "GRPC"));

        ApiScenarioStepCommonDTO httpStep = new ApiScenarioStepCommonDTO();
        httpStep.setStepType(ApiScenarioStepType.API_CASE.name());
        httpStep.setConfig(Map.of("protocol", "HTTP"));

        Assertions.assertFalse(GrpcScenarioStepSupport.supportsInProcessRun(List.of(grpcStep, httpStep)));
        Assertions.assertTrue(GrpcScenarioStepSupport.isMixedScenario(List.of(grpcStep, httpStep)));
    }
}
