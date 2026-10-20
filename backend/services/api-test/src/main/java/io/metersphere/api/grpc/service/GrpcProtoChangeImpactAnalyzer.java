package io.metersphere.api.grpc.service;

import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcProtoMessageChangeDTO;
import io.metersphere.api.grpc.util.GrpcProtoSchemaSnapshotBuilder.GrpcProtoSchemaSnapshot;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GrpcProtoChangeImpactAnalyzer {

    public static final String LEVEL_BREAKING = "BREAKING";
    public static final String LEVEL_WARNING = "WARNING";
    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_NONE = "NONE";

    public GrpcProtoChangeImpactDTO analyze(GrpcProtoSchemaSnapshot oldSnapshot,
                                            GrpcProtoSchemaSnapshot newSnapshot,
                                            String serviceName,
                                            String methodName,
                                            String requestType,
                                            String responseType) {
        GrpcProtoChangeImpactDTO impact = new GrpcProtoChangeImpactDTO();
        if (oldSnapshot == null || newSnapshot == null) {
            impact.setImpactLevel(LEVEL_NONE);
            impact.setImpactSummary("No comparable proto snapshot");
            return impact;
        }

        String resolvedService = resolveServiceName(oldSnapshot, serviceName);
        GrpcProtoSchemaSnapshot.MethodSnapshot oldMethod = findMethod(oldSnapshot, resolvedService, methodName);
        GrpcProtoSchemaSnapshot.MethodSnapshot newMethod = findMethod(newSnapshot, resolvedService, methodName);

        compareServices(oldSnapshot, newSnapshot, resolvedService, impact);
        compareMethod(oldMethod, newMethod, methodName, impact);
        String requestMessageType = firstNonBlank(requestType,
                oldMethod != null ? oldMethod.getInputType() : null,
                newMethod != null ? newMethod.getInputType() : null);
        String responseMessageType = firstNonBlank(responseType,
                oldMethod != null ? oldMethod.getOutputType() : null,
                newMethod != null ? newMethod.getOutputType() : null);
        compareMessage(oldSnapshot, newSnapshot, requestMessageType, "request", impact);
        compareMessage(oldSnapshot, newSnapshot, responseMessageType, "response", impact);

        impact.setImpactLevel(resolveOverallLevel(impact));
        impact.setImpactSummary(buildSummary(impact));
        return impact;
    }

    private void compareServices(GrpcProtoSchemaSnapshot oldSnapshot,
                                 GrpcProtoSchemaSnapshot newSnapshot,
                                 String serviceName,
                                 GrpcProtoChangeImpactDTO impact) {
        if (StringUtils.isBlank(serviceName)) {
            return;
        }
        GrpcProtoSchemaSnapshot.ServiceSnapshot oldService = findService(oldSnapshot, serviceName);
        GrpcProtoSchemaSnapshot.ServiceSnapshot newService = findService(newSnapshot, serviceName);
        if (oldService != null && newService == null) {
            impact.getChangedServices().add(serviceName + " (removed)");
        }
    }

    private void compareMethod(GrpcProtoSchemaSnapshot.MethodSnapshot oldMethod,
                             GrpcProtoSchemaSnapshot.MethodSnapshot newMethod,
                             String methodName,
                             GrpcProtoChangeImpactDTO impact) {
        if (oldMethod == null) {
            return;
        }
        if (newMethod == null) {
            impact.getChangedMethods().add((StringUtils.defaultIfBlank(methodName, oldMethod.getName())) + " (removed)");
            return;
        }
        if (!StringUtils.equals(oldMethod.getInputType(), newMethod.getInputType())) {
            impact.getChangedMethods().add(newMethod.getName() + " requestType: "
                    + oldMethod.getInputType() + " -> " + newMethod.getInputType());
        }
        if (!StringUtils.equals(oldMethod.getOutputType(), newMethod.getOutputType())) {
            impact.getChangedMethods().add(newMethod.getName() + " responseType: "
                    + oldMethod.getOutputType() + " -> " + newMethod.getOutputType());
        }
    }

    private void compareMessage(GrpcProtoSchemaSnapshot oldSnapshot,
                                GrpcProtoSchemaSnapshot newSnapshot,
                                String messageType,
                                String role,
                                GrpcProtoChangeImpactDTO impact) {
        if (StringUtils.isBlank(messageType)) {
            return;
        }
        GrpcProtoSchemaSnapshot.MessageSnapshot oldMessage = oldSnapshot.getMessages().get(messageType);
        GrpcProtoSchemaSnapshot.MessageSnapshot newMessage = newSnapshot.getMessages().get(messageType);
        if (oldMessage != null && newMessage == null) {
            addMessageChange(impact, messageType, "MESSAGE_REMOVED", null,
                    role + " message removed", LEVEL_BREAKING);
            return;
        }
        if (oldMessage == null || newMessage == null) {
            return;
        }
        Set<String> visited = new HashSet<>();
        for (Map.Entry<String, GrpcProtoSchemaSnapshot.FieldSnapshot> entry : oldMessage.getFields().entrySet()) {
            String fieldName = entry.getKey();
            GrpcProtoSchemaSnapshot.FieldSnapshot oldField = entry.getValue();
            GrpcProtoSchemaSnapshot.FieldSnapshot newField = newMessage.getFields().get(fieldName);
            if (newField == null) {
                addMessageChange(impact, messageType, "FIELD_REMOVED", fieldName,
                        role + " field removed: " + fieldName, LEVEL_BREAKING);
                continue;
            }
            if (!StringUtils.equals(oldField.getTypeName(), newField.getTypeName())) {
                addMessageChange(impact, messageType, "FIELD_TYPE_CHANGED", fieldName,
                        role + " field type changed: " + oldField.getTypeName() + " -> " + newField.getTypeName(),
                        LEVEL_BREAKING);
            }
            compareEnumValues(messageType, fieldName, oldField, newField, impact);
            visited.add(fieldName);
        }
        for (Map.Entry<String, GrpcProtoSchemaSnapshot.FieldSnapshot> entry : newMessage.getFields().entrySet()) {
            if (!oldMessage.getFields().containsKey(entry.getKey())) {
                addMessageChange(impact, messageType, "FIELD_ADDED", entry.getKey(),
                        role + " field added: " + entry.getKey(), LEVEL_WARNING);
            }
        }
    }

    private void compareEnumValues(String messageType,
                                   String fieldName,
                                   GrpcProtoSchemaSnapshot.FieldSnapshot oldField,
                                   GrpcProtoSchemaSnapshot.FieldSnapshot newField,
                                   GrpcProtoChangeImpactDTO impact) {
        if (oldField.getEnumValues().isEmpty() && newField.getEnumValues().isEmpty()) {
            return;
        }
        Set<String> oldValues = new HashSet<>(oldField.getEnumValues());
        Set<String> newValues = new HashSet<>(newField.getEnumValues());
        newValues.removeAll(oldValues);
        if (!newValues.isEmpty()) {
            addMessageChange(impact, messageType, "ENUM_VALUE_ADDED", fieldName,
                    "enum values added: " + String.join(", ", newValues), LEVEL_WARNING);
        }
    }

    private void addMessageChange(GrpcProtoChangeImpactDTO impact,
                                String messageName,
                                String changeType,
                                String fieldName,
                                String detail,
                                String level) {
        GrpcProtoMessageChangeDTO change = new GrpcProtoMessageChangeDTO();
        change.setMessageName(messageName);
        change.setChangeType(changeType);
        change.setFieldName(fieldName);
        change.setDetail(detail);
        change.setImpactLevel(level);
        impact.getChangedMessages().add(change);
    }

    private String resolveOverallLevel(GrpcProtoChangeImpactDTO impact) {
        String level = LEVEL_NONE;
        if (!impact.getChangedServices().isEmpty() || !impact.getChangedMethods().isEmpty()) {
            level = LEVEL_BREAKING;
        }
        for (GrpcProtoMessageChangeDTO change : impact.getChangedMessages()) {
            level = maxLevel(level, change.getImpactLevel());
        }
        if (LEVEL_NONE.equals(level) && hasAnyChange(impact)) {
            return LEVEL_INFO;
        }
        return level;
    }

    private boolean hasAnyChange(GrpcProtoChangeImpactDTO impact) {
        return !impact.getChangedServices().isEmpty()
                || !impact.getChangedMethods().isEmpty()
                || !impact.getChangedMessages().isEmpty();
    }

    private String maxLevel(String current, String candidate) {
        List<String> order = List.of(LEVEL_NONE, LEVEL_INFO, LEVEL_WARNING, LEVEL_BREAKING);
        if (order.indexOf(candidate) > order.indexOf(current)) {
            return candidate;
        }
        return current;
    }

    private String buildSummary(GrpcProtoChangeImpactDTO impact) {
        if (LEVEL_NONE.equals(impact.getImpactLevel())) {
            return "No proto structure changes detected";
        }
        if (LEVEL_INFO.equals(impact.getImpactLevel())) {
            return "Proto version changed without structural differences";
        }
        List<String> parts = new ArrayList<>();
        if (!impact.getChangedServices().isEmpty()) {
            parts.add("services changed: " + impact.getChangedServices().size());
        }
        if (!impact.getChangedMethods().isEmpty()) {
            parts.add("methods changed: " + impact.getChangedMethods().size());
        }
        if (!impact.getChangedMessages().isEmpty()) {
            parts.add("message fields changed: " + impact.getChangedMessages().size());
        }
        return String.join("; ", parts);
    }

    private GrpcProtoSchemaSnapshot.ServiceSnapshot findService(GrpcProtoSchemaSnapshot snapshot, String serviceName) {
        if (snapshot.getServices().containsKey(serviceName)) {
            return snapshot.getServices().get(serviceName);
        }
        String shortName = serviceName.contains(".")
                ? serviceName.substring(serviceName.lastIndexOf('.') + 1) : serviceName;
        return snapshot.getServices().get(shortName);
    }

    private String resolveServiceName(GrpcProtoSchemaSnapshot snapshot, String serviceName) {
        if (StringUtils.isNotBlank(serviceName) && findService(snapshot, serviceName) != null) {
            return serviceName;
        }
        return serviceName;
    }

    private GrpcProtoSchemaSnapshot.MethodSnapshot findMethod(GrpcProtoSchemaSnapshot snapshot,
                                                              String serviceName,
                                                              String methodName) {
        if (StringUtils.isBlank(methodName)) {
            return null;
        }
        GrpcProtoSchemaSnapshot.ServiceSnapshot service = findService(snapshot, serviceName);
        if (service == null) {
            for (GrpcProtoSchemaSnapshot.ServiceSnapshot item : snapshot.getServices().values()) {
                if (item.getMethods().containsKey(methodName)) {
                    return item.getMethods().get(methodName);
                }
            }
            return null;
        }
        return service.getMethods().get(methodName);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
