package io.metersphere.api.grpc.service;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.constants.ApiDefinitionStatus;
import io.metersphere.api.constants.ApiScenarioStatus;
import io.metersphere.api.constants.ApiScenarioStepRefType;
import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiDefinitionExample;
import io.metersphere.api.domain.ApiScenario;
import io.metersphere.api.domain.ApiScenarioExample;
import io.metersphere.api.domain.ApiScenarioModule;
import io.metersphere.api.domain.ApiTestCase;
import io.metersphere.api.domain.ApiTestCaseExample;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.dto.definition.ApiTestCaseAddRequest;
import io.metersphere.api.dto.definition.ApiTestCaseUpdateRequest;
import io.metersphere.api.dto.scenario.ApiScenarioAddRequest;
import io.metersphere.api.dto.scenario.ApiScenarioStepRequest;
import io.metersphere.api.dto.scenario.ApiScenarioUpdateRequest;
import io.metersphere.api.dto.scenario.ScenarioConfig;
import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseExtractConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseSettingsDTO;
import io.metersphere.api.grpc.dto.GrpcScenarioAiCreateRequest;
import io.metersphere.api.grpc.dto.GrpcScenarioAiCreateResultDTO;
import io.metersphere.api.grpc.dto.GrpcScenarioAiDraftDTO;
import io.metersphere.api.grpc.dto.GrpcScenarioAiGenerateRequest;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.mapper.ApiScenarioMapper;
import io.metersphere.api.mapper.ApiScenarioModuleMapper;
import io.metersphere.api.mapper.ApiTestCaseMapper;
import io.metersphere.api.service.definition.ApiTestCaseService;
import io.metersphere.api.service.scenario.ApiScenarioService;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.request.ai.AIChatAttachment;
import io.metersphere.system.dto.request.ai.AIChatOption;
import io.metersphere.system.dto.request.ai.AiModelSourceDTO;
import io.metersphere.system.service.AiChatBaseService;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GrpcScenarioAiService {

    private static final String AI_TAG = "AI_GENERATED";
    private static final int DEFAULT_TIMEOUT = 60_000;

    @Resource
    private ApiDefinitionMapper apiDefinitionMapper;
    @Resource
    private ApiTestCaseMapper apiTestCaseMapper;
    @Resource
    private ApiScenarioMapper apiScenarioMapper;
    @Resource
    private ApiScenarioModuleMapper apiScenarioModuleMapper;
    @Resource
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;
    @Resource
    private GrpcScenarioSourceFileExtractor sourceFileExtractor;
    @Resource
    private AiChatBaseService aiChatBaseService;
    @Resource
    private ApiTestCaseService apiTestCaseService;
    @Resource
    private ApiScenarioService apiScenarioService;

    public GrpcScenarioAiDraftDTO generate(GrpcScenarioAiGenerateRequest request, MultipartFile[] files,
                                           String userId) {
        validateModule(request.getProjectId(), request.getModuleId());
        List<DefinitionContext> definitions = loadDefinitions(request.getProjectId());
        if (definitions.isEmpty()) {
            GrpcScenarioAiDraftDTO draft = new GrpcScenarioAiDraftDTO();
            draft.setProtoRequired(true);
            draft.getAmbiguities().add("当前项目没有可用的 Unary gRPC 接口定义，请先上传并解析 Proto 后再生成。");
            return draft;
        }

        GrpcScenarioSourceFileExtractor.ExtractionResult extracted = sourceFileExtractor.extract(files);
        String source = StringUtils.trimToEmpty(request.getSourceText());
        if (StringUtils.isNotBlank(extracted.text())) {
            source = StringUtils.isBlank(source) ? extracted.text() : source + "\n\n" + extracted.text();
        }
        if (StringUtils.isBlank(source) && StringUtils.isBlank(request.getProjectContext())
                && request.getCurrentDraft() == null && extracted.images().isEmpty()) {
            throw new MSException("请输入或上传 gRPC 测试用例内容");
        }

        AiModelSourceDTO model = aiChatBaseService.getModule(request, userId);
        List<AIChatAttachment> attachments = new ArrayList<>(
                Objects.requireNonNullElse(request.getAttachments(), List.of()));
        attachments.addAll(toImageAttachments(extracted.images()));
        String prompt = buildPrompt(request, source, definitions);
        String content = aiChatBaseService.chat(AIChatOption.builder()
                        .conversationId(request.getConversationId())
                        .module(model)
                        .prompt(prompt)
                        .attachments(attachments)
                        .build())
                .content();
        RawOutput output = parseOutput(content);
        return normalizeDraft(output, request, definitions);
    }

    @Transactional(rollbackFor = Exception.class)
    public GrpcScenarioAiCreateResultDTO create(GrpcScenarioAiCreateRequest request, String userId) {
        validateModule(request.getProjectId(), request.getModuleId());
        Map<String, DefinitionContext> definitions = loadDefinitions(request.getProjectId()).stream()
                .collect(Collectors.toMap(context -> context.definition().getId(), Function.identity()));
        GrpcScenarioAiCreateResultDTO result = new GrpcScenarioAiCreateResultDTO();
        Map<String, ApiTestCase> savedCases = new LinkedHashMap<>();

        for (GrpcScenarioAiDraftDTO.ScenarioDraft scenarioDraft : nullSafe(request.getDraft().getScenarios())) {
            if (!scenarioDraft.isSelected() || scenarioDraft.isBlocked()) {
                continue;
            }
            if (StringUtils.isBlank(scenarioDraft.getName())) {
                addSkipped(result, "SCENARIO", "未命名场景", "场景名称不能为空");
                continue;
            }
            List<ApiScenarioStepRequest> steps = new ArrayList<>();
            for (GrpcScenarioAiDraftDTO.CaseDraft caseDraft : nullSafe(scenarioDraft.getCases())) {
                if (!caseDraft.isSelected() || caseDraft.isBlocked()) {
                    continue;
                }
                String validationMessage = validateCaseDraft(caseDraft);
                if (validationMessage != null) {
                    addSkipped(result, "CASE", StringUtils.defaultIfBlank(caseDraft.getName(), "未命名用例"),
                            validationMessage);
                    continue;
                }
                String caseKey = caseDraft.getApiDefinitionId() + "::" + caseDraft.getName();
                ApiTestCase testCase = savedCases.get(caseKey);
                if (testCase == null) {
                    DefinitionContext definition = definitions.get(caseDraft.getApiDefinitionId());
                    if (definition == null) {
                        addSkipped(result, "CASE", caseDraft.getName(), "接口定义不存在或已不是可执行 Unary gRPC 定义");
                        continue;
                    }
                    testCase = saveCase(request.getProjectId(), caseDraft, definition, userId, result);
                    if (testCase == null) {
                        continue;
                    }
                    savedCases.put(caseKey, testCase);
                }
                steps.add(buildCaseStep(request.getProjectId(), testCase));
            }
            if (steps.isEmpty()) {
                addSkipped(result, "SCENARIO", scenarioDraft.getName(), "没有可创建的已选 gRPC 用例步骤");
                continue;
            }
            saveScenario(request, scenarioDraft, steps, userId, result);
        }
        return result;
    }

    private List<DefinitionContext> loadDefinitions(String projectId) {
        ApiDefinitionExample example = new ApiDefinitionExample();
        example.createCriteria()
                .andProjectIdEqualTo(projectId)
                .andProtocolEqualTo(ApiConstants.GRPC_PROTOCOL)
                .andLatestEqualTo(true)
                .andDeletedEqualTo(false);
        List<DefinitionContext> result = new ArrayList<>();
        for (ApiDefinition definition : apiDefinitionMapper.selectByExample(example)) {
            ApiGrpcDefinitionDTO grpc = grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(definition.getId());
            if (grpc != null && BooleanUtils.isTrue(grpc.getInvokable())
                    && StringUtils.equalsIgnoreCase(grpc.getMethodType(), "UNARY")) {
                result.add(new DefinitionContext(definition, grpc, resolveRequestTemplate(grpc)));
            }
        }
        return result;
    }

    private String resolveRequestTemplate(ApiGrpcDefinitionDTO definition) {
        if (CollectionUtils.isEmpty(definition.getServices())) {
            return "{}";
        }
        return definition.getServices().stream()
                .filter(service -> StringUtils.equalsAny(service.getFullName(), definition.getServiceName())
                        || StringUtils.equals(service.getName(), definition.getServiceName()))
                .flatMap(service -> service.getMethods().stream())
                .filter(method -> StringUtils.equals(method.getName(), definition.getMethodName())
                        || StringUtils.equals(method.getFullName(), definition.getFullMethodName()))
                .map(ApiGrpcDefinitionDTO.GrpcMethodInfoDTO::getRequestJsonTemplate)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse("{}");
    }

    private String buildPrompt(GrpcScenarioAiGenerateRequest request, String source,
                               List<DefinitionContext> definitions) {
        List<Map<String, String>> catalog = definitions.stream().map(context -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("definitionId", context.definition().getId());
            item.put("definitionName", context.definition().getName());
            item.put("serviceName", context.grpc().getServiceName());
            item.put("methodName", context.grpc().getMethodName());
            item.put("fullMethodName", context.grpc().getFullMethodName());
            item.put("requestType", context.grpc().getRequestType());
            item.put("responseType", context.grpc().getResponseType());
            item.put("requestJsonTemplate", context.requestTemplate());
            item.put("defaultServerAddress", StringUtils.defaultIfBlank(
                    context.grpc().getServerAddress(), context.grpc().getReflectionServerAddress()));
            return item;
        }).toList();
        String currentDraft = request.getCurrentDraft() == null
                ? "无"
                : JSON.toJSONString(request.getCurrentDraft());
        return """
                你是 MeterSphere gRPC 自动化场景设计器。根据用户测试用例和当前项目接口目录输出严格 JSON。

                规则：
                1. 只能引用目录中的 definitionId，且当前执行引擎仅支持 Unary RPC。
                2. 保留输入文档明确的分组和顺序；没有明确分组时按业务链路智能分组。
                3. 原始测试用例必须 selected=true、suggested=false；补充的边界/异常建议必须 selected=false、suggested=true。
                4. 优先从用户需求、图片、本机项目上下文、接口模板和默认配置中提取并补全业务参数；仅在全部来源都没有依据时填写 ambiguity，其他项目继续生成。
                5. requestJson 必须是 JSON 对象。断言仅使用 JSON_PATH、GRPC_STATUS、GRPC_CODE。
                6. safety 仅为 SAFE 或 RISK；读操作通常 SAFE，写入、删除、状态切换通常 RISK。
                7. 用户给出修正要求时，在 currentDraft 基础上修改，不遗漏未被要求删除的内容。
                8. 不输出 Markdown，不输出代码围栏，只输出一个 JSON 对象。

                JSON 结构：
                {"sourceSummary":"摘要","ambiguities":["全局问题"],"warnings":["提示"],"scenarios":[{"name":"场景名","description":"说明","priority":"P0|P1|P2|P3","safety":"SAFE|RISK","selected":true,"suggested":false,"ambiguity":"","cases":[{"definitionId":"目录ID","definitionName":"目录名称","name":"用例名","requestJson":{},"serverAddress":"可空，优先定义默认地址","priority":"P0|P1|P2|P3","selected":true,"suggested":false,"ambiguity":"","assertionConfig":{"items":[]},"extractConfig":{"items":[]}}]}]}

                全局服务地址覆盖：%s
                修正要求：%s
                currentDraft：%s
                MeterSphere 项目：%s
                已授权的本机项目上下文：%s
                当前项目 gRPC 接口目录：%s
                用户测试用例：%s
                """.formatted(
                StringUtils.defaultIfBlank(request.getServerAddress(), "无"),
                StringUtils.defaultIfBlank(request.getCorrection(), "无"),
                currentDraft,
                StringUtils.defaultIfBlank(request.getProjectName(), request.getProjectId()),
                StringUtils.defaultIfBlank(request.getProjectContext(), "未关联本机项目目录"),
                JSON.toJSONString(catalog),
                StringUtils.defaultIfBlank(source, "内容位于图片附件中"));
    }

    private RawOutput parseOutput(String content) {
        if (StringUtils.isBlank(content)) {
            throw new MSException("AI 未返回 gRPC 场景草稿");
        }
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new MSException("AI 返回内容不是有效的场景 JSON，请调整要求后重试");
        }
        try {
            RawOutput output = JSON.parseObject(json.substring(start, end + 1), RawOutput.class);
            if (output == null) {
                throw new IllegalArgumentException("empty output");
            }
            return output;
        } catch (Exception e) {
            throw new MSException("AI 返回的场景 JSON 解析失败，请重试或缩小输入范围", e);
        }
    }

    private GrpcScenarioAiDraftDTO normalizeDraft(RawOutput output, GrpcScenarioAiGenerateRequest request,
                                                   List<DefinitionContext> definitions) {
        GrpcScenarioAiDraftDTO draft = new GrpcScenarioAiDraftDTO();
        draft.setSourceSummary(StringUtils.defaultString(output.getSourceSummary()));
        draft.setAmbiguities(nullSafe(output.getAmbiguities()));
        draft.setWarnings(nullSafe(output.getWarnings()));

        for (RawScenario rawScenario : nullSafe(output.getScenarios())) {
            GrpcScenarioAiDraftDTO.ScenarioDraft scenario = new GrpcScenarioAiDraftDTO.ScenarioDraft();
            scenario.setClientId(UUID.randomUUID().toString());
            scenario.setName(limit(StringUtils.defaultIfBlank(rawScenario.getName(), "AI gRPC 场景"), 255));
            scenario.setDescription(limit(StringUtils.defaultString(rawScenario.getDescription()), 1000));
            scenario.setPriority(normalizePriority(rawScenario.getPriority()));
            scenario.setSafety(normalizeSafety(rawScenario.getSafety()));
            scenario.setSuggested(BooleanUtils.isTrue(rawScenario.getSuggested()));
            scenario.setSelected(!scenario.isSuggested() && !BooleanUtils.isFalse(rawScenario.getSelected()));
            scenario.setAmbiguity(StringUtils.trimToNull(rawScenario.getAmbiguity()));
            scenario.setBlocked(StringUtils.isNotBlank(scenario.getAmbiguity()));

            for (RawCase rawCase : nullSafe(rawScenario.getCases())) {
                GrpcScenarioAiDraftDTO.CaseDraft caseDraft = normalizeCase(rawCase, request, definitions);
                scenario.getCases().add(caseDraft);
            }
            if (scenario.getCases().isEmpty()) {
                scenario.setBlocked(true);
                scenario.setAmbiguity("该场景没有识别到可映射的 gRPC 用例");
            }
            draft.getScenarios().add(scenario);
        }
        if (draft.getScenarios().isEmpty()) {
            draft.getWarnings().add("未从输入内容中识别到可生成的 gRPC 自动化场景");
        }
        return draft;
    }

    private GrpcScenarioAiDraftDTO.CaseDraft normalizeCase(RawCase rawCase, GrpcScenarioAiGenerateRequest request,
                                                            List<DefinitionContext> definitions) {
        GrpcScenarioAiDraftDTO.CaseDraft result = new GrpcScenarioAiDraftDTO.CaseDraft();
        result.setClientId(UUID.randomUUID().toString());
        result.setName(limit(StringUtils.defaultIfBlank(rawCase.getName(), "AI gRPC 用例"), 255));
        result.setPriority(normalizePriority(rawCase.getPriority()));
        result.setSuggested(BooleanUtils.isTrue(rawCase.getSuggested()));
        result.setSelected(!result.isSuggested() && !BooleanUtils.isFalse(rawCase.getSelected()));
        result.setAmbiguity(StringUtils.trimToNull(rawCase.getAmbiguity()));

        DefinitionContext definition = resolveDefinition(rawCase, definitions);
        if (definition == null) {
            block(result, StringUtils.defaultIfBlank(result.getAmbiguity(),
                    "无法唯一匹配项目中的 gRPC 接口定义，请选择正确的接口"));
            return result;
        }
        result.setApiDefinitionId(definition.definition().getId());
        result.setDefinitionName(definition.definition().getName());
        result.setServiceName(definition.grpc().getServiceName());
        result.setMethodName(definition.grpc().getMethodName());
        result.setServerAddress(StringUtils.defaultIfBlank(rawCase.getServerAddress(),
                StringUtils.defaultIfBlank(request.getServerAddress(), StringUtils.defaultIfBlank(
                        definition.grpc().getServerAddress(), definition.grpc().getReflectionServerAddress()))));
        result.setRequestJson(normalizeRequestJson(rawCase.getRequestJson(), definition.requestTemplate(), result));
        result.setAssertionConfig(rawCase.getAssertionConfig() == null
                ? new GrpcCaseAssertionConfigDTO() : rawCase.getAssertionConfig());
        result.setExtractConfig(rawCase.getExtractConfig() == null
                ? new GrpcCaseExtractConfigDTO() : rawCase.getExtractConfig());
        if (StringUtils.isBlank(result.getServerAddress())) {
            block(result, "接口定义没有默认服务地址，请为该用例填写 serverAddress");
        } else if (StringUtils.isNotBlank(result.getAmbiguity())) {
            block(result, result.getAmbiguity());
        }
        return result;
    }

    private DefinitionContext resolveDefinition(RawCase rawCase, List<DefinitionContext> definitions) {
        if (StringUtils.isNotBlank(rawCase.getDefinitionId())) {
            return definitions.stream()
                    .filter(context -> StringUtils.equals(context.definition().getId(), rawCase.getDefinitionId()))
                    .findFirst().orElse(null);
        }
        String requested = StringUtils.trimToEmpty(rawCase.getDefinitionName());
        if (StringUtils.isBlank(requested)) {
            requested = StringUtils.trimToEmpty(rawCase.getFullMethodName());
        }
        final String lookup = StringUtils.removeStart(requested, ".");
        List<DefinitionContext> matches = definitions.stream().filter(context ->
                        StringUtils.equalsIgnoreCase(context.definition().getName(), lookup)
                                || StringUtils.equalsIgnoreCase(StringUtils.removeStart(context.grpc().getFullMethodName(), "."), lookup)
                                || StringUtils.equalsIgnoreCase(context.grpc().getServiceName() + "/" + context.grpc().getMethodName(), lookup))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String normalizeRequestJson(Object raw, String fallback, GrpcScenarioAiDraftDTO.CaseDraft result) {
        String value;
        if (raw == null) {
            value = StringUtils.defaultIfBlank(fallback, "{}");
        } else if (raw instanceof String string) {
            value = StringUtils.defaultIfBlank(string, "{}");
        } else {
            value = JSON.toJSONString(raw);
        }
        try {
            Object parsed = JSON.parseObject(value);
            if (!(parsed instanceof Map<?, ?>)) {
                block(result, "requestJson 必须是 JSON 对象");
            }
        } catch (Exception e) {
            block(result, "requestJson 不是合法 JSON，请修正该用例");
        }
        return value;
    }

    private ApiTestCase saveCase(String projectId, GrpcScenarioAiDraftDTO.CaseDraft draft,
                                 DefinitionContext definition, String userId,
                                 GrpcScenarioAiCreateResultDTO result) {
        ApiTestCase existing = findCase(projectId, draft.getApiDefinitionId(), draft.getName());
        ApiGrpcCaseConfigAddDTO config = buildCaseConfig(draft, definition.grpc());
        List<String> tags = List.of(AI_TAG, "GRPC_AI", draft.isSuggested() ? "AI_SUGGESTED" : "AI_SOURCE");
        if (existing != null && !BooleanUtils.isTrue(existing.getAiCreate())) {
            addSkipped(result, "CASE", draft.getName(), "存在同名人工用例，未覆盖");
            return null;
        }
        if (existing == null) {
            ApiTestCaseAddRequest add = new ApiTestCaseAddRequest();
            add.setName(draft.getName());
            add.setProjectId(projectId);
            add.setPriority(draft.getPriority());
            add.setStatus(ApiDefinitionStatus.PROCESSING.name());
            add.setApiDefinitionId(draft.getApiDefinitionId());
            add.setTags(new LinkedHashSet<>(tags));
            add.setRequest(Collections.emptyMap());
            add.setUploadFileIds(List.of());
            add.setLinkFileIds(List.of());
            add.setAiCreate(true);
            add.setGrpcCaseConfig(config);
            ApiTestCase created = apiTestCaseService.addCase(add, userId);
            result.setCreatedCaseCount(result.getCreatedCaseCount() + 1);
            result.getItems().add(new GrpcScenarioAiCreateResultDTO.Item(
                    "CASE", draft.getName(), "CREATED", created.getId(), null));
            return created;
        }

        ApiTestCaseUpdateRequest update = new ApiTestCaseUpdateRequest();
        update.setId(existing.getId());
        update.setName(draft.getName());
        update.setPriority(draft.getPriority());
        update.setStatus(ApiDefinitionStatus.PROCESSING.name());
        update.setTags(tags);
        update.setRequest(Collections.emptyMap());
        update.setUploadFileIds(List.of());
        update.setLinkFileIds(List.of());
        update.setDeleteFileIds(List.of());
        update.setUnLinkFileIds(List.of());
        update.setGrpcCaseConfig(config);
        ApiTestCase updated = apiTestCaseService.update(update, userId);
        result.setUpdatedCaseCount(result.getUpdatedCaseCount() + 1);
        result.getItems().add(new GrpcScenarioAiCreateResultDTO.Item(
                "CASE", draft.getName(), "UPDATED", updated.getId(), null));
        return updated;
    }

    private String validateCaseDraft(GrpcScenarioAiDraftDTO.CaseDraft draft) {
        if (StringUtils.isAnyBlank(draft.getApiDefinitionId(), draft.getName(), draft.getServerAddress())) {
            return "接口定义、用例名称和服务地址不能为空";
        }
        try {
            Object requestJson = JSON.parseObject(draft.getRequestJson());
            if (!(requestJson instanceof Map<?, ?>)) {
                return "requestJson 必须是合法 JSON 对象";
            }
        } catch (Exception e) {
            return "requestJson 必须是合法 JSON 对象";
        }
        return null;
    }

    private ApiGrpcCaseConfigAddDTO buildCaseConfig(GrpcScenarioAiDraftDTO.CaseDraft draft,
                                                     ApiGrpcDefinitionDTO definition) {
        ApiGrpcCaseConfigAddDTO config = new ApiGrpcCaseConfigAddDTO();
        config.setProtoId(definition.getProtoId());
        config.setServiceName(definition.getServiceName());
        config.setMethodName(definition.getMethodName());
        config.setServerAddress(draft.getServerAddress());
        config.setDescriptorContent(definition.getDescriptorContent());
        config.setProtoContent(definition.getProtoContent());
        config.setFileName(definition.getFileName());
        config.setMainFileName(definition.getMainFileName());
        config.setProtoFiles(definition.getProtoFiles());
        config.setRequestJson(draft.getRequestJson());
        config.setTimeout(DEFAULT_TIMEOUT);
        config.setAssertionConfig(Objects.requireNonNullElseGet(
                draft.getAssertionConfig(), GrpcCaseAssertionConfigDTO::new));
        config.setExtractConfig(Objects.requireNonNullElseGet(
                draft.getExtractConfig(), GrpcCaseExtractConfigDTO::new));
        GrpcCaseSettingsDTO settings = new GrpcCaseSettingsDTO();
        settings.setUseTls(false);
        settings.setVerifyServerCertificate(false);
        settings.setIncludeDefaultValues(true);
        settings.setMaxResponseSizeMb(16);
        settings.setConnectTimeout(8_000);
        config.setSettings(settings);
        return config;
    }

    private ApiScenarioStepRequest buildCaseStep(String projectId, ApiTestCase testCase) {
        String stepId = nextId();
        ApiScenarioStepRequest step = new ApiScenarioStepRequest();
        step.setId(stepId);
        step.setUniqueId(stepId);
        step.setEnable(true);
        step.setResourceId(testCase.getId());
        step.setOriginProjectId(projectId);
        step.setStepType(ApiScenarioStepType.API_CASE.name());
        step.setRefType(ApiScenarioStepRefType.REF.name());
        step.setConfig(Map.of(
                "id", "",
                "name", "",
                "enable", true,
                "protocol", ApiConstants.GRPC_PROTOCOL,
                "method", ApiConstants.GRPC_PROTOCOL));
        step.setCsvIds(List.of());
        step.setProjectId(projectId);
        step.setName(testCase.getName());
        step.setResourceNum(String.valueOf(testCase.getNum()));
        step.setChildren(List.of());
        step.setIsNew(true);
        return step;
    }

    String nextId() {
        return IDGenerator.nextStr();
    }

    private void saveScenario(GrpcScenarioAiCreateRequest request,
                              GrpcScenarioAiDraftDTO.ScenarioDraft draft,
                              List<ApiScenarioStepRequest> steps,
                              String userId,
                              GrpcScenarioAiCreateResultDTO result) {
        ApiScenario existing = findScenario(request.getProjectId(), request.getModuleId(), draft.getName());
        List<String> tags = List.of(AI_TAG, "GRPC_AI", draft.getSafety());
        if (existing != null && !isAiScenario(existing)) {
            addSkipped(result, "SCENARIO", draft.getName(), "存在同名人工场景，未覆盖");
            return;
        }
        if (existing == null) {
            ApiScenarioAddRequest add = new ApiScenarioAddRequest();
            fillScenarioRequest(add, request, draft, steps, tags);
            ApiScenario created = apiScenarioService.add(add, userId);
            result.setCreatedScenarioCount(result.getCreatedScenarioCount() + 1);
            result.getScenarioIds().add(created.getId());
            result.getItems().add(new GrpcScenarioAiCreateResultDTO.Item(
                    "SCENARIO", draft.getName(), "CREATED", created.getId(), null));
            return;
        }

        ApiScenarioUpdateRequest update = new ApiScenarioUpdateRequest();
        update.setId(existing.getId());
        update.setName(draft.getName());
        update.setPriority(draft.getPriority());
        update.setStatus(ApiScenarioStatus.UNDERWAY.name());
        update.setProjectId(request.getProjectId());
        update.setModuleId(request.getModuleId());
        update.setDescription(draft.getDescription());
        update.setTags(tags);
        update.setGrouped(false);
        update.setScenarioConfig(new ScenarioConfig());
        update.setSteps(steps);
        update.setStepDetails(Map.of());
        update.setStepFileParam(Map.of());
        ApiScenario updated = apiScenarioService.update(update, userId);
        result.setUpdatedScenarioCount(result.getUpdatedScenarioCount() + 1);
        result.getScenarioIds().add(updated.getId());
        result.getItems().add(new GrpcScenarioAiCreateResultDTO.Item(
                "SCENARIO", draft.getName(), "UPDATED", updated.getId(), null));
    }

    private void fillScenarioRequest(ApiScenarioAddRequest add, GrpcScenarioAiCreateRequest request,
                                     GrpcScenarioAiDraftDTO.ScenarioDraft draft,
                                     List<ApiScenarioStepRequest> steps, List<String> tags) {
        add.setName(draft.getName());
        add.setPriority(draft.getPriority());
        add.setStatus(ApiScenarioStatus.UNDERWAY.name());
        add.setProjectId(request.getProjectId());
        add.setModuleId(request.getModuleId());
        add.setDescription(draft.getDescription());
        add.setTags(tags);
        add.setGrouped(false);
        add.setScenarioConfig(new ScenarioConfig());
        add.setSteps(steps);
        add.setStepDetails(Map.of());
        add.setStepFileParam(Map.of());
    }

    private ApiTestCase findCase(String projectId, String definitionId, String name) {
        ApiTestCaseExample example = new ApiTestCaseExample();
        example.createCriteria()
                .andProjectIdEqualTo(projectId)
                .andApiDefinitionIdEqualTo(definitionId)
                .andNameEqualTo(name)
                .andDeletedEqualTo(false);
        List<ApiTestCase> cases = apiTestCaseMapper.selectByExample(example);
        return cases.isEmpty() ? null : cases.getFirst();
    }

    private ApiScenario findScenario(String projectId, String moduleId, String name) {
        ApiScenarioExample example = new ApiScenarioExample();
        example.createCriteria()
                .andProjectIdEqualTo(projectId)
                .andModuleIdEqualTo(moduleId)
                .andNameEqualTo(name)
                .andDeletedEqualTo(false)
                .andLatestEqualTo(true);
        List<ApiScenario> scenarios = apiScenarioMapper.selectByExample(example);
        return scenarios.isEmpty() ? null : scenarios.getFirst();
    }

    private boolean isAiScenario(ApiScenario scenario) {
        return scenario.getTags() != null && scenario.getTags().contains(AI_TAG);
    }

    private void validateModule(String projectId, String moduleId) {
        if (StringUtils.equals(moduleId, ModuleConstants.DEFAULT_NODE_ID)) {
            return;
        }
        ApiScenarioModule module = apiScenarioModuleMapper.selectByPrimaryKey(moduleId);
        if (module == null || !StringUtils.equals(module.getProjectId(), projectId)) {
            throw new MSException("所选场景模块不属于当前项目");
        }
    }

    private List<AIChatAttachment> toImageAttachments(List<MultipartFile> images) {
        List<AIChatAttachment> result = new ArrayList<>();
        for (MultipartFile image : images) {
            try {
                AIChatAttachment attachment = new AIChatAttachment();
                attachment.setId(UUID.randomUUID().toString());
                attachment.setName(image.getOriginalFilename());
                String extension = StringUtils.substringAfterLast(
                        StringUtils.defaultString(image.getOriginalFilename()), ".").toLowerCase(Locale.ROOT);
                attachment.setMimeType("png".equals(extension) ? "image/png" : "image/jpeg");
                attachment.setData(Base64.getEncoder().encodeToString(image.getBytes()));
                attachment.setSize(image.getSize());
                result.add(attachment);
            } catch (IOException e) {
                throw new MSException("读取测试用例图片失败：" + image.getOriginalFilename(), e);
            }
        }
        return result;
    }

    private void addSkipped(GrpcScenarioAiCreateResultDTO result, String type, String name, String message) {
        result.setSkippedCount(result.getSkippedCount() + 1);
        result.getItems().add(new GrpcScenarioAiCreateResultDTO.Item(type, name, "SKIPPED", null, message));
    }

    private void block(GrpcScenarioAiDraftDTO.CaseDraft draft, String ambiguity) {
        draft.setBlocked(true);
        draft.setAmbiguity(ambiguity);
    }

    private String normalizePriority(String priority) {
        String value = StringUtils.upperCase(StringUtils.trimToEmpty(priority));
        return Set.of("P0", "P1", "P2", "P3").contains(value) ? value : "P1";
    }

    private String normalizeSafety(String safety) {
        return StringUtils.equalsIgnoreCase(safety, "RISK") ? "RISK" : "SAFE";
    }

    private String limit(String value, int maxLength) {
        return StringUtils.substring(value, 0, maxLength);
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private record DefinitionContext(ApiDefinition definition, ApiGrpcDefinitionDTO grpc, String requestTemplate) {
    }

    @Data
    private static class RawOutput {
        private String sourceSummary;
        private List<String> ambiguities;
        private List<String> warnings;
        private List<RawScenario> scenarios;
    }

    @Data
    private static class RawScenario {
        private String name;
        private String description;
        private String priority;
        private String safety;
        private Boolean selected;
        private Boolean suggested;
        private String ambiguity;
        private List<RawCase> cases;
    }

    @Data
    private static class RawCase {
        private String definitionId;
        private String definitionName;
        private String fullMethodName;
        private String name;
        private Object requestJson;
        private String serverAddress;
        private String priority;
        private Boolean selected;
        private Boolean suggested;
        private String ambiguity;
        private GrpcCaseAssertionConfigDTO assertionConfig;
        private GrpcCaseExtractConfigDTO extractConfig;
    }
}
