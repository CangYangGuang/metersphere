package io.metersphere.api.grpc.service;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunParamDTO;
import io.metersphere.api.grpc.dto.GrpcCaseRunResultDTO;
import io.metersphere.api.grpc.dto.GrpcInvokeRequest;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.api.grpc.assertion.GrpcAssertionEvaluator;
import io.metersphere.api.grpc.assertion.GrpcExtractEvaluator;
import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.processor.GrpcProcessorRunner;
import io.metersphere.api.grpc.scenario.GrpcScenarioVariableResolver;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.service.definition.ApiTestCaseService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.dto.api.result.ResponseAssertionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcCaseRunServiceTest {

    private static final String CASE_ID = "case-id";
    private static final String API_ID = "grpc-api-id";
    private static final String SERVER_ADDRESS = "127.0.0.1:50051";

    @InjectMocks
    private GrpcCaseRunService grpcCaseRunService;

    @Mock
    private ApiTestCaseService apiTestCaseService;
    @Mock
    private ApiDefinitionMapper apiDefinitionMapper;
    @Mock
    private GrpcCasePersistenceService grpcCasePersistenceService;
    @Mock
    private GrpcInvokeService grpcInvokeService;
    @Mock
    private GrpcAssertionEvaluator grpcAssertionEvaluator;
    @Mock
    private GrpcExtractEvaluator grpcExtractEvaluator;
    @Mock
    private GrpcProcessorRunner grpcProcessorRunner;
    @Mock
    private GrpcScenarioVariableResolver grpcScenarioVariableResolver;

    @BeforeEach
    void setUpProcessors() {
        lenient().when(grpcProcessorRunner.runPre(any(), any())).thenReturn(List.of());
        lenient().when(grpcProcessorRunner.runPost(any(), any(), any())).thenReturn(List.of());
        lenient().when(grpcScenarioVariableResolver.resolve(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldRunGrpcCaseSuccessfully() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcInvokeResponse invokeResponse = successResponse("{\"message\":\"Hello MeterSphere\"}");
        when(grpcInvokeService.invoke(any())).thenReturn(invokeResponse);

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, buildParam());

        Assertions.assertTrue(result.getSuccess());
        Assertions.assertTrue(result.getResponseJson().contains("Hello MeterSphere"));
        ArgumentCaptor<GrpcInvokeRequest> captor = ArgumentCaptor.forClass(GrpcInvokeRequest.class);
        verify(grpcInvokeService).invoke(captor.capture());
        GrpcInvokeRequest invokeRequest = captor.getValue();
        Assertions.assertEquals(SERVER_ADDRESS, invokeRequest.getServerAddress());
        Assertions.assertEquals("helloworld.Greeter", invokeRequest.getServiceName());
        Assertions.assertEquals("SayHello", invokeRequest.getMethodName());
        Assertions.assertEquals("{\"name\":\"MeterSphere\"}", invokeRequest.getRequestJson());
    }

    @Test
    void shouldReturnJsonConvertFailed() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcInvokeResponse invokeResponse = errorResponse(GrpcResultCode.GRPC_JSON_CONVERT_FAILED);
        when(grpcInvokeService.invoke(any())).thenReturn(invokeResponse);

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, buildParam());

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(GrpcResultCode.GRPC_JSON_CONVERT_FAILED.name(), result.getErrorCode());
    }

    @Test
    void shouldReturnMethodNotFound() {
        mockGrpcCaseContext(buildCaseConfig());
        when(grpcInvokeService.invoke(any())).thenReturn(errorResponse(GrpcResultCode.GRPC_METHOD_NOT_FOUND));

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, buildParam());

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(GrpcResultCode.GRPC_METHOD_NOT_FOUND.name(), result.getErrorCode());
    }

    @Test
    void shouldReturnConnectFailed() {
        mockGrpcCaseContext(buildCaseConfig());
        when(grpcInvokeService.invoke(any())).thenReturn(errorResponse(GrpcResultCode.GRPC_CONNECT_FAILED));

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, buildParam());

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(GrpcResultCode.GRPC_CONNECT_FAILED.name(), result.getErrorCode());
    }

    @Test
    void shouldReturnTimeout() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcInvokeResponse invokeResponse = errorResponse(GrpcResultCode.GRPC_TIMEOUT);
        invokeResponse.setGrpcStatus("DEADLINE_EXCEEDED");
        when(grpcInvokeService.invoke(any())).thenReturn(invokeResponse);

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, buildParam());

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(GrpcResultCode.GRPC_TIMEOUT.name(), result.getErrorCode());
    }

    @Test
    void shouldPassMetadataToInvokeRequest() {
        ApiGrpcCaseConfigDTO caseConfig = buildCaseConfig();
        GrpcMetadataItemDTO metadataItem = new GrpcMetadataItemDTO();
        metadataItem.setKey("x-test-key");
        metadataItem.setValue("invoke-metadata");
        metadataItem.setEnabled(true);
        caseConfig.setMetadata(List.of(metadataItem));
        mockGrpcCaseContext(caseConfig);
        when(grpcInvokeService.invoke(any())).thenReturn(successResponse("{}"));

        grpcCaseRunService.run(CASE_ID, buildParam());

        ArgumentCaptor<GrpcInvokeRequest> captor = ArgumentCaptor.forClass(GrpcInvokeRequest.class);
        verify(grpcInvokeService).invoke(captor.capture());
        Assertions.assertEquals(1, captor.getValue().getMetadata().size());
        Assertions.assertEquals("x-test-key", captor.getValue().getMetadata().get(0).getKey());
        Assertions.assertEquals("invoke-metadata", captor.getValue().getMetadata().get(0).getValue());
    }

    @Test
    void shouldReturnErrorWhenCaseConfigMissing() {
        mockGrpcCaseWithoutConfig();

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, buildParam());

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND.name(), result.getErrorCode());
    }

    @Test
    void shouldReturnErrorWhenServerAddressMissing() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, new GrpcCaseRunParamDTO());

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID.name(), result.getErrorCode());
    }

    @Test
    void shouldFallbackToPersistedServerAddress() {
        ApiGrpcCaseConfigDTO caseConfig = buildCaseConfig();
        caseConfig.setServerAddress(SERVER_ADDRESS);
        mockGrpcCaseContext(caseConfig);
        when(grpcInvokeService.invoke(any())).thenReturn(successResponse("{}"));

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, new GrpcCaseRunParamDTO());

        Assertions.assertTrue(result.getSuccess());
        ArgumentCaptor<GrpcInvokeRequest> captor = ArgumentCaptor.forClass(GrpcInvokeRequest.class);
        verify(grpcInvokeService).invoke(captor.capture());
        Assertions.assertEquals(SERVER_ADDRESS, captor.getValue().getServerAddress());
    }

    @Test
    void shouldConvertInvokeExceptionToResult() {
        mockGrpcCaseContext(buildCaseConfig());
        when(grpcInvokeService.invoke(any())).thenThrow(new MSException(GrpcResultCode.GRPC_CALL_FAILED, "invoke failed"));

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, buildParam());

        Assertions.assertFalse(result.getSuccess());
        Assertions.assertEquals(GrpcResultCode.GRPC_CALL_FAILED.name(), result.getErrorCode());
        Assertions.assertEquals("invoke failed", result.getErrorMessage());
    }

    @Test
    void shouldPassExpectedNonOkResponseWhenGrpcAssertionPasses() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcInvokeResponse response = errorResponse(GrpcResultCode.GRPC_INVALID_ARGUMENT);
        response.setGrpcStatus("INVALID_ARGUMENT");
        response.setGrpcCode(3);
        when(grpcInvokeService.invoke(any())).thenReturn(response);
        when(grpcAssertionEvaluator.evaluate(any(), eq(response)))
                .thenReturn(List.of(assertion("GRPC_STATUS", true)));

        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, buildParam());

        Assertions.assertTrue(result.getSuccess());
        Assertions.assertEquals("INVALID_ARGUMENT", result.getGrpcStatus());
        Assertions.assertEquals(3, result.getGrpcCode());
    }

    @Test
    void shouldFailNonOkResponseWhenGrpcAssertionFails() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcInvokeResponse response = errorResponse(GrpcResultCode.GRPC_INVALID_ARGUMENT);
        response.setGrpcStatus("INVALID_ARGUMENT");
        response.setGrpcCode(3);
        when(grpcInvokeService.invoke(any())).thenReturn(response);
        when(grpcAssertionEvaluator.evaluate(any(), eq(response)))
                .thenReturn(List.of(assertion("GRPC_STATUS", false)));

        Assertions.assertFalse(grpcCaseRunService.run(CASE_ID, buildParam()).getSuccess());
    }

    @Test
    void shouldFailNonOkResponseWithoutGrpcStatusAssertion() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcInvokeResponse response = errorResponse(GrpcResultCode.GRPC_INVALID_ARGUMENT);
        response.setGrpcStatus("INVALID_ARGUMENT");
        response.setGrpcCode(3);
        when(grpcInvokeService.invoke(any())).thenReturn(response);
        when(grpcAssertionEvaluator.evaluate(any(), eq(response)))
                .thenReturn(List.of(assertion("JSON_PATH", true)));

        Assertions.assertFalse(grpcCaseRunService.run(CASE_ID, buildParam()).getSuccess());
    }

    @Test
    void shouldFailTransportErrorEvenWhenNotEqualsStatusAssertionPasses() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcInvokeResponse response = errorResponse(GrpcResultCode.GRPC_CONNECT_FAILED);
        when(grpcInvokeService.invoke(any())).thenReturn(response);
        when(grpcAssertionEvaluator.evaluate(any(), eq(response)))
                .thenReturn(List.of(assertion("GRPC_STATUS", true)));

        Assertions.assertFalse(grpcCaseRunService.run(CASE_ID, buildParam()).getSuccess());
    }

    @Test
    void shouldFailOkResponseWhenAssertionFails() {
        mockGrpcCaseContext(buildCaseConfig());
        GrpcInvokeResponse response = successResponse("{}");
        response.setGrpcStatus("OK");
        response.setGrpcCode(0);
        when(grpcInvokeService.invoke(any())).thenReturn(response);
        when(grpcAssertionEvaluator.evaluate(any(), eq(response)))
                .thenReturn(List.of(assertion("GRPC_STATUS", false)));

        Assertions.assertFalse(grpcCaseRunService.run(CASE_ID, buildParam()).getSuccess());
    }

    private void mockGrpcCaseContext(ApiGrpcCaseConfigDTO caseConfig) {
        ApiTestCase testCase = new ApiTestCase();
        testCase.setId(CASE_ID);
        testCase.setApiDefinitionId(API_ID);
        when(apiTestCaseService.checkResourceExist(eq(CASE_ID))).thenReturn(testCase);

        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId(API_ID);
        apiDefinition.setProtocol(ApiConstants.GRPC_PROTOCOL);
        when(apiDefinitionMapper.selectByPrimaryKey(API_ID)).thenReturn(apiDefinition);

        when(grpcCasePersistenceService.loadDtoByCaseId(CASE_ID)).thenReturn(caseConfig);
    }

    private void mockGrpcCaseWithoutConfig() {
        ApiTestCase testCase = new ApiTestCase();
        testCase.setId(CASE_ID);
        testCase.setApiDefinitionId(API_ID);
        when(apiTestCaseService.checkResourceExist(eq(CASE_ID))).thenReturn(testCase);

        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId(API_ID);
        apiDefinition.setProtocol(ApiConstants.GRPC_PROTOCOL);
        when(apiDefinitionMapper.selectByPrimaryKey(API_ID)).thenReturn(apiDefinition);

        when(grpcCasePersistenceService.loadDtoByCaseId(CASE_ID)).thenReturn(null);
    }

    private ApiGrpcCaseConfigDTO buildCaseConfig() {
        ApiGrpcCaseConfigDTO config = new ApiGrpcCaseConfigDTO();
        config.setApiCaseId(CASE_ID);
        config.setApiDefinitionId(API_ID);
        config.setServiceName("helloworld.Greeter");
        config.setMethodName("SayHello");
        config.setDescriptorContent("descriptor-content");
        config.setRequestJson("{\"name\":\"MeterSphere\"}");
        config.setTimeout(5000);
        return config;
    }

    private GrpcCaseRunParamDTO buildParam() {
        GrpcCaseRunParamDTO param = new GrpcCaseRunParamDTO();
        param.setServerAddress(SERVER_ADDRESS);
        return param;
    }

    private GrpcInvokeResponse successResponse(String responseJson) {
        GrpcInvokeResponse response = new GrpcInvokeResponse();
        response.setSuccess(true);
        response.setResponseJson(responseJson);
        response.setDuration(10L);
        return response;
    }

    private GrpcInvokeResponse errorResponse(GrpcResultCode resultCode) {
        GrpcInvokeResponse response = new GrpcInvokeResponse();
        response.setSuccess(false);
        response.setErrorCode(resultCode.name());
        response.setErrorMessage(resultCode.name());
        response.setDuration(5L);
        return response;
    }

    private ResponseAssertionResult assertion(String type, boolean pass) {
        ResponseAssertionResult assertion = new ResponseAssertionResult();
        assertion.setAssertionType(type);
        assertion.setPass(pass);
        return assertion;
    }
}
