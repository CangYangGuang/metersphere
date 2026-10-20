package io.metersphere.api.grpc.report;

import io.metersphere.api.grpc.dto.GrpcCaseReportContentDTO;
import io.metersphere.sdk.dto.api.result.RequestResult;
import io.metersphere.sdk.util.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ApiReportDetailContentParserTest {

    @Test
    void shouldParseGrpcReportContentWithFastjsonFields() {
        GrpcCaseReportContentDTO content = new GrpcCaseReportContentDTO();
        content.setProtocol("GRPC");
        content.setMethod("GRPC");
        content.setServiceName("hello.HelloService");
        content.setMethodName("SayHello");
        content.setGrpcStatus("OK");
        content.setGrpcCode(0);
        content.setRequestJson("{\"greeting\":\"hi\"}");
        content.setResponseJson("{\"reply\":\"hello\"}");
        content.setErrorMessage(null);

        RequestResult parsed = ApiReportDetailContentParser.parse(JSON.toJSONBytes(content));

        Assertions.assertInstanceOf(GrpcCaseReportContentDTO.class, parsed);
        GrpcCaseReportContentDTO grpc = (GrpcCaseReportContentDTO) parsed;
        Assertions.assertEquals("hello.HelloService", grpc.getServiceName());
        Assertions.assertEquals("OK", grpc.getGrpcStatus());
        Assertions.assertEquals("{\"reply\":\"hello\"}", grpc.getResponseJson());
    }

    @Test
    void shouldParseHttpReportContentWithoutRegression() {
        String httpJson = """
                {"resourceId":"","method":"POST","status":"SUCCESS","responseResult":{"responseCode":"200","body":"ok"}}
                """;

        RequestResult parsed = ApiReportDetailContentParser.parse(httpJson.getBytes());

        Assertions.assertNotNull(parsed);
        Assertions.assertFalse(parsed instanceof GrpcCaseReportContentDTO);
        Assertions.assertEquals("POST", parsed.getMethod());
        Assertions.assertNotNull(parsed.getResponseResult());
    }

    @Test
    void shouldParseGrpcErrorResponseWithoutNpe() {
        GrpcCaseReportContentDTO content = new GrpcCaseReportContentDTO();
        content.setProtocol("GRPC");
        content.setMethod("GRPC");
        content.setGrpcStatus("UNAVAILABLE");
        content.setGrpcCode(14);
        content.setSuccess(false);
        content.setErrorMessage("connection refused");
        content.setResponseJson(null);

        RequestResult parsed = ApiReportDetailContentParser.parse(JSON.toJSONBytes(content));

        Assertions.assertInstanceOf(GrpcCaseReportContentDTO.class, parsed);
        GrpcCaseReportContentDTO grpc = (GrpcCaseReportContentDTO) parsed;
        Assertions.assertEquals("UNAVAILABLE", grpc.getGrpcStatus());
        Assertions.assertEquals(14, grpc.getGrpcCode());
        Assertions.assertEquals("connection refused", grpc.getErrorMessage());
        Assertions.assertNull(grpc.getResponseJson());
    }

    @Test
    void shouldReturnNullForEmptyContent() {
        Assertions.assertNull(ApiReportDetailContentParser.parse(null));
        Assertions.assertNull(ApiReportDetailContentParser.parse(new byte[0]));
    }
}
