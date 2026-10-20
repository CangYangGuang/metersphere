package io.metersphere.api.service.definition;

import io.metersphere.api.domain.ApiReportDetail;
import io.metersphere.api.dto.definition.ApiReportDetailDTO;
import io.metersphere.api.grpc.dto.GrpcCaseReportContentDTO;
import io.metersphere.api.mapper.ApiReportDetailMapper;
import io.metersphere.sdk.dto.api.result.RequestResult;
import io.metersphere.sdk.util.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiReportServiceGrpcParseTest {

    private static final String REPORT_ID = "report-id";
    private static final String STEP_ID = "step-id";

    @Mock
    private ApiReportDetailMapper apiReportDetailMapper;

    private ApiReportService apiReportService;

    @BeforeEach
    void setUp() {
        apiReportService = new ApiReportService();
        ReflectionTestUtils.setField(apiReportService, "apiReportDetailMapper", apiReportDetailMapper);
    }

    @Test
    void shouldParseGrpcReportDetailContent() {
        GrpcCaseReportContentDTO content = new GrpcCaseReportContentDTO();
        content.setMethod("GRPC");
        content.setProtocol("GRPC");
        content.setServiceName("helloworld.Greeter");
        content.setMethodName("SayHello");
        content.setGrpcStatus("OK");
        content.setGrpcCode(0);
        content.setRequestJson("{\"name\":\"MeterSphere\"}");
        content.setResponseJson("{\"message\":\"hello\"}");

        ApiReportDetail detail = new ApiReportDetail();
        detail.setReportId(REPORT_ID);
        detail.setStepId(STEP_ID);
        detail.setContent(JSON.toJSONBytes(content));
        when(apiReportDetailMapper.selectByExampleWithBLOBs(any())).thenReturn(List.of(detail));

        List<ApiReportDetailDTO> results = apiReportService.getDetail(REPORT_ID, STEP_ID);

        Assertions.assertEquals(1, results.size());
        RequestResult parsed = results.get(0).getContent();
        Assertions.assertInstanceOf(GrpcCaseReportContentDTO.class, parsed);
        GrpcCaseReportContentDTO grpcContent = (GrpcCaseReportContentDTO) parsed;
        Assertions.assertEquals("GRPC", grpcContent.getMethod());
        Assertions.assertEquals("helloworld.Greeter", grpcContent.getServiceName());
        Assertions.assertEquals("SayHello", grpcContent.getMethodName());
        Assertions.assertEquals("OK", grpcContent.getGrpcStatus());
        Assertions.assertEquals(0, grpcContent.getGrpcCode());
        Assertions.assertEquals("{\"name\":\"MeterSphere\"}", grpcContent.getRequestJson());
        Assertions.assertEquals("{\"message\":\"hello\"}", grpcContent.getResponseJson());
    }
}
