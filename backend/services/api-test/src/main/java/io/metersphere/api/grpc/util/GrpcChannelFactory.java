package io.metersphere.api.grpc.util;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.net.ssl.SSLException;
import java.util.concurrent.TimeUnit;

public final class GrpcChannelFactory {

    private GrpcChannelFactory() {
    }

    public record ParsedAddress(String host, int port) {
    }

    /**
     * gRPC channel 构建设置，来源于用例 / 调试的 Settings 配置。
     */
    public record ChannelOptions(boolean useTls,
                                 boolean verifyServerCertificate,
                                 String serverNameOverride,
                                 String authority,
                                 Integer maxResponseSizeMb) {
        public static ChannelOptions plaintext() {
            return new ChannelOptions(false, true, null, null, null);
        }
    }

    public static ParsedAddress parseServerAddress(String serverAddress) {
        if (StringUtils.isBlank(serverAddress)) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID);
        }
        String trimmed = serverAddress.trim();
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon <= 0 || lastColon >= trimmed.length() - 1) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID);
        }
        String host = trimmed.substring(0, lastColon);
        String portText = trimmed.substring(lastColon + 1);
        if (!NumberUtils.isCreatable(portText)) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID);
        }
        int port = NumberUtils.createInteger(portText);
        if (port <= 0 || port > 65535) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID);
        }
        return new ParsedAddress(host, port);
    }

    public static ManagedChannel createPlaintextChannel(String serverAddress) {
        return createChannel(serverAddress, ChannelOptions.plaintext());
    }

    public static ManagedChannel createChannel(String serverAddress, boolean useTls, String authority) {
        return createChannel(serverAddress, new ChannelOptions(useTls, true, null, authority, null));
    }

    public static ManagedChannel createChannel(String serverAddress, ChannelOptions options) {
        ParsedAddress parsedAddress = parseServerAddress(serverAddress);
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(parsedAddress.host(), parsedAddress.port());
        if (options == null) {
            options = ChannelOptions.plaintext();
        }
        if (options.useTls()) {
            if (options.verifyServerCertificate()) {
                builder.useTransportSecurity();
            } else {
                builder.sslContext(buildInsecureSslContext());
            }
        } else {
            builder.usePlaintext();
        }
        String authorityOverride = StringUtils.defaultIfBlank(options.serverNameOverride(), options.authority());
        if (StringUtils.isNotBlank(authorityOverride)) {
            builder.overrideAuthority(authorityOverride);
        }
        Integer maxMb = options.maxResponseSizeMb();
        if (maxMb != null && maxMb > 0) {
            builder.maxInboundMessageSize(maxMb * 1024 * 1024);
        }
        return builder.build();
    }

    private static SslContext buildInsecureSslContext() {
        try {
            return GrpcSslContexts.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } catch (SSLException exception) {
            throw new MSException(GrpcResultCode.GRPC_CALL_FAILED,
                    "TLS 证书跳过校验能力不可用: " + exception.getMessage());
        }
    }

    public static void shutdownChannel(ManagedChannel channel) {
        if (channel == null) {
            return;
        }
        channel.shutdown();
        try {
            channel.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    public static boolean shouldSkipCertVerify(Boolean verifyServerCertificate) {
        return BooleanUtils.isFalse(verifyServerCertificate);
    }
}
