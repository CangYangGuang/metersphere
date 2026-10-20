package io.metersphere.api.grpc.service;

import io.metersphere.api.domain.ApiGrpcCaseConfig;
import io.metersphere.api.domain.ApiGrpcDefinition;
import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.grpc.dto.GrpcProtoAffectedCaseDTO;
import io.metersphere.api.grpc.dto.GrpcProtoAffectedDefinitionDTO;
import io.metersphere.api.grpc.dto.GrpcProtoChangeImpactDTO;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.util.GrpcProtoSchemaSnapshotBuilder;
import io.metersphere.api.grpc.util.GrpcProtoSchemaSnapshotBuilder.GrpcProtoSchemaSnapshot;
import io.metersphere.api.mapper.ApiGrpcCaseConfigMapper;
import io.metersphere.api.mapper.ApiGrpcDefinitionMapper;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class GrpcProtoChangeImpactService {

    @Resource
    private ApiGrpcProtoMapper apiGrpcProtoMapper;
    @Resource
    private ApiGrpcDefinitionMapper apiGrpcDefinitionMapper;
    @Resource
    private ApiGrpcCaseConfigMapper apiGrpcCaseConfigMapper;
    @Resource
    private GrpcProtoSchemaSnapshotBuilder schemaSnapshotBuilder;
    @Resource
    private GrpcProtoChangeImpactAnalyzer impactAnalyzer;
    @Resource
    private GrpcProtoVersionService grpcProtoVersionService;

    public GrpcProtoChangeImpactDTO analyzeForDefinition(ApiGrpcDefinition grpcDefinition, ApiGrpcProto currentProto) {
        if (grpcDefinition == null || currentProto == null) {
            return emptyImpact();
        }
        ApiGrpcProto previousProto = resolvePreviousProto(currentProto);
        if (previousProto == null || StringUtils.equals(previousProto.getId(), currentProto.getId())) {
            return emptyImpact();
        }
        GrpcProtoChangeImpactDTO impact = compareProtosSafely(
                previousProto,
                currentProto,
                grpcDefinition.getServiceName(),
                grpcDefinition.getMethodName(),
                grpcDefinition.getRequestType(),
                grpcDefinition.getResponseType());
        if (GrpcProtoChangeImpactAnalyzer.LEVEL_NONE.equals(impact.getImpactLevel())) {
            impact.setImpactLevel(GrpcProtoChangeImpactAnalyzer.LEVEL_INFO);
            impact.setImpactSummary("Proto version changed without structural differences");
        }
        enrichAffectedResources(impact, previousProto.getId(), grpcDefinition.getApiDefinitionId());
        return impact;
    }

    public GrpcProtoChangeImpactDTO analyzeForCase(ApiGrpcCaseConfig caseConfig, String activeProtoId) {
        if (caseConfig == null || StringUtils.isBlank(caseConfig.getProtoId())) {
            return emptyImpact();
        }
        if (StringUtils.isBlank(activeProtoId) || StringUtils.equals(caseConfig.getProtoId(), activeProtoId)) {
            return emptyImpact();
        }
        ApiGrpcProto caseProto = apiGrpcProtoMapper.selectByPrimaryKey(caseConfig.getProtoId());
        ApiGrpcProto activeProto = apiGrpcProtoMapper.selectByPrimaryKey(activeProtoId);
        if (caseProto == null || activeProto == null) {
            return emptyImpact();
        }
        GrpcProtoChangeImpactDTO impact = compareProtosSafely(
                caseProto,
                activeProto,
                caseConfig.getServiceName(),
                caseConfig.getMethodName(),
                null,
                null);
        if (GrpcProtoChangeImpactAnalyzer.LEVEL_NONE.equals(impact.getImpactLevel())) {
            impact.setImpactLevel(GrpcProtoChangeImpactAnalyzer.LEVEL_INFO);
            impact.setImpactSummary("Case uses an older proto version; review changes before upgrading");
        }
        GrpcProtoAffectedCaseDTO affectedCase = new GrpcProtoAffectedCaseDTO();
        affectedCase.setApiCaseId(caseConfig.getApiCaseId());
        affectedCase.setServiceName(caseConfig.getServiceName());
        affectedCase.setMethodName(caseConfig.getMethodName());
        affectedCase.setProtoId(caseConfig.getProtoId());
        impact.getAffectedCases().add(affectedCase);
        return impact;
    }

    private GrpcProtoChangeImpactDTO compareProtosSafely(ApiGrpcProto oldProto,
                                                         ApiGrpcProto newProto,
                                                         String serviceName,
                                                         String methodName,
                                                         String requestType,
                                                         String responseType) {
        try {
            return compareProtos(oldProto, newProto, serviceName, methodName, requestType, responseType);
        } catch (RuntimeException exception) {
            log.warn("Unable to compare gRPC proto versions {} and {}",
                    oldProto.getId(), newProto.getId(), exception);
            GrpcProtoChangeImpactDTO impact = emptyImpact();
            impact.setImpactLevel(GrpcProtoChangeImpactAnalyzer.LEVEL_INFO);
            impact.setImpactSummary("Proto change impact is unavailable because a stored version could not be parsed");
            return impact;
        }
    }

    private GrpcProtoChangeImpactDTO compareProtos(ApiGrpcProto oldProto,
                                                   ApiGrpcProto newProto,
                                                   String serviceName,
                                                   String methodName,
                                                   String requestType,
                                                   String responseType) {
        GrpcProtoSchemaSnapshot oldSnapshot = schemaSnapshotBuilder.build(oldProto);
        GrpcProtoSchemaSnapshot newSnapshot = schemaSnapshotBuilder.build(newProto);
        return impactAnalyzer.analyze(oldSnapshot, newSnapshot, serviceName, methodName, requestType, responseType);
    }

    private ApiGrpcProto resolvePreviousProto(ApiGrpcProto currentProto) {
        List<GrpcProtoVersionDTO> history = grpcProtoVersionService.listVersionHistory(currentProto);
        if (history.size() < 2) {
            return null;
        }
        GrpcProtoVersionDTO currentVersion = history.stream()
                .filter(item -> StringUtils.equals(item.getProtoId(), currentProto.getId()))
                .findFirst()
                .orElse(history.get(history.size() - 1));
        int currentIndex = history.indexOf(currentVersion);
        if (currentIndex <= 0) {
            return null;
        }
        String previousProtoId = history.get(currentIndex - 1).getProtoId();
        return apiGrpcProtoMapper.selectByPrimaryKey(previousProtoId);
    }

    private void enrichAffectedResources(GrpcProtoChangeImpactDTO impact,
                                         String previousProtoId,
                                         String currentApiDefinitionId) {
        if (GrpcProtoChangeImpactAnalyzer.LEVEL_NONE.equals(impact.getImpactLevel())
                || GrpcProtoChangeImpactAnalyzer.LEVEL_INFO.equals(impact.getImpactLevel())) {
            return;
        }
        List<ApiGrpcDefinition> definitions = apiGrpcDefinitionMapper.selectActiveByProtoId(previousProtoId);
        if (!CollectionUtils.isEmpty(definitions)) {
            for (ApiGrpcDefinition definition : definitions) {
                if (StringUtils.equals(definition.getApiDefinitionId(), currentApiDefinitionId)) {
                    continue;
                }
                GrpcProtoAffectedDefinitionDTO item = new GrpcProtoAffectedDefinitionDTO();
                item.setApiDefinitionId(definition.getApiDefinitionId());
                item.setServiceName(definition.getServiceName());
                item.setMethodName(definition.getMethodName());
                impact.getAffectedDefinitions().add(item);
            }
        }
        List<ApiGrpcCaseConfig> cases = apiGrpcCaseConfigMapper.selectActiveByProtoId(previousProtoId);
        if (!CollectionUtils.isEmpty(cases)) {
            for (ApiGrpcCaseConfig caseConfig : cases) {
                GrpcProtoAffectedCaseDTO item = new GrpcProtoAffectedCaseDTO();
                item.setApiCaseId(caseConfig.getApiCaseId());
                item.setServiceName(caseConfig.getServiceName());
                item.setMethodName(caseConfig.getMethodName());
                item.setProtoId(caseConfig.getProtoId());
                impact.getAffectedCases().add(item);
            }
        }
    }

    private GrpcProtoChangeImpactDTO emptyImpact() {
        GrpcProtoChangeImpactDTO impact = new GrpcProtoChangeImpactDTO();
        impact.setImpactLevel(GrpcProtoChangeImpactAnalyzer.LEVEL_NONE);
        impact.setImpactSummary("No proto change impact");
        return impact;
    }

}
