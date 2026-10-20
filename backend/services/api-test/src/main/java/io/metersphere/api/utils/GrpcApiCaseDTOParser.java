package io.metersphere.api.utils;

import io.metersphere.api.constants.ApiConstants;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigAddDTO;
import io.metersphere.api.dto.definition.ApiGrpcCaseConfigDTO;
import io.metersphere.api.dto.definition.ApiGrpcDefinitionDTO;
import io.metersphere.api.dto.definition.ApiTestCaseDTO;
import io.metersphere.api.grpc.dto.GrpcCaseAssertionConfigDTO;
import io.metersphere.api.grpc.dto.GrpcCaseExtractConfigDTO;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GrpcApiCaseDTOParser {

    private static final Pattern CASE_BLOCK_PATTERN = Pattern.compile("apiCaseStart(.*?)apiCaseEnd", Pattern.DOTALL);
    private static final Pattern CASE_NAME_PATTERN = Pattern.compile("##\\s*用例名称\\s*[：:]\\s*(.+)");
    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("##\\s*serviceName\\s*[：:]\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("##\\s*methodName\\s*[：:]\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_SECTION_PATTERN = Pattern.compile(
            "##\\s*(requestJson|assertionConfig|extractConfig)\\s*\\R```json\\s*\\R(.*?)\\R```",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private GrpcApiCaseDTOParser() {
    }

    public static ApiTestCaseDTO parse(String apiDefinitionId, String content, ApiGrpcDefinitionDTO definition) {
        if (definition == null) {
            throw new MSException("gRPC AI 用例解析失败：未找到 gRPC 接口定义");
        }
        String caseContent = extractCaseBlock(content);
        ApiTestCaseDTO caseDTO = new ApiTestCaseDTO();
        caseDTO.setApiDefinitionId(apiDefinitionId);
        caseDTO.setProtocol(ApiConstants.GRPC_PROTOCOL);
        caseDTO.setName(parseCaseName(caseContent));
        caseDTO.setGrpcCaseConfig(buildGrpcCaseConfig(caseContent, definition));
        return caseDTO;
    }

    public static ApiGrpcCaseConfigAddDTO toAddDTO(ApiGrpcCaseConfigDTO configDTO) {
        if (configDTO == null) {
            throw new MSException("gRPC AI 用例解析失败：用例配置为空");
        }
        ApiGrpcCaseConfigAddDTO addDTO = new ApiGrpcCaseConfigAddDTO();
        addDTO.setProtoId(configDTO.getProtoId());
        addDTO.setServiceName(configDTO.getServiceName());
        addDTO.setMethodName(configDTO.getMethodName());
        addDTO.setServerAddress(configDTO.getServerAddress());
        addDTO.setDescriptorContent(configDTO.getDescriptorContent());
        addDTO.setProtoContent(configDTO.getProtoContent());
        addDTO.setFileName(configDTO.getFileName());
        addDTO.setMainFileName(configDTO.getMainFileName());
        addDTO.setProtoFiles(configDTO.getProtoFiles());
        addDTO.setRequestJson(configDTO.getRequestJson());
        addDTO.setMetadata(configDTO.getMetadata());
        addDTO.setTimeout(configDTO.getTimeout());
        addDTO.setAssertionConfig(configDTO.getAssertionConfig());
        addDTO.setExtractConfig(configDTO.getExtractConfig());
        addDTO.setSettings(configDTO.getSettings());
        addDTO.setPreProcessorConfig(configDTO.getPreProcessorConfig());
        addDTO.setPostProcessorConfig(configDTO.getPostProcessorConfig());
        return addDTO;
    }

    private static String extractCaseBlock(String content) {
        if (StringUtils.isBlank(content)) {
            throw new MSException("gRPC AI 用例解析失败：内容为空");
        }
        Matcher matcher = CASE_BLOCK_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new MSException("gRPC AI 用例解析失败：未找到 apiCaseStart/apiCaseEnd 标记");
        }
        return matcher.group(1);
    }

    private static String parseCaseName(String caseContent) {
        Matcher matcher = CASE_NAME_PATTERN.matcher(caseContent);
        if (!matcher.find()) {
            throw new MSException("gRPC AI 用例解析失败：未找到用例名称");
        }
        String name = matcher.group(1).trim();
        if (StringUtils.isBlank(name)) {
            throw new MSException("gRPC AI 用例解析失败：用例名称为空");
        }
        return name;
    }

    private static ApiGrpcCaseConfigDTO buildGrpcCaseConfig(String caseContent, ApiGrpcDefinitionDTO definition) {
        validateServiceAndMethod(caseContent, definition);

        String requestJson = parseJsonSection(caseContent, "requestJson");
        validateJsonObject(requestJson, "requestJson");

        GrpcCaseAssertionConfigDTO assertionConfig = parseAssertionConfig(caseContent);
        GrpcCaseExtractConfigDTO extractConfig = parseExtractConfig(caseContent);

        ApiGrpcCaseConfigDTO configDTO = new ApiGrpcCaseConfigDTO();
        configDTO.setProtoId(definition.getProtoId());
        configDTO.setServiceName(definition.getServiceName());
        configDTO.setMethodName(definition.getMethodName());
        configDTO.setServerAddress(StringUtils.defaultIfBlank(
                definition.getServerAddress(), definition.getReflectionServerAddress()));
        configDTO.setDescriptorContent(definition.getDescriptorContent());
        configDTO.setProtoContent(definition.getProtoContent());
        configDTO.setFileName(definition.getFileName());
        configDTO.setMainFileName(definition.getMainFileName());
        configDTO.setProtoFiles(definition.getProtoFiles());
        configDTO.setRequestJson(requestJson);
        configDTO.setAssertionConfig(assertionConfig);
        configDTO.setExtractConfig(extractConfig);
        return configDTO;
    }

    private static void validateServiceAndMethod(String caseContent, ApiGrpcDefinitionDTO definition) {
        Matcher serviceMatcher = SERVICE_NAME_PATTERN.matcher(caseContent);
        if (serviceMatcher.find()) {
            String serviceName = serviceMatcher.group(1).trim();
            if (!StringUtils.equals(serviceName, definition.getServiceName())) {
                throw new MSException("gRPC AI 用例解析失败：serviceName 与接口定义不一致");
            }
        }
        Matcher methodMatcher = METHOD_NAME_PATTERN.matcher(caseContent);
        if (methodMatcher.find()) {
            String methodName = methodMatcher.group(1).trim();
            if (!StringUtils.equals(methodName, definition.getMethodName())) {
                throw new MSException("gRPC AI 用例解析失败：methodName 与接口定义不一致");
            }
        }
    }

    private static String parseJsonSection(String caseContent, String sectionName) {
        Matcher matcher = JSON_SECTION_PATTERN.matcher(caseContent);
        while (matcher.find()) {
            if (StringUtils.equalsIgnoreCase(sectionName, matcher.group(1).trim())) {
                return matcher.group(2).trim();
            }
        }
        if (StringUtils.equalsIgnoreCase(sectionName, "requestJson")) {
            throw new MSException("gRPC AI 用例解析失败：未找到 requestJson");
        }
        return null;
    }

    private static void validateJsonObject(String json, String fieldName) {
        if (StringUtils.isBlank(json)) {
            throw new MSException("gRPC AI 用例解析失败：" + fieldName + " 不能为空");
        }
        try {
            Object parsed = JSON.parseObject(json);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new MSException("gRPC AI 用例解析失败：" + fieldName + " 必须是合法 JSON 对象");
            }
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MSException("gRPC AI 用例解析失败：" + fieldName + " 必须是合法 JSON 对象");
        }
    }

    private static GrpcCaseAssertionConfigDTO parseAssertionConfig(String caseContent) {
        String assertionJson = parseJsonSection(caseContent, "assertionConfig");
        if (StringUtils.isBlank(assertionJson)) {
            return new GrpcCaseAssertionConfigDTO();
        }
        validateJsonObject(assertionJson, "assertionConfig");
        GrpcCaseAssertionConfigDTO config = JSON.parseObject(assertionJson, GrpcCaseAssertionConfigDTO.class);
        return config != null ? config : new GrpcCaseAssertionConfigDTO();
    }

    private static GrpcCaseExtractConfigDTO parseExtractConfig(String caseContent) {
        String extractJson = parseJsonSection(caseContent, "extractConfig");
        if (StringUtils.isBlank(extractJson)) {
            return new GrpcCaseExtractConfigDTO();
        }
        validateJsonObject(extractJson, "extractConfig");
        GrpcCaseExtractConfigDTO config = JSON.parseObject(extractJson, GrpcCaseExtractConfigDTO.class);
        return config != null ? config : new GrpcCaseExtractConfigDTO();
    }
}
