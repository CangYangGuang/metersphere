package io.metersphere.api.grpc;

import io.metersphere.api.domain.ApiDebug;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiDebugModule;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.dto.debug.ApiDebugAddRequest;
import io.metersphere.api.dto.debug.ApiDebugUpdateRequest;
import io.metersphere.api.dto.definition.ApiDefinitionAddRequest;
import io.metersphere.api.grpc.dto.GrpcBatchGenerateRequest;
import io.metersphere.api.grpc.dto.GrpcBatchGenerateResultDTO;
import io.metersphere.api.grpc.dto.GrpcCaseDebugRequestDTO;
import io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.dto.GrpcServiceInfoDTO;
import io.metersphere.api.grpc.service.GrpcBatchGenerateService;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import io.metersphere.api.mapper.ApiDebugMapper;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.mapper.ApiDefinitionModuleMapper;
import io.metersphere.api.service.debug.ApiDebugModuleService;
import io.metersphere.api.service.debug.ApiDebugService;
import io.metersphere.api.service.definition.ApiDefinitionService;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcBatchGenerateServiceTest {
    @Mock
    GrpcDefinitionPersistenceService persistenceService;
    @Mock
    ApiDebugService debugService;
    @Mock
    ApiDebugModuleService debugModuleService;
    @Mock
    ApiDebugMapper debugMapper;
    @Mock
    ApiDefinitionMapper definitionMapper;
    @Mock
    ApiDefinitionModuleMapper definitionModuleMapper;
    @Mock
    ApiDefinitionService definitionService;
    @Mock
    PlatformTransactionManager transactionManager;
    @Mock
    TransactionStatus transactionStatus;
    private GrpcBatchGenerateService service;

    @BeforeEach
    void setUp() {
        service = new GrpcBatchGenerateService();
        ReflectionTestUtils.setField(service, "grpcDefinitionPersistenceService", persistenceService);
        ReflectionTestUtils.setField(service, "apiDebugService", debugService);
        ReflectionTestUtils.setField(service, "apiDebugModuleService", debugModuleService);
        ReflectionTestUtils.setField(service, "apiDebugMapper", debugMapper);
        ReflectionTestUtils.setField(service, "apiDefinitionMapper", definitionMapper);
        ReflectionTestUtils.setField(service, "apiDefinitionModuleMapper", definitionModuleMapper);
        ReflectionTestUtils.setField(service, "apiDefinitionService", definitionService);
        ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void savesUnaryMethodsAsDebugRequestsAndContinuesWhenStreamingMethodFails() {
        GrpcParseProtoResponse parsed = parsed(
                method("ListDevices", "Polar.v1.DeviceService/ListDevices", "UNARY", true),
                method("WatchDevices", "Polar.v1.DeviceService/WatchDevices", "SERVER_STREAMING", false));
        when(persistenceService.parseProto(any())).thenReturn(parsed);
        when(debugMapper.selectByExample(any())).thenReturn(List.of());
        ApiDebug created = new ApiDebug();
        created.setId("debug-1");
        when(debugService.add(any(), anyString())).thenReturn(created);

        GrpcBatchGenerateResultDTO result = service.generate(request(List.of(
                "Polar.v1.DeviceService/ListDevices", "Polar.v1.DeviceService/WatchDevices")), "user-1");

        assertEquals(1, result.getCreatedCount());
        assertEquals(0, result.getUpdatedCount());
        assertEquals(1, result.getFailedCount());
        assertEquals("debug-1", result.getItems().getFirst().getApiDebugId());
        assertEquals("FAILED", result.getItems().get(1).getAction());
        verify(persistenceService, times(1)).parseProto(any());
        ArgumentCaptor<ApiDebugAddRequest> addRequestCaptor = ArgumentCaptor.forClass(ApiDebugAddRequest.class);
        verify(debugService).add(addRequestCaptor.capture(), anyString());
        ApiDebugAddRequest addRequest = addRequestCaptor.getValue();
        assertEquals("Polar.v1.DeviceService / ListDevices", addRequest.getName());
        assertEquals("root", addRequest.getModuleId());
        GrpcCaseDebugRequestDTO savedRequest = (GrpcCaseDebugRequestDTO) addRequest.getRequest();
        assertEquals("DeviceService", savedRequest.getGrpcCaseConfig().getServiceName());
        assertEquals("ListDevices", savedRequest.getGrpcCaseConfig().getMethodName());
        assertEquals("{\"page\":1}", savedRequest.getGrpcCaseConfig().getRequestJson());
        assertEquals(parsed.getDescriptorContent(), savedRequest.getGrpcCaseConfig().getDescriptorContent());
    }

    @Test
    void returnsOneSuccessfulDebugRequestForEachOfFiftyNineUnaryMethods() {
        List<GrpcMethodInfoDTO> methods = IntStream.rangeClosed(1, 59)
                .mapToObj(index -> method("Method" + index,
                        "Polar.v1.DeviceService/Method" + index, "UNARY", true))
                .toList();
        GrpcParseProtoResponse parsed = parsed(methods.toArray(GrpcMethodInfoDTO[]::new));
        when(persistenceService.parseProto(any())).thenReturn(parsed);
        when(debugMapper.selectByExample(any())).thenReturn(List.of());
        ApiDebug created = new ApiDebug();
        created.setId("debug-created");
        when(debugService.add(any(), anyString())).thenReturn(created);

        List<String> requestedMethods = methods.stream().map(GrpcMethodInfoDTO::getFullName).toList();
        GrpcBatchGenerateResultDTO result = service.generate(request(requestedMethods), "user-1");

        assertEquals(59, result.getItems().size());
        assertEquals(59, result.getCreatedCount());
        assertEquals(0, result.getUpdatedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(result.getItems().size(),
                result.getCreatedCount() + result.getUpdatedCount() + result.getFailedCount());
        assertTrue(result.getItems().stream().allMatch(item -> "CREATED".equals(item.getAction())));
        verify(persistenceService, times(1)).parseProto(any());
        verify(debugService, times(59)).add(any(), anyString());
    }

    @Test
    void updatesExistingDebugRequestInTheSelectedModule() {
        GrpcParseProtoResponse parsed = parsed(
                method("ListDevices", "Polar.v1.DeviceService/ListDevices", "UNARY", true));
        when(persistenceService.parseProto(any())).thenReturn(parsed);
        ApiDebug existing = new ApiDebug();
        existing.setId("debug-existing");
        when(debugMapper.selectByExample(any())).thenReturn(List.of(existing));

        GrpcBatchGenerateResultDTO result = service.generate(
                request(List.of("Polar.v1.DeviceService/ListDevices")), "user-1");

        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals("debug-existing", result.getItems().getFirst().getApiDebugId());
        ArgumentCaptor<ApiDebugUpdateRequest> updateCaptor = ArgumentCaptor.forClass(ApiDebugUpdateRequest.class);
        verify(debugService).update(updateCaptor.capture(), anyString());
        assertEquals("debug-existing", updateCaptor.getValue().getId());
        assertEquals("root", updateCaptor.getValue().getModuleId());
        verify(debugService, never()).add(any(), anyString());
    }

    @Test
    void acceptsUniqueShortMethodNameRestoredFromSavedDebugRequest() {
        GrpcParseProtoResponse parsed = parsed(
                method("ListDevices", "Polar.v1.DeviceService/ListDevices", "UNARY", true));
        when(persistenceService.parseProto(any())).thenReturn(parsed);
        when(debugMapper.selectByExample(any())).thenReturn(List.of());
        ApiDebug created = new ApiDebug();
        created.setId("debug-short-name");
        when(debugService.add(any(), anyString())).thenReturn(created);

        GrpcBatchGenerateResultDTO result = service.generate(
                request(List.of("DeviceService/ListDevices")), "user-1");

        assertEquals(1, result.getCreatedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals("debug-short-name", result.getItems().getFirst().getApiDebugId());
    }

    @Test
    void rejectsDebugModuleFromAnotherProjectBeforeParsingProto() {
        GrpcBatchGenerateRequest request = request(List.of("Polar.v1.DeviceService/ListDevices"));
        request.setParentModuleId("foreign-module");
        ApiDebugModule module = new ApiDebugModule();
        module.setId("foreign-module");
        module.setProjectId("project-2");
        module.setCreateUser("user-1");
        when(debugModuleService.checkModuleExist("foreign-module")).thenReturn(module);

        assertThrows(MSException.class, () -> service.generate(request, "user-1"));

        verify(persistenceService, never()).parseProto(any());
        verify(debugService, never()).add(any(), anyString());
    }

    @Test
    void savesSelectedUnaryMethodsAsApiDefinitions() {
        GrpcParseProtoResponse parsed = parsed(
                method("ListDevices", "Polar.v1.DeviceService/ListDevices", "UNARY", true));
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("proto-1");
        when(persistenceService.parseProto(any())).thenReturn(parsed);
        when(persistenceService.saveOrGetProto(anyString(), any(), any(), anyString())).thenReturn(proto);
        when(definitionMapper.selectByExample(any())).thenReturn(List.of());
        ApiDefinition created = new ApiDefinition();
        created.setId("definition-1");
        when(definitionService.createGrpcDefinitionFromParsed(any(), anyString(), eq(parsed), eq(proto)))
                .thenReturn(created);

        GrpcBatchGenerateResultDTO result = service.saveDefinitions(
                request(List.of("Polar.v1.DeviceService/ListDevices")), "user-1");

        assertEquals(1, result.getCreatedCount());
        assertEquals(0, result.getUpdatedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals("definition-1", result.getItems().getFirst().getApiDefinitionId());
        ArgumentCaptor<ApiDefinitionAddRequest> addCaptor = ArgumentCaptor.forClass(ApiDefinitionAddRequest.class);
        verify(definitionService).createGrpcDefinitionFromParsed(addCaptor.capture(), eq("user-1"), eq(parsed), eq(proto));
        assertEquals("Polar.v1.DeviceService / ListDevices", addCaptor.getValue().getName());
        assertEquals("GRPC", addCaptor.getValue().getProtocol());
        assertEquals("root", addCaptor.getValue().getModuleId());
        assertEquals("Polar.v1.DeviceService", addCaptor.getValue().getGrpcDefinition().getServiceName());
        assertEquals("ListDevices", addCaptor.getValue().getGrpcDefinition().getMethodName());
    }

    @Test
    void updatesExistingApiDefinitionOnRepeatedBatchSave() {
        GrpcParseProtoResponse parsed = parsed(
                method("ListDevices", "Polar.v1.DeviceService/ListDevices", "UNARY", true));
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId("proto-1");
        ApiDefinition existing = new ApiDefinition();
        existing.setId("definition-existing");
        when(persistenceService.parseProto(any())).thenReturn(parsed);
        when(persistenceService.saveOrGetProto(anyString(), any(), any(), anyString())).thenReturn(proto);
        when(definitionMapper.selectByExample(any())).thenReturn(List.of(existing));

        GrpcBatchGenerateResultDTO result = service.saveDefinitions(
                request(List.of("Polar.v1.DeviceService/ListDevices")), "user-1");

        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals("definition-existing", result.getItems().getFirst().getApiDefinitionId());
        verify(persistenceService).updateGrpcDefinition(
                eq("definition-existing"), any(), eq(proto), eq(parsed), eq("user-1"));
        verify(definitionService, never()).createGrpcDefinitionFromParsed(any(), anyString(), any(), any());
    }

    private GrpcBatchGenerateRequest request(List<String> methods) {
        GrpcBatchGenerateRequest request = new GrpcBatchGenerateRequest();
        request.setProjectId("project-1");
        request.setParentModuleId("root");
        request.setServerAddress("localhost:50051");
        request.setMainFileName("polar.proto");
        request.setProtoFiles(List.of(
                protoFile("polar.proto", "syntax = \"proto3\";", true),
                protoFile("common.proto", "syntax = \"proto3\";", false)));
        request.setMethodFullNames(methods);
        return request;
    }

    private GrpcProtoFileDTO protoFile(String fileName, String content, boolean main) {
        GrpcProtoFileDTO file = new GrpcProtoFileDTO();
        file.setFileName(fileName);
        file.setContent(content);
        file.setMain(main);
        return file;
    }

    private GrpcParseProtoResponse parsed(GrpcMethodInfoDTO... methods) {
        GrpcServiceInfoDTO grpcService = new GrpcServiceInfoDTO();
        grpcService.setName("DeviceService");
        grpcService.setFullName("Polar.v1.DeviceService");
        grpcService.setMethods(List.of(methods));
        GrpcParseProtoResponse response = new GrpcParseProtoResponse();
        response.setDescriptorContent("descriptor-base64");
        response.setServices(List.of(grpcService));
        return response;
    }

    private GrpcMethodInfoDTO method(String name, String fullName, String type, boolean invokable) {
        GrpcMethodInfoDTO method = new GrpcMethodInfoDTO();
        method.setName(name);
        method.setFullName(fullName);
        method.setMethodType(type);
        method.setInvokable(invokable);
        method.setRequestJsonTemplate("{\"page\":1}");
        return method;
    }
}
