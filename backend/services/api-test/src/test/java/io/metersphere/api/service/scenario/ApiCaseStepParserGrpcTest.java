package io.metersphere.api.service.scenario;

import io.metersphere.api.constants.ApiScenarioStepRefType;
import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.grpc.service.ApiTestCaseGrpcRunService;
import io.metersphere.api.parser.step.ApiCaseStepParser;
import io.metersphere.sdk.util.CommonBeanFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiCaseStepParserGrpcTest {

    @Mock
    private ApiTestCaseGrpcRunService apiTestCaseGrpcRunService;

    private AnnotationConfigApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("apiTestCaseGrpcRunService", apiTestCaseGrpcRunService);
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
    void shouldReturnNullForGrpcApiCaseStep() {
        ApiScenarioStepCommonDTO step = buildGrpcStep("grpc-case-1");

        ApiCaseStepParser parser = new ApiCaseStepParser();
        Assertions.assertNull(parser.parseTestElement(step, "{}", null));
    }

    @Test
    void shouldParseHttpApiCaseStepAsBefore() {
        ApiScenarioStepCommonDTO step = buildHttpStep("http-case-1");
        when(apiTestCaseGrpcRunService.isGrpcCase("http-case-1")).thenReturn(false);

        ApiCaseStepParser parser = new ApiCaseStepParser();
        Assertions.assertNull(parser.parseTestElement(step, "", null));
    }

    private ApiScenarioStepCommonDTO buildGrpcStep(String caseId) {
        ApiScenarioStepCommonDTO step = new ApiScenarioStepCommonDTO();
        step.setId("step-1");
        step.setEnable(true);
        step.setStepType(ApiScenarioStepType.API_CASE.name());
        step.setRefType(ApiScenarioStepRefType.REF.name());
        step.setResourceId(caseId);
        step.setProjectId("project-1");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("protocol", "GRPC");
        step.setConfig(config);
        return step;
    }

    private ApiScenarioStepCommonDTO buildHttpStep(String caseId) {
        ApiScenarioStepCommonDTO step = new ApiScenarioStepCommonDTO();
        step.setId("step-2");
        step.setEnable(true);
        step.setStepType(ApiScenarioStepType.API_CASE.name());
        step.setRefType(ApiScenarioStepRefType.REF.name());
        step.setResourceId(caseId);
        step.setProjectId("project-1");
        return step;
    }
}
