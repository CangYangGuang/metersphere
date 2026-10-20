package io.metersphere.api.service.scenario;

import io.metersphere.api.constants.ApiScenarioStepRefType;
import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.domain.ApiScenarioStepBlob;
import io.metersphere.api.dto.scenario.ApiScenarioDebugRequest;
import io.metersphere.api.dto.scenario.ApiScenarioStepRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiScenarioRunServiceGrpcTest {

    @InjectMocks
    private ApiScenarioRunService apiScenarioRunService;

    @Mock
    private ApiScenarioService apiScenarioService;

    @Test
    void shouldHydrateMissingGrpcStepDetailFromPersistedBlob() {
        ApiScenarioStepRequest step = new ApiScenarioStepRequest();
        step.setId("step-1");
        step.setEnable(true);
        step.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
        step.setRefType(ApiScenarioStepRefType.DIRECT.name());

        ApiScenarioDebugRequest request = new ApiScenarioDebugRequest();
        request.setSteps(List.of(step));
        request.setStepDetails(new LinkedHashMap<>());

        ApiScenarioStepBlob blob = new ApiScenarioStepBlob();
        blob.setId("step-1");
        blob.setContent("{\"grpcCaseConfig\":{\"serviceName\":\"ProductService\"}}".getBytes(StandardCharsets.UTF_8));
        when(apiScenarioService.getStepBlobByIds(List.of("step-1"))).thenReturn(List.of(blob));

        ReflectionTestUtils.invokeMethod(apiScenarioRunService, "hydrateGrpcStepDetails", request);

        Assertions.assertEquals(
                "{\"grpcCaseConfig\":{\"serviceName\":\"ProductService\"}}",
                request.getStepDetails().get("step-1")
        );
    }

    @Test
    void shouldPreserveFrontendGrpcStepDetailString() {
        ApiScenarioStepRequest step = new ApiScenarioStepRequest();
        step.setId("step-1");
        step.setEnable(true);
        step.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
        step.setRefType(ApiScenarioStepRefType.DIRECT.name());

        String detail = "{\"grpcCaseConfig\":{\"serviceName\":\"ProductService\"}}";
        ApiScenarioDebugRequest request = new ApiScenarioDebugRequest();
        request.setSteps(List.of(step));
        request.setStepDetails(new LinkedHashMap<>(Map.of("step-1", detail)));

        ReflectionTestUtils.invokeMethod(apiScenarioRunService, "hydrateGrpcStepDetails", request);

        Assertions.assertEquals(detail, request.getStepDetails().get("step-1"));
    }
}
