package io.metersphere.api.grpc.util;

import io.metersphere.api.dto.definition.ApiGrpcDefinitionAddDTO;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stores Reflection descriptor-only rows in {@code api_grpc_proto.content} without DB migration.
 */
public final class GrpcReflectionProtoStorage {

    private static final String REFLECTION_MARKER = "\"reflection\":true";
    private static final String SOURCE_TYPE = "REFLECTION";
    private static final String FILE_KEY_PREFIX = "reflection://";

    private GrpcReflectionProtoStorage() {
    }

    public static boolean hasReflectionInput(ApiGrpcDefinitionAddDTO grpcAdd) {
        return grpcAdd != null
                && StringUtils.isNotBlank(grpcAdd.getDescriptorContent())
                && (Boolean.TRUE.equals(grpcAdd.getReflection())
                || StringUtils.isNotBlank(grpcAdd.getReflectionServerAddress()));
    }

    public static boolean isReflectionContent(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("{") && trimmed.contains(REFLECTION_MARKER);
    }

    public static String serializeForStorage(ApiGrpcDefinitionAddDTO grpcAdd) {
        StoredReflection stored = StoredReflection.fromAddDto(grpcAdd);
        return JSON.toJSONString(stored);
    }

    public static String hashForStorage(ApiGrpcDefinitionAddDTO grpcAdd) {
        return sha256Hex(grpcAdd.getDescriptorContent());
    }

    public static String resolveStoredFileName(ApiGrpcDefinitionAddDTO grpcAdd) {
        return resolveFileKey(grpcAdd.getReflectionServerAddress());
    }

    public static String resolveFileKey(String serverAddress) {
        String normalized = StringUtils.trimToEmpty(serverAddress);
        if (StringUtils.isBlank(normalized)) {
            return "reflection://unknown";
        }
        return FILE_KEY_PREFIX + normalized;
    }

    public static ParsedReflection parseStored(String content) {
        if (!isReflectionContent(content)) {
            return null;
        }
        StoredReflection stored = JSON.parseObject(content, StoredReflection.class);
        if (stored == null || !Boolean.TRUE.equals(stored.getReflection())) {
            return null;
        }
        return ParsedReflection.fromStored(stored);
    }

    public static ParsedReflection parseStored(ApiGrpcProto proto) {
        if (proto == null) {
            return null;
        }
        return parseStored(proto.getContent());
    }

    public record ParsedReflection(
            String sourceType,
            String serverAddress,
            Boolean useTls,
            String authority,
            String serviceFullName,
            String discoveredAt) {

        static ParsedReflection fromStored(StoredReflection stored) {
            return new ParsedReflection(
                    stored.getSourceType(),
                    stored.getServerAddress(),
                    stored.getUseTls(),
                    stored.getAuthority(),
                    stored.getServiceFullName(),
                    stored.getDiscoveredAt());
        }
    }

    @lombok.Data
    private static class StoredReflection {
        private Boolean reflection;
        private String sourceType;
        private String serverAddress;
        private Boolean useTls;
        private String authority;
        private String serviceFullName;
        private String discoveredAt;

        static StoredReflection fromAddDto(ApiGrpcDefinitionAddDTO grpcAdd) {
            StoredReflection stored = new StoredReflection();
            stored.setReflection(true);
            stored.setSourceType(StringUtils.defaultIfBlank(grpcAdd.getReflectionSourceType(), SOURCE_TYPE));
            stored.setServerAddress(StringUtils.trimToEmpty(grpcAdd.getReflectionServerAddress()));
            stored.setUseTls(grpcAdd.getReflectionUseTls());
            stored.setAuthority(StringUtils.defaultString(grpcAdd.getReflectionAuthority()));
            stored.setServiceFullName(StringUtils.defaultIfBlank(
                    grpcAdd.getReflectionServiceFullName(), grpcAdd.getServiceName()));
            stored.setDiscoveredAt(grpcAdd.getReflectionDiscoveredAt());
            return stored;
        }
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(StringUtils.defaultString(content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
