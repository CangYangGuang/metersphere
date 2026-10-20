package io.metersphere.api.grpc.service;

import io.metersphere.api.domain.ApiGrpcDefinition;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcMethodInfoDTO;
import io.metersphere.api.grpc.dto.GrpcParseProtoResponse;
import io.metersphere.api.grpc.dto.GrpcProtoFileDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.dto.GrpcServiceInfoDTO;
import io.metersphere.api.grpc.exception.GrpcResultCode;
import io.metersphere.api.grpc.util.GrpcProtoBundleStorage;
import io.metersphere.api.grpc.util.GrpcProtoBundleStorage.ParsedBundle;
import io.metersphere.api.grpc.util.GrpcReflectionProtoStorage;
import io.metersphere.api.grpc.util.GrpcReflectionProtoStorage.ParsedReflection;
import io.metersphere.api.mapper.ApiGrpcDefinitionMapper;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class GrpcDefinitionPersistenceService {

    @Resource
    private ApiGrpcProtoMapper apiGrpcProtoMapper;
    @Resource
    private ApiGrpcDefinitionMapper apiGrpcDefinitionMapper;
    @Resource
    private GrpcProtoService grpcProtoService;
    @Resource
    private GrpcProtoVersionService grpcProtoVersionService;
    @Resource
    private GrpcProtoChangeImpactService grpcProtoChangeImpactService;

    public GrpcParseProtoResponse parseProto(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (GrpcReflectionProtoStorage.hasReflectionInput(grpcAdd)) {
            return parseFromDescriptorContent(grpcAdd.getDescriptorContent());
        }
        return grpcProtoService.parseProto(GrpcProtoBundleStorage.toParseRequest(grpcAdd));
    }

    public ApiGrpcProto saveOrGetProto(String projectId, ApiGrpcDefinitionAddDTO grpcAdd,
                                       GrpcParseProtoResponse parseResponse, String userId) {
        String contentHash = resolveContentHash(grpcAdd);
        ApiGrpcProto existing = apiGrpcProtoMapper.selectByProjectIdAndContentHash(projectId, contentHash);
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId(IDGenerator.nextStr());
        proto.setProjectId(projectId);
        proto.setName(resolveProtoName(grpcAdd));
        proto.setFileName(resolveStoredFileName(grpcAdd));
        proto.setContent(serializeForStorage(grpcAdd));
        proto.setDescriptorContent(StringUtils.defaultIfBlank(parseResponse.getDescriptorContent(),
                grpcAdd.getDescriptorContent()));
        proto.setContentHash(contentHash);
        proto.setCreateUser(userId);
        proto.setUpdateUser(userId);
        proto.setCreateTime(now);
        proto.setUpdateTime(now);
        proto.setDeleted(false);
        apiGrpcProtoMapper.insertSelective(proto);
        return proto;
    }

    public ApiGrpcDefinition saveGrpcDefinition(String apiDefinitionId, ApiGrpcDefinitionAddDTO grpcAdd,
                                                ApiGrpcProto proto, GrpcParseProtoResponse parseResponse,
                                                String userId) {
        long now = System.currentTimeMillis();
        ApiGrpcDefinition grpcDefinition = new ApiGrpcDefinition();
        grpcDefinition.setId(IDGenerator.nextStr());
        grpcDefinition.setApiDefinitionId(apiDefinitionId);
        grpcDefinition.setCreateUser(userId);
        grpcDefinition.setCreateTime(now);
        grpcDefinition.setDeleted(false);
        applyMethodInfo(grpcDefinition, grpcAdd, proto, parseResponse, userId);
        apiGrpcDefinitionMapper.insertSelective(grpcDefinition);
        return grpcDefinition;
    }

    public ApiGrpcDefinition updateGrpcDefinition(String apiDefinitionId, String projectId,
                                                  ApiGrpcDefinitionAddDTO grpcAdd, String userId) {
        ApiGrpcDefinition existing = getActiveGrpcDefinition(apiDefinitionId);
        String oldProtoId = existing.getProtoId();
        restoreStoredBundleDependencies(existing, grpcAdd);
        GrpcParseProtoResponse parseResponse = parseProto(grpcAdd);
        ApiGrpcProto proto = saveOrGetProto(projectId, grpcAdd, parseResponse, userId);
        applyMethodInfo(existing, grpcAdd, proto, parseResponse, userId);
        apiGrpcDefinitionMapper.updateByPrimaryKeySelective(existing);
        if (!StringUtils.equals(oldProtoId, proto.getId())) {
            releaseProtoIfUnused(oldProtoId, userId);
        }
        return existing;
    }

    public ApiGrpcDefinition updateGrpcDefinition(String apiDefinitionId, ApiGrpcDefinitionAddDTO grpcAdd,
                                                  ApiGrpcProto proto, GrpcParseProtoResponse parseResponse,
                                                  String userId) {
        ApiGrpcDefinition existing = getActiveGrpcDefinition(apiDefinitionId);
        String oldProtoId = existing.getProtoId();
        applyMethodInfo(existing, grpcAdd, proto, parseResponse, userId);
        apiGrpcDefinitionMapper.updateByPrimaryKeySelective(existing);
        if (!StringUtils.equals(oldProtoId, proto.getId())) {
            releaseProtoIfUnused(oldProtoId, userId);
        }
        return existing;
    }

    private void restoreStoredBundleDependencies(ApiGrpcDefinition existing, ApiGrpcDefinitionAddDTO grpcAdd) {
        if (grpcAdd == null || GrpcReflectionProtoStorage.hasReflectionInput(grpcAdd)
                || (grpcAdd.getProtoFiles() != null && grpcAdd.getProtoFiles().size() > 1)) {
            return;
        }
        ApiGrpcProto storedProto = apiGrpcProtoMapper.selectByPrimaryKey(existing.getProtoId());
        ParsedBundle storedBundle = GrpcProtoBundleStorage.parseStored(storedProto);
        if (storedBundle == null) {
            return;
        }

        String mainFileName = storedBundle.mainFileName();
        String incomingMainContent = StringUtils.trimToNull(grpcAdd.getProtoContent());
        List<GrpcProtoFileDTO> restoredFiles = new ArrayList<>();
        for (GrpcProtoFileDTO storedFile : storedBundle.protoFiles()) {
            GrpcProtoFileDTO restored = new GrpcProtoFileDTO();
            restored.setFileName(storedFile.getFileName());
            boolean main = StringUtils.equals(storedFile.getFileName(), mainFileName);
            restored.setMain(main);
            restored.setContent(main && incomingMainContent != null ? incomingMainContent : storedFile.getContent());
            restoredFiles.add(restored);
        }
        grpcAdd.setProtoFiles(restoredFiles);
        grpcAdd.setMainFileName(mainFileName);
        grpcAdd.setFileName(mainFileName);
        grpcAdd.setProtoContent(restoredFiles.stream()
                .filter(file -> Boolean.TRUE.equals(file.getMain()))
                .map(GrpcProtoFileDTO::getContent)
                .findFirst()
                .orElse(grpcAdd.getProtoContent()));
    }

    public void deleteByApiDefinitionIds(List<String> apiDefinitionIds, String userId) {
        if (CollectionUtils.isEmpty(apiDefinitionIds)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String apiDefinitionId : apiDefinitionIds) {
            List<ApiGrpcDefinition> grpcDefinitions = apiGrpcDefinitionMapper.selectByApiDefinitionId(apiDefinitionId);
            if (CollectionUtils.isEmpty(grpcDefinitions)) {
                continue;
            }
            for (ApiGrpcDefinition grpcDefinition : grpcDefinitions) {
                softDeleteGrpcDefinition(grpcDefinition.getId(), userId, now);
                releaseProtoIfUnused(grpcDefinition.getProtoId(), userId);
            }
        }
    }

    public void recoverByApiDefinitionIds(List<String> apiDefinitionIds, String userId) {
        if (CollectionUtils.isEmpty(apiDefinitionIds)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String apiDefinitionId : apiDefinitionIds) {
            List<ApiGrpcDefinition> deletedDefinitions =
                    apiGrpcDefinitionMapper.selectDeletedByApiDefinitionId(apiDefinitionId);
            if (CollectionUtils.isEmpty(deletedDefinitions)) {
                continue;
            }
            for (ApiGrpcDefinition grpcDefinition : deletedDefinitions) {
                restoreGrpcDefinition(grpcDefinition.getId(), userId, now);
                restoreProtoIfNeeded(grpcDefinition.getProtoId(), userId, now);
            }
        }
    }

    public void ensureInactiveByApiDefinitionIds(List<String> apiDefinitionIds, String userId) {
        deleteByApiDefinitionIds(apiDefinitionIds, userId);
    }

    public ApiGrpcDefinitionDTO loadDtoByApiDefinitionId(String apiDefinitionId) {
        List<ApiGrpcDefinition> grpcDefinitions = apiGrpcDefinitionMapper.selectByApiDefinitionId(apiDefinitionId);
        if (grpcDefinitions == null || grpcDefinitions.isEmpty()) {
            return null;
        }
        ApiGrpcDefinition grpcDefinition = grpcDefinitions.getFirst();
        ApiGrpcProto proto = apiGrpcProtoMapper.selectByPrimaryKey(grpcDefinition.getProtoId());
        return toDto(grpcDefinition, proto);
    }

    public String getActiveProtoId(String apiDefinitionId) {
        List<ApiGrpcDefinition> grpcDefinitions = apiGrpcDefinitionMapper.selectByApiDefinitionId(apiDefinitionId);
        if (CollectionUtils.isEmpty(grpcDefinitions)) {
            return null;
        }
        return grpcDefinitions.getFirst().getProtoId();
    }

    private ApiGrpcDefinition getActiveGrpcDefinition(String apiDefinitionId) {
        List<ApiGrpcDefinition> grpcDefinitions = apiGrpcDefinitionMapper.selectByApiDefinitionId(apiDefinitionId);
        if (CollectionUtils.isEmpty(grpcDefinitions)) {
            throw new MSException(GrpcResultCode.GRPC_DEFINITION_NOT_FOUND);
        }
        return grpcDefinitions.getFirst();
    }

    private void applyMethodInfo(ApiGrpcDefinition grpcDefinition, ApiGrpcDefinitionAddDTO grpcAdd,
                                 ApiGrpcProto proto, GrpcParseProtoResponse parseResponse, String userId) {
        GrpcMethodInfoDTO methodInfo = findMethod(parseResponse, grpcAdd.getServiceName(), grpcAdd.getMethodName());
        if (methodInfo == null) {
            throw new MSException(GrpcResultCode.GRPC_METHOD_NOT_FOUND);
        }
        GrpcServiceInfoDTO serviceInfo = findService(parseResponse, grpcAdd.getServiceName());
        long now = System.currentTimeMillis();
        grpcDefinition.setProtoId(proto.getId());
        grpcDefinition.setPackageName(parseResponse.getPackageName());
        grpcDefinition.setServiceName(serviceInfo != null ? serviceInfo.getFullName() : grpcAdd.getServiceName());
        grpcDefinition.setMethodName(methodInfo.getName());
        grpcDefinition.setFullMethodName(methodInfo.getFullName());
        grpcDefinition.setServerAddress(StringUtils.trimToNull(grpcAdd.getServerAddress()));
        grpcDefinition.setMethodType(methodInfo.getMethodType());
        grpcDefinition.setRequestType(methodInfo.getInputType());
        grpcDefinition.setResponseType(methodInfo.getOutputType());
        grpcDefinition.setDescriptorContent(parseResponse.getDescriptorContent());
        grpcDefinition.setUpdateUser(userId);
        grpcDefinition.setUpdateTime(now);
    }

    private void softDeleteGrpcDefinition(String grpcDefinitionId, String userId, long updateTime) {
        ApiGrpcDefinition update = new ApiGrpcDefinition();
        update.setId(grpcDefinitionId);
        update.setDeleted(true);
        update.setUpdateUser(userId);
        update.setUpdateTime(updateTime);
        apiGrpcDefinitionMapper.updateByPrimaryKeySelective(update);
    }

    private void restoreGrpcDefinition(String grpcDefinitionId, String userId, long updateTime) {
        ApiGrpcDefinition update = new ApiGrpcDefinition();
        update.setId(grpcDefinitionId);
        update.setDeleted(false);
        update.setUpdateUser(userId);
        update.setUpdateTime(updateTime);
        apiGrpcDefinitionMapper.updateByPrimaryKeySelective(update);
    }

    private void restoreProtoIfNeeded(String protoId, String userId, long updateTime) {
        if (StringUtils.isBlank(protoId)) {
            return;
        }
        ApiGrpcProto proto = apiGrpcProtoMapper.selectByPrimaryKey(protoId);
        if (proto == null || !Boolean.TRUE.equals(proto.getDeleted())) {
            return;
        }
        ApiGrpcProto update = new ApiGrpcProto();
        update.setId(protoId);
        update.setDeleted(false);
        update.setUpdateUser(userId);
        update.setUpdateTime(updateTime);
        apiGrpcProtoMapper.updateByPrimaryKeySelective(update);
    }

    private void releaseProtoIfUnused(String protoId, String userId) {
        if (StringUtils.isBlank(protoId)) {
            return;
        }
        if (apiGrpcDefinitionMapper.countByProtoId(protoId) > 0) {
            return;
        }
        ApiGrpcProto proto = apiGrpcProtoMapper.selectByPrimaryKey(protoId);
        if (proto == null || Boolean.TRUE.equals(proto.getDeleted())) {
            return;
        }
        long now = System.currentTimeMillis();
        ApiGrpcProto update = new ApiGrpcProto();
        update.setId(protoId);
        update.setDeleted(true);
        update.setUpdateUser(userId);
        update.setUpdateTime(now);
        apiGrpcProtoMapper.updateByPrimaryKeySelective(update);
    }

    private ApiGrpcDefinitionDTO toDto(ApiGrpcDefinition grpcDefinition, ApiGrpcProto proto) {
        ApiGrpcDefinitionDTO dto = new ApiGrpcDefinitionDTO();
        dto.setProtoId(grpcDefinition.getProtoId());
        dto.setFileName(proto != null ? proto.getFileName() : null);
        dto.setPackageName(grpcDefinition.getPackageName());
        dto.setServiceName(grpcDefinition.getServiceName());
        dto.setMethodName(grpcDefinition.getMethodName());
        dto.setFullMethodName(grpcDefinition.getFullMethodName());
        dto.setServerAddress(grpcDefinition.getServerAddress());
        dto.setMethodType(grpcDefinition.getMethodType());
        dto.setInvokable("UNARY".equals(grpcDefinition.getMethodType()));
        dto.setRequestType(grpcDefinition.getRequestType());
        dto.setResponseType(grpcDefinition.getResponseType());
        dto.setDescriptorContent(grpcDefinition.getDescriptorContent());
        if (proto != null) {
            applyStoredProtoFields(dto, proto);
            GrpcProtoVersionDTO protoVersion = grpcProtoVersionService.resolveVersion(proto);
            dto.setProtoVersion(protoVersion);
            dto.setProtoVersionHistory(grpcProtoVersionService.listVersionHistory(proto));
            GrpcProtoChangeImpactDTO impact = grpcProtoChangeImpactService.analyzeForDefinition(grpcDefinition, proto);
            dto.setProtoChangeImpact(impact);
        }
        GrpcParseProtoResponse parseResponse = resolveServicesParseResponse(grpcDefinition, proto);
        if (parseResponse != null) {
            dto.setServices(convertServices(parseResponse.getServices()));
        }
        return dto;
    }

    private GrpcParseProtoResponse resolveServicesParseResponse(ApiGrpcDefinition grpcDefinition, ApiGrpcProto proto) {
        if (proto != null && GrpcReflectionProtoStorage.isReflectionContent(proto.getContent())) {
            return parseFromDescriptorContent(grpcDefinition.getDescriptorContent());
        }
        if (proto != null && StringUtils.isNotBlank(proto.getContent())
                && !GrpcReflectionProtoStorage.isReflectionContent(proto.getContent())) {
            try {
                return grpcProtoService.parseProto(GrpcProtoBundleStorage.toParseRequest(proto));
            } catch (MSException exception) {
                if (StringUtils.isBlank(grpcDefinition.getDescriptorContent())) {
                    throw exception;
                }
                return parseFromDescriptorContent(grpcDefinition.getDescriptorContent());
            }
        }
        if (StringUtils.isNotBlank(grpcDefinition.getDescriptorContent())) {
            return parseFromDescriptorContent(grpcDefinition.getDescriptorContent());
        }
        return null;
    }

    private GrpcParseProtoResponse parseFromDescriptorContent(String descriptorContent) {
        if (StringUtils.isBlank(descriptorContent)) {
            throw new MSException(GrpcResultCode.GRPC_PROTO_COMPILE_FAILED);
        }
        byte[] descriptorBytes = Base64.getDecoder().decode(descriptorContent);
        return grpcProtoService.parseAllServicesFromDescriptorSet(descriptorBytes);
    }

    private void applyStoredProtoFields(ApiGrpcDefinitionDTO dto, ApiGrpcProto proto) {
        ParsedReflection reflection = GrpcReflectionProtoStorage.parseStored(proto);
        if (reflection != null) {
            applyReflectionFields(dto, reflection);
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

    private void applyReflectionFields(ApiGrpcDefinitionDTO dto, ParsedReflection reflection) {
        dto.setReflection(true);
        dto.setReflectionSourceType(reflection.sourceType());
        dto.setReflectionServerAddress(reflection.serverAddress());
        dto.setReflectionUseTls(reflection.useTls());
        dto.setReflectionAuthority(reflection.authority());
        dto.setReflectionServiceFullName(reflection.serviceFullName());
        dto.setReflectionDiscoveredAt(reflection.discoveredAt());
    }

    private List<ApiGrpcDefinitionDTO.GrpcServiceInfoDTO> convertServices(List<GrpcServiceInfoDTO> services) {
        if (services == null) {
            return new ArrayList<>();
        }
        List<ApiGrpcDefinitionDTO.GrpcServiceInfoDTO> result = new ArrayList<>();
        for (GrpcServiceInfoDTO service : services) {
            ApiGrpcDefinitionDTO.GrpcServiceInfoDTO item = new ApiGrpcDefinitionDTO.GrpcServiceInfoDTO();
            item.setName(service.getName());
            item.setFullName(service.getFullName());
            item.setMethods(convertMethods(service.getMethods()));
            result.add(item);
        }
        return result;
    }

    private List<ApiGrpcDefinitionDTO.GrpcMethodInfoDTO> convertMethods(List<GrpcMethodInfoDTO> methods) {
        if (methods == null) {
            return new ArrayList<>();
        }
        List<ApiGrpcDefinitionDTO.GrpcMethodInfoDTO> result = new ArrayList<>();
        for (GrpcMethodInfoDTO method : methods) {
            ApiGrpcDefinitionDTO.GrpcMethodInfoDTO item = new ApiGrpcDefinitionDTO.GrpcMethodInfoDTO();
            item.setName(method.getName());
            item.setFullName(method.getFullName());
            item.setMethodType(method.getMethodType());
            item.setInvokable(method.getInvokable());
            item.setInputType(method.getInputType());
            item.setOutputType(method.getOutputType());
            item.setRequestJsonTemplate(method.getRequestJsonTemplate());
            result.add(item);
        }
        return result;
    }

    private GrpcServiceInfoDTO findService(GrpcParseProtoResponse parseResponse, String serviceName) {
        if (parseResponse.getServices() == null) {
            return null;
        }
        for (GrpcServiceInfoDTO service : parseResponse.getServices()) {
            if (StringUtils.equals(service.getName(), serviceName)
                    || StringUtils.equals(service.getFullName(), serviceName)) {
                return service;
            }
        }
        String shortName = serviceName.contains(".") ? serviceName.substring(serviceName.lastIndexOf('.') + 1) : serviceName;
        for (GrpcServiceInfoDTO service : parseResponse.getServices()) {
            if (StringUtils.equals(service.getName(), shortName)) {
                return service;
            }
        }
        return null;
    }

    private GrpcMethodInfoDTO findMethod(GrpcParseProtoResponse parseResponse, String serviceName, String methodName) {
        GrpcServiceInfoDTO service = findService(parseResponse, serviceName);
        if (service == null || service.getMethods() == null) {
            return null;
        }
        return service.getMethods().stream()
                .filter(method -> StringUtils.equals(method.getName(), methodName))
                .findFirst()
                .orElse(null);
    }

    private String resolveProtoName(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (GrpcReflectionProtoStorage.hasReflectionInput(grpcAdd)) {
            return GrpcReflectionProtoStorage.resolveStoredFileName(grpcAdd);
        }
        String fileName = GrpcProtoBundleStorage.resolveStoredFileName(grpcAdd);
        if (StringUtils.isNotBlank(fileName)) {
            return fileName;
        }
        return "upload.proto";
    }

    private String resolveStoredFileName(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (GrpcReflectionProtoStorage.hasReflectionInput(grpcAdd)) {
            return GrpcReflectionProtoStorage.resolveStoredFileName(grpcAdd);
        }
        return GrpcProtoBundleStorage.resolveStoredFileName(grpcAdd);
    }

    private String serializeForStorage(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (GrpcReflectionProtoStorage.hasReflectionInput(grpcAdd)) {
            return GrpcReflectionProtoStorage.serializeForStorage(grpcAdd);
        }
        return GrpcProtoBundleStorage.serializeForStorage(grpcAdd);
    }

    private String resolveContentHash(ApiGrpcDefinitionAddDTO grpcAdd) {
        if (GrpcReflectionProtoStorage.hasReflectionInput(grpcAdd)) {
            return GrpcReflectionProtoStorage.hashForStorage(grpcAdd);
        }
        return GrpcProtoBundleStorage.hashForStorage(grpcAdd);
    }
}
