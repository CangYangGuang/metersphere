package io.metersphere.api.service.debug;

import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.grpc.dto.GrpcCaseDebugRequestDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.util.GrpcProtocolUtils;
import io.metersphere.api.utils.ApiDataUtils;
import io.metersphere.plugin.api.spi.AbstractMsTestElement;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.utils.ServiceUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * gRPC 调试页保存的 request blob 与 HTTP MsTestElement 分离，避免 polymorphicName 解析。
 */
public final class ApiDebugGrpcRequestSupport {

    private ApiDebugGrpcRequestSupport() {
    }

    public static byte[] serializeRequest(String protocol, Object request) {
        if (GrpcProtocolUtils.isGrpcProtocol(protocol)) {
            return serializeGrpcDebugRequest(request);
        }
        return serializeMsTestElementRequest(request);
    }

    public static Object deserializeRequest(String protocol, byte[] requestBytes) {
        if (requestBytes == null || requestBytes.length == 0) {
            return null;
        }
        if (GrpcProtocolUtils.isGrpcProtocol(protocol)) {
            return parseGrpcDebugRequest(requestBytes);
        }
        AbstractMsTestElement msTestElement = ApiDataUtils.parseObject(new String(requestBytes), AbstractMsTestElement.class);
        return msTestElement;
    }

    static GrpcCaseDebugRequestDTO parseGrpcDebugRequestObject(Object request) {
        if (request == null) {
            throw new MSException(GrpcResultCode.GRPC_CASE_CONFIG_REQUIRED);
        }
        if (request instanceof GrpcCaseDebugRequestDTO dto) {
            return dto;
        }
        return JSON.parseObject(JSON.toJSONString(request), GrpcCaseDebugRequestDTO.class);
    }

    private static byte[] serializeGrpcDebugRequest(Object request) {
        GrpcCaseDebugRequestDTO dto = parseGrpcDebugRequestObject(request);
        validateGrpcDebugRequest(dto);
        return JSON.toJSONString(dto).getBytes();
    }

    private static byte[] serializeMsTestElementRequest(Object request) {
        String requestStr = JSON.toJSONString(request);
        AbstractMsTestElement msTestElement = ApiDataUtils.parseObject(requestStr, AbstractMsTestElement.class);
        ServiceUtils.validateParam(msTestElement);
        return requestStr.getBytes();
    }

    private static GrpcCaseDebugRequestDTO parseGrpcDebugRequest(byte[] requestBytes) {
        GrpcCaseDebugRequestDTO dto = JSON.parseObject(new String(requestBytes), GrpcCaseDebugRequestDTO.class);
        validateGrpcDebugRequest(dto);
        return dto;
    }

    private static void validateGrpcDebugRequest(GrpcCaseDebugRequestDTO dto) {
        if (dto == null || dto.getGrpcCaseConfig() == null) {
            throw new MSException(GrpcResultCode.GRPC_CASE_CONFIG_REQUIRED);
        }
        ApiGrpcCaseConfigAddDTO config = dto.getGrpcCaseConfig();
        if (StringUtils.isBlank(config.getServiceName()) || StringUtils.isBlank(config.getMethodName())) {
            throw new MSException(GrpcResultCode.GRPC_METHOD_NOT_FOUND);
        }
        if (StringUtils.isBlank(config.getDescriptorContent())) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED);
        }
    }
}
