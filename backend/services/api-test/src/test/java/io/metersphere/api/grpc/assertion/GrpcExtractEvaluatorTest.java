package io.metersphere.api.grpc.assertion;

import io.metersphere.api.grpc.dto.GrpcCaseExtractConfigDTO;
import io.metersphere.api.grpc.dto.GrpcExtractItemDTO;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class GrpcExtractEvaluatorTest {

    private final GrpcExtractEvaluator evaluator = new GrpcExtractEvaluator();

    @Test
    void shouldExtractJsonPathVariable() {
        GrpcInvokeResponse response = new GrpcInvokeResponse();
        response.setResponseJson("{\"message\":\"Hello MeterSphere\"}");

        GrpcExtractItemDTO item = new GrpcExtractItemDTO();
        item.setVariableName("msg");
        item.setExpression("$.message");
        item.setEnable(true);
        GrpcCaseExtractConfigDTO config = new GrpcCaseExtractConfigDTO();
        config.setItems(List.of(item));

        List<ExtractResult> results = evaluator.evaluate(config, response);

        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals("msg", results.getFirst().getName());
        Assertions.assertEquals("Hello MeterSphere", results.getFirst().getValue());
        Assertions.assertEquals("JSON_PATH", results.getFirst().getType());
    }

    @Test
    void shouldReturnEmptyValueWhenPathNotFound() {
        GrpcInvokeResponse response = new GrpcInvokeResponse();
        response.setResponseJson("{\"message\":\"Hello\"}");

        GrpcExtractItemDTO item = new GrpcExtractItemDTO();
        item.setVariableName("missing");
        item.setExpression("$.missing");
        item.setEnable(true);
        GrpcCaseExtractConfigDTO config = new GrpcCaseExtractConfigDTO();
        config.setItems(List.of(item));

        List<ExtractResult> results = evaluator.evaluate(config, response);

        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals("", results.getFirst().getValue());
    }
}
