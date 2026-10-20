package io.metersphere.api.grpc.report;

import io.metersphere.api.grpc.dto.GrpcCaseReportContentDTO;
import io.metersphere.api.utils.ApiDataUtils;
import io.metersphere.sdk.dto.api.result.RequestResult;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 解析 api_report_detail.content：HTTP 报告走 Jackson，gRPC 报告走 Fastjson（与持久化一致）。
 */
public final class ApiReportDetailContentParser {

    private ApiReportDetailContentParser() {
    }

    public static RequestResult parse(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        String json = new String(content, StandardCharsets.UTF_8);
        if (isGrpcReportContent(json)) {
            return JSON.parseObject(json, GrpcCaseReportContentDTO.class);
        }
        return ApiDataUtils.parseObject(json, RequestResult.class);
    }

    static boolean isGrpcReportContent(String json) {
        if (StringUtils.isBlank(json)) {
            return false;
        }
        try {
            Map<?, ?> object = JSON.parseObject(json, Map.class);
            if (object == null || object.isEmpty()) {
                return false;
            }
            Object protocol = object.get("protocol");
            if (protocol != null && "GRPC".equalsIgnoreCase(String.valueOf(protocol))) {
                return true;
            }
            Object method = object.get("method");
            if (method != null && "GRPC".equalsIgnoreCase(String.valueOf(method))) {
                return true;
            }
            return object.containsKey("grpcStatus")
                    || object.containsKey("serviceName")
                    || object.containsKey("grpcCode")
                    || object.containsKey("responseJson");
        } catch (Exception ignored) {
            return false;
        }
    }
}
