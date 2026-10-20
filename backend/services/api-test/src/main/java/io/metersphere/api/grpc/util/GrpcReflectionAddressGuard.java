package io.metersphere.api.grpc.util;

import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.dto.GrpcReflectionDescribeRequest;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Minimal SSRF guard for Reflection describe (and reusable target validation).
 */
public final class GrpcReflectionAddressGuard {

    public static final int MIN_TIMEOUT_MS = 1000;
    public static final int MAX_TIMEOUT_MS = 60000;
    public static final int MAX_METADATA_ITEMS = 20;
    public static final int MAX_METADATA_KEY_LENGTH = 256;
    public static final int MAX_METADATA_VALUE_LENGTH = 8192;
    public static final int MAX_METADATA_TOTAL_BYTES = 16384;

    private static final String ALLOW_PRIVATE_NETWORKS_PROPERTY =
            "metersphere.grpc.reflection.allow-private-networks";
    private static final String ALLOW_PRIVATE_NETWORKS_ENV =
            "MS_GRPC_REFLECTION_ALLOW_PRIVATE_NETWORKS";

    private static volatile boolean allowPrivateNetworks = resolveAllowPrivateNetworks();

    private GrpcReflectionAddressGuard() {
    }

    private static boolean resolveAllowPrivateNetworks() {
        String configured = System.getProperty(ALLOW_PRIVATE_NETWORKS_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            configured = System.getenv(ALLOW_PRIVATE_NETWORKS_ENV);
        }
        return Boolean.parseBoolean(StringUtils.trimToEmpty(configured));
    }

    public static void setAllowPrivateNetworksForTests(boolean allow) {
        allowPrivateNetworks = allow;
    }

    public static boolean isAllowPrivateNetworks() {
        return allowPrivateNetworks;
    }

    public static void validateDescribeRequest(GrpcReflectionDescribeRequest request) {
        if (request == null || StringUtils.isBlank(request.getServerAddress())) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID, "server address is required");
        }
        validateServerAddress(request.getServerAddress().trim());
        validateTimeout(request.getTimeout());
        validateMetadata(request.getMetadata());
    }

    public static void validateServerAddress(String serverAddress) {
        if (StringUtils.isBlank(serverAddress)) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID, "server address is required");
        }
        GrpcChannelFactory.ParsedAddress parsed = GrpcChannelFactory.parseServerAddress(serverAddress);
        rejectBlockedHost(parsed.host());
    }

    public static void validateTimeout(Integer timeout) {
        if (timeout == null) {
            return;
        }
        if (timeout < MIN_TIMEOUT_MS || timeout > MAX_TIMEOUT_MS) {
            throw new MSException(GrpcResultCode.GRPC_CALL_FAILED,
                    "timeout must be between " + MIN_TIMEOUT_MS + " and " + MAX_TIMEOUT_MS + " ms");
        }
    }

    public static void validateMetadata(List<GrpcMetadataItemDTO> metadataItems) {
        if (CollectionUtils.isEmpty(metadataItems)) {
            return;
        }
        int itemCount = 0;
        int totalBytes = 0;
        for (GrpcMetadataItemDTO item : metadataItems) {
            if (item == null || Boolean.FALSE.equals(item.getEnabled())) {
                continue;
            }
            if (StringUtils.isBlank(item.getKey())) {
                continue;
            }
            itemCount++;
            if (itemCount > MAX_METADATA_ITEMS) {
                throw new MSException(GrpcResultCode.GRPC_INVALID_ARGUMENT,
                        "metadata item count exceeds limit of " + MAX_METADATA_ITEMS);
            }
            String key = item.getKey().trim();
            if (key.length() > MAX_METADATA_KEY_LENGTH) {
                throw new MSException(GrpcResultCode.GRPC_INVALID_ARGUMENT,
                        "metadata key exceeds max length of " + MAX_METADATA_KEY_LENGTH);
            }
            String value = StringUtils.defaultString(item.getValue());
            if (value.length() > MAX_METADATA_VALUE_LENGTH) {
                throw new MSException(GrpcResultCode.GRPC_INVALID_ARGUMENT,
                        "metadata value exceeds max length of " + MAX_METADATA_VALUE_LENGTH);
            }
            totalBytes += key.length() + value.length();
            if (totalBytes > MAX_METADATA_TOTAL_BYTES) {
                throw new MSException(GrpcResultCode.GRPC_INVALID_ARGUMENT,
                        "metadata total size exceeds limit of " + MAX_METADATA_TOTAL_BYTES + " bytes");
            }
        }
    }

    private static void rejectBlockedHost(String host) {
        String normalizedHost = normalizeHost(host);
        if ("localhost".equals(normalizedHost)) {
            throw blockedAddress("localhost is not allowed");
        }
        if ("::1".equals(normalizedHost) || "0:0:0:0:0:0:0:1".equals(normalizedHost)) {
            throw blockedAddress("loopback address is not allowed");
        }
        if (normalizedHost.startsWith("fe80:") || normalizedHost.startsWith("fe80::")) {
            throw blockedAddress("link-local address is not allowed");
        }
        if ("169.254.169.254".equals(normalizedHost)) {
            throw blockedAddress("metadata service address is not allowed");
        }
        if (isIpv4(normalizedHost)) {
            rejectIpv4(normalizedHost);
            return;
        }
        try {
            InetAddress[] resolved = InetAddress.getAllByName(host);
            for (InetAddress address : resolved) {
                rejectResolvedAddress(address);
            }
        } catch (UnknownHostException exception) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID,
                    "unable to resolve server address: " + host);
        }
    }

    private static void rejectIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID, "invalid server address: " + host);
        }
        for (String part : parts) {
            if (!NumberUtils.isCreatable(part)) {
                throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID, "invalid server address: " + host);
            }
            int value = NumberUtils.createInteger(part);
            if (value < 0 || value > 255) {
                throw new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID, "invalid server address: " + host);
            }
        }
        int first = NumberUtils.createInteger(parts[0]);
        int second = NumberUtils.createInteger(parts[1]);
        if (first == 127) {
            throw blockedAddress("loopback address is not allowed");
        }
        if (first == 0) {
            throw blockedAddress("unspecified address is not allowed");
        }
        if (first == 169 && second == 254) {
            throw blockedAddress("link-local address is not allowed");
        }
        if (!allowPrivateNetworks) {
            if (first == 10 || (first == 172 && second >= 16 && second <= 31) || (first == 192 && second == 168)) {
                throw blockedAddress("private network address is not allowed");
            }
        }
    }

    private static void rejectResolvedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
            throw blockedAddress("blocked server address: " + address.getHostAddress());
        }
        if ("169.254.169.254".equals(address.getHostAddress())) {
            throw blockedAddress("metadata service address is not allowed");
        }
        if (!allowPrivateNetworks && address.isSiteLocalAddress()) {
            throw blockedAddress("private network address is not allowed");
        }
    }

    private static MSException blockedAddress(String message) {
        return new MSException(GrpcResultCode.GRPC_ADDRESS_INVALID, message);
    }

    private static String normalizeHost(String host) {
        String trimmed = StringUtils.trimToEmpty(host).toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isIpv4(String host) {
        return host.chars().filter(ch -> ch == '.').count() == 3;
    }
}
