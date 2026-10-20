package io.metersphere.api.controller.scenario;

import io.metersphere.api.grpc.dto.GrpcScenarioAiCreateRequest;
import io.metersphere.api.grpc.dto.GrpcScenarioAiCreateResultDTO;
import io.metersphere.api.grpc.dto.GrpcScenarioAiDraftDTO;
import io.metersphere.api.grpc.dto.GrpcScenarioAiGenerateRequest;
import io.metersphere.api.grpc.service.GrpcScenarioAiService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/scenario/ai/grpc")
@Tag(name = "接口测试-gRPC 场景-AI 智能生成")
public class GrpcScenarioAiController {

    @Resource
    private GrpcScenarioAiService grpcScenarioAiService;

    @PostMapping(value = "/generate", consumes = "multipart/form-data")
    @Operation(summary = "根据文本或文件生成可编辑的 gRPC 自动化场景草稿")
    @RequiresPermissions(PermissionConstants.PROJECT_API_SCENARIO_ADD)
    @CheckOwner(resourceId = "#request.projectId", resourceType = "project")
    public GrpcScenarioAiDraftDTO generate(
            @Validated @RequestPart("request") GrpcScenarioAiGenerateRequest request,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        return grpcScenarioAiService.generate(request, files, SessionUtils.getUserId());
    }

    @PostMapping("/create")
    @Operation(summary = "确认创建或更新 AI gRPC 用例和自动化场景")
    @RequiresPermissions(PermissionConstants.PROJECT_API_SCENARIO_ADD)
    @CheckOwner(resourceId = "#request.projectId", resourceType = "project")
    public GrpcScenarioAiCreateResultDTO create(@Validated @RequestBody GrpcScenarioAiCreateRequest request) {
        return grpcScenarioAiService.create(request, SessionUtils.getUserId());
    }
}
