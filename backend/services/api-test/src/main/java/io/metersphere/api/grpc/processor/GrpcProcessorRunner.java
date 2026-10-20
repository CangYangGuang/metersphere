package io.metersphere.api.grpc.processor;

import io.metersphere.api.dto.request.processors.MsProcessorConfig;
import io.metersphere.api.grpc.assertion.GrpcJsonPathReader;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.api.grpc.dto.GrpcProcessorResultDTO;
import io.metersphere.api.grpc.scenario.GrpcScenarioVariableContext;
import io.metersphere.project.api.processor.ExtractPostProcessor;
import io.metersphere.project.api.processor.MsProcessor;
import io.metersphere.project.api.processor.ScriptProcessor;
import io.metersphere.project.api.processor.TimeWaitingProcessor;
import io.metersphere.project.api.processor.extract.JSONPathExtract;
import io.metersphere.project.api.processor.extract.MsExtract;
import io.metersphere.sdk.dto.api.result.ExtractResult;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.List;

/**
 * gRPC 前置 / 后置操作在服务端进程内执行。
 * 复用 HTTP 的 {@link MsProcessorConfig} 配置结构，支持：
 * <ul>
 *     <li>SCRIPT：Groovy 脚本，脚本内通过 vars 读写变量、读取响应（后置）</li>
 *     <li>EXTRACT：JSONPath 变量提取（后置）</li>
 *     <li>TIME_WAITING：等待</li>
 * </ul>
 * 不支持的类型（SQL、公共脚本、Regex/XPath 提取）会在结果中给出明确提示，不静默忽略。
 */
@Component
public class GrpcProcessorRunner {

    public static final String PHASE_PRE = "PRE";
    public static final String PHASE_POST = "POST";

    private static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();

    public List<GrpcProcessorResultDTO> runPre(MsProcessorConfig config, GrpcScenarioVariableContext context) {
        return run(config, PHASE_PRE, context, null);
    }

    public List<GrpcProcessorResultDTO> runPost(MsProcessorConfig config, GrpcScenarioVariableContext context,
                                                GrpcInvokeResponse response) {
        return run(config, PHASE_POST, context, response);
    }

    private List<GrpcProcessorResultDTO> run(MsProcessorConfig config, String phase,
                                             GrpcScenarioVariableContext context, GrpcInvokeResponse response) {
        List<GrpcProcessorResultDTO> results = new ArrayList<>();
        if (config == null || CollectionUtils.isEmpty(config.getProcessors())) {
            return results;
        }
        for (MsProcessor processor : config.getProcessors()) {
            if (processor == null || BooleanUtils.isFalse(processor.getEnable())) {
                continue;
            }
            results.add(runProcessor(processor, phase, context, response));
        }
        return results;
    }

    private GrpcProcessorResultDTO runProcessor(MsProcessor processor, String phase,
                                                GrpcScenarioVariableContext context, GrpcInvokeResponse response) {
        GrpcProcessorResultDTO result = new GrpcProcessorResultDTO();
        result.setPhase(phase);
        result.setName(processor.getName());
        long start = System.currentTimeMillis();
        try {
            if (processor instanceof ScriptProcessor scriptProcessor) {
                runScript(scriptProcessor, context, response, result);
            } else if (processor instanceof ExtractPostProcessor extractProcessor) {
                runExtract(extractProcessor, context, response, result);
            } else if (processor instanceof TimeWaitingProcessor timeWaitingProcessor) {
                runWait(timeWaitingProcessor, result);
            } else {
                result.setProcessorType(processor.getClass().getSimpleName());
                result.setSuccess(true);
                result.setMessage("gRPC 暂不支持该处理器类型，已跳过：仅支持脚本 / JSONPath 提取 / 等待");
            }
        } catch (Exception exception) {
            LogUtils.error("gRPC processor execution failed", exception);
            result.setSuccess(false);
            result.setMessage(exception.getMessage());
        }
        result.setDuration(System.currentTimeMillis() - start);
        return result;
    }

    private void runScript(ScriptProcessor processor, GrpcScenarioVariableContext context,
                           GrpcInvokeResponse response, GrpcProcessorResultDTO result) throws Exception {
        result.setProcessorType("SCRIPT");
        if (processor.isEnableCommonScript()) {
            result.setSuccess(false);
            result.setMessage("gRPC 前置/后置暂不支持公共脚本，请使用内联脚本");
            return;
        }
        if (StringUtils.isBlank(processor.getScript())) {
            result.setSuccess(true);
            result.setMessage("脚本为空，已跳过");
            return;
        }
        ScriptEngine engine = SCRIPT_ENGINE_MANAGER.getEngineByName("groovy");
        if (engine == null) {
            result.setSuccess(false);
            result.setMessage("Groovy 脚本引擎不可用，无法执行 gRPC 脚本操作");
            return;
        }
        engine.put("vars", new GrpcScriptVars(context));
        if (response != null) {
            engine.put("responseJson", StringUtils.defaultString(response.getResponseJson()));
            engine.put("grpcStatus", response.getGrpcStatus());
            engine.put("grpcCode", response.getGrpcCode());
            engine.put("success", BooleanUtils.isTrue(response.getSuccess()));
            engine.put("errorCode", response.getErrorCode());
            engine.put("errorMessage", response.getErrorMessage());
        }
        Object evalResult = engine.eval(processor.getScript());
        result.setSuccess(true);
        result.setMessage(evalResult == null ? "脚本执行成功" : "脚本执行成功，返回：" + evalResult);
    }

    private void runExtract(ExtractPostProcessor processor, GrpcScenarioVariableContext context,
                            GrpcInvokeResponse response, GrpcProcessorResultDTO result) {
        result.setProcessorType("EXTRACT");
        List<ExtractResult> extractResults = new ArrayList<>();
        String responseJson = response == null ? null : response.getResponseJson();
        List<String> unsupported = new ArrayList<>();
        if (!CollectionUtils.isEmpty(processor.getExtractors())) {
            for (MsExtract extract : processor.getExtractors()) {
                if (extract == null || BooleanUtils.isFalse(extract.getEnable())) {
                    continue;
                }
                if (!(extract instanceof JSONPathExtract)) {
                    unsupported.add(StringUtils.defaultString(extract.getVariableName()));
                    continue;
                }
                if (StringUtils.isBlank(extract.getVariableName()) || StringUtils.isBlank(extract.getExpression())) {
                    continue;
                }
                Object value = GrpcJsonPathReader.read(responseJson, extract.getExpression());
                String stringValue = stringify(value);
                context.put(extract.getVariableName(), stringValue);
                ExtractResult extractResult = new ExtractResult();
                extractResult.setName(extract.getVariableName());
                extractResult.setValue(stringValue);
                extractResult.setType("JSON_PATH");
                extractResult.setExpression(extract.getExpression());
                extractResults.add(extractResult);
            }
        }
        result.setExtractResults(extractResults);
        result.setSuccess(true);
        if (!unsupported.isEmpty()) {
            result.setMessage("gRPC 后置提取暂仅支持 JSONPath，已跳过：" + StringUtils.join(unsupported, ", "));
        } else {
            result.setMessage("提取完成，共 " + extractResults.size() + " 项");
        }
    }

    private void runWait(TimeWaitingProcessor processor, GrpcProcessorResultDTO result) throws InterruptedException {
        result.setProcessorType("TIME_WAITING");
        long delay = processor.getDelay() == null ? 0L : Math.max(0L, processor.getDelay());
        if (delay > 0) {
            Thread.sleep(delay);
        }
        result.setSuccess(true);
        result.setMessage("等待 " + delay + " ms");
    }

    private String stringify(Object value) {
        if (value == null) {
            return StringUtils.EMPTY;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return JSON.toJSONString(value);
    }
}
