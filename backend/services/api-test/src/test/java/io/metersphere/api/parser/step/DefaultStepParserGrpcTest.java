package io.metersphere.api.parser.step;

import io.metersphere.api.constants.ApiScenarioStepRefType;
import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.domain.ApiScenarioStepBlob;
import io.metersphere.api.dto.scenario.ApiScenarioStepDetailRequest;
import io.metersphere.api.mapper.ApiScenarioStepBlobMapper;
import io.metersphere.sdk.util.CommonBeanFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultStepParserGrpcTest {

    private static final String STEP_ID = "178349961167700000";

    private static final String GRPC_STEP_BLOB = """
            {
              "protocol": "GRPC",
              "method": "GRPC",
              "grpcCaseConfig": {
                "serviceName": "HelloService",
                "methodName": "SayHello",
                "serverAddress": "host.docker.internal:50051",
                "requestJson": "{\\"greeting\\":\\"scene-ui-click-flow\\"}"
              },
              "grpc": {
                "serverAddress": "host.docker.internal:50051",
                "serviceName": "HelloService",
                "methodName": "SayHello",
                "requestJson": "{\\"greeting\\":\\"scene-ui-click-flow\\"}"
              }
            }
            """;

    @Mock
    private ApiScenarioStepBlobMapper apiScenarioStepBlobMapper;

    private AnnotationConfigApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("apiScenarioStepBlobMapper", apiScenarioStepBlobMapper);
        applicationContext.refresh();
        new CommonBeanFactory().setApplicationContext(applicationContext);
    }

    @AfterEach
    void tearDown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    void shouldParseGrpcCustomStepDetailWithoutPolymorphicName() {
        ApiScenarioStepBlob blob = new ApiScenarioStepBlob();
        blob.setId(STEP_ID);
        blob.setContent(GRPC_STEP_BLOB.getBytes(StandardCharsets.UTF_8));
        when(apiScenarioStepBlobMapper.selectByPrimaryKey(STEP_ID)).thenReturn(blob);

        ApiScenarioStepDetailRequest request = new ApiScenarioStepDetailRequest();
        request.setId(STEP_ID);
        request.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
        request.setRefType(ApiScenarioStepRefType.DIRECT.name());

        Object detail = StepParser.parseStoredStepDetail(STEP_ID);

        Assertions.assertNotNull(detail);
        Assertions.assertInstanceOf(Map.class, detail);
        @SuppressWarnings("unchecked")
        Map<String, Object> detailMap = (Map<String, Object>) detail;
        Assertions.assertEquals("GRPC", detailMap.get("protocol"));
        Assertions.assertEquals("GRPC", detailMap.get("method"));

        @SuppressWarnings("unchecked")
        Map<String, Object> grpcCaseConfig = (Map<String, Object>) detailMap.get("grpcCaseConfig");
        Assertions.assertNotNull(grpcCaseConfig);
        Assertions.assertEquals("HelloService", grpcCaseConfig.get("serviceName"));
        Assertions.assertEquals("SayHello", grpcCaseConfig.get("methodName"));
        Assertions.assertEquals("host.docker.internal:50051", grpcCaseConfig.get("serverAddress"));
        Assertions.assertEquals("{\"greeting\":\"scene-ui-click-flow\"}", grpcCaseConfig.get("requestJson"));
    }

    @Test
    void shouldDetectGrpcScenarioStepBlob() {
        Assertions.assertTrue(StepParser.isGrpcScenarioStepBlob(GRPC_STEP_BLOB));
    }
}
