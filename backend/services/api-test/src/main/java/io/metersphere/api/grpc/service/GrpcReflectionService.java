package io.metersphere.api.grpc.service;

import com.google.protobuf.Descriptors;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.stub.MetadataUtils;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcReflectionDescribeRequest;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.util.GrpcChannelFactory;
import io.metersphere.api.grpc.util.GrpcMetadataBuilder;
import io.metersphere.api.grpc.util.GrpcReflectionAddressGuard;
import io.metersphere.api.grpc.util.GrpcReflectionClient;
import io.metersphere.api.grpc.util.GrpcReflectionDescriptorConverter;
import io.metersphere.api.grpc.util.GrpcStatusMapper;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class GrpcReflectionService {

    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private final GrpcProtoService grpcProtoService;

    public GrpcReflectionService(GrpcProtoService grpcProtoService) {
        this.grpcProtoService = grpcProtoService;
    }

    public GrpcParseProtoResponse describe(GrpcReflectionDescribeRequest request) {
        GrpcReflectionAddressGuard.validateDescribeRequest(request);
        long timeoutMs = resolveTimeout(request.getTimeout());
        boolean useTls = Boolean.TRUE.equals(request.getUseTls());
        ManagedChannel channel = null;
        try {
            channel = GrpcChannelFactory.createChannel(request.getServerAddress(), useTls, request.getAuthority());
            Channel callChannel = attachMetadata(channel, GrpcMetadataBuilder.buildMetadata(request.getMetadata()));
            return describeOverChannel(callChannel, timeoutMs);
        } catch (MSException exception) {
            throw exception;
        } catch (TimeoutException exception) {
            throw new MSException(GrpcResultCode.GRPC_TIMEOUT, exception.getMessage());
        } catch (StatusRuntimeException exception) {
            throw mapStatusRuntimeException(exception);
        } catch (Exception exception) {
            throw new MSException(GrpcResultCode.GRPC_REFLECTION_FAILED, exception.getMessage());
        } finally {
            GrpcChannelFactory.shutdownChannel(channel);
        }
    }

    public GrpcParseProtoResponse describeOverChannel(Channel channel, long timeoutMs) throws Exception {
        List<String> serviceNames = GrpcReflectionClient.listServices(channel, timeoutMs);
        if (serviceNames.isEmpty()) {
            return buildEmptyResponse("No services discovered via reflection");
        }
        Set<Descriptors.FileDescriptor> fileDescriptors = GrpcReflectionClient.retrieveDescriptors(
                channel, serviceNames, timeoutMs);
        if (fileDescriptors.isEmpty()) {
            return buildEmptyResponse("No descriptors returned via reflection");
        }
        byte[] descriptorBytes = GrpcReflectionDescriptorConverter.toDescriptorSetBytes(fileDescriptors);
        return grpcProtoService.parseAllServicesFromDescriptorSet(descriptorBytes);
    }

    private GrpcParseProtoResponse buildEmptyResponse(String warning) {
        GrpcParseProtoResponse response = new GrpcParseProtoResponse();
        response.setServices(new ArrayList<>());
        response.setWarnings(List.of(warning));
        return response;
    }

    private Channel attachMetadata(ManagedChannel channel, Metadata metadata) {
        if (metadata.keys().isEmpty()) {
            return channel;
        }
        ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
        return ClientInterceptors.intercept(channel, interceptor);
    }

    private MSException mapStatusRuntimeException(StatusRuntimeException exception) {
        Status status = exception.getStatus();
        GrpcResultCode resultCode = GrpcStatusMapper.mapStatusCode(status.getCode());
        String message = StringUtils.defaultIfBlank(status.getDescription(), exception.getMessage());
        if (resultCode == GrpcResultCode.GRPC_CALL_FAILED) {
            return new MSException(GrpcResultCode.GRPC_REFLECTION_FAILED, message);
        }
        return new MSException(resultCode, message);
    }

    private long resolveTimeout(Integer timeout) {
        if (timeout == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        GrpcReflectionAddressGuard.validateTimeout(timeout);
        return timeout;
    }
}
