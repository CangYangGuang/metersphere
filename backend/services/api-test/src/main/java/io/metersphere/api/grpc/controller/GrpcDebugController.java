package io.metersphere.api.grpc.controller;

import io.metersphere.api.grpc.dto.GrpcInvokeRequest;
import io.metersphere.api.grpc.dto.GrpcInvokeResponse;
import io.metersphere.api.grpc.dto.GrpcParseProtoRequest;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcReflectionDescribeRequest;
import io.metersphere.api.grpc.service.GrpcInvokeService;
import io.metersphere.api.grpc.service.GrpcProtoService;
import io.metersphere.api.grpc.service.GrpcReflectionService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grpc/debug")
@Tag(name = "gRPC 调试")
public class GrpcDebugController {

    @Resource
    private GrpcProtoService grpcProtoService;
    @Resource
    private GrpcInvokeService grpcInvokeService;
    @Resource
    private GrpcReflectionService grpcReflectionService;

    @PostMapping("/parse")
    @Operation(summary = "解析 proto 并返回 Service/Method")
    @RequiresPermissions(PermissionConstants.PROJECT_API_DEBUG_READ)
    public GrpcParseProtoResponse parse(@Validated @RequestBody GrpcParseProtoRequest request) {
        return grpcProtoService.parseProto(request);
    }

    @PostMapping("/send")
    @Operation(summary = "发送 gRPC Unary 调试请求")
    @RequiresPermissions(PermissionConstants.PROJECT_API_DEBUG_EXECUTE)
    public GrpcInvokeResponse send(@Validated @RequestBody GrpcInvokeRequest request) {
        return grpcInvokeService.invoke(request);
    }

    @PostMapping("/reflection/describe")
    @Operation(summary = "通过 gRPC Reflection 发现 Service/Method")
    @RequiresPermissions(PermissionConstants.PROJECT_API_DEBUG_READ)
    public GrpcParseProtoResponse describeViaReflection(@Validated @RequestBody GrpcReflectionDescribeRequest request) {
        return grpcReflectionService.describe(request);
    }
}
