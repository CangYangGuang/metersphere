package io.metersphere.api.grpc.service;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.constants.ApiDefinitionStatus;
import io.metersphere.api.domain.ApiDefinition;
import io.metersphere.api.domain.ApiDefinitionExample;
import io.metersphere.api.domain.ApiDefinitionModule;
import io.metersphere.api.domain.ApiDebug;
import io.metersphere.api.domain.ApiDebugExample;
import io.metersphere.api.domain.ApiDebugModule;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.dto.debug.ApiDebugAddRequest;
import io.metersphere.api.dto.debug.ApiDebugUpdateRequest;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionAddDTO;
import io.metersphere.api.dto.definition.ApiDefinitionAddRequest;
import io.metersphere.api.grpc.dto.GrpcBatchGenerateRequest;
import io.metersphere.api.grpc.dto.GrpcBatchGenerateResultDTO;
import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseDebugRequestDTO;
import io.metersphere.api.grpc.dto.GrpcCaseExtractConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseSettingsDTO;
import io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.dto.GrpcServiceInfoDTO;
import io.metersphere.api.mapper.ApiDebugMapper;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.mapper.ApiDefinitionModuleMapper;
import io.metersphere.api.service.debug.ApiDebugModuleService;
import io.metersphere.api.service.debug.ApiDebugService;
import io.metersphere.api.service.definition.ApiDefinitionService;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class GrpcBatchGenerateService {
    private static final int DEFAULT_TIMEOUT = 60000;

    @Resource
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;
    @Resource
    private ApiDebugService apiDebugService;
    @Resource
    private ApiDebugModuleService apiDebugModuleService;
    @Resource
    private ApiDebugMapper apiDebugMapper;
    @Resource
    private ApiDefinitionMapper apiDefinitionMapper;
    @Resource
    private ApiDefinitionModuleMapper apiDefinitionModuleMapper;
    @Resource
    private ApiDefinitionService apiDefinitionService;
    @Resource
    private PlatformTransactionManager transactionManager;

    public GrpcBatchGenerateResultDTO generate(GrpcBatchGenerateRequest request, String userId) {
        validateTargetModule(request, userId);
        GrpcParseProtoResponse parsed = grpcDefinitionPersistenceService.parseProto(toParseRequest(request));
        Map<String, MethodSelection> available = indexMethods(parsed);
        GrpcBatchGenerateResultDTO result = new GrpcBatchGenerateResultDTO();
        TransactionTemplate itemTransaction = new TransactionTemplate(transactionManager);
        itemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (String fullMethodName : new LinkedHashSet<>(request.getMethodFullNames())) {
            GrpcBatchGenerateResultDTO.Item item = new GrpcBatchGenerateResultDTO.Item();
            item.setFullMethodName(fullMethodName);
            item.setModuleId(request.getParentModuleId());
            result.getItems().add(item);
            try {
                MethodSelection selection = resolveMethod(available, fullMethodName);
                if (selection == null) {
                    throw new IllegalArgumentException("Method not found in parsed Proto");
                }
                if (!Boolean.TRUE.equals(selection.method().getInvokable())
                        || !"UNARY".equalsIgnoreCase(selection.method().getMethodType())) {
                    throw new IllegalArgumentException("Streaming method is not supported");
                }
                MethodProcessResult processed = itemTransaction.execute(status ->
                        saveDebugRequest(request, userId, parsed, selection));
                if (processed == null) {
                    throw new IllegalStateException("Batch item transaction returned no result");
                }
                item.setApiDebugId(processed.apiDebugId());
                item.setAction(processed.action());
                if ("CREATED".equals(processed.action())) {
                    result.setCreatedCount(result.getCreatedCount() + 1);
                } else {
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                }
            } catch (Exception e) {
                item.setApiDebugId(null);
                item.setAction("FAILED");
                item.setErrorMessage(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
                result.setFailedCount(result.getFailedCount() + 1);
                log.warn("Failed to batch-save gRPC debug request for method {}", fullMethodName, e);
            }
        }
        return result;
    }

    public GrpcBatchGenerateResultDTO saveDefinitions(GrpcBatchGenerateRequest request, String userId) {
        validateDefinitionTargetModule(request);
        ApiGrpcDefinitionAddDTO parseRequest = toParseRequest(request);
        GrpcParseProtoResponse parsed = grpcDefinitionPersistenceService.parseProto(parseRequest);
        ApiGrpcProto proto = grpcDefinitionPersistenceService.saveOrGetProto(
                request.getProjectId(), parseRequest, parsed, userId);
        Map<String, MethodSelection> available = indexMethods(parsed);
        GrpcBatchGenerateResultDTO result = new GrpcBatchGenerateResultDTO();
        TransactionTemplate itemTransaction = new TransactionTemplate(transactionManager);
        itemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (String fullMethodName : new LinkedHashSet<>(request.getMethodFullNames())) {
            GrpcBatchGenerateResultDTO.Item item = new GrpcBatchGenerateResultDTO.Item();
            item.setFullMethodName(fullMethodName);
            item.setModuleId(request.getParentModuleId());
            result.getItems().add(item);
            try {
                MethodSelection selection = resolveMethod(available, fullMethodName);
                if (selection == null) {
                    throw new IllegalArgumentException("Method not found in parsed Proto");
                }
                if (!Boolean.TRUE.equals(selection.method().getInvokable())
                        || !"UNARY".equalsIgnoreCase(selection.method().getMethodType())) {
                    throw new IllegalArgumentException("Streaming method is not supported");
                }
                DefinitionProcessResult processed = itemTransaction.execute(status ->
                        saveDefinition(request, userId, parsed, proto, selection));
                if (processed == null) {
                    throw new IllegalStateException("Batch definition transaction returned no result");
                }
                item.setApiDefinitionId(processed.apiDefinitionId());
                item.setAction(processed.action());
                if ("CREATED".equals(processed.action())) {
                    result.setCreatedCount(result.getCreatedCount() + 1);
                } else {
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                }
            } catch (Exception e) {
                item.setApiDefinitionId(null);
                item.setAction("FAILED");
                item.setErrorMessage(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
                result.setFailedCount(result.getFailedCount() + 1);
                log.warn("Failed to batch-save gRPC definition for method {}", fullMethodName, e);
            }
        }
        return result;
    }

    private void validateDefinitionTargetModule(GrpcBatchGenerateRequest request) {
        if (StringUtils.equals(request.getParentModuleId(), ModuleConstants.DEFAULT_NODE_ID)) {
            return;
        }
        ApiDefinitionModule module = apiDefinitionModuleMapper.selectByPrimaryKey(request.getParentModuleId());
        if (module == null || !StringUtils.equals(module.getProjectId(), request.getProjectId())) {
            throw new MSException("The selected definition module does not belong to the current project");
        }
    }

    private DefinitionProcessResult saveDefinition(GrpcBatchGenerateRequest request, String userId,
                                                   GrpcParseProtoResponse parsed, ApiGrpcProto proto,
                                                   MethodSelection selection) {
        String name = selection.service().getFullName() + " / " + selection.method().getName();
        ApiGrpcDefinitionAddDTO grpcDefinition = buildDefinitionRequest(request, parsed, selection);
        ApiDefinition existing = findExistingDefinition(request, name);
        if (existing != null) {
            grpcDefinitionPersistenceService.updateGrpcDefinition(
                    existing.getId(), grpcDefinition, proto, parsed, userId);
            return new DefinitionProcessResult(existing.getId(), "UPDATED");
        }

        ApiDefinitionAddRequest addRequest = new ApiDefinitionAddRequest();
        addRequest.setName(name);
        addRequest.setProtocol(ApiConstants.GRPC_PROTOCOL);
        addRequest.setMethod(ApiConstants.GRPC_PROTOCOL);
        addRequest.setPath(StringUtils.defaultString(request.getServerAddress()));
        addRequest.setStatus(ApiDefinitionStatus.PROCESSING.name());
        addRequest.setProjectId(request.getProjectId());
        addRequest.setModuleId(request.getParentModuleId());
        addRequest.setDescription("");
        addRequest.setTags(new LinkedHashSet<>());
        addRequest.setRequest(Collections.emptyMap());
        addRequest.setResponse(Collections.emptyList());
        addRequest.setUploadFileIds(Collections.emptyList());
        addRequest.setLinkFileIds(Collections.emptyList());
        addRequest.setCustomFields(Collections.emptyList());
        addRequest.setGrpcDefinition(grpcDefinition);
        ApiDefinition created = apiDefinitionService.createGrpcDefinitionFromParsed(
                addRequest, userId, parsed, proto);
        return new DefinitionProcessResult(created.getId(), "CREATED");
    }

    private ApiDefinition findExistingDefinition(GrpcBatchGenerateRequest request, String name) {
        ApiDefinitionExample example = new ApiDefinitionExample();
        example.createCriteria()
                .andProjectIdEqualTo(request.getProjectId())
                .andModuleIdEqualTo(request.getParentModuleId())
                .andProtocolEqualTo(ApiConstants.GRPC_PROTOCOL)
                .andNameEqualTo(name)
                .andLatestEqualTo(true)
                .andDeletedEqualTo(false);
        List<ApiDefinition> matches = apiDefinitionMapper.selectByExample(example);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private ApiGrpcDefinitionAddDTO buildDefinitionRequest(GrpcBatchGenerateRequest request,
                                                           GrpcParseProtoResponse parsed,
                                                           MethodSelection selection) {
        ApiGrpcDefinitionAddDTO definition = toParseRequest(request);
        definition.setDescriptorContent(parsed.getDescriptorContent());
        definition.setServerAddress(request.getServerAddress());
        definition.setServiceName(selection.service().getFullName());
        definition.setMethodName(selection.method().getName());
        return definition;
    }

    private void validateTargetModule(GrpcBatchGenerateRequest request, String userId) {
        if (StringUtils.equals(request.getParentModuleId(), ModuleConstants.DEFAULT_NODE_ID)) {
            return;
        }
        ApiDebugModule module = apiDebugModuleService.checkModuleExist(request.getParentModuleId());
        if (!StringUtils.equals(module.getProjectId(), request.getProjectId())
                || !StringUtils.equals(module.getCreateUser(), userId)) {
            throw new MSException("The selected debug module does not belong to the current project");
        }
    }

    private MethodProcessResult saveDebugRequest(GrpcBatchGenerateRequest request, String userId,
                                                 GrpcParseProtoResponse parsed, MethodSelection selection) {
        String name = selection.service().getFullName() + " / " + selection.method().getName();
        GrpcCaseDebugRequestDTO grpcRequest = buildDebugRequest(request, parsed, selection);
        ApiDebug existing = findExisting(request, userId, name);
        if (existing == null) {
            ApiDebugAddRequest addRequest = new ApiDebugAddRequest();
            addRequest.setName(name);
            addRequest.setProtocol(ApiConstants.GRPC_PROTOCOL);
            addRequest.setMethod(ApiConstants.GRPC_PROTOCOL);
            addRequest.setPath(StringUtils.defaultString(request.getServerAddress()));
            addRequest.setProjectId(request.getProjectId());
            addRequest.setModuleId(request.getParentModuleId());
            addRequest.setRequest(grpcRequest);
            addRequest.setUploadFileIds(Collections.emptyList());
            addRequest.setLinkFileIds(Collections.emptyList());
            ApiDebug created = apiDebugService.add(addRequest, userId);
            return new MethodProcessResult(created.getId(), "CREATED");
        }

        ApiDebugUpdateRequest updateRequest = new ApiDebugUpdateRequest();
        updateRequest.setId(existing.getId());
        updateRequest.setName(name);
        updateRequest.setMethod(ApiConstants.GRPC_PROTOCOL);
        updateRequest.setPath(StringUtils.defaultString(request.getServerAddress()));
        updateRequest.setModuleId(request.getParentModuleId());
        updateRequest.setRequest(grpcRequest);
        updateRequest.setUploadFileIds(Collections.emptyList());
        updateRequest.setLinkFileIds(Collections.emptyList());
        apiDebugService.update(updateRequest, userId);
        return new MethodProcessResult(existing.getId(), "UPDATED");
    }

    private ApiDebug findExisting(GrpcBatchGenerateRequest request, String userId, String name) {
        ApiDebugExample example = new ApiDebugExample();
        example.createCriteria()
                .andProjectIdEqualTo(request.getProjectId())
                .andModuleIdEqualTo(request.getParentModuleId())
                .andCreateUserEqualTo(userId)
                .andProtocolEqualTo(ApiConstants.GRPC_PROTOCOL)
                .andNameEqualTo(name);
        List<ApiDebug> matches = apiDebugMapper.selectByExample(example);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private GrpcCaseDebugRequestDTO buildDebugRequest(GrpcBatchGenerateRequest request,
                                                      GrpcParseProtoResponse parsed,
                                                      MethodSelection selection) {
        ApiGrpcCaseConfigAddDTO config = new ApiGrpcCaseConfigAddDTO();
        config.setServiceName(selection.service().getName());
        config.setMethodName(selection.method().getName());
        config.setServerAddress(request.getServerAddress());
        config.setDescriptorContent(parsed.getDescriptorContent());
        config.setProtoContent(resolveMainProtoContent(request));
        config.setFileName(request.getFileName());
        config.setMainFileName(request.getMainFileName());
        config.setProtoFiles(request.getProtoFiles());
        config.setRequestJson(StringUtils.defaultIfBlank(selection.method().getRequestJsonTemplate(), "{}"));
        config.setTimeout(DEFAULT_TIMEOUT);
        config.setAssertionConfig(new GrpcCaseAssertionConfigDTO());
        config.setExtractConfig(new GrpcCaseExtractConfigDTO());
        GrpcCaseSettingsDTO settings = new GrpcCaseSettingsDTO();
        settings.setVerifyServerCertificate(false);
        settings.setIncludeDefaultValues(true);
        config.setSettings(settings);

        GrpcCaseDebugRequestDTO debugRequest = new GrpcCaseDebugRequestDTO();
        debugRequest.setServerAddress(request.getServerAddress());
        debugRequest.setGrpcCaseConfig(config);
        return debugRequest;
    }

    private ApiGrpcDefinitionAddDTO toParseRequest(GrpcBatchGenerateRequest request) {
        ApiGrpcDefinitionAddDTO dto = new ApiGrpcDefinitionAddDTO();
        dto.setProtoContent(resolveMainProtoContent(request));
        dto.setFileName(request.getFileName());
        dto.setProtoFiles(request.getProtoFiles());
        dto.setMainFileName(request.getMainFileName());
        dto.setServerAddress(request.getServerAddress());
        dto.setDescriptorContent(request.getDescriptorContent());
        dto.setReflection(request.getReflection());
        dto.setReflectionSourceType(request.getReflectionSourceType());
        dto.setReflectionServerAddress(request.getReflectionServerAddress());
        dto.setReflectionUseTls(request.getReflectionUseTls());
        dto.setReflectionAuthority(request.getReflectionAuthority());
        dto.setReflectionServiceFullName(request.getReflectionServiceFullName());
        dto.setReflectionDiscoveredAt(request.getReflectionDiscoveredAt());
        dto.setServiceName("__batch__");
        dto.setMethodName("__batch__");
        return dto;
    }

    private String resolveMainProtoContent(GrpcBatchGenerateRequest request) {
        if (StringUtils.isNotBlank(request.getProtoContent()) || request.getProtoFiles() == null) {
            return request.getProtoContent();
        }
        return request.getProtoFiles().stream()
                .filter(Objects::nonNull)
                .filter(file -> Boolean.TRUE.equals(file.getMain())
                        || StringUtils.equals(file.getFileName(), request.getMainFileName()))
                .map(GrpcProtoFileDTO::getContent)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private Map<String, MethodSelection> indexMethods(GrpcParseProtoResponse parsed) {
        Map<String, MethodSelection> result = new LinkedHashMap<>();
        for (GrpcServiceInfoDTO service : parsed.getServices()) {
            for (GrpcMethodInfoDTO method : service.getMethods()) {
                result.put(method.getFullName(), new MethodSelection(service, method));
            }
        }
        return result;
    }

    private MethodSelection resolveMethod(Map<String, MethodSelection> available, String requestedName) {
        String normalizedName = StringUtils.removeStart(StringUtils.trimToEmpty(requestedName), ".");
        MethodSelection exact = available.get(normalizedName);
        if (exact != null) {
            return exact;
        }

        List<MethodSelection> matches = available.entrySet().stream()
                .filter(entry -> StringUtils.removeStart(entry.getKey(), ".").endsWith("." + normalizedName))
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private record MethodSelection(GrpcServiceInfoDTO service, GrpcMethodInfoDTO method) {
    }

    private record MethodProcessResult(String apiDebugId, String action) {
    }

    private record DefinitionProcessResult(String apiDefinitionId, String action) {
    }
}
