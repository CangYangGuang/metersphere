package io.metersphere.api.grpc.util;

import io.metersphere.api.constants.ApiConstants;
import org.apache.commons.lang3.StringUtils;

public final class GrpcProtocolUtils {

    private GrpcProtocolUtils() {
    }

    public static boolean isGrpcProtocol(String protocol) {
        return StringUtils.equalsIgnoreCase(protocol, ApiConstants.GRPC_PROTOCOL);
    }

    public static boolean isHttpProtocol(String protocol) {
        return StringUtils.isBlank(protocol) || StringUtils.equalsIgnoreCase(protocol, ApiConstants.HTTP_PROTOCOL);
    }
}
