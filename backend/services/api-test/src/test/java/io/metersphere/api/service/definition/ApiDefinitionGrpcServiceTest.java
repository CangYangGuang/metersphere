package io.metersphere.api.service.definition;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.controller.MsHTTPElementTest;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiDefinitionModule;
import io.metersphere.api.dto.definition.ApiDefinitionAddRequest;
import io.metersphere.api.dto.definition.ApiDefinitionDTO;
import io.metersphere.api.dto.definition.ApiDefinitionUpdateRequest;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.grpc.util.GrpcProtocolUtils;
import io.metersphere.api.dto.request.http.MsHTTPElement;
import io.metersphere.api.mapper.ApiDefinitionFollowerMapper;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.mapper.ApiDefinitionModuleMapper;
import io.metersphere.api.mapper.ExtApiDefinitionCustomFieldMapper;
import io.metersphere.api.mapper.ExtApiTestCaseMapper;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.service.UserLoginService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiDefinitionGrpcServiceTest {

    @Spy
    @InjectMocks
    private ApiDefinitionService apiDefinitionService;

    @Mock
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;
    @Mock
    private ApiDefinitionMapper apiDefinitionMapper;
    @Mock
    private UserLoginService userLoginService;
    @Mock
    private ApiDefinitionFollowerMapper apiDefinitionFollowerMapper;
    @Mock
    private ApiDefinitionModuleMapper apiDefinitionModuleMapper;
    @Mock
    private ExtApiDefinitionCustomFieldMapper extApiDefinitionCustomFieldMapper;
    @Mock
    private ExtApiTestCaseMapper extApiTestCaseMapper;
    @Mock
    private ApiDefinitionMockService apiDefinitionMockService;

    @Test
    void shouldGetGrpcDefinitionDetailSuccessfully() {
        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId("grpc-api-id");
        apiDefinition.setProjectId("project-id");
        apiDefinition.setProtocol(ApiConstants.GRPC_PROTOCOL);
        apiDefinition.setCreateUser("admin");
        apiDefinition.setUpdateUser("admin");
        apiDefinition.setModuleId("module-id");

        ApiGrpcDefinitionDTO grpcDefinition = new ApiGrpcDefinitionDTO();
        grpcDefinition.setMethodName("SayHello");
        grpcDefinition.setInvokable(true);

        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId("grpc-api-id")).thenReturn(grpcDefinition);
        when(userLoginService.getUserNameMap(any())).thenReturn(Collections.emptyMap());
        when(apiDefinitionFollowerMapper.countByExample(any())).thenReturn(0L);
        when(extApiDefinitionCustomFieldMapper.getApiCustomFields(any(), any())).thenReturn(Collections.emptyList());
        ApiDefinitionModule module = new ApiDefinitionModule();
        module.setName("grpc-module");
        when(apiDefinitionModuleMapper.selectByPrimaryKey("module-id")).thenReturn(module);

        ApiDefinitionDTO detail = apiDefinitionService.getApiDefinitionInfo("grpc-api-id", "admin", apiDefinition);

        Assertions.assertEquals(ApiConstants.GRPC_PROTOCOL, detail.getProtocol());
        Assertions.assertNotNull(detail.getGrpcDefinition());
        Assertions.assertEquals("SayHello", detail.getGrpcDefinition().getMethodName());
        Assertions.assertNull(detail.getRequest());
        Assertions.assertNull(detail.getResponse());
        verify(grpcDefinitionPersistenceService).loadDtoByApiDefinitionId("grpc-api-id");
    }

    @Test
    void shouldKeepHttpDefinitionDetailWorking() {
        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setId("http-api-id");
        apiDefinition.setProjectId("project-id");
        apiDefinition.setProtocol(ApiConstants.HTTP_PROTOCOL);
        apiDefinition.setCreateUser("admin");
        apiDefinition.setUpdateUser("admin");
        apiDefinition.setModuleId("module-id");

        doAnswer(invocation -> {
            ApiDefinitionDTO dto = invocation.getArgument(1);
            dto.setRequest(new MsHTTPElement());
            dto.setResponse(MsHTTPElementTest.getMsHttpResponse());
            return null;
        }).when(apiDefinitionService).handleBlob(any(), any());

        when(userLoginService.getUserNameMap(any())).thenReturn(Collections.emptyMap());
        when(apiDefinitionFollowerMapper.countByExample(any())).thenReturn(0L);
        when(extApiDefinitionCustomFieldMapper.getApiCustomFields(any(), any())).thenReturn(Collections.emptyList());
        ApiDefinitionModule module = new ApiDefinitionModule();
        module.setName("http-module");
        when(apiDefinitionModuleMapper.selectByPrimaryKey("module-id")).thenReturn(module);

        ApiDefinitionDTO detail = apiDefinitionService.getApiDefinitionInfo("http-api-id", "admin", apiDefinition);

        Assertions.assertEquals(ApiConstants.HTTP_PROTOCOL, detail.getProtocol());
        Assertions.assertNull(detail.getGrpcDefinition());
        Assertions.assertNotNull(detail.getRequest());
        Assertions.assertNotNull(detail.getResponse());
        verify(grpcDefinitionPersistenceService, never()).loadDtoByApiDefinitionId(any());
    }

    @Test
    void shouldRequireRequestAndResponseForHttpCreate() {
        ApiDefinitionAddRequest request = new ApiDefinitionAddRequest();
        request.setProtocol(ApiConstants.HTTP_PROTOCOL);
        request.setProjectId("project-id");

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> ReflectionTestUtils.invokeMethod(apiDefinitionService, "validateHttpAddRequest", request));
        Assertions.assertTrue(exception.getMessage().contains("request and response are required"));
    }

    @Test
    void shouldFailWhenHttpRequestIsNull() {
        ApiDefinitionAddRequest request = new ApiDefinitionAddRequest();
        request.setProtocol(ApiConstants.HTTP_PROTOCOL);
        request.setResponse(MsHTTPElementTest.getMsHttpResponse());

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> ReflectionTestUtils.invokeMethod(apiDefinitionService, "validateHttpAddRequest", request));
        Assertions.assertTrue(exception.getMessage().contains("request and response are required"));
    }

    @Test
    void shouldFailWhenHttpResponseIsNull() {
        ApiDefinitionAddRequest request = new ApiDefinitionAddRequest();
        request.setProtocol(ApiConstants.HTTP_PROTOCOL);
        request.setRequest(MsHTTPElementTest.getMsHttpElement());

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> ReflectionTestUtils.invokeMethod(apiDefinitionService, "validateHttpAddRequest", request));
        Assertions.assertTrue(exception.getMessage().contains("request and response are required"));
    }

    @Test
    void shouldFailWhenBlankProtocolAndRequestMissing() {
        ApiDefinitionAddRequest request = new ApiDefinitionAddRequest();
        request.setProtocol(null);

        MSException exception = Assertions.assertThrows(MSException.class,
                () -> ReflectionTestUtils.invokeMethod(apiDefinitionService, "validateHttpAddRequest", request));
        Assertions.assertTrue(exception.getMessage().contains("request and response are required"));
    }

    @Test
    void shouldAllowReflectionDescriptorOnlyCreate() {
        ApiDefinitionAddRequest request = new ApiDefinitionAddRequest();
        request.setProtocol(ApiConstants.GRPC_PROTOCOL);
        ApiGrpcDefinitionAddDTO grpcDefinition = new ApiGrpcDefinitionAddDTO();
        grpcDefinition.setDescriptorContent("dGVzdA==");
        grpcDefinition.setReflection(true);
        grpcDefinition.setReflectionServerAddress("public.example.com:50051");
        grpcDefinition.setServiceName("demo.Service");
        grpcDefinition.setMethodName("Call");
        request.setGrpcDefinition(grpcDefinition);

        Assertions.assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(apiDefinitionService, "validateGrpcAddRequest", request));
    }

    @Test
    void shouldNotRequireHttpPayloadForGrpcCreate() {
        ApiDefinitionAddRequest request = new ApiDefinitionAddRequest();
        request.setProtocol(ApiConstants.GRPC_PROTOCOL);
        ApiGrpcDefinitionAddDTO grpcDefinition = new ApiGrpcDefinitionAddDTO();
        grpcDefinition.setProtoContent("syntax = \"proto3\";\npackage demo;\n");
        grpcDefinition.setServiceName("demo.Service");
        grpcDefinition.setMethodName("Call");
        request.setGrpcDefinition(grpcDefinition);

        Assertions.assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(apiDefinitionService, "validateGrpcAddRequest", request));
    }

    @Test
    void shouldRouteGrpcUpdateToPersistenceService() {
        ApiDefinition origin = new ApiDefinition();
        origin.setId("grpc-api-id");
        origin.setProjectId("project-id");
        origin.setProtocol(ApiConstants.GRPC_PROTOCOL);
        origin.setModuleId("module-id");
        origin.setRefId("ref-id");

        ApiDefinitionUpdateRequest request = new ApiDefinitionUpdateRequest();
        request.setId("grpc-api-id");
        request.setName("grpc-updated");
        request.setModuleId("module-id");
        ApiGrpcDefinitionAddDTO grpcDefinition = new ApiGrpcDefinitionAddDTO();
        grpcDefinition.setProtoContent("syntax = \"proto3\";\npackage demo;\n");
        grpcDefinition.setServiceName("demo.Service");
        grpcDefinition.setMethodName("Call");
        request.setGrpcDefinition(grpcDefinition);

        doAnswer(invocation -> origin).when(apiDefinitionService).checkApiDefinition("grpc-api-id");
        when(apiDefinitionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        when(apiDefinitionMapper.countByExample(any())).thenReturn(0L);

        apiDefinitionService.update(request, "admin");

        verify(grpcDefinitionPersistenceService).updateGrpcDefinition(
                eq("grpc-api-id"), eq("project-id"), eq(grpcDefinition), eq("admin"));
    }

    @Test
    void shouldRecoverGrpcDefinitionWhenApiDefinitionRecovered() {
        when(extApiTestCaseMapper.getCaseInfoByApiIds(any(), eq(true))).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(apiDefinitionService, "recoverApiRelatedData",
                List.of("grpc-api-id"), "admin", "project-id");
        verify(grpcDefinitionPersistenceService).recoverByApiDefinitionIds(List.of("grpc-api-id"), "admin");
    }

    @Test
    void shouldEnsureGrpcInactiveWhenPermanentDelete() {
        when(extApiTestCaseMapper.getCaseInfoByApiIds(any(), eq(true))).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(apiDefinitionService, "trashDelApiRelatedData",
                List.of("grpc-api-id"), "admin", "project-id");
        verify(grpcDefinitionPersistenceService).ensureInactiveByApiDefinitionIds(List.of("grpc-api-id"), "admin");
    }

    @Test
    void shouldDeleteGrpcDefinitionWhenApiDefinitionDeleted() {
        when(extApiTestCaseMapper.getCaseInfoByApiIds(any(), eq(false))).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(apiDefinitionService, "deleteApiRelatedData",
                List.of("grpc-api-id"), "admin", "project-id");
        verify(grpcDefinitionPersistenceService).deleteByApiDefinitionIds(List.of("grpc-api-id"), "admin");
    }

    @Test
    void shouldTreatBlankProtocolAsHttp() {
        Assertions.assertTrue(GrpcProtocolUtils.isHttpProtocol(null));
        Assertions.assertTrue(GrpcProtocolUtils.isHttpProtocol(""));
        Assertions.assertFalse(GrpcProtocolUtils.isGrpcProtocol(null));
        Assertions.assertFalse(GrpcProtocolUtils.isGrpcProtocol(ApiConstants.HTTP_PROTOCOL));
    }

    @Test
    void shouldUseNameBasedExistCheckForGrpcDefinition() {
        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setProtocol(ApiConstants.GRPC_PROTOCOL);
        apiDefinition.setName("grpc-def");
        apiDefinition.setModuleId("module-id");
        apiDefinition.setProjectId("project-id");

        when(apiDefinitionMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(apiDefinitionService, "checkAddExist", apiDefinition);

        verify(apiDefinitionMapper).selectByExample(any());
    }

    @Test
    void shouldUsePathMethodExistCheckForHttpDefinition() {
        ApiDefinition apiDefinition = new ApiDefinition();
        apiDefinition.setProtocol(ApiConstants.HTTP_PROTOCOL);
        apiDefinition.setPath("/api/test");
        apiDefinition.setMethod("GET");
        apiDefinition.setProjectId("project-id");

        when(apiDefinitionMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        ReflectionTestUtils.invokeMethod(apiDefinitionService, "checkAddExist", apiDefinition);

        verify(apiDefinitionMapper).selectByExample(any());
    }

    @Test
    void shouldAcceptHttpCreateRequestPayload() {
        ApiDefinitionAddRequest request = new ApiDefinitionAddRequest();
        request.setProtocol(ApiConstants.HTTP_PROTOCOL);
        request.setRequest(MsHTTPElementTest.getMsHttpElement());
        request.setResponse(MsHTTPElementTest.getMsHttpResponse());

        Assertions.assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(apiDefinitionService, "validateHttpAddRequest", request));
    }
}
