package io.metersphere.api.grpc.assertion;

import io.metersphere.api.grpc.dto.GrpcAssertionItemDTO;
import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.sdk.constants.MsAssertionCondition;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class GrpcAssertionEvaluatorTest {

    private final GrpcAssertionEvaluator evaluator = new GrpcAssertionEvaluator();

    @Test
    void shouldPassJsonPathAssertion() {
        GrpcInvokeResponse response = buildResponse("{\"message\":\"Hello MeterSphere\"}", "OK", 0);
        GrpcCaseAssertionConfigDTO config = new GrpcCaseAssertionConfigDTO();
        config.setItems(List.of(buildAssertion(GrpcAssertionType.JSON_PATH.name(), "$.message",
                MsAssertionCondition.EQUALS.name(), "Hello MeterSphere")));

        List<ResponseAssertionResult> results = evaluator.evaluate(config, response);

        Assertions.assertEquals(1, results.size());
        Assertions.assertTrue(results.getFirst().isPass());
        Assertions.assertEquals("Hello MeterSphere", results.getFirst().getActualValue());
    }

    @Test
    void shouldFailJsonPathAssertion() {
        GrpcInvokeResponse response = buildResponse("{\"message\":\"Hello\"}", "OK", 0);
        GrpcCaseAssertionConfigDTO config = new GrpcCaseAssertionConfigDTO();
        config.setItems(List.of(buildAssertion(GrpcAssertionType.JSON_PATH.name(), "$.message",
                MsAssertionCondition.EQUALS.name(), "World")));

        List<ResponseAssertionResult> results = evaluator.evaluate(config, response);

        Assertions.assertEquals(1, results.size());
        Assertions.assertFalse(results.getFirst().isPass());
    }

    @Test
    void shouldPassJsonPathExistsAssertionWhenValueIsPresent() {
        GrpcInvokeResponse response = buildResponse("{\"version\":\"0.9.8.1\"}", "OK", 0);
        GrpcCaseAssertionConfigDTO config = new GrpcCaseAssertionConfigDTO();
        config.setItems(List.of(buildAssertion(GrpcAssertionType.JSON_PATH.name(), "$.version",
                "EXISTS", null)));

        List<ResponseAssertionResult> results = evaluator.evaluate(config, response);

        Assertions.assertEquals(1, results.size());
        Assertions.assertTrue(results.getFirst().isPass());
        Assertions.assertEquals("0.9.8.1", results.getFirst().getActualValue());
    }

    @Test
    void shouldFailJsonPathExistsAssertionWhenValueIsMissing() {
        GrpcInvokeResponse response = buildResponse("{\"message\":\"ok\"}", "OK", 0);
        GrpcCaseAssertionConfigDTO config = new GrpcCaseAssertionConfigDTO();
        config.setItems(List.of(buildAssertion(GrpcAssertionType.JSON_PATH.name(), "$.version",
                "EXISTS", null)));

        List<ResponseAssertionResult> results = evaluator.evaluate(config, response);

        Assertions.assertEquals(1, results.size());
        Assertions.assertFalse(results.getFirst().isPass());
    }

    @Test
    void shouldPassGrpcStatusAssertion() {
        GrpcInvokeResponse response = buildResponse("{}", "OK", 0);
        GrpcCaseAssertionConfigDTO config = new GrpcCaseAssertionConfigDTO();
        config.setItems(List.of(buildAssertion(GrpcAssertionType.GRPC_STATUS.name(), null,
                MsAssertionCondition.EQUALS.name(), "OK")));

        List<ResponseAssertionResult> results = evaluator.evaluate(config, response);

        Assertions.assertEquals(1, results.size());
        Assertions.assertTrue(results.getFirst().isPass());
        Assertions.assertEquals("OK", results.getFirst().getActualValue());
    }

    @Test
    void shouldPassGrpcCodeAssertion() {
        GrpcInvokeResponse response = buildResponse("{}", "OK", 0);
        GrpcCaseAssertionConfigDTO config = new GrpcCaseAssertionConfigDTO();
        config.setItems(List.of(buildAssertion(GrpcAssertionType.GRPC_CODE.name(), null,
                MsAssertionCondition.EQUALS.name(), "0")));

        List<ResponseAssertionResult> results = evaluator.evaluate(config, response);

        Assertions.assertEquals(1, results.size());
        Assertions.assertTrue(results.getFirst().isPass());
        Assertions.assertEquals("0", results.getFirst().getActualValue());
    }

    @Test
    void shouldSkipDisabledAssertion() {
        GrpcInvokeResponse response = buildResponse("{}", "OK", 0);
        GrpcAssertionItemDTO item = buildAssertion(GrpcAssertionType.GRPC_CODE.name(), null,
                MsAssertionCondition.EQUALS.name(), "0");
        item.setEnable(false);
        GrpcCaseAssertionConfigDTO config = new GrpcCaseAssertionConfigDTO();
        config.setItems(List.of(item));

        List<ResponseAssertionResult> results = evaluator.evaluate(config, response);

        Assertions.assertTrue(results.isEmpty());
    }

    private GrpcAssertionItemDTO buildAssertion(String type, String expression, String condition, String expectedValue) {
        GrpcAssertionItemDTO item = new GrpcAssertionItemDTO();
        item.setType(type);
        item.setName(type + "-assertion");
        item.setExpression(expression);
        item.setCondition(condition);
        item.setExpectedValue(expectedValue);
        item.setEnable(true);
        return item;
    }

    private GrpcInvokeResponse buildResponse(String responseJson, String grpcStatus, Integer grpcCode) {
        GrpcInvokeResponse response = new GrpcInvokeResponse();
        response.setSuccess(true);
        response.setResponseJson(responseJson);
        response.setGrpcStatus(grpcStatus);
        response.setGrpcCode(grpcCode);
        return response;
    }
}
