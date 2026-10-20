package io.metersphere.api.grpc.processor;

import io.metersphere.api.dto.request.processors.MsProcessorConfig;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.api.grpc.dto.GrpcProcessorResultDTO;
import io.metersphere.api.grpc.scenario.GrpcScenarioVariableContext;
import io.metersphere.project.api.processor.ExtractPostProcessor;
import io.metersphere.project.api.processor.ScriptProcessor;
import io.metersphere.project.api.processor.extract.JSONPathExtract;
import io.metersphere.project.api.processor.extract.MsExtract;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * gRPC 前置 / 后置执行器单测：验证前置脚本写变量、后置 JSONPath 提取，
 * 并确认运行环境具备 Groovy 脚本引擎。
 */
class GrpcProcessorRunnerTest {

    private final GrpcProcessorRunner runner = new GrpcProcessorRunner();

    @Test
    void preScriptShouldSetVariable() {
        ScriptProcessor script = new ScriptProcessor();
        script.setName("pre-set-greeting");
        script.setEnable(true);
        script.setScriptLanguage("groovy");
        script.setScript("vars.put(\"greeting\", \"pre-value\")");

        MsProcessorConfig config = new MsProcessorConfig();
        List<io.metersphere.project.api.processor.MsProcessor> processors = new ArrayList<>();
        processors.add(script);
        config.setProcessors(processors);

        GrpcScenarioVariableContext context = new GrpcScenarioVariableContext();
        List<GrpcProcessorResultDTO> results = runner.runPre(config, context);

        Assertions.assertEquals(1, results.size());
        Assertions.assertTrue(results.get(0).getSuccess(),
                "pre script should succeed, message=" + results.get(0).getMessage());
        Assertions.assertEquals("pre-value", context.get("greeting"));
    }

    @Test
    void postExtractShouldReadResponseJson() {
        JSONPathExtract extract = new JSONPathExtract();
        extract.setVariableName("reply");
        extract.setExpression("$.reply");
        extract.setEnable(true);

        ExtractPostProcessor extractProcessor = new ExtractPostProcessor();
        extractProcessor.setName("post-extract-reply");
        extractProcessor.setEnable(true);
        List<MsExtract> extractors = new ArrayList<>();
        extractors.add(extract);
        extractProcessor.setExtractors(extractors);

        MsProcessorConfig config = new MsProcessorConfig();
        List<io.metersphere.project.api.processor.MsProcessor> processors = new ArrayList<>();
        processors.add(extractProcessor);
        config.setProcessors(processors);

        GrpcInvokeResponse response = new GrpcInvokeResponse();
        response.setSuccess(true);
        response.setResponseJson("{\"reply\":\"hello pre-value\"}");

        GrpcScenarioVariableContext context = new GrpcScenarioVariableContext();
        List<GrpcProcessorResultDTO> results = runner.runPost(config, context, response);

        Assertions.assertEquals(1, results.size());
        GrpcProcessorResultDTO result = results.get(0);
        Assertions.assertTrue(result.getSuccess());
        List<ExtractResult> extractResults = result.getExtractResults();
        Assertions.assertEquals(1, extractResults.size());
        Assertions.assertEquals("reply", extractResults.get(0).getName());
        Assertions.assertEquals("hello pre-value", extractResults.get(0).getValue());
        Assertions.assertEquals("hello pre-value", context.get("reply"));
    }
}
