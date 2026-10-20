package io.metersphere.api.grpc.service;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.CallOptions;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.metersphere.api.grpc.dto.GrpcInvokeRequest;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.util.GrpcChannelFactory;
import io.metersphere.api.grpc.util.GrpcJsonConverter;
import io.metersphere.api.grpc.util.GrpcMetadataBuilder;
import io.metersphere.api.grpc.util.GrpcStatusMapper;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class GrpcInvokeService {

    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final int MIN_TIMEOUT_MS = 1000;
    private static final int MAX_TIMEOUT_MS = 60000;

    private final GrpcProtoService grpcProtoService;

    public GrpcInvokeService(GrpcProtoService grpcProtoService) {
        this.grpcProtoService = grpcProtoService;
    }

    public GrpcInvokeResponse invoke(GrpcInvokeRequest request) {
        if (request == null) {
            throw new MSException(GrpcResultCode.GRPC_CALL_FAILED, "invoke request is empty");
        }
        if (StringUtils.isBlank(request.getServerAddress())) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID);
        }
        if (StringUtils.isBlank(request.getServiceName()) || StringUtils.isBlank(request.getMethodName())) {
            throw new MSException(GrpcResultCode.GRPC_METHOD_NOT_FOUND);
        }
        long timeoutMs = resolveTimeout(request.getTimeout());
        Descriptors.FileDescriptor fileDescriptor = grpcProtoService.resolveFileDescriptor(
                request.getDescriptorContent(), request.getProtoContent(), "upload.proto");
        boolean includeDefaultValues = request.getIncludeDefaultValues() == null
                || Boolean.TRUE.equals(request.getIncludeDefaultValues());
        GrpcChannelFactory.ChannelOptions channelOptions = new GrpcChannelFactory.ChannelOptions(
                Boolean.TRUE.equals(request.getUseTls()),
                request.getVerifyServerCertificate() == null || Boolean.TRUE.equals(request.getVerifyServerCertificate()),
                request.getServerNameOverride(),
                request.getAuthority(),
                request.getMaxResponseSizeMb());
        ManagedChannel channel = null;
        try {
            channel = GrpcChannelFactory.createChannel(request.getServerAddress(), channelOptions);
            Metadata metadata = GrpcMetadataBuilder.buildMetadata(request.getMetadata());
            io.grpc.Channel callChannel = attachMetadata(channel, metadata);
            return invokeUnary(callChannel, fileDescriptor, request.getServiceName(),
                    request.getMethodName(), request.getRequestJson(), timeoutMs, includeDefaultValues);
        } finally {
            GrpcChannelFactory.shutdownChannel(channel);
        }
    }

    public DynamicMessage requestJsonToDynamicMessage(String requestJson, Descriptors.Descriptor inputDescriptor) {
        try {
            return GrpcJsonConverter.requestJsonToDynamicMessage(requestJson, inputDescriptor);
        } catch (InvalidProtocolBufferException exception) {
            throw new MSException(GrpcResultCode.GRPC_JSON_CONVERT_FAILED, exception.getMessage());
        }
    }

    public String dynamicMessageToResponseJson(DynamicMessage response) {
        return dynamicMessageToResponseJson(response, true);
    }

    public String dynamicMessageToResponseJson(DynamicMessage response, boolean includeDefaultValues) {
        try {
            return GrpcJsonConverter.dynamicMessageToResponseJson(response, includeDefaultValues);
        } catch (InvalidProtocolBufferException exception) {
            throw new MSException(GrpcResultCode.GRPC_JSON_CONVERT_FAILED, exception.getMessage());
        }
    }

    public GrpcInvokeResponse invokeUnary(
            io.grpc.Channel channel,
            Descriptors.FileDescriptor fileDescriptor,
            String serviceName,
            String methodName,
            String requestJson,
            long timeoutMs
    ) {
        return invokeUnary(channel, fileDescriptor, serviceName, methodName, requestJson, timeoutMs, true);
    }

    public GrpcInvokeResponse invokeUnary(
            io.grpc.Channel channel,
            Descriptors.FileDescriptor fileDescriptor,
            String serviceName,
            String methodName,
            String requestJson,
            long timeoutMs,
            boolean includeDefaultValues
    ) {
        long start = System.currentTimeMillis();
        try {
            Descriptors.ServiceDescriptor serviceDescriptor = resolveService(fileDescriptor, serviceName);
            if (serviceDescriptor == null) {
                return buildErrorResponse(start, GrpcResultCode.GRPC_SERVICE_NOT_FOUND, null, null);
            }
            Descriptors.MethodDescriptor protoMethodDescriptor = serviceDescriptor.findMethodByName(methodName);
            if (protoMethodDescriptor == null) {
                return buildErrorResponse(start, GrpcResultCode.GRPC_METHOD_NOT_FOUND, null, null);
            }
            if (protoMethodDescriptor.isClientStreaming() || protoMethodDescriptor.isServerStreaming()) {
                return buildErrorResponse(start, GrpcResultCode.GRPC_STREAM_NOT_SUPPORTED, null, null);
            }

            Descriptors.Descriptor inputDescriptor = protoMethodDescriptor.getInputType();
            Descriptors.Descriptor outputDescriptor = protoMethodDescriptor.getOutputType();
            DynamicMessage request = requestJsonToDynamicMessage(
                    StringUtils.defaultIfBlank(requestJson, "{}"), inputDescriptor);

            io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod =
                    io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(serviceDescriptor.getFullName() + "/" + protoMethodDescriptor.getName())
                            .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(inputDescriptor)))
                            .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(outputDescriptor)))
                            .build();

            CallOptions callOptions = CallOptions.DEFAULT.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
            DynamicMessage response = ClientCalls.blockingUnaryCall(channel, grpcMethod, callOptions, request);
            String responseJson = dynamicMessageToResponseJson(response, includeDefaultValues);
            return buildSuccessResponse(start, responseJson);
        } catch (MSException exception) {
            GrpcResultCode resultCode = exception.getErrorCode() instanceof GrpcResultCode grpcResultCode
                    ? grpcResultCode : GrpcResultCode.GRPC_JSON_CONVERT_FAILED;
            return buildErrorResponse(start, resultCode, exception.getMessage(), null);
        } catch (StatusRuntimeException exception) {
            Status status = exception.getStatus();
            GrpcResultCode resultCode = GrpcStatusMapper.mapStatusCode(status.getCode());
            String message = status.getDescription() != null ? status.getDescription() : exception.getMessage();
            return buildErrorResponse(start, resultCode, message, status.getCode());
        } catch (Exception exception) {
            return buildErrorResponse(start, GrpcResultCode.GRPC_CALL_FAILED, exception.getMessage(), null);
        }
    }

    private io.grpc.Channel attachMetadata(ManagedChannel channel, Metadata metadata) {
        if (metadata.keys().isEmpty()) {
            return channel;
        }
        ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
        return ClientInterceptors.intercept(channel, interceptor);
    }

    private Descriptors.ServiceDescriptor resolveService(Descriptors.FileDescriptor fileDescriptor, String serviceName) {
        Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName(serviceName);
        if (serviceDescriptor != null) {
            return serviceDescriptor;
        }
        int lastDot = serviceName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fileDescriptor.findServiceByName(serviceName.substring(lastDot + 1));
        }
        return null;
    }

    private long resolveTimeout(Integer timeout) {
        if (timeout == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        if (timeout < MIN_TIMEOUT_MS || timeout > MAX_TIMEOUT_MS) {
            throw new MSException(GrpcResultCode.GRPC_CALL_FAILED, "timeout must be between 1000 and 60000 ms");
        }
        return timeout;
    }

    private GrpcInvokeResponse buildSuccessResponse(long start, String responseJson) {
        GrpcInvokeResponse response = new GrpcInvokeResponse();
        response.setSuccess(true);
        response.setGrpcStatus(Status.Code.OK.name());
        response.setGrpcCode(Status.Code.OK.value());
        response.setDuration(System.currentTimeMillis() - start);
        response.setResponseJson(responseJson);
        return response;
    }

    private GrpcInvokeResponse buildErrorResponse(long start, GrpcResultCode resultCode, String message, Status.Code statusCode) {
        GrpcInvokeResponse response = new GrpcInvokeResponse();
        response.setSuccess(false);
        response.setDuration(System.currentTimeMillis() - start);
        response.setErrorCode(resultCode.name());
        response.setErrorMessage(message != null ? message : resultCode.name());
        if (statusCode != null) {
            response.setGrpcStatus(statusCode.name());
            response.setGrpcCode(statusCode.value());
        }
        return response;
    }
}
