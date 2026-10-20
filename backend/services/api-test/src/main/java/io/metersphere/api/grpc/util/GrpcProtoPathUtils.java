package io.metersphere.api.grpc.util;

import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;

public final class GrpcProtoPathUtils {

    private GrpcProtoPathUtils() {
    }

    public static String normalizeFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "upload.proto";
        }
        String name = Path.of(fileName).getFileName().toString();
        if (!name.endsWith(".proto")) {
            name = name + ".proto";
        }
        return sanitizeSimpleFileName(name);
    }

    public static String normalizeRelativePath(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "proto file name is required");
        }
        String normalized = fileName.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "invalid proto file path: " + fileName);
        }
        if (!normalized.endsWith(".proto")) {
            normalized = normalized + ".proto";
        }
        return normalized;
    }

    private static String sanitizeSimpleFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
