package io.metersphere.api.grpc.demo;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HelloWorldInProcessServer implements AutoCloseable {

    private final String serverName;
    private final long responseDelayMs;
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
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new GreeterImpl(responseDelayMs))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    }

    public ManagedChannel getChannel() {
        return channel;
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
