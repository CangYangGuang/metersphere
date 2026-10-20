package io.metersphere.api.grpc.util;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Converts reflection {@link Descriptors.FileDescriptor} instances into a serialized FileDescriptorSet.
 */
public final class GrpcReflectionDescriptorConverter {

    private GrpcReflectionDescriptorConverter() {
    }

    public static byte[] toDescriptorSetBytes(Collection<Descriptors.FileDescriptor> fileDescriptors) {
        DescriptorProtos.FileDescriptorSet.Builder builder = DescriptorProtos.FileDescriptorSet.newBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (Descriptors.FileDescriptor fileDescriptor : fileDescriptors) {
            addFileAndDependencies(fileDescriptor, builder, seen);
        }
        return builder.build().toByteArray();
    }

    private static void addFileAndDependencies(Descriptors.FileDescriptor fileDescriptor,
                                               DescriptorProtos.FileDescriptorSet.Builder builder,
                                               Set<String> seen) {
        if (fileDescriptor == null || !seen.add(fileDescriptor.getName())) {
            return;
        }
        for (Descriptors.FileDescriptor dependency : fileDescriptor.getDependencies()) {
            addFileAndDependencies(dependency, builder, seen);
        }
        builder.addFile(fileDescriptor.toProto());
    }
}
