package io.metersphere.api.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;

import java.io.IOException;

/**
 * Reflection-enabled in-process server without application services.
 */
class EmptyReflectionInProcessServer implements AutoCloseable {

    private final String serverName;
    private Server server;
    private ManagedChannel channel;

    EmptyReflectionInProcessServer() {
        this.serverName = InProcessServerBuilder.generateName();
    }

    ManagedChannel start() throws IOException {
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
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
}
