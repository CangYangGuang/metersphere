package io.metersphere.api.grpc.util;

import com.google.protobuf.Descriptors;
import io.metersphere.sdk.util.JSON;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates example JSON request body templates from protobuf message descriptors.
 */
public final class GrpcRequestJsonTemplateGenerator {

    public static final int DEFAULT_MAX_DEPTH = 10;

    private GrpcRequestJsonTemplateGenerator() {
    }

    public static String generate(Descriptors.Descriptor descriptor) {
        return generate(descriptor, DEFAULT_MAX_DEPTH);
    }

    public static String generate(Descriptors.Descriptor descriptor, int maxDepth) {
        Object template = buildMessageTemplate(descriptor, maxDepth, new HashSet<>(), 0);
        return JSON.toFormatJSONString(template);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildMessageTemplate(Descriptors.Descriptor descriptor,
                                                            int maxDepth,
                                                            Set<String> visiting,
                                                            int depth) {
        if (depth >= maxDepth || visiting.contains(descriptor.getFullName())) {
            return new LinkedHashMap<>();
        }
        visiting.add(descriptor.getFullName());
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            Set<Descriptors.OneofDescriptor> processedOneofs = new HashSet<>();
            for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
                Descriptors.OneofDescriptor oneof = field.getRealContainingOneof();
                if (oneof != null) {
                    if (!processedOneofs.add(oneof)) {
                        continue;
                    }
                    field = oneof.getField(0);
                }
                result.put(resolveFieldName(field), buildFieldTemplate(field, maxDepth, visiting, depth + 1));
            }
            return result;
        } finally {
            visiting.remove(descriptor.getFullName());
        }
    }

    private static Object buildFieldTemplate(Descriptors.FieldDescriptor field,
                                             int maxDepth,
                                             Set<String> visiting,
                                             int depth) {
        if (field.isMapField()) {
            return buildMapTemplate(field, maxDepth, visiting, depth);
        }
        if (field.isRepeated()) {
            return Collections.singletonList(buildSingularTemplate(field, maxDepth, visiting, depth));
        }
        return buildSingularTemplate(field, maxDepth, visiting, depth);
    }

    private static Map<String, Object> buildMapTemplate(Descriptors.FieldDescriptor field,
                                                       int maxDepth,
                                                       Set<String> visiting,
                                                       int depth) {
        Descriptors.FieldDescriptor valueField = field.getMessageType().findFieldByName("value");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", buildSingularTemplate(valueField, maxDepth, visiting, depth));
        return map;
    }

    private static Object buildSingularTemplate(Descriptors.FieldDescriptor field,
                                                int maxDepth,
                                                Set<String> visiting,
                                                int depth) {
        return switch (field.getType()) {
            case STRING -> "string";
            case BOOL -> true;
            case BYTES -> "base64";
            case ENUM -> field.getEnumType().getValues().get(0).getName();
            case MESSAGE, GROUP -> buildMessageTemplate(field.getMessageType(), maxDepth, visiting, depth);
            case FLOAT, DOUBLE -> 0;
            default -> 0;
        };
    }

    private static String resolveFieldName(Descriptors.FieldDescriptor field) {
        if (field.getJsonName() != null && !field.getJsonName().isBlank()) {
            return field.getJsonName();
        }
        return field.getName();
    }

    /**
     * Exposed for unit tests to inspect structured template without JSON serialization.
     */
    static Map<String, Object> generateObject(Descriptors.Descriptor descriptor, int maxDepth) {
        return buildMessageTemplate(descriptor, maxDepth, new HashSet<>(), 0);
    }
}
