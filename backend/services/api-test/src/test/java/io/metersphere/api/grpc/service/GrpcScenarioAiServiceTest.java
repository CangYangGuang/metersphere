package io.metersphere.api.grpc.service;

import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiScenario;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.dto.definition.ApiTestCaseAddRequest;
import io.metersphere.api.dto.scenario.ApiScenarioAddRequest;
import io.metersphere.api.grpc.dto.GrpcScenarioAiCreateRequest;
import io.metersphere.api.grpc.dto.GrpcScenarioAiCreateResultDTO;
import io.metersphere.api.grpc.dto.GrpcScenarioAiDraftDTO;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.mapper.ApiScenarioMapper;
import io.metersphere.api.mapper.ApiScenarioModuleMapper;
import io.metersphere.api.mapper.ApiTestCaseMapper;
import io.metersphere.api.service.definition.ApiTestCaseService;
import io.metersphere.api.service.scenario.ApiScenarioService;
import io.metersphere.system.service.AiChatBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcScenarioAiServiceTest {

    @Mock
    private ApiDefinitionMapper apiDefinitionMapper;
    @Mock
    private ApiTestCaseMapper apiTestCaseMapper;
    @Mock
    private ApiScenarioMapper apiScenarioMapper;
    @Mock
    private ApiScenarioModuleMapper apiScenarioModuleMapper;
    @Mock
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;
    @Mock
    private GrpcScenarioSourceFileExtractor sourceFileExtractor;
    @Mock
    private AiChatBaseService aiChatBaseService;
    @Mock
    private ApiTestCaseService apiTestCaseService;
    @Mock
    private ApiScenarioService apiScenarioService;

    @Spy
    @InjectMocks
    private GrpcScenarioAiService service;

    @BeforeEach
    void setUpDefinition() {
        ApiDefinition definition = new ApiDefinition();
        definition.setId("definition-1");
        definition.setName("demo.ProductService / GetProduct");
        when(apiDefinitionMapper.selectByExample(any())).thenReturn(List.of(definition));

        ApiGrpcDefinitionDTO grpc = new ApiGrpcDefinitionDTO();
        grpc.setInvokable(true);
        grpc.setMethodType("UNARY");
        grpc.setServiceName("demo.ProductService");
        grpc.setMethodName("GetProduct");
        grpc.setServerAddress("localhost:50051");
        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId("definition-1")).thenReturn(grpc);
    }

    @Test
    void createsAiCaseAndEnablesStepRegardlessOfRiskLabel() {
        doReturn("step-1").when(service).nextId();
        when(apiTestCaseMapper.selectByExample(any())).thenReturn(List.of());
        when(apiScenarioMapper.selectByExample(any())).thenReturn(List.of());
        ApiTestCase createdCase = new ApiTestCase();
        createdCase.setId("case-1");
        createdCase.setNum(100001L);
        createdCase.setName("查询产品");
        when(apiTestCaseService.addCase(any(), anyString())).thenReturn(createdCase);
        ApiScenario createdScenario = new ApiScenario();
        createdScenario.setId("scenario-1");
        when(apiScenarioService.add(any(), anyString())).thenReturn(createdScenario);

        GrpcScenarioAiCreateResultDTO result = service.create(createRequest(), "user-1");

        assertEquals(1, result.getCreatedCaseCount());
        assertEquals(1, result.getCreatedScenarioCount());
        assertEquals(List.of("scenario-1"), result.getScenarioIds());
        ArgumentCaptor<ApiTestCaseAddRequest> caseCaptor = ArgumentCaptor.forClass(ApiTestCaseAddRequest.class);
        verify(apiTestCaseService).addCase(caseCaptor.capture(), anyString());
        assertTrue(caseCaptor.getValue().getAiCreate());
        assertEquals("{\"id\":1}", caseCaptor.getValue().getGrpcCaseConfig().getRequestJson());
        ArgumentCaptor<ApiScenarioAddRequest> scenarioCaptor = ArgumentCaptor.forClass(ApiScenarioAddRequest.class);
        verify(apiScenarioService).add(scenarioCaptor.capture(), anyString());
        assertTrue(scenarioCaptor.getValue().getTags().contains("AI_GENERATED"));
        assertTrue(scenarioCaptor.getValue().getSteps().getFirst().getEnable());
    }

    @Test
    void neverOverwritesManualCaseWithSameName() {
        ApiTestCase manualCase = new ApiTestCase();
        manualCase.setId("manual-case");
        manualCase.setName("查询产品");
        manualCase.setAiCreate(false);
        when(apiTestCaseMapper.selectByExample(any())).thenReturn(List.of(manualCase));

        GrpcScenarioAiCreateResultDTO result = service.create(createRequest(), "user-1");

        assertEquals(2, result.getSkippedCount());
        assertTrue(result.getItems().stream().anyMatch(item -> item.getMessage().contains("人工用例")));
        verify(apiTestCaseService, never()).update(any(), anyString());
        verify(apiScenarioService, never()).add(any(), anyString());
    }

    private GrpcScenarioAiCreateRequest createRequest() {
        GrpcScenarioAiDraftDTO.CaseDraft caseDraft = new GrpcScenarioAiDraftDTO.CaseDraft();
        caseDraft.setApiDefinitionId("definition-1");
        caseDraft.setName("查询产品");
        caseDraft.setRequestJson("{\"id\":1}");
        caseDraft.setServerAddress("localhost:50051");
        caseDraft.setPriority("P0");
        caseDraft.setSelected(true);

        GrpcScenarioAiDraftDTO.ScenarioDraft scenarioDraft = new GrpcScenarioAiDraftDTO.ScenarioDraft();
        scenarioDraft.setName("产品查询链路");
        scenarioDraft.setPriority("P0");
        scenarioDraft.setSafety("RISK");
        scenarioDraft.setSelected(true);
        scenarioDraft.setCases(List.of(caseDraft));

        GrpcScenarioAiDraftDTO draft = new GrpcScenarioAiDraftDTO();
        draft.setScenarios(List.of(scenarioDraft));
        GrpcScenarioAiCreateRequest request = new GrpcScenarioAiCreateRequest();
        request.setProjectId("project-1");
        request.setModuleId("root");
        request.setDraft(draft);
        return request;
    }
}
