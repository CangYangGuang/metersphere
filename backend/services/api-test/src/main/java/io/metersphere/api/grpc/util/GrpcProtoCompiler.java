package io.metersphere.api.grpc.util;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.metersphere.api.grpc.exception.GrpcProtoCompileException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class GrpcProtoCompiler {

    private static final String DEFAULT_PROTOC_VERSION = "3.25.5";

    private GrpcProtoCompiler() {
    }

    public static byte[] compileProtoContentToDescriptorSet(String protoContent, String fileName)
            throws GrpcProtoCompileException, IOException {
        String normalized = GrpcProtoPathUtils.normalizeFileName(fileName);
        return compileProtoBundleToDescriptorSet(
                List.of(new GrpcProtoBundleResolver.ProtoFileEntry(normalized, protoContent)),
                normalized);
    }

    public static byte[] compileProtoBundleToDescriptorSet(List<GrpcProtoBundleResolver.ProtoFileEntry> files,
                                                           String mainFileName)
            throws GrpcProtoCompileException, IOException {
        if (files == null || files.isEmpty()) {
            throw new GrpcProtoCompileException("proto files are empty");
        }
        Path tempDir = Files.createTempDirectory("ms-grpc-proto-");
        try {
            for (GrpcProtoBundleResolver.ProtoFileEntry entry : files) {
                Path target = tempDir.resolve(entry.fileName()).normalize();
                if (!target.startsWith(tempDir)) {
                    throw new GrpcProtoCompileException("invalid proto file path: " + entry.fileName());
                }
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.writeString(target, entry.content(), StandardCharsets.UTF_8);
            }
            Path outputDesc = tempDir.resolve("output.desc");
            Path mainFile = tempDir.resolve(mainFileName).normalize();
            if (!mainFile.startsWith(tempDir) || !Files.exists(mainFile)) {
                throw new GrpcProtoCompileException("main proto file not found: " + mainFileName);
            }
            compileProtoToDescriptorSet(mainFile, tempDir, outputDesc);
            return Files.readAllBytes(outputDesc);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public static void compileProtoToDescriptorSet(Path protoFile, Path protoPath, Path outputDesc)
            throws GrpcProtoCompileException {
        String protoc = resolveProtocExecutable();
        String relativeMain = protoPath.relativize(protoFile.toAbsolutePath()).toString().replace('\\', '/');
        ProcessBuilder processBuilder = new ProcessBuilder(
                protoc,
                "--proto_path=" + protoPath.toAbsolutePath(),
                "--descriptor_set_out=" + outputDesc.toAbsolutePath(),
                "--include_imports",
                relativeMain
        );
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GrpcProtoCompileException("protoc timed out");
            }
            if (process.exitValue() != 0) {
                throw new GrpcProtoCompileException(output.isBlank() ? "protoc failed" : output.trim());
            }
        } catch (GrpcProtoCompileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GrpcProtoCompileException("Failed to invoke protoc", exception);
        }
    }

    public static DescriptorProtos.FileDescriptorSet readFileDescriptorSet(byte[] descriptorBytes)
            throws IOException {
        return DescriptorProtos.FileDescriptorSet.parseFrom(descriptorBytes);
    }

    public static Descriptors.FileDescriptor buildFileDescriptor(DescriptorProtos.FileDescriptorSet descriptorSet)
            throws Descriptors.DescriptorValidationException {
        Map<String, Descriptors.FileDescriptor> built = buildAllFileDescriptors(descriptorSet);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Empty FileDescriptorSet");
        }
        List<DescriptorProtos.FileDescriptorProto> files = descriptorSet.getFileList();
        return built.get(files.get(files.size() - 1).getName());
    }

    public static Descriptors.FileDescriptor buildFileDescriptor(byte[] descriptorBytes)
            throws IOException, Descriptors.DescriptorValidationException {
        return buildMainFileDescriptor(descriptorBytes, null);
    }

    public static Descriptors.FileDescriptor buildMainFileDescriptor(byte[] descriptorBytes, String mainFileName)
            throws IOException, Descriptors.DescriptorValidationException {
        DescriptorProtos.FileDescriptorSet descriptorSet = readFileDescriptorSet(descriptorBytes);
        Map<String, Descriptors.FileDescriptor> built = buildAllFileDescriptors(descriptorSet);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Empty FileDescriptorSet");
        }
        if (mainFileName != null) {
            String normalizedMain = GrpcProtoPathUtils.normalizeRelativePath(mainFileName);
            Descriptors.FileDescriptor mainDescriptor = built.get(normalizedMain);
            if (mainDescriptor != null) {
                return mainDescriptor;
            }
        }
        List<DescriptorProtos.FileDescriptorProto> files = descriptorSet.getFileList();
        return built.get(files.get(files.size() - 1).getName());
    }

    public static Map<String, Descriptors.FileDescriptor> buildAllFileDescriptors(
            DescriptorProtos.FileDescriptorSet descriptorSet) throws Descriptors.DescriptorValidationException {
        Map<String, DescriptorProtos.FileDescriptorProto> protoByName = new LinkedHashMap<>();
        for (DescriptorProtos.FileDescriptorProto proto : descriptorSet.getFileList()) {
            protoByName.put(proto.getName(), proto);
        }
        Map<String, Descriptors.FileDescriptor> built = new HashMap<>();
        List<String> pending = new ArrayList<>(protoByName.keySet());
        int guard = pending.size() * pending.size() + 1;
        while (!pending.isEmpty() && guard-- > 0) {
            boolean progress = false;
            Iterator<String> iterator = pending.iterator();
            while (iterator.hasNext()) {
                String name = iterator.next();
                DescriptorProtos.FileDescriptorProto proto = protoByName.get(name);
                if (!areDependenciesReady(proto, built)) {
                    continue;
                }
                built.put(name, buildFileDescriptorFromProto(proto, built));
                iterator.remove();
                progress = true;
            }
            if (!progress) {
                throw new IllegalStateException("Unresolved proto dependencies in descriptor set");
            }
        }
        return built;
    }

    private static boolean areDependenciesReady(DescriptorProtos.FileDescriptorProto proto,
                                                  Map<String, Descriptors.FileDescriptor> built) {
        for (String dependency : proto.getDependencyList()) {
            if (!built.containsKey(dependency) && !isWellKnownDependency(dependency)) {
                return false;
            }
        }
        return true;
    }

    private static Descriptors.FileDescriptor buildFileDescriptorFromProto(
            DescriptorProtos.FileDescriptorProto proto,
            Map<String, Descriptors.FileDescriptor> built) throws Descriptors.DescriptorValidationException {
        int dependencyCount = proto.getDependencyCount();
        Descriptors.FileDescriptor[] dependencies = new Descriptors.FileDescriptor[dependencyCount];
        for (int index = 0; index < dependencyCount; index++) {
            String dependencyName = proto.getDependency(index);
            if (built.containsKey(dependencyName)) {
                dependencies[index] = built.get(dependencyName);
            } else {
                dependencies[index] = resolveWellKnownDescriptor(dependencyName);
            }
        }
        return Descriptors.FileDescriptor.buildFrom(proto, dependencies);
    }

    private static boolean isWellKnownDependency(String dependencyName) {
        return dependencyName.startsWith("google/protobuf/");
    }

    private static Descriptors.FileDescriptor resolveWellKnownDescriptor(String dependencyName)
            throws Descriptors.DescriptorValidationException {
        return switch (dependencyName) {
            case "google/protobuf/any.proto" -> com.google.protobuf.AnyProto.getDescriptor();
            case "google/protobuf/api.proto" -> com.google.protobuf.ApiProto.getDescriptor();
            case "google/protobuf/descriptor.proto" -> com.google.protobuf.DescriptorProtos.getDescriptor();
            case "google/protobuf/duration.proto" -> com.google.protobuf.DurationProto.getDescriptor();
            case "google/protobuf/empty.proto" -> com.google.protobuf.EmptyProto.getDescriptor();
            case "google/protobuf/field_mask.proto" -> com.google.protobuf.FieldMaskProto.getDescriptor();
            case "google/protobuf/source_context.proto" -> com.google.protobuf.SourceContextProto.getDescriptor();
            case "google/protobuf/struct.proto" -> com.google.protobuf.StructProto.getDescriptor();
            case "google/protobuf/timestamp.proto" -> com.google.protobuf.TimestampProto.getDescriptor();
            case "google/protobuf/type.proto" -> com.google.protobuf.TypeProto.getDescriptor();
            case "google/protobuf/wrappers.proto" -> com.google.protobuf.WrappersProto.getDescriptor();
            default -> throw new IllegalArgumentException("Unknown proto dependency: " + dependencyName);
        };
    }

    public static String resolveProtocExecutable() throws GrpcProtoCompileException {
        String fromProperty = System.getProperty("protoc.executable");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromEnv = System.getenv("PROTOC");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        String version = System.getProperty("protoc.version", DEFAULT_PROTOC_VERSION);
        Path localProtoc = locateProtocInLocalRepository(version);
        if (localProtoc != null) {
            return localProtoc.toString();
        }
        throw new GrpcProtoCompileException(
                "protoc executable not configured. Set -Dprotoc.executable, PROTOC env, or run Maven once to download protoc.");
    }

    public static Path locateProtocInLocalRepository(String protocVersion) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String classifier;
        if (osName.contains("win")) {
            classifier = "windows-x86_64.exe";
        } else if (osName.contains("mac")) {
            classifier = "osx-x86_64";
        } else {
            classifier = "linux-x86_64";
        }
        Path protocPath = Path.of(System.getProperty("user.home"), ".m2", "repository",
                "com", "google", "protobuf", "protoc", protocVersion,
                "protoc-" + protocVersion + "-" + classifier);
        if (Files.exists(protocPath)) {
            return protocPath;
        }
        Path mavenExecutableArtifact = protocPath.resolveSibling(protocPath.getFileName() + ".exe");
        return Files.exists(mavenExecutableArtifact) ? mavenExecutableArtifact : null;
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
