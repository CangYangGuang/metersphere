package io.metersphere.api.grpc.scenario;

import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.grpc.dto.GrpcCaseReportContentDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioStepResultDTO;
import io.metersphere.sdk.dto.api.result.RequestResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class GrpcScenarioResultConverterTest {

    private final GrpcScenarioResultConverter converter = new GrpcScenarioResultConverter();

    @Test
    void shouldKeepGrpcFailureDetailsInScenarioReportContent() {
        ResponseAssertionResult assertion = new ResponseAssertionResult();
        assertion.setName("Recorder file path");
        assertion.setPass(false);
        assertion.setExpectedValue("non-empty");
        assertion.setActualValue("");

        GrpcScenarioStepResultDTO step = new GrpcScenarioStepResultDTO();
        step.setStepId("step-id");
        step.setStepName("stop recorder");
        step.setSuccess(false);
        step.setServerAddress("127.0.0.1:9099");
        step.setServiceName("polarcontrol.RecorderService");
        step.setMethodName("Stop");
        step.setGrpcStatus("OK");
        step.setGrpcCode(0);
        step.setRequestJson("{}");
        step.setResponseJson("{\"filePath\":\"\"}");
        step.setErrorMessage("assertion failed");
        step.setAssertionResults(List.of(assertion));

        RequestResult converted = converter.toRequestResult(step);

        Assertions.assertInstanceOf(GrpcCaseReportContentDTO.class, converted);
        GrpcCaseReportContentDTO content = (GrpcCaseReportContentDTO) converted;
        Assertions.assertEquals("127.0.0.1:9099", content.getServerAddress());
        Assertions.assertEquals("polarcontrol.RecorderService", content.getServiceName());
        Assertions.assertEquals("Stop", content.getMethodName());
        Assertions.assertEquals("assertion failed", content.getErrorMessage());
        Assertions.assertEquals(1, content.getAssertionResults().size());
        Assertions.assertFalse(content.getAssertionResults().getFirst().isPass());
    }

    @Test
    void shouldKeepWaitStepDetailsInScenarioReportContent() {
        GrpcScenarioStepResultDTO step = new GrpcScenarioStepResultDTO();
        step.setStepId("wait-step");
        step.setStepName("wait time");
        step.setStepType(ApiScenarioStepType.CONSTANT_TIMER.name());
        step.setSuccess(true);
        step.setDuration(3001L);
        step.setRequestJson("{\"delayMs\":3000}");
        step.setResponseJson("{\"message\":\"Wait completed\",\"actualDurationMs\":3001}");

        GrpcCaseReportContentDTO content = (GrpcCaseReportContentDTO) converter.toRequestResult(step);

        Assertions.assertEquals("WAIT", content.getMethod());
        Assertions.assertEquals("TIMER", content.getProtocol());
        Assertions.assertEquals(3001L, content.getDuration());
        Assertions.assertEquals(step.getRequestJson(), content.getRequestJson());
        Assertions.assertEquals(step.getResponseJson(), content.getResponseJson());
    }
}
