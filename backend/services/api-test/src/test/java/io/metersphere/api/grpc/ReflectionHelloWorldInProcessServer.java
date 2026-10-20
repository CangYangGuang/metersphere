package io.metersphere.api.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import io.metersphere.api.grpc.demo.GreeterGrpc;
import io.metersphere.api.grpc.demo.HelloReply;
import io.metersphere.api.grpc.demo.HelloRequest;

import java.io.IOException;

/**
 * In-process gRPC server with Server Reflection enabled for unit tests.
 */
public class ReflectionHelloWorldInProcessServer implements AutoCloseable {

    private final String serverName;
    private Server server;
    private ManagedChannel channel;

    public ReflectionHelloWorldInProcessServer() {
        this.serverName = InProcessServerBuilder.generateName();
    }

    public void start() throws IOException {
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new GreeterImpl())
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    }

    public ManagedChannel getChannel() {
        return channel;
    }

    public String getServerAddress() {
        return "inprocess://" + serverName;
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

        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            responseObserver.onNext(HelloReply.newBuilder()
                    .setMessage("Hello " + request.getName())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
