package io.metersphere.api.grpc.util;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.util.GrpcProtoBundleStorage;
import io.metersphere.api.grpc.util.GrpcProtoBundleStorage.ParsedBundle;
import io.metersphere.api.grpc.util.GrpcReflectionProtoStorage;
import io.metersphere.api.grpc.util.GrpcProtoCompiler;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GrpcProtoSchemaSnapshotBuilder {

    @Resource
    private GrpcProtoService grpcProtoService;

    public GrpcProtoSchemaSnapshot build(ApiGrpcProto proto) {
        if (proto == null) {
            return new GrpcProtoSchemaSnapshot();
        }
        ParsedBundle bundle = GrpcProtoBundleStorage.parseStored(proto);
        if (bundle != null) {
            byte[] descriptorBytes = grpcProtoService.compileBundle(
                    GrpcProtoBundleStorage.toResolvedBundle(bundle));
            return buildFromDescriptorBytes(descriptorBytes, bundle.mainFileName());
        }
        if (GrpcReflectionProtoStorage.isReflectionContent(proto.getContent())) {
            return buildFromDescriptor(proto.getDescriptorContent(), proto.getFileName());
        }
        if (StringUtils.isNotBlank(proto.getContent())) {
            return buildFromContent(proto.getContent(), proto.getFileName());
        }
        if (StringUtils.isNotBlank(proto.getDescriptorContent())) {
            return buildFromDescriptor(proto.getDescriptorContent(), proto.getFileName());
        }
        return new GrpcProtoSchemaSnapshot();
    }

    public GrpcProtoSchemaSnapshot buildFromContent(String protoContent, String fileName) {
        byte[] descriptorBytes = grpcProtoService.compileProtoToDescriptorSet(
                protoContent, StringUtils.defaultIfBlank(fileName, "upload.proto"));
        return buildFromDescriptorBytes(descriptorBytes);
    }

    public GrpcProtoSchemaSnapshot buildFromDescriptor(String descriptorContent) {
        return buildFromDescriptor(descriptorContent, null);
    }

    public GrpcProtoSchemaSnapshot buildFromDescriptor(String descriptorContent, String fileName) {
        byte[] descriptorBytes = Base64.getDecoder().decode(descriptorContent);
        String mainFileName = StringUtils.isNotBlank(fileName) ? fileName : null;
        return buildFromDescriptorBytes(descriptorBytes, mainFileName);
    }

    private GrpcProtoSchemaSnapshot buildFromDescriptorBytes(byte[] descriptorBytes) {
        return buildFromDescriptorBytes(descriptorBytes, null);
    }

    private GrpcProtoSchemaSnapshot buildFromDescriptorBytes(byte[] descriptorBytes, String mainFileName) {
        Descriptors.FileDescriptor fileDescriptor = grpcProtoService.buildMainFileDescriptor(descriptorBytes, mainFileName);
        GrpcProtoSchemaSnapshot snapshot = new GrpcProtoSchemaSnapshot();
        for (Descriptors.ServiceDescriptor serviceDescriptor : fileDescriptor.getServices()) {
            GrpcProtoSchemaSnapshot.ServiceSnapshot serviceSnapshot = new GrpcProtoSchemaSnapshot.ServiceSnapshot();
            serviceSnapshot.setName(serviceDescriptor.getName());
            serviceSnapshot.setFullName(serviceDescriptor.getFullName());
            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                GrpcProtoSchemaSnapshot.MethodSnapshot methodSnapshot = new GrpcProtoSchemaSnapshot.MethodSnapshot();
                methodSnapshot.setName(methodDescriptor.getName());
                methodSnapshot.setFullName(serviceDescriptor.getFullName() + "/" + methodDescriptor.getName());
                methodSnapshot.setInputType(methodDescriptor.getInputType().getFullName());
                methodSnapshot.setOutputType(methodDescriptor.getOutputType().getFullName());
                serviceSnapshot.getMethods().put(methodSnapshot.getName(), methodSnapshot);
            }
            snapshot.getServices().put(serviceSnapshot.getFullName(), serviceSnapshot);
            snapshot.getServices().put(serviceSnapshot.getName(), serviceSnapshot);
        }
        try {
            DescriptorProtos.FileDescriptorSet descriptorSet = GrpcProtoCompiler.readFileDescriptorSet(descriptorBytes);
            Map<String, Descriptors.FileDescriptor> allDescriptors = GrpcProtoCompiler.buildAllFileDescriptors(descriptorSet);
            for (Descriptors.FileDescriptor descriptor : allDescriptors.values()) {
                for (Descriptors.Descriptor messageDescriptor : descriptor.getMessageTypes()) {
                    collectMessage(messageDescriptor, snapshot.getMessages());
                }
            }
        } catch (Exception exception) {
            for (Descriptors.Descriptor messageDescriptor : fileDescriptor.getMessageTypes()) {
                collectMessage(messageDescriptor, snapshot.getMessages());
            }
        }
        return snapshot;
    }

    private void collectMessage(Descriptors.Descriptor descriptor, Map<String, GrpcProtoSchemaSnapshot.MessageSnapshot> messages) {
        if (messages.containsKey(descriptor.getFullName())) {
            return;
        }
        GrpcProtoSchemaSnapshot.MessageSnapshot messageSnapshot = new GrpcProtoSchemaSnapshot.MessageSnapshot();
        messageSnapshot.setFullName(descriptor.getFullName());
        for (Descriptors.FieldDescriptor fieldDescriptor : descriptor.getFields()) {
            GrpcProtoSchemaSnapshot.FieldSnapshot fieldSnapshot = new GrpcProtoSchemaSnapshot.FieldSnapshot();
            fieldSnapshot.setName(fieldDescriptor.getName());
            fieldSnapshot.setJsonName(resolveFieldName(fieldDescriptor));
            fieldSnapshot.setTypeName(resolveFieldTypeName(fieldDescriptor));
            fieldSnapshot.setRepeated(fieldDescriptor.isRepeated() && !fieldDescriptor.isMapField());
            fieldSnapshot.setEnumValues(resolveEnumValues(fieldDescriptor));
            messageSnapshot.getFields().put(fieldSnapshot.getJsonName(), fieldSnapshot);
        }
        messages.put(descriptor.getFullName(), messageSnapshot);
        for (Descriptors.Descriptor nested : descriptor.getNestedTypes()) {
            collectMessage(nested, messages);
        }
    }

    private String resolveFieldName(Descriptors.FieldDescriptor fieldDescriptor) {
        if (StringUtils.isNotBlank(fieldDescriptor.getJsonName())) {
            return fieldDescriptor.getJsonName();
        }
        return fieldDescriptor.getName();
    }

    private String resolveFieldTypeName(Descriptors.FieldDescriptor fieldDescriptor) {
        if (fieldDescriptor.getType() == Descriptors.FieldDescriptor.Type.ENUM) {
            return fieldDescriptor.getEnumType().getFullName();
        }
        if (fieldDescriptor.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
            return fieldDescriptor.getMessageType().getFullName();
        }
        return fieldDescriptor.getType().name();
    }

    private java.util.List<String> resolveEnumValues(Descriptors.FieldDescriptor fieldDescriptor) {
        if (fieldDescriptor.getType() != Descriptors.FieldDescriptor.Type.ENUM) {
            return java.util.List.of();
        }
        return fieldDescriptor.getEnumType().getValues().stream()
                .map(Descriptors.EnumValueDescriptor::getName)
                .toList();
    }

    public static final class GrpcProtoSchemaSnapshot {
        private final Map<String, ServiceSnapshot> services = new HashMap<>();
        private final Map<String, MessageSnapshot> messages = new LinkedHashMap<>();

        public Map<String, ServiceSnapshot> getServices() {
            return services;
        }

        public Map<String, MessageSnapshot> getMessages() {
            return messages;
        }

        @lombok.Data
        public static class ServiceSnapshot {
            private String name;
            private String fullName;
            private Map<String, MethodSnapshot> methods = new LinkedHashMap<>();
        }

        @lombok.Data
        public static class MethodSnapshot {
            private String name;
            private String fullName;
            private String inputType;
            private String outputType;
        }

        @lombok.Data
        public static class MessageSnapshot {
            private String fullName;
            private Map<String, FieldSnapshot> fields = new LinkedHashMap<>();
        }

        @lombok.Data
        public static class FieldSnapshot {
            private String name;
            private String jsonName;
            private String typeName;
            private boolean repeated;
            private java.util.List<String> enumValues = java.util.List.of();
        }
    }
}
