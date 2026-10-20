package io.metersphere.api.grpc.service;

import com.google.protobuf.Descriptors;
import io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;
import io.metersphere.api.grpc.dto.GrpcParseProtoRequest;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcServiceInfoDTO;
import io.metersphere.api.grpc.exception.GrpcProtoCompileException;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.util.GrpcProtoBundleResolver;
import io.metersphere.api.grpc.util.GrpcProtoBundleResolver.ResolvedBundle;
import io.metersphere.api.grpc.util.GrpcProtoCompiler;
import io.metersphere.api.grpc.util.GrpcProtoPathUtils;
import io.metersphere.api.grpc.util.GrpcRequestJsonTemplateGenerator;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GrpcProtoService {

    public GrpcParseProtoResponse parseProto(GrpcParseProtoRequest request) {
        ResolvedBundle bundle = GrpcProtoBundleResolver.resolve(request);
        byte[] descriptorBytes = compileBundle(bundle);
        Descriptors.FileDescriptor fileDescriptor = buildMainFileDescriptor(descriptorBytes, bundle.mainFileName());
        GrpcParseProtoResponse response = parseServicesAndMethods(fileDescriptor, descriptorBytes);
        response.setPackageName(fileDescriptor.getPackage());
        return response;
    }

    public byte[] compileProtoToDescriptorSet(String protoContent, String fileName) {
        try {
            return GrpcProtoCompiler.compileProtoContentToDescriptorSet(protoContent, fileName);
        } catch (GrpcProtoCompileException exception) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getMessage());
        } catch (Exception exception) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception);
        }
    }

    public byte[] compileBundle(ResolvedBundle bundle) {
        try {
            return GrpcProtoCompiler.compileProtoBundleToDescriptorSet(bundle.files(), bundle.mainFileName());
        } catch (GrpcProtoCompileException exception) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getMessage());
        } catch (Exception exception) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception);
        }
    }

    public Descriptors.FileDescriptor buildFileDescriptor(byte[] descriptorBytes) {
        return buildMainFileDescriptor(descriptorBytes, null);
    }

    public Descriptors.FileDescriptor buildMainFileDescriptor(byte[] descriptorBytes, String mainFileName) {
        try {
            return GrpcProtoCompiler.buildMainFileDescriptor(descriptorBytes, mainFileName);
        } catch (Exception exception) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception);
        }
    }

    public GrpcParseProtoResponse parseServicesAndMethods(Descriptors.FileDescriptor fileDescriptor, byte[] descriptorBytes) {
        return parseAllServicesFromDescriptorSet(descriptorBytes);
    }

    public GrpcParseProtoResponse parseAllServicesFromDescriptorSet(byte[] descriptorBytes) {
        try {
            GrpcParseProtoResponse response = new GrpcParseProtoResponse();
            response.setDescriptorContent(Base64.getEncoder().encodeToString(descriptorBytes));
            Map<String, Descriptors.FileDescriptor> allDescriptors = GrpcProtoCompiler.buildAllFileDescriptors(
                    GrpcProtoCompiler.readFileDescriptorSet(descriptorBytes));
            if (allDescriptors.isEmpty()) {
                response.setWarnings(List.of("No file descriptors available"));
                return response;
            }
            response.setPackageName(allDescriptors.values().stream()
                    .map(Descriptors.FileDescriptor::getPackage)
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .orElse(""));
            List<GrpcServiceInfoDTO> services = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (Descriptors.FileDescriptor fileDescriptor : allDescriptors.values()) {
                appendServices(fileDescriptor, services, warnings);
            }
            response.setServices(services);
            response.setWarnings(warnings);
            return response;
        } catch (Exception exception) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, exception.getMessage());
        }
    }

    private void appendServices(Descriptors.FileDescriptor fileDescriptor,
                                List<GrpcServiceInfoDTO> services,
                                List<String> warnings) {
        for (Descriptors.ServiceDescriptor serviceDescriptor : fileDescriptor.getServices()) {
            GrpcServiceInfoDTO serviceInfo = new GrpcServiceInfoDTO();
            serviceInfo.setName(serviceDescriptor.getName());
            serviceInfo.setFullName(serviceDescriptor.getFullName());
            List<GrpcMethodInfoDTO> methods = new ArrayList<>();
            for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                GrpcMethodInfoDTO methodInfo = toMethodInfo(serviceDescriptor, methodDescriptor);
                methods.add(methodInfo);
                if (!Boolean.TRUE.equals(methodInfo.getInvokable())) {
                    warnings.add("Streaming method not supported for invoke: " + methodInfo.getFullName());
                }
            }
            serviceInfo.setMethods(methods);
            services.add(serviceInfo);
        }
    }

    public Descriptors.FileDescriptor resolveFileDescriptor(String descriptorContent, String protoContent, String fileName) {
        if (StringUtils.isNotBlank(descriptorContent)) {
            byte[] descriptorBytes;
            try {
                descriptorBytes = Base64.getDecoder().decode(descriptorContent);
            } catch (IllegalArgumentException exception) {
                throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "Invalid descriptor content");
            }
            String mainFileName = StringUtils.isNotBlank(fileName)
                    ? GrpcProtoPathUtils.normalizeRelativePath(fileName)
                    : null;
            return buildMainFileDescriptor(descriptorBytes, mainFileName);
        }
        if (StringUtils.isNotBlank(protoContent)) {
            byte[] descriptorBytes = compileProtoToDescriptorSet(protoContent, fileName);
            return buildFileDescriptor(descriptorBytes);
        }
        throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED, "descriptorContent or protoContent is required");
    }

    private GrpcMethodInfoDTO toMethodInfo(Descriptors.ServiceDescriptor serviceDescriptor,
                                           Descriptors.MethodDescriptor methodDescriptor) {
        GrpcMethodInfoDTO methodInfo = new GrpcMethodInfoDTO();
        methodInfo.setName(methodDescriptor.getName());
        methodInfo.setFullName(serviceDescriptor.getFullName() + "/" + methodDescriptor.getName());
        String methodType = resolveMethodType(methodDescriptor);
        methodInfo.setMethodType(methodType);
        methodInfo.setInvokable("UNARY".equals(methodType));
        methodInfo.setInputType(methodDescriptor.getInputType().getFullName());
        methodInfo.setOutputType(methodDescriptor.getOutputType().getFullName());
        methodInfo.setRequestJsonTemplate(
                GrpcRequestJsonTemplateGenerator.generate(methodDescriptor.getInputType()));
        return methodInfo;
    }

    private String resolveMethodType(Descriptors.MethodDescriptor methodDescriptor) {
        if (methodDescriptor.isClientStreaming() && methodDescriptor.isServerStreaming()) {
            return "BIDI_STREAMING";
        }
        if (methodDescriptor.isClientStreaming()) {
            return "CLIENT_STREAMING";
        }
        if (methodDescriptor.isServerStreaming()) {
            return "SERVER_STREAMING";
        }
        return "UNARY";
    }
}
