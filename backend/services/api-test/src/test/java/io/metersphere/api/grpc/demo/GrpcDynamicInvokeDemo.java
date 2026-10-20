package io.metersphere.api.grpc.demo;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;

import java.util.concurrent.TimeUnit;

public class GrpcDynamicInvokeDemo {

    public record InvokeResult(
            boolean success,
            String responseJson,
            String errorMessage,
            long durationMs,
            Status.Code statusCode
    ) {
    }

    public InvokeResult invokeUnary(
            ManagedChannel channel,
            Descriptors.FileDescriptor fileDescriptor,
            String serviceName,
            String methodName,
            String requestJson,
            long timeoutMs
    ) {
        long start = System.currentTimeMillis();
        try {
            Descriptors.ServiceDescriptor serviceDescriptor = resolveService(fileDescriptor, serviceName);
            if (serviceDescriptor == null) {
                return fail(start, "Service not found: " + serviceName, null);
            }
            Descriptors.MethodDescriptor protoMethodDescriptor = serviceDescriptor.findMethodByName(methodName);
            if (protoMethodDescriptor == null) {
                return fail(start, "Method not found: " + methodName, null);
            }
            if (protoMethodDescriptor.isClientStreaming() || protoMethodDescriptor.isServerStreaming()) {
                return fail(start, "Only UNARY method is supported", null);
            }

            Descriptors.Descriptor inputDescriptor = protoMethodDescriptor.getInputType();
            Descriptors.Descriptor outputDescriptor = protoMethodDescriptor.getOutputType();

            DynamicMessage.Builder requestBuilder = DynamicMessage.newBuilder(inputDescriptor);
            JsonFormat.parser().ignoringUnknownFields().merge(requestJson, requestBuilder);
            DynamicMessage request = requestBuilder.build();

            io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod =
                    io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(serviceDescriptor.getFullName() + "/" + protoMethodDescriptor.getName())
                            .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(inputDescriptor)))
                            .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(outputDescriptor)))
                            .build();

            CallOptions callOptions = CallOptions.DEFAULT.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
            DynamicMessage response = ClientCalls.blockingUnaryCall(channel, grpcMethod, callOptions, request);
            String responseJson = JsonFormat.printer().includingDefaultValueFields().print(response);
            return new InvokeResult(true, responseJson, null, elapsed(start), Status.Code.OK);
        } catch (StatusRuntimeException exception) {
            Status status = exception.getStatus();
            String message = status.getDescription() != null ? status.getDescription() : exception.getMessage();
            return new InvokeResult(false, null, message, elapsed(start), status.getCode());
        } catch (InvalidProtocolBufferException exception) {
            return fail(start, "Invalid request JSON: " + exception.getMessage(), null);
        } catch (Exception exception) {
            return fail(start, exception.getMessage(), null);
        }
    }

    private static Descriptors.ServiceDescriptor resolveService(
            Descriptors.FileDescriptor fileDescriptor,
            String serviceName
    ) {
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

    private static InvokeResult fail(long start, String message, Status.Code statusCode) {
        return new InvokeResult(false, null, message, elapsed(start), statusCode);
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
