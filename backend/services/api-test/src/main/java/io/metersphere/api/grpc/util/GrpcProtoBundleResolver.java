package io.metersphere.api.grpc.util;

import io.metersphere.api.grpc.dto.GrpcParseProtoRequest;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes single- or multi-proto parse requests and validates import dependencies.
 */
public final class GrpcProtoBundleResolver {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+\"([^\"]+)\"\\s*;");

    private GrpcProtoBundleResolver() {
    }

    public static ResolvedBundle resolve(GrpcParseProtoRequest request) {
        if (request == null) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "proto content is empty");
        }
        if (hasProtoFiles(request)) {
            return resolveBundle(request.getProtoFiles(), request.getMainFileName());
        }
        return resolveLegacySingle(request.getProtoContent(), request.getFileName());
    }

    public static boolean hasProtoFiles(GrpcParseProtoRequest request) {
        return request != null && request.getProtoFiles() != null && !request.getProtoFiles().isEmpty();
    }

    private static ResolvedBundle resolveLegacySingle(String protoContent, String fileName) {
        if (StringUtils.isBlank(protoContent)) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "proto content is empty");
        }
        validateLegacyImports(protoContent);
        String normalizedFileName = GrpcProtoPathUtils.normalizeFileName(fileName);
        return new ResolvedBundle(normalizedFileName,
                List.of(new ProtoFileEntry(normalizedFileName, protoContent)),
                false);
    }

    private static ResolvedBundle resolveBundle(List<GrpcProtoFileDTO> protoFiles, String mainFileName) {
        Map<String, ProtoFileEntry> entries = new LinkedHashMap<>();
        for (GrpcProtoFileDTO file : protoFiles) {
            if (file == null || StringUtils.isBlank(file.getContent())) {
                throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "proto file content is empty");
            }
            if (StringUtils.isBlank(file.getFileName())) {
                throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "proto file name is required");
            }
            String normalizedName = GrpcProtoPathUtils.normalizeRelativePath(file.getFileName());
            if (entries.containsKey(normalizedName)) {
                throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED,
                        "duplicate proto file name: " + normalizedName);
            }
            entries.put(normalizedName, new ProtoFileEntry(normalizedName, file.getContent()));
        }
        entries = alignUploadedFileNamesWithImports(entries);
        String resolvedMain = resolveMainFileName(entries, mainFileName, protoFiles);
        if (!entries.containsKey(resolvedMain)) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED,
                    "main proto file not found: " + resolvedMain);
        }
        validateBundleImports(entries);
        return new ResolvedBundle(resolvedMain, new ArrayList<>(entries.values()), true);
    }

    private static String resolveMainFileName(Map<String, ProtoFileEntry> entries,
                                              String mainFileName,
                                              List<GrpcProtoFileDTO> protoFiles) {
        if (StringUtils.isNotBlank(mainFileName)) {
            return GrpcProtoPathUtils.normalizeRelativePath(mainFileName);
        }
        List<String> markedMain = new ArrayList<>();
        for (GrpcProtoFileDTO file : protoFiles) {
            if (file != null && Boolean.TRUE.equals(file.getMain())) {
                markedMain.add(GrpcProtoPathUtils.normalizeRelativePath(file.getFileName()));
            }
        }
        if (markedMain.size() == 1) {
            return markedMain.get(0);
        }
        if (markedMain.size() > 1) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED,
                    "multiple main proto files specified");
        }
        if (entries.size() == 1) {
            return entries.keySet().iterator().next();
        }
        throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED,
                "mainFileName is required when multiple proto files are provided");
    }

    private static Map<String, ProtoFileEntry> alignUploadedFileNamesWithImports(Map<String, ProtoFileEntry> entries) {
        Map<String, ProtoFileEntry> aligned = new LinkedHashMap<>(entries);
        boolean changed;
        do {
            changed = false;
            List<String> missingImports = findMissingImports(aligned);
            for (String importPath : missingImports) {
                String uploadedName = findUniqueUploadedFileNameByBaseName(aligned, importPath);
                if (uploadedName == null) {
                    continue;
                }
                ProtoFileEntry uploaded = aligned.remove(uploadedName);
                aligned.put(importPath, new ProtoFileEntry(importPath, uploaded.content()));
                changed = true;
            }
        } while (changed);
        return aligned;
    }

    private static List<String> findMissingImports(Map<String, ProtoFileEntry> entries) {
        Set<String> available = new HashSet<>(entries.keySet());
        List<String> missing = new ArrayList<>();
        for (ProtoFileEntry entry : entries.values()) {
            Matcher matcher = IMPORT_PATTERN.matcher(entry.content());
            while (matcher.find()) {
                String importPath = GrpcProtoPathUtils.normalizeRelativePath(matcher.group(1));
                if (importPath.startsWith("google/protobuf/") || available.contains(importPath)) {
                    continue;
                }
                missing.add(importPath);
            }
        }
        return missing;
    }

    private static String findUniqueUploadedFileNameByBaseName(Map<String, ProtoFileEntry> entries, String importPath) {
        String baseName = baseName(importPath);
        String matched = null;
        for (String uploadedName : entries.keySet()) {
            if (uploadedName.contains("/") || !StringUtils.equals(uploadedName, baseName)) {
                continue;
            }
            if (matched != null) {
                return null;
            }
            matched = uploadedName;
        }
        return matched;
    }

    private static String baseName(String path) {
        String normalized = GrpcProtoPathUtils.normalizeRelativePath(path);
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private static void validateLegacyImports(String protoContent) {
        Matcher matcher = IMPORT_PATTERN.matcher(protoContent);
        while (matcher.find()) {
            String importPath = matcher.group(1);
            if (!importPath.startsWith("google/protobuf/")) {
                throw new MSException(GrpcResultCode.GRPC_IMPORT_NOT_SUPPORTED);
            }
        }
    }

    private static void validateBundleImports(Map<String, ProtoFileEntry> entries) {
        Set<String> available = new HashSet<>(entries.keySet());
        for (ProtoFileEntry entry : entries.values()) {
            Matcher matcher = IMPORT_PATTERN.matcher(entry.content());
            while (matcher.find()) {
                String importPath = GrpcProtoPathUtils.normalizeRelativePath(matcher.group(1));
                if (importPath.startsWith("google/protobuf/")) {
                    continue;
                }
                if (!available.contains(importPath)) {
                    throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED,
                            "missing import proto file: " + importPath);
                }
            }
        }
    }

    public record ProtoFileEntry(String fileName, String content) {
    }

    public record ResolvedBundle(String mainFileName, List<ProtoFileEntry> files, boolean bundleMode) {
    }
}
