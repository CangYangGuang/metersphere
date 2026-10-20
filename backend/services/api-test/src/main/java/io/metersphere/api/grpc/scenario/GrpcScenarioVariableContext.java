package io.metersphere.api.grpc.scenario;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景运行时变量上下文（最小实现）。
 */
public class GrpcScenarioVariableContext {

    private final Map<String, String> variables = new LinkedHashMap<>();

    public void put(String name, String value) {
        if (StringUtils.isNotBlank(name) && value != null) {
            variables.put(name, value);
        }
    }

    public String get(String name) {
        return variables.get(name);
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(variables);
    }
}
