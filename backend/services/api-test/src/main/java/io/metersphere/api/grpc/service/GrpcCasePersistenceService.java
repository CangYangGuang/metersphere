package io.metersphere.api.grpc.service;

import io.metersphere.api.domain.ApiGrpcCaseConfig;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.dto.request.processors.MsProcessorConfig;
import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseExtractConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseSettingsDTO;
import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.util.GrpcProtoBundleStorage;
import io.metersphere.api.grpc.util.GrpcProtoBundleStorage.ParsedBundle;
import io.metersphere.api.grpc.util.GrpcReflectionProtoStorage;
import io.metersphere.api.grpc.util.GrpcReflectionProtoStorage.ParsedReflection;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.mapper.ApiGrpcCaseConfigMapper;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class GrpcCasePersistenceService {

    public static final int DEFAULT_TIMEOUT_MS = 60000;

    @Resource
    private ApiGrpcCaseConfigMapper apiGrpcCaseConfigMapper;
    @Resource
    private ApiGrpcProtoMapper apiGrpcProtoMapper;
    @Resource
    private GrpcDefinitionPersistenceService grpcDefinitionPersistenceService;
    @Resource
    private GrpcProtoVersionService grpcProtoVersionService;
    @Resource
    private GrpcProtoChangeImpactService grpcProtoChangeImpactService;

    public ApiGrpcCaseConfig saveCaseConfig(String apiCaseId, String apiDefinitionId,
                                            ApiGrpcCaseConfigAddDTO config, String userId) {
        validateConfig(config);
        ApiGrpcDefinitionDTO definition = requireGrpcDefinition(apiDefinitionId);
        long now = System.currentTimeMillis();
        ApiGrpcCaseConfig caseConfig = new ApiGrpcCaseConfig();
        caseConfig.setId(IDGenerator.nextStr());
        caseConfig.setApiCaseId(apiCaseId);
        caseConfig.setApiDefinitionId(apiDefinitionId);
        applyConfigFields(caseConfig, config, definition, userId, now, true);
        caseConfig.setCreateUser(userId);
        caseConfig.setCreateTime(now);
        caseConfig.setDeleted(false);
        apiGrpcCaseConfigMapper.insertSelective(caseConfig);
        return caseConfig;
    }

    public ApiGrpcCaseConfig updateCaseConfig(String apiCaseId, String apiDefinitionId,
                                              ApiGrpcCaseConfigAddDTO config, String userId) {
        validateConfig(config);
        ApiGrpcCaseConfig existing = getActiveCaseConfig(apiCaseId);
        ApiGrpcDefinitionDTO definition = requireGrpcDefinition(apiDefinitionId);
        long now = System.currentTimeMillis();
        applyConfigFields(existing, config, definition, userId, now, false);
        apiGrpcCaseConfigMapper.updateByPrimaryKeySelective(existing);
        return existing;
    }

    public ApiGrpcCaseConfigDTO loadDtoByCaseId(String apiCaseId) {
        ApiGrpcCaseConfig caseConfig = apiGrpcCaseConfigMapper.selectByApiCaseId(apiCaseId);
        if (caseConfig == null) {
            return null;
        }
        return toDto(caseConfig);
    }

    public void softDeleteByCaseIds(List<String> apiCaseIds, String userId) {
        if (CollectionUtils.isEmpty(apiCaseIds)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String apiCaseId : apiCaseIds) {
            ApiGrpcCaseConfig caseConfig = apiGrpcCaseConfigMapper.selectByApiCaseId(apiCaseId);
            if (caseConfig == null) {
                continue;
            }
            ApiGrpcCaseConfig update = new ApiGrpcCaseConfig();
            update.setId(caseConfig.getId());
            update.setDeleted(true);
            update.setUpdateUser(userId);
            update.setUpdateTime(now);
            apiGrpcCaseConfigMapper.updateByPrimaryKeySelective(update);
        }
    }

    public void recoverByCaseIds(List<String> apiCaseIds, String userId) {
        if (CollectionUtils.isEmpty(apiCaseIds)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String apiCaseId : apiCaseIds) {
            List<ApiGrpcCaseConfig> deletedConfigs = apiGrpcCaseConfigMapper.selectDeletedByApiCaseId(apiCaseId);
            if (CollectionUtils.isEmpty(deletedConfigs)) {
                continue;
            }
            for (ApiGrpcCaseConfig caseConfig : deletedConfigs) {
                ApiGrpcCaseConfig update = new ApiGrpcCaseConfig();
                update.setId(caseConfig.getId());
                update.setDeleted(false);
                update.setUpdateUser(userId);
                update.setUpdateTime(now);
                apiGrpcCaseConfigMapper.updateByPrimaryKeySelective(update);
            }
        }
    }

    public void deleteByCaseIds(List<String> apiCaseIds) {
        if (CollectionUtils.isEmpty(apiCaseIds)) {
            return;
        }
        apiGrpcCaseConfigMapper.deleteByApiCaseIds(apiCaseIds);
    }

    private ApiGrpcCaseConfig getActiveCaseConfig(String apiCaseId) {
        ApiGrpcCaseConfig caseConfig = apiGrpcCaseConfigMapper.selectByApiCaseId(apiCaseId);
        if (caseConfig == null) {
            throw new MSException(GrpcResultCode.GRPC_CASE_CONFIG_NOT_FOUND);
        }
        return caseConfig;
    }

    private ApiGrpcDefinitionDTO requireGrpcDefinition(String apiDefinitionId) {
        ApiGrpcDefinitionDTO definition = grpcDefinitionPersistenceService.loadDtoByApiDefinitionId(apiDefinitionId);
        if (definition == null) {
            throw new MSException(GrpcResultCode.GRPC_DEFINITION_NOT_FOUND);
        }
        return definition;
    }

    private void validateConfig(ApiGrpcCaseConfigAddDTO config) {
        if (config == null) {
            throw new MSException(GrpcResultCode.GRPC_CASE_CONFIG_REQUIRED);
        }
    }

    private void applyConfigFields(ApiGrpcCaseConfig target, ApiGrpcCaseConfigAddDTO config,
                                   ApiGrpcDefinitionDTO definition, String userId, long now, boolean isCreate) {
        if (isCreate) {
            target.setProtoId(definition.getProtoId());
        } else if (StringUtils.isNotBlank(config.getProtoId())) {
            target.setProtoId(config.getProtoId());
        }
        target.setServiceName(StringUtils.defaultIfBlank(config.getServiceName(), definition.getServiceName()));
        target.setMethodName(StringUtils.defaultIfBlank(config.getMethodName(), definition.getMethodName()));
        target.setServerAddress(StringUtils.trimToNull(config.getServerAddress()));
        target.setDescriptorContent(StringUtils.defaultIfBlank(config.getDescriptorContent(),
                definition.getDescriptorContent()));
        target.setRequestJson(StringUtils.defaultString(config.getRequestJson(), "{}"));
        target.setMetadata(serializeMetadata(config.getMetadata()));
        target.setTimeout(config.getTimeout() != null ? config.getTimeout() : DEFAULT_TIMEOUT_MS);
        target.setAssertionJson(serializeAssertionConfig(config.getAssertionConfig()));
        target.setExtractJson(serializeExtractConfig(config.getExtractConfig()));
        target.setSettingsJson(serializeJson(config.getSettings()));
        target.setPreProcessorJson(serializeJson(config.getPreProcessorConfig()));
        target.setPostProcessorJson(serializeJson(config.getPostProcessorConfig()));
        target.setUpdateUser(userId);
        target.setUpdateTime(now);
        if (StringUtils.isBlank(target.getServiceName()) || StringUtils.isBlank(target.getMethodName())) {
            throw new MSException(GrpcResultCode.GRPC_METHOD_NOT_FOUND);
        }
        if (StringUtils.isBlank(target.getDescriptorContent())) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED);
        }
    }

    private String serializeMetadata(List<GrpcMetadataItemDTO> metadata) {
        if (metadata == null) {
            return JSON.toJSONString(new ArrayList<>());
        }
        return JSON.toJSONString(metadata);
    }

    private List<GrpcMetadataItemDTO> deserializeMetadata(String metadataJson) {
        if (StringUtils.isBlank(metadataJson)) {
            return new ArrayList<>();
        }
        return JSON.parseArray(metadataJson, GrpcMetadataItemDTO.class);
    }

    private ApiGrpcCaseConfigDTO toDto(ApiGrpcCaseConfig caseConfig) {
        ApiGrpcCaseConfigDTO dto = new ApiGrpcCaseConfigDTO();
        dto.setId(caseConfig.getId());
        dto.setApiCaseId(caseConfig.getApiCaseId());
        dto.setApiDefinitionId(caseConfig.getApiDefinitionId());
        dto.setProtoId(caseConfig.getProtoId());
        dto.setServiceName(caseConfig.getServiceName());
        dto.setMethodName(caseConfig.getMethodName());
        dto.setServerAddress(caseConfig.getServerAddress());
        dto.setDescriptorContent(caseConfig.getDescriptorContent());
        dto.setRequestJson(caseConfig.getRequestJson());
        dto.setMetadata(deserializeMetadata(caseConfig.getMetadata()));
        dto.setTimeout(caseConfig.getTimeout());
        dto.setAssertionConfig(deserializeAssertionConfig(caseConfig.getAssertionJson()));
        dto.setExtractConfig(deserializeExtractConfig(caseConfig.getExtractJson()));
        dto.setSettings(deserializeSettings(caseConfig.getSettingsJson()));
        dto.setPreProcessorConfig(deserializeProcessorConfig(caseConfig.getPreProcessorJson()));
        dto.setPostProcessorConfig(deserializeProcessorConfig(caseConfig.getPostProcessorJson()));
        enrichProtoVersion(dto, caseConfig);
        return dto;
    }

    private String serializeJson(Object value) {
        if (value == null) {
            return null;
        }
        return JSON.toJSONString(value);
    }

    private GrpcCaseSettingsDTO deserializeSettings(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json, GrpcCaseSettingsDTO.class);
    }

    private MsProcessorConfig deserializeProcessorConfig(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json, MsProcessorConfig.class);
    }

    private void enrichProtoVersion(ApiGrpcCaseConfigDTO dto, ApiGrpcCaseConfig caseConfig) {
        if (StringUtils.isBlank(caseConfig.getProtoId())) {
            return;
        }
        ApiGrpcProto proto = apiGrpcProtoMapper.selectByPrimaryKey(caseConfig.getProtoId());
        dto.setProtoVersion(grpcProtoVersionService.resolveVersion(proto));
        applyStoredProtoFields(dto, proto);
        String activeProtoId = grpcDefinitionPersistenceService.getActiveProtoId(caseConfig.getApiDefinitionId());
        if (StringUtils.isNotBlank(activeProtoId)) {
            dto.setProtoVersionOutdated(!StringUtils.equals(caseConfig.getProtoId(), activeProtoId));
            GrpcProtoChangeImpactDTO impact = grpcProtoChangeImpactService.analyzeForCase(caseConfig, activeProtoId);
            dto.setProtoChangeImpact(impact);
        }
    }

    private String serializeAssertionConfig(GrpcCaseAssertionConfigDTO config) {
        if (config == null) {
            return null;
        }
        return JSON.toJSONString(config);
    }

    private String serializeExtractConfig(GrpcCaseExtractConfigDTO config) {
        if (config == null) {
            return null;
        }
        return JSON.toJSONString(config);
    }

    private GrpcCaseAssertionConfigDTO deserializeAssertionConfig(String assertionJson) {
        if (StringUtils.isBlank(assertionJson)) {
            return new GrpcCaseAssertionConfigDTO();
        }
        GrpcCaseAssertionConfigDTO config = JSON.parseObject(assertionJson, GrpcCaseAssertionConfigDTO.class);
        return config != null ? config : new GrpcCaseAssertionConfigDTO();
    }

    private GrpcCaseExtractConfigDTO deserializeExtractConfig(String extractJson) {
        if (StringUtils.isBlank(extractJson)) {
            return new GrpcCaseExtractConfigDTO();
        }
        GrpcCaseExtractConfigDTO config = JSON.parseObject(extractJson, GrpcCaseExtractConfigDTO.class);
        return config != null ? config : new GrpcCaseExtractConfigDTO();
    }

    private void applyStoredProtoFields(ApiGrpcCaseConfigDTO dto, ApiGrpcProto proto) {
        if (proto == null) {
            return;
        }
        ParsedReflection reflection = GrpcReflectionProtoStorage.parseStored(proto);
        if (reflection != null) {
            dto.setFileName(GrpcReflectionProtoStorage.resolveFileKey(reflection.serverAddress()));
            dto.setMainFileName(dto.getFileName());
            dto.setProtoContent(null);
            dto.setProtoFiles(null);
            return;
        }
        ParsedBundle bundle = GrpcProtoBundleStorage.parseStored(proto);
        if (bundle != null) {
            dto.setProtoContent(bundle.mainContent());
            dto.setFileName(bundle.mainFileName());
            dto.setMainFileName(bundle.mainFileName());
            dto.setProtoFiles(bundle.protoFiles());
            return;
        }
        dto.setProtoContent(proto.getContent());
        dto.setFileName(proto.getFileName());
        dto.setMainFileName(proto.getFileName());
    }
}
