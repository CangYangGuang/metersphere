package io.metersphere.api.grpc.report;

import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseReportContentDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class GrpcCaseReportConverterTest {

    private final GrpcCaseReportConverter converter = new GrpcCaseReportConverter();

    @Test
    void shouldConvertSuccessResultToReportContent() {
        ApiGrpcCaseConfigDTO caseConfig = new ApiGrpcCaseConfigDTO();
        caseConfig.setServiceName("helloworld.Greeter");
        caseConfig.setMethodName("SayHello");
        caseConfig.setRequestJson("{\"name\":\"ms\"}");

        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress("127.0.0.1:50051");

        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(true);
        result.setGrpcStatus("OK");
        result.setGrpcCode(0);
        result.setDuration(120L);
        result.setResponseJson("{\"message\":\"hello\"}");

        ResponseAssertionResult assertion = new ResponseAssertionResult();
        assertion.setName("status");
        assertion.setPass(true);
        result.setAssertionResults(List.of(assertion));

        ExtractResult extract = new ExtractResult();
        extract.setName("msg");
        extract.setValue("hello");
        result.setExtractResults(List.of(extract));

        GrpcCaseReportContentDTO content = converter.toReportContent(
                caseConfig, param, result, "case-1", "gRPC Case");

        Assertions.assertEquals("GRPC", content.getProtocol());
        Assertions.assertEquals("GRPC", content.getMethod());
        Assertions.assertEquals("helloworld.Greeter", content.getServiceName());
        Assertions.assertEquals("SayHello", content.getMethodName());
        Assertions.assertEquals("OK", content.getGrpcStatus());
        Assertions.assertEquals(0, content.getGrpcCode());
        Assertions.assertTrue(content.getSuccess());
        Assertions.assertEquals(120L, content.getDuration());
        Assertions.assertEquals("{\"name\":\"ms\"}", content.getRequestJson());
        Assertions.assertEquals("{\"message\":\"hello\"}", content.getResponseJson());
        Assertions.assertEquals(1, content.getAssertionResults().size());
        Assertions.assertEquals(1, content.getExtractResults().size());
        Assertions.assertEquals("0", content.getResponseResult().getResponseCode());
        Assertions.assertEquals("OK", content.getResponseResult().getResponseMessage());
    }

    @Test
    void shouldConvertFailedResultToReportContent() {
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress("127.0.0.1:50051");
        param.setServiceName("svc.Service");
        param.setMethodName("Fail");
        param.setRequestJson("{\"id\":1}");

        GrpcCaseRunResultDTO result = new GrpcCaseRunResultDTO();
        result.setSuccess(false);
        result.setGrpcStatus("UNAVAILABLE");
        result.setGrpcCode(14);
        result.setDuration(50L);
        result.setErrorCode("GRPC_CALL_FAILED");
        result.setErrorMessage("connection refused");

        GrpcCaseReportContentDTO content = converter.toReportContent(
                null, param, result, "case-2", "Failed Case");

        Assertions.assertEquals("GRPC", content.getMethod());
        Assertions.assertFalse(content.getSuccess());
        Assertions.assertEquals("ERROR", content.getStatus());
        Assertions.assertEquals("svc.Service", content.getServiceName());
        Assertions.assertEquals("Fail", content.getMethodName());
        Assertions.assertEquals("GRPC_CALL_FAILED", content.getErrorCode());
        Assertions.assertEquals("connection refused", content.getErrorMessage());
        Assertions.assertEquals("14", content.getResponseResult().getResponseCode());
    }
}
