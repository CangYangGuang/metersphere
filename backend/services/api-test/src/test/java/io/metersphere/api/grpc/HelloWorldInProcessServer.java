package io.metersphere.api.grpc;

import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.metersphere.api.grpc.demo.GreeterGrpc;
import io.metersphere.api.grpc.demo.HelloReply;
import io.metersphere.api.grpc.demo.HelloRequest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class HelloWorldInProcessServer implements AutoCloseable {

    public static final Metadata.Key<String> TEST_METADATA_KEY =
            Metadata.Key.of("x-test-key", Metadata.ASCII_STRING_MARSHALLER);

    private final String serverName;
    private final long responseDelayMs;
    private final AtomicReference<String> lastMetadataValue = new AtomicReference<>();
    private Server server;
    private ManagedChannel channel;

    public HelloWorldInProcessServer() {
        this(0L);
    }

    public HelloWorldInProcessServer(long responseDelayMs) {
        this.serverName = InProcessServerBuilder.generateName();
        this.responseDelayMs = responseDelayMs;
    }

    public void start() throws IOException {
        ServerInterceptor metadataInterceptor = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next
            ) {
                lastMetadataValue.set(headers.get(TEST_METADATA_KEY));
                return next.startCall(call, headers);
            }
        };
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .intercept(metadataInterceptor)
                .addService(new GreeterImpl(responseDelayMs))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    }

    public ManagedChannel getChannel() {
        return channel;
    }

    public String getLastMetadataValue() {
        return lastMetadataValue.get();
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    private static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

        private final long responseDelayMs;

        private GreeterImpl(long responseDelayMs) {
            this.responseDelayMs = responseDelayMs;
        }

        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            if (responseDelayMs > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(responseDelayMs);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    responseObserver.onError(exception);
                    return;
                }
            }
            responseObserver.onNext(HelloReply.newBuilder()
                    .setMessage("Hello " + request.getName())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
