#!/usr/bin/env python3
"""Apply CASE-002 + REPORT-001 fixes without corrupting files on Windows."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch_file(rel_path: str, old: str, new: str, count: int = 1) -> None:
    path = ROOT / rel_path
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"PATCH MISS in {rel_path}: {old[:80]!r}")
    updated = text.replace(old, new, count)
    path.write_text(updated, encoding="utf-8", newline="\n")
    print(f"patched {rel_path}")


def main() -> None:
    # --- Backend domain ---
    patch_file(
        "backend/framework/domain/src/main/java/io/metersphere/api/domain/ApiGrpcCaseConfig.java",
        "    private String methodName;\n    private String descriptorContent;",
        "    private String methodName;\n    private String serverAddress;\n    private String descriptorContent;",
    )

    patch_file(
        "backend/framework/domain/src/main/java/io/metersphere/api/mapper/ApiGrpcCaseConfigMapper.xml",
        '    <result column="method_name" jdbcType="VARCHAR" property="methodName" />\n    <result column="descriptor_content"',
        '    <result column="method_name" jdbcType="VARCHAR" property="methodName" />\n    <result column="server_address" jdbcType="VARCHAR" property="serverAddress" />\n    <result column="descriptor_content"',
    )
    patch_file(
        "backend/framework/domain/src/main/java/io/metersphere/api/mapper/ApiGrpcCaseConfigMapper.xml",
        "    id, api_case_id, api_definition_id, proto_id, service_name, method_name, descriptor_content,",
        "    id, api_case_id, api_definition_id, proto_id, service_name, method_name, server_address, descriptor_content,",
    )
    patch_file(
        "backend/framework/domain/src/main/java/io/metersphere/api/mapper/ApiGrpcCaseConfigMapper.xml",
        "      <if test=\"methodName != null\">method_name,</if>\n      <if test=\"descriptorContent != null\">descriptor_content,</if>",
        "      <if test=\"methodName != null\">method_name,</if>\n      <if test=\"serverAddress != null\">server_address,</if>\n      <if test=\"descriptorContent != null\">descriptor_content,</if>",
    )
    patch_file(
        "backend/framework/domain/src/main/java/io/metersphere/api/mapper/ApiGrpcCaseConfigMapper.xml",
        "      <if test=\"methodName != null\">#{methodName,jdbcType=VARCHAR},</if>\n      <if test=\"descriptorContent != null\">#{descriptorContent,jdbcType=LONGVARCHAR},</if>",
        "      <if test=\"methodName != null\">#{methodName,jdbcType=VARCHAR},</if>\n      <if test=\"serverAddress != null\">#{serverAddress,jdbcType=VARCHAR},</if>\n      <if test=\"descriptorContent != null\">#{descriptorContent,jdbcType=LONGVARCHAR},</if>",
    )
    patch_file(
        "backend/framework/domain/src/main/java/io/metersphere/api/mapper/ApiGrpcCaseConfigMapper.xml",
        "      <if test=\"methodName != null\">method_name = #{methodName,jdbcType=VARCHAR},</if>\n      <if test=\"descriptorContent != null\">descriptor_content = #{descriptorContent,jdbcType=LONGVARCHAR},</if>",
        "      <if test=\"methodName != null\">method_name = #{methodName,jdbcType=VARCHAR},</if>\n      <if test=\"serverAddress != null\">server_address = #{serverAddress,jdbcType=VARCHAR},</if>\n      <if test=\"descriptorContent != null\">descriptor_content = #{descriptorContent,jdbcType=LONGVARCHAR},</if>",
    )

    patch_file(
        "backend/services/api-test/src/main/java/io/metersphere/api/dto/definition/ApiGrpcCaseConfigAddDTO.java",
        "    @Schema(description = \"Method 名称，默认继承接口定义\")\n    @Size(max = 255)\n    private String methodName;\n\n    @Schema(description = \"descriptor 快照，默认继承接口定义\")",
        "    @Schema(description = \"Method 名称，默认继承接口定义\")\n    @Size(max = 255)\n    private String methodName;\n\n    @Schema(description = \"gRPC 服务地址，例如 host:50051\")\n    @Size(max = 512)\n    private String serverAddress;\n\n    @Schema(description = \"descriptor 快照，默认继承接口定义\")",
    )

    patch_file(
        "backend/services/api-test/src/main/java/io/metersphere/api/dto/definition/ApiGrpcCaseConfigDTO.java",
        "    private String methodName;\n    private String descriptorContent;",
        "    private String methodName;\n\n    @Schema(description = \"gRPC 服务地址\")\n    private String serverAddress;\n\n    private String descriptorContent;",
    )

    patch_file(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcCasePersistenceService.java",
        "        target.setMethodName(StringUtils.defaultIfBlank(config.getMethodName(), definition.getMethodName()));\n        target.setDescriptorContent(StringUtils.defaultIfBlank(config.getDescriptorContent(),",
        "        target.setMethodName(StringUtils.defaultIfBlank(config.getMethodName(), definition.getMethodName()));\n        target.setServerAddress(StringUtils.trimToNull(config.getServerAddress()));\n        target.setDescriptorContent(StringUtils.defaultIfBlank(config.getDescriptorContent(),",
    )
    patch_file(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcCasePersistenceService.java",
        "        dto.setMethodName(caseConfig.getMethodName());\n        dto.setDescriptorContent(caseConfig.getDescriptorContent());",
        "        dto.setMethodName(caseConfig.getMethodName());\n        dto.setServerAddress(caseConfig.getServerAddress());\n        dto.setDescriptorContent(caseConfig.getDescriptorContent());",
    )

    patch_file(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcCaseRunService.java",
        "        if (StringUtils.isBlank(param.getServerAddress())) {\n            return GrpcCaseRunResultDTO.error(GrpcResultCode.GRPC_ADDRESS_INVALID,\n                    GrpcResultCode.GRPC_ADDRESS_INVALID.name());\n        }\n\n        ApiTestCase apiTestCase = apiTestCaseService.checkResourceExist(apiCaseId);",
        "        ApiTestCase apiTestCase = apiTestCaseService.checkResourceExist(apiCaseId);",
    )
    patch_file(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/GrpcCaseRunService.java",
        "        ApiGrpcCaseConfigDTO caseConfig = grpcCasePersistenceService.loadDtoByCaseId(apiCaseId);\n        if (caseConfig == null) {\n            return GrpcCaseRunResultDTO.error(GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND,\n                    GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND.name());\n        }\n\n        GrpcInvokeRequest invokeRequest = buildInvokeRequest(param, caseConfig);",
        "        ApiGrpcCaseConfigDTO caseConfig = grpcCasePersistenceService.loadDtoByCaseId(apiCaseId);\n        if (caseConfig == null) {\n            return GrpcCaseRunResultDTO.error(GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND,\n                    GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND.name());\n        }\n        if (StringUtils.isBlank(param.getServerAddress())) {\n            param.setServerAddress(caseConfig.getServerAddress());\n        }\n        if (StringUtils.isBlank(param.getServerAddress())) {\n            return GrpcCaseRunResultDTO.error(GrpcResultCode.GRPC_ADDRESS_INVALID,\n                    GrpcResultCode.GRPC_ADDRESS_INVALID.name());\n        }\n\n        GrpcInvokeRequest invokeRequest = buildInvokeRequest(param, caseConfig);",
    )

    patch_file(
        "backend/services/api-test/src/main/java/io/metersphere/api/grpc/service/ApiTestCaseGrpcRunService.java",
        "        if (StringUtils.isNotBlank(config.getMethodName())) {\n            param.setMethodName(config.getMethodName());\n        }\n        if (StringUtils.isNotBlank(config.getDescriptorContent())) {",
        "        if (StringUtils.isNotBlank(config.getMethodName())) {\n            param.setMethodName(config.getMethodName());\n        }\n        if (StringUtils.isBlank(param.getServerAddress()) && StringUtils.isNotBlank(config.getServerAddress())) {\n            param.setServerAddress(config.getServerAddress());\n        }\n        if (StringUtils.isNotBlank(config.getDescriptorContent())) {",
    )

    # --- Tests ---
    patch_file(
        "backend/services/api-test/src/test/java/io/metersphere/api/grpc/GrpcCasePersistenceServiceTest.java",
        "        config.setMethodName(\"SayHello\");\n        config.setDescriptorContent(DESCRIPTOR);",
        "        config.setMethodName(\"SayHello\");\n        config.setServerAddress(\"127.0.0.1:50051\");\n        config.setDescriptorContent(DESCRIPTOR);",
    )
    patch_file(
        "backend/services/api-test/src/test/java/io/metersphere/api/grpc/GrpcCasePersistenceServiceTest.java",
        "            Assertions.assertEquals(\"SayHello\", saved.getMethodName());\n            Assertions.assertEquals(\"{\\\"name\\\":\\\"world\\\"}\", saved.getRequestJson());",
        "            Assertions.assertEquals(\"SayHello\", saved.getMethodName());\n            Assertions.assertEquals(\"127.0.0.1:50051\", saved.getServerAddress());\n            Assertions.assertEquals(\"{\\\"name\\\":\\\"world\\\"}\", saved.getRequestJson());",
    )
    patch_file(
        "backend/services/api-test/src/test/java/io/metersphere/api/grpc/GrpcCasePersistenceServiceTest.java",
        "        config.setMethodName(\"SayHello\");\n        config.setDescriptorContent(DESCRIPTOR);\n        config.setRequestJson(\"{\\\"name\\\":\\\"world\\\"}\");",
        "        config.setMethodName(\"SayHello\");\n        config.setServerAddress(\"127.0.0.1:50051\");\n        config.setDescriptorContent(DESCRIPTOR);\n        config.setRequestJson(\"{\\\"name\\\":\\\"world\\\"}\");",
    )

    patch_file(
        "backend/services/api-test/src/test/java/io/metersphere/api/grpc/service/GrpcCaseRunServiceTest.java",
        "import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;\nimport io.metersphere.api.grpc.exception.GrpcResultCode;",
        "import io.metersphere.api.grpc.assertion.GrpcAssertionEvaluator;\nimport io.metersphere.api.grpc.assertion.GrpcExtractEvaluator;\nimport io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;\nimport io.metersphere.api.grpc.exception.GrpcResultCode;",
    )
    patch_file(
        "backend/services/api-test/src/test/java/io/metersphere/api/grpc/service/GrpcCaseRunServiceTest.java",
        "    @Mock\n    private GrpcInvokeService grpcInvokeService;\n\n    @Test",
        "    @Mock\n    private GrpcInvokeService grpcInvokeService;\n    @Mock\n    private GrpcAssertionEvaluator grpcAssertionEvaluator;\n    @Mock\n    private GrpcExtractEvaluator grpcExtractEvaluator;\n\n    @Test",
    )
    patch_file(
        "backend/services/api-test/src/test/java/io/metersphere/api/grpc/service/GrpcCaseRunServiceTest.java",
        "    @Test\n    void shouldReturnErrorWhenServerAddressMissing() {\n        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, new GrpcCaseRunParamDTO());\n\n        Assertions.assertFalse(result.getSuccess());\n        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID.name(), result.getErrorCode());\n    }",
        "    @Test\n    void shouldReturnErrorWhenServerAddressMissing() {\n        mockGrpcCaseContext(buildCaseConfig());\n        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, new GrpcCaseRunParamDTO());\n\n        Assertions.assertFalse(result.getSuccess());\n        Assertions.assertEquals(GrpcResultCode.GRPC_ADDRESS_INVALID.name(), result.getErrorCode());\n    }\n\n    @Test\n    void shouldFallbackToPersistedServerAddress() {\n        ApiGrpcCaseConfigDTO caseConfig = buildCaseConfig();\n        caseConfig.setServerAddress(SERVER_ADDRESS);\n        mockGrpcCaseContext(caseConfig);\n        when(grpcInvokeService.invoke(any())).thenReturn(successResponse(\"{}\"));\n\n        GrpcCaseRunResultDTO result = grpcCaseRunService.run(CASE_ID, new GrpcCaseRunParamDTO());\n\n        Assertions.assertTrue(result.getSuccess());\n        ArgumentCaptor<GrpcInvokeRequest> captor = ArgumentCaptor.forClass(GrpcInvokeRequest.class);\n        verify(grpcInvokeService).invoke(captor.capture());\n        Assertions.assertEquals(SERVER_ADDRESS, captor.getValue().getServerAddress());\n    }",
    )

    # --- Frontend models ---
    patch_file(
        "frontend/src/models/apiTest/grpc.ts",
        "  methodName?: string;\n  descriptorContent?: string;\n  protoContent?: string;\n  fileName?: string;\n  mainFileName?: string;\n  protoFiles?: GrpcProtoFile[];\n  requestJson?: string;",
        "  methodName?: string;\n  serverAddress?: string;\n  descriptorContent?: string;\n  protoContent?: string;\n  fileName?: string;\n  mainFileName?: string;\n  protoFiles?: GrpcProtoFile[];\n  requestJson?: string;",
        count=2,
    )

    patch_file(
        "frontend/src/views/api-test/components/requestComposition/grpc/utils.ts",
        "    serviceName: grpc.serviceName,\n    methodName: grpc.methodName,\n    descriptorContent: grpc.descriptorContent,",
        "    serviceName: grpc.serviceName,\n    methodName: grpc.methodName,\n    serverAddress: grpc.serverAddress?.trim() || undefined,\n    descriptorContent: grpc.descriptorContent,",
    )

    patch_file(
        "frontend/src/views/api-test/components/requestComposition/grpc/constants.ts",
        "  if (grpcCaseConfig.methodName) {\n    state.methodName = grpcCaseConfig.methodName;\n  }\n  if (grpcCaseConfig.descriptorContent) {",
        "  if (grpcCaseConfig.methodName) {\n    state.methodName = grpcCaseConfig.methodName;\n  }\n  if (grpcCaseConfig.serverAddress?.trim()) {\n    state.serverAddress = grpcCaseConfig.serverAddress.trim();\n  }\n  if (grpcCaseConfig.descriptorContent) {",
    )

    # --- Frontend report ---
    index_old = """<script setup lang=\"ts\">
  import { ref } from 'vue';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import ReportList from './component/reportList.vue';

  import { useI18n } from '@/hooks/useI18n';

  import { ReportEnum } from '@/enums/reportEnum';

  const { t } = useI18n();
  const activeTab = ref<keyof typeof ReportEnum>(ReportEnum.API_SCENARIO_REPORT);"""
    index_new = """<script setup lang=\"ts\">
  import { ref, watch } from 'vue';
  import { useRoute } from 'vue-router';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import ReportList from './component/reportList.vue';

  import { useI18n } from '@/hooks/useI18n';

  import { ReportEnum } from '@/enums/reportEnum';

  const { t } = useI18n();
  const route = useRoute();
  const activeTab = ref<keyof typeof ReportEnum>(ReportEnum.API_SCENARIO_REPORT);

  function resolveReportTab(type?: string | string[]) {
    const normalized = Array.isArray(type) ? type[0] : type;
    if (normalized === 'API_SCENARIO' || normalized === ReportEnum.API_SCENARIO_REPORT) {
      return ReportEnum.API_SCENARIO_REPORT;
    }
    if (
      normalized === 'API_CASE' ||
      normalized === 'API_REPORT' ||
      normalized === ReportEnum.API_REPORT ||
      route.query.id ||
      route.query.reportId
    ) {
      return ReportEnum.API_REPORT;
    }
    return ReportEnum.API_SCENARIO_REPORT;
  }

  activeTab.value = resolveReportTab(route.query.type as string | undefined);

  watch(
    () => [route.query.type, route.query.id, route.query.reportId],
    () => {
      activeTab.value = resolveReportTab(route.query.type as string | undefined);
    }
  );"""
    patch_file("frontend/src/views/api-test/report/index.vue", index_old, index_new)

    patch_file(
        "frontend/src/views/api-test/report/component/reportList.vue",
        "  function showDetail() {\n    if ((route.query.reportId || route.query.id) && route.query.type && !route.query.task) {",
        "  function showDetail() {\n    if ((route.query.reportId || route.query.id) && !route.query.task) {",
    )

    # --- Migration files ---
    migration = ROOT / "backend/framework/domain/src/main/resources/migration/3.6.0/ddl/V3.6.0_6__grpc_api_case_server_address.sql"
    migration.parent.mkdir(parents=True, exist_ok=True)
    migration.write_text(
        "ALTER TABLE api_grpc_case_config\n"
        "    ADD COLUMN server_address VARCHAR(512) NULL COMMENT 'gRPC server address' AFTER method_name;\n",
        encoding="utf-8",
        newline="\n",
    )
    print(f"written {migration.relative_to(ROOT)}")

    upgrade = ROOT / "docker/grpc-schema-patch-upgrade.sql"
    upgrade.write_text(
        upgrade.read_text(encoding="utf-8").rstrip()
        + "\nSET @exist = (SELECT COUNT(*) FROM information_schema.COLUMNS\n"
        + "              WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'api_grpc_case_config' AND COLUMN_NAME = 'server_address');\n"
        + "SET @sql = IF(@exist = 0,\n"
        + "    'ALTER TABLE api_grpc_case_config ADD COLUMN server_address VARCHAR(512) NULL COMMENT ''gRPC server address'' AFTER method_name',\n"
        + "    'SELECT 1');\n"
        + "PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;\n",
        encoding="utf-8",
        newline="\n",
    )
    print("patched docker/grpc-schema-patch-upgrade.sql")

    print("All patches applied.")


if __name__ == "__main__":
    main()
