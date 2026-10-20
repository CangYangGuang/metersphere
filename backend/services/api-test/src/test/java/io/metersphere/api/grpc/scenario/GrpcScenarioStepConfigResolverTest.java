package io.metersphere.api.grpc.scenario;

import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunContext;
import io.metersphere.api.grpc.service.GrpcCasePersistenceService;
import io.metersphere.api.grpc.service.GrpcDefinitionPersistenceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcScenarioStepConfigResolverTest {

    @InjectMocks
    private GrpcScenarioStepConfigResolver resolver;

    @Mock
    private GrpcCasePersistenceService grpcCasePersistenceService;
    @Mock
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;

    @Test
    void shouldRestoreRequestTemplateFromReferencedDefinition() {
        ApiGrpcDefinitionDTO definition = new ApiGrpcDefinitionDTO();
        definition.setServiceName("Polar.v1.DeviceService");
        definition.setMethodName("ActivateDevice");
        definition.setDescriptorContent("descriptor");
        definition.setServerAddress("127.0.0.1:9099");

        ApiGrpcDefinitionDTO.GrpcMethodInfoDTO method = new ApiGrpcDefinitionDTO.GrpcMethodInfoDTO();
        method.setName("ActivateDevice");
        method.setFullName("Polar.v1.DeviceService/ActivateDevice");
        method.setRequestJsonTemplate("{\"deviceId\":\"string\"}");
        ApiGrpcDefinitionDTO.GrpcServiceInfoDTO service = new ApiGrpcDefinitionDTO.GrpcServiceInfoDTO();
        service.setName("DeviceService");
        service.setFullName("Polar.v1.DeviceService");
        service.setMethods(List.of(method));
        definition.setServices(List.of(service));

        when(grpcDefinitionPersistenceService.loadDtoByApiDefinitionId("definition-1")).thenReturn(definition);
        ApiScenarioStepCommonDTO step = new ApiScenarioStepCommonDTO();
        step.setStepType(ApiScenarioStepType.API.name());
        step.setResourceId("definition-1");
        step.setConfig(Map.of("protocol", "GRPC"));

        ApiGrpcCaseConfigDTO resolved = resolver.resolve(step, new GrpcScenarioRunContext());

        Assertions.assertNotNull(resolved);
        Assertions.assertEquals("{\"deviceId\":\"string\"}", resolved.getRequestJson());
        Assertions.assertEquals("127.0.0.1:9099", resolved.getServerAddress());
    }

    @Test
    void shouldResolvePersistedCustomGrpcDetailString() {
        ApiScenarioStepCommonDTO step = new ApiScenarioStepCommonDTO();
        step.setId("step-1");
        step.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
        step.setConfig(Map.of("protocol", "GRPC"));

        GrpcScenarioRunContext context = new GrpcScenarioRunContext();
        context.setStepDetails(Map.of("step-1", """
                {
                  "grpcCaseConfig": {
                    "serviceName": "msdemo.scenario.ProductService",
                    "methodName": "ListProducts",
                    "serverAddress": "127.0.0.1:50051",
                    "descriptorContent": "descriptor",
                    "requestJson": "{}"
                  }
                }
                """));

        ApiGrpcCaseConfigDTO resolved = resolver.resolve(step, context);

        Assertions.assertNotNull(resolved);
        Assertions.assertEquals("msdemo.scenario.ProductService", resolved.getServiceName());
        Assertions.assertEquals("ListProducts", resolved.getMethodName());
        Assertions.assertEquals("127.0.0.1:50051", resolved.getServerAddress());
        Assertions.assertEquals("{}", resolved.getRequestJson());
    }
}
