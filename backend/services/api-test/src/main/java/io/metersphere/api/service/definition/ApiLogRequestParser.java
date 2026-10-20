package io.metersphere.api.service.definition;

import io.metersphere.api.grpc.util.GrpcProtocolUtils;
import io.metersphere.api.utils.ApiDataUtils;
import io.metersphere.plugin.api.spi.AbstractMsTestElement;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

final class ApiLogRequestParser {

    private ApiLogRequestParser() {
    }

    static AbstractMsTestElement parseMsTestElementForLog(String protocol, byte[] requestBytes) {
        if (requestBytes == null || requestBytes.length == 0 || GrpcProtocolUtils.isGrpcProtocol(protocol)) {
            return null;
        }
        String content = new String(requestBytes, StandardCharsets.UTF_8);
        if (!hasPolymorphicDiscriminator(content)) {
            return null;
        }
        return ApiDataUtils.parseObject(content, AbstractMsTestElement.class);
    }

    private static boolean hasPolymorphicDiscriminator(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        try {
            Map<?, ?> map = JSON.parseObject(content, Map.class);
            Object polymorphicName = map == null ? null : map.get("polymorphicName");
            return polymorphicName != null && StringUtils.isNotBlank(String.valueOf(polymorphicName));
        } catch (Exception e) {
            return false;
        }
    }
}
