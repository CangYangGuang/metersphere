package io.metersphere.api.grpc.demo;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GrpcProtoCompiler {

    private GrpcProtoCompiler() {
    }

    public static Descriptors.FileDescriptor loadDescriptorSetFromClasspath(String resourcePath) throws IOException {
        try (InputStream inputStream = GrpcProtoCompiler.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Descriptor resource not found: " + resourcePath);
            }
            DescriptorProtos.FileDescriptorSet descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(inputStream);
            try {
                return buildFileDescriptor(descriptorSet);
            } catch (Descriptors.DescriptorValidationException exception) {
                throw new IOException("Invalid descriptor set", exception);
            }
        }
    }

    public static void compileProtoToDescriptorSet(Path protoFile, Path protoPath, Path outputDesc)
            throws GrpcProtoCompileException {
        String protoc = resolveProtocExecutable();
        ProcessBuilder processBuilder = new ProcessBuilder(
                protoc,
                "--proto_path=" + protoPath.toAbsolutePath(),
                "--descriptor_set_out=" + outputDesc.toAbsolutePath(),
                protoFile.getFileName().toString()
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

    public static String resolveProtocExecutable() throws GrpcProtoCompileException {
        String fromProperty = System.getProperty("protoc.executable");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromEnv = System.getenv("PROTOC");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        Path localProtoc = locateProtocInLocalRepository("3.25.5");
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
        return Files.exists(protocPath) ? protocPath : null;
    }

    private static Descriptors.FileDescriptor buildFileDescriptor(DescriptorProtos.FileDescriptorSet descriptorSet)
            throws Descriptors.DescriptorValidationException {
        if (descriptorSet.getFileCount() == 0) {
            throw new IllegalArgumentException("Empty FileDescriptorSet");
        }
        List<Descriptors.FileDescriptor> built = new ArrayList<>();
        for (DescriptorProtos.FileDescriptorProto fileDescriptorProto : descriptorSet.getFileList()) {
            Descriptors.FileDescriptor[] dependencies = built.toArray(new Descriptors.FileDescriptor[0]);
            built.add(Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies));
        }
        return built.get(built.size() - 1);
    }
}
