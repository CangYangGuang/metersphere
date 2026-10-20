package io.metersphere.api.grpc.assertion;

import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

public final class GrpcJsonPathReader {

    private GrpcJsonPathReader() {
    }

    public static Object read(String json, String expression) {
        if (StringUtils.isBlank(json) || StringUtils.isBlank(expression)) {
            return null;
        }
        String expr = expression.trim();
        if (!expr.startsWith("$")) {
            return null;
        }
        Object current = JSON.parseObject(json, Object.class);
        String remaining = expr.substring(1);
        if (remaining.startsWith(".")) {
            remaining = remaining.substring(1);
        }
        while (StringUtils.isNotBlank(remaining)) {
            if (remaining.startsWith("[")) {
                int endIndex = remaining.indexOf(']');
                if (endIndex < 0) {
                    return null;
                }
                int index = parseIndex(remaining.substring(1, endIndex));
                if (index < 0) {
                    return null;
                }
                current = readListIndex(current, index);
                remaining = remaining.substring(endIndex + 1);
                if (remaining.startsWith(".")) {
                    remaining = remaining.substring(1);
                }
                continue;
            }
            int dotIndex = remaining.indexOf('.');
            int bracketIndex = remaining.indexOf('[');
            int splitIndex = minPositive(dotIndex, bracketIndex);
            String field = splitIndex < 0 ? remaining : remaining.substring(0, splitIndex);
            current = readMapField(current, field);
            remaining = splitIndex < 0 ? StringUtils.EMPTY : remaining.substring(splitIndex);
            if (remaining.startsWith(".")) {
                remaining = remaining.substring(1);
            }
        }
        return current;
    }

    private static Object readMapField(Object current, String field) {
        if (!(current instanceof Map<?, ?> map)) {
            return null;
        }
        return map.get(field);
    }

    private static Object readListIndex(Object current, int index) {
        if (!(current instanceof List<?> list)) {
            return null;
        }
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    private static int parseIndex(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static int minPositive(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }
}
