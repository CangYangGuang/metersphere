package io.metersphere.api.grpc.util;

import io.metersphere.api.dto.definition.ApiGrpcDefinitionAddDTO;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.grpc.dto.GrpcParseProtoRequest;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Stores multi-proto bundles in {@code api_grpc_proto.content} without DB migration.
 * Single-proto rows keep plain text content for backward compatibility.
 */
public final class GrpcProtoBundleStorage {

    private static final String BUNDLE_MARKER = "\"bundle\":true";

    private GrpcProtoBundleStorage() {
    }

    public static boolean hasBundleInput(ApiGrpcDefinitionAddDTO grpcAdd) {
        return grpcAdd != null
                && grpcAdd.getProtoFiles() != null
                && grpcAdd.getProtoFiles().size() > 1;
    }

    public static boolean isBundleContent(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("{") && trimmed.contains(BUNDLE_MARKER);
    }

    public static String serializeForStorage(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (!hasBundleInput(grpcAdd)) {
            return grpcAdd.getProtoContent();
        }
        StoredBundle bundle = StoredBundle.fromAddDto(grpcAdd);
        return JSON.toJSONString(bundle);
    }

    public static String hashForStorage(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (!hasBundleInput(grpcAdd)) {
            return sha256Hex(grpcAdd.getProtoContent());
        }
        StoredBundle bundle = StoredBundle.fromAddDto(grpcAdd);
        return sha256Hex(bundle.canonicalPayload());
    }

    public static ParsedBundle parseStored(String content) {
        if (!isBundleContent(content)) {
            return null;
        }
        StoredBundle stored = JSON.parseObject(content, StoredBundle.class);
        if (stored == null || !Boolean.TRUE.equals(stored.getBundle())) {
            return null;
        }
        return ParsedBundle.fromStored(stored);
    }

    public static ParsedBundle parseStored(ApiGrpcProto proto) {
        if (proto == null) {
            return null;
        }
        return parseStored(proto.getContent());
    }

    public static GrpcParseProtoRequest toParseRequest(ApiGrpcDefinitionAddDTO grpcAdd) {
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        if (hasBundleInput(grpcAdd)) {
            request.setProtoFiles(normalizeFiles(grpcAdd.getProtoFiles()));
            request.setMainFileName(resolveMainFileName(grpcAdd));
            return request;
        }
        request.setProtoContent(grpcAdd.getProtoContent());
        request.setFileName(grpcAdd.getFileName());
        return request;
    }

    public static GrpcParseProtoRequest toParseRequest(ApiGrpcProto proto) {
        ParsedBundle parsed = parseStored(proto);
        if (parsed != null) {
            GrpcParseProtoRequest request = new GrpcParseProtoRequest();
            request.setProtoFiles(parsed.protoFiles());
            request.setMainFileName(parsed.mainFileName());
            return request;
        }
        GrpcParseProtoRequest request = new GrpcParseProtoRequest();
        request.setProtoContent(proto.getContent());
        request.setFileName(proto.getFileName());
        return request;
    }

    public static String resolveStoredFileName(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (hasBundleInput(grpcAdd)) {
            return resolveMainFileName(grpcAdd);
        }
        return StringUtils.defaultIfBlank(grpcAdd.getFileName(), "upload.proto");
    }

    public static GrpcProtoBundleResolver.ResolvedBundle toResolvedBundle(ParsedBundle parsed) {
        List<GrpcProtoBundleResolver.ProtoFileEntry> entries = new ArrayList<>();
        for (GrpcProtoFileDTO file : parsed.protoFiles()) {
            entries.add(new GrpcProtoBundleResolver.ProtoFileEntry(file.getFileName(), file.getContent()));
        }
        return new GrpcProtoBundleResolver.ResolvedBundle(parsed.mainFileName(), entries, true);
    }

    private static String resolveMainFileName(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (StringUtils.isNotBlank(grpcAdd.getMainFileName())) {
            return GrpcProtoPathUtils.normalizeRelativePath(grpcAdd.getMainFileName());
        }
        List<GrpcProtoFileDTO> markedMain = grpcAdd.getProtoFiles().stream()
                .filter(file -> file != null && Boolean.TRUE.equals(file.getMain()))
                .toList();
        if (markedMain.size() == 1) {
            return GrpcProtoPathUtils.normalizeRelativePath(markedMain.get(0).getFileName());
        }
        if (StringUtils.isNotBlank(grpcAdd.getFileName())) {
            return GrpcProtoPathUtils.normalizeRelativePath(grpcAdd.getFileName());
        }
        return GrpcProtoPathUtils.normalizeRelativePath(grpcAdd.getProtoFiles().get(0).getFileName());
    }

    private static List<GrpcProtoFileDTO> normalizeFiles(List<GrpcProtoFileDTO> protoFiles) {
        List<GrpcProtoFileDTO> normalized = new ArrayList<>();
        for (GrpcProtoFileDTO file : protoFiles) {
            if (file == null || StringUtils.isBlank(file.getContent())) {
                continue;
            }
            GrpcProtoFileDTO copy = new GrpcProtoFileDTO();
            copy.setFileName(GrpcProtoPathUtils.normalizeRelativePath(file.getFileName()));
            copy.setContent(file.getContent());
            copy.setMain(file.getMain());
            normalized.add(copy);
        }
        return normalized;
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

    public record ParsedBundle(String mainFileName, String mainContent, List<GrpcProtoFileDTO> protoFiles) {

        static ParsedBundle fromStored(StoredBundle stored) {
            String mainFileName = GrpcProtoPathUtils.normalizeRelativePath(stored.getMainFileName());
            List<GrpcProtoFileDTO> files = normalizeFiles(stored.getProtoFiles());
            String mainContent = files.stream()
                    .filter(file -> StringUtils.equals(file.getFileName(), mainFileName))
                    .map(GrpcProtoFileDTO::getContent)
                    .findFirst()
                    .orElse("");
            return new ParsedBundle(mainFileName, mainContent, files);
        }
    }

    @lombok.Data
    private static class StoredBundle {
        private Boolean bundle;
        private String mainFileName;
        private List<GrpcProtoFileDTO> protoFiles;

        static StoredBundle fromAddDto(ApiGrpcDefinitionAddDTO grpcAdd) {
            StoredBundle stored = new StoredBundle();
            stored.setBundle(true);
            stored.setMainFileName(resolveMainFileName(grpcAdd));
            stored.setProtoFiles(normalizeFiles(grpcAdd.getProtoFiles()));
            return stored;
        }

        String canonicalPayload() {
            List<GrpcProtoFileDTO> sorted = new ArrayList<>(protoFiles);
            sorted.sort(Comparator.comparing(GrpcProtoFileDTO::getFileName, Comparator.nullsLast(String::compareTo)));
            return mainFileName + "\n" + JSON.toJSONString(sorted);
        }
    }
}
