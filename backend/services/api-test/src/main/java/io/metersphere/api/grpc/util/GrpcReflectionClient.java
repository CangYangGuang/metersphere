package io.metersphere.api.grpc.util;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal synchronous client for gRPC Server Reflection v1alpha.
 */
public final class GrpcReflectionClient {

    private GrpcReflectionClient() {
    }

    public static List<String> listServices(Channel channel, long timeoutMs) throws Exception {
        ReflectionSession session = new ReflectionSession(channel, timeoutMs);
        try {
            session.start();
            session.send(ServerReflectionRequest.newBuilder().setListServices("").build());
            List<String> services = new ArrayList<>();
            while (session.hasNext()) {
                ServerReflectionResponse response = session.next();
                if (response.hasListServicesResponse()) {
                    response.getListServicesResponse().getServiceList().forEach(service -> {
                        if (StringUtils.isNotBlank(service.getName())
                                && !service.getName().startsWith("grpc.reflection")) {
                            services.add(service.getName());
                        }
                    });
                    break;
                }
                handleErrorResponse(response);
            }
            session.throwIfFailed();
            return services;
        } finally {
            session.close();
        }
    }

    public static Set<Descriptors.FileDescriptor> retrieveDescriptors(Channel channel,
                                                                      List<String> serviceNames,
                                                                      long timeoutMs) throws Exception {
        ReflectionSession session = new ReflectionSession(channel, timeoutMs);
        try {
            session.start();
            Map<String, DescriptorProtos.FileDescriptorProto> protoMap = new LinkedHashMap<>();
            for (String serviceName : serviceNames) {
                session.send(ServerReflectionRequest.newBuilder()
                        .setFileContainingSymbol(serviceName)
                        .build());
                while (session.hasNext()) {
                    ServerReflectionResponse response = session.next();
                    if (response.hasFileDescriptorResponse()) {
                        for (com.google.protobuf.ByteString fileDescriptorBytes
                                : response.getFileDescriptorResponse().getFileDescriptorProtoList()) {
                            DescriptorProtos.FileDescriptorProto proto =
                                    DescriptorProtos.FileDescriptorProto.parseFrom(fileDescriptorBytes);
                            protoMap.put(proto.getName(), proto);
                        }
                        break;
                    }
                    handleErrorResponse(response);
                }
            }
            DescriptorProtos.FileDescriptorSet.Builder builder = DescriptorProtos.FileDescriptorSet.newBuilder();
            protoMap.values().forEach(builder::addFile);
            session.throwIfFailed();
            Map<String, Descriptors.FileDescriptor> built = GrpcProtoCompiler.buildAllFileDescriptors(builder.build());
            return new LinkedHashSet<>(built.values());
        } finally {
            session.close();
        }
    }

    private static void handleErrorResponse(ServerReflectionResponse response) {
        if (response.hasErrorResponse()) {
            throw new MSException(GrpcResultCode.GRPC_REFLECTION_FAILED,
                    response.getErrorResponse().getErrorMessage());
        }
    }

    private static final class ReflectionSession {
        private final Channel channel;
        private final long timeoutMs;
        private final List<ServerReflectionResponse> responses = new ArrayList<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private StreamObserver<ServerReflectionRequest> requestObserver;
        private int cursor;

        private ReflectionSession(Channel channel, long timeoutMs) {
            this.channel = channel;
            this.timeoutMs = timeoutMs;
        }

        private void start() {
            ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
            requestObserver = stub.serverReflectionInfo(new StreamObserver<>() {
                @Override
                public void onNext(ServerReflectionResponse value) {
                    synchronized (responses) {
                        responses.add(value);
                        responses.notifyAll();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    error.set(throwable);
                    completed.countDown();
                    synchronized (responses) {
                        responses.notifyAll();
                    }
                }

                @Override
                public void onCompleted() {
                    completed.countDown();
                    synchronized (responses) {
                        responses.notifyAll();
                    }
                }
            });
        }

        private void send(ServerReflectionRequest request) {
            if (error.get() != null) {
                throw toReflectionException(error.get());
            }
            requestObserver.onNext(request);
        }

        private boolean hasNext() throws TimeoutException {
            awaitAvailable();
            return cursor < responses.size() || error.get() == null && completed.getCount() > 0;
        }

        private ServerReflectionResponse next() throws TimeoutException {
            awaitAvailable();
            if (cursor >= responses.size()) {
                throw new TimeoutException("reflection response timeout");
            }
            return responses.get(cursor++);
        }

        private void awaitAvailable() throws TimeoutException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (responses) {
                while (cursor >= responses.size() && error.get() == null && completed.getCount() > 0) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        throw new TimeoutException("reflection response timeout");
                    }
                    try {
                        responses.wait(remaining);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new MSException(GrpcResultCode.GRPC_REFLECTION_FAILED, exception.getMessage());
                    }
                }
            }
        }

        private void close() {
            if (requestObserver != null) {
                requestObserver.onCompleted();
            }
        }

        private void throwIfFailed() {
            if (error.get() != null) {
                throw toReflectionException(error.get());
            }
        }
    }

    private static RuntimeException toReflectionException(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException;
        }
        return new MSException(GrpcResultCode.GRPC_REFLECTION_FAILED, throwable.getMessage());
    }
}
