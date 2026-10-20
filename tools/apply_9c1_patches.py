#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"patch miss in {path}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")


def main() -> None:
    patch(
        "backend/framework/domain/src/main/java/io/metersphere/api/mapper/ApiGrpcDefinitionMapper.xml",
        "  </select>\n  <insert id=\"insertSelective\" parameterType=\"io.metersphere.api.domain.ApiGrpcDefinition\">",
        "  </select>\n  <select id=\"selectActiveByProtoId\" resultMap=\"BaseResultMap\">\n"
        "    select\n    <include refid=\"Base_Column_List\" />\n"
        "    from api_grpc_definition\n    where proto_id = #{protoId,jdbcType=VARCHAR}\n"
        "      and deleted = 0\n  </select>\n"
        "  <insert id=\"insertSelective\" parameterType=\"io.metersphere.api.domain.ApiGrpcDefinition\">",
    )
    patch(
        "backend/framework/domain/src/main/java/io/metersphere/api/mapper/ApiGrpcCaseConfigMapper.java",
        "    int deleteByApiCaseIds(@Param(\"apiCaseIds\") List<String> apiCaseIds);\n}",
        "    int deleteByApiCaseIds(@Param(\"apiCaseIds\") List<String> apiCaseIds);\n\n"
        "    List<ApiGrpcCaseConfig> selectActiveByProtoId(@Param(\"protoId\") String protoId);\n}",
    )
    patch(
        "backend/framework/domain/src/main/java/io/metersphere/api/mapper/ApiGrpcCaseConfigMapper.xml",
        "  </delete>\n</mapper>",
        "  </delete>\n\n  <select id=\"selectActiveByProtoId\" resultMap=\"BaseResultMap\">\n"
        "    select\n    <include refid=\"Base_Column_List\" />\n"
        "    from api_grpc_case_config\n    where proto_id = #{protoId,jdbcType=VARCHAR}\n"
        "      and deleted = 0\n  </select>\n</mapper>",
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/dto/definition/ApiGrpcDefinitionDTO.java",
        "import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;",
        "import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;\nimport io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;",
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/dto/definition/ApiGrpcDefinitionDTO.java",
        '    @Schema(description = "同文件名 proto 版本历史")\n    private List<GrpcProtoVersionDTO> protoVersionHistory;',
        '    @Schema(description = "同文件名 proto 版本历史")\n    private List<GrpcProtoVersionDTO> protoVersionHistory;\n\n'
        '    @Schema(description = "相对上一 proto 版本的变更影响提示")\n    private GrpcProtoChangeImpactDTO protoChangeImpact;',
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/dto/definition/ApiGrpcCaseConfigDTO.java",
        "import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;",
        "import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;\nimport io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;",
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/dto/definition/ApiGrpcCaseConfigDTO.java",
        '    @Schema(description = "用例 proto 版本是否落后于接口定义当前版本")\n    private Boolean protoVersionOutdated;',
        '    @Schema(description = "用例 proto 版本是否落后于接口定义当前版本")\n    private Boolean protoVersionOutdated;\n\n'
        '    @Schema(description = "用例 proto 相对接口当前 proto 的变更影响提示")\n    private GrpcProtoChangeImpactDTO protoChangeImpact;',
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcDefinitionPersistenceService.java",
        "import io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;",
        "import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;\nimport io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;",
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcDefinitionPersistenceService.java",
        "    @Resource\n    private GrpcProtoVersionService grpcProtoVersionService;",
        "    @Resource\n    private GrpcProtoVersionService grpcProtoVersionService;\n    @Resource\n"
        "    private GrpcProtoChangeImpactService grpcProtoChangeImpactService;",
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcDefinitionPersistenceService.java",
        "            dto.setProtoVersionHistory(grpcProtoVersionService.listVersionHistory(proto));\n        }",
        "            dto.setProtoVersionHistory(grpcProtoVersionService.listVersionHistory(proto));\n"
        "            GrpcProtoChangeImpactDTO impact = grpcProtoChangeImpactService.analyzeForDefinition(grpcDefinition, proto);\n"
        "            dto.setProtoChangeImpact(impact);\n        }",
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcCasePersistenceService.java",
        "import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;",
        "import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;\nimport io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;",
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcCasePersistenceService.java",
        "    @Resource\n    private GrpcProtoVersionService grpcProtoVersionService;",
        "    @Resource\n    private GrpcProtoVersionService grpcProtoVersionService;\n    @Resource\n"
        "    private GrpcProtoChangeImpactService grpcProtoChangeImpactService;",
    )
    patch(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcCasePersistenceService.java",
        "        if (StringUtils.isNotBlank(activeProtoId)) {\n"
        "            dto.setProtoVersionOutdated(!StringUtils.equals(caseConfig.getProtoId(), activeProtoId));\n        }",
        "        if (StringUtils.isNotBlank(activeProtoId)) {\n"
        "            dto.setProtoVersionOutdated(!StringUtils.equals(caseConfig.getProtoId(), activeProtoId));\n"
        "            GrpcProtoChangeImpactDTO impact = grpcProtoChangeImpactService.analyzeForCase(caseConfig, activeProtoId);\n"
        "            dto.setProtoChangeImpact(impact);\n        }",
    )
    print("backend patches ok")


if __name__ == "__main__":
    main()
