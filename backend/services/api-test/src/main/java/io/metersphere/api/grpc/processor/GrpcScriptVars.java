package io.metersphere.api.grpc.processor;

import io.metersphere.api.grpc.scenario.GrpcScenarioVariableContext;

/**
 * gRPC 前置 / 后置脚本可访问的变量对象，桥接到运行时变量上下文。
 * 脚本中通过 vars.put("name", value) / vars.get("name") 读写变量，
 * 与 HTTP 脚本处理器的 vars 用法保持一致。
 */
public class GrpcScriptVars {

    private final GrpcScenarioVariableContext context;

    public GrpcScriptVars(GrpcScenarioVariableContext context) {
        this.context = context;
    }

    public void put(String name, Object value) {
        context.put(name, value == null ? null : String.valueOf(value));
    }

    public String get(String name) {
        return context.get(name);
    }
}
