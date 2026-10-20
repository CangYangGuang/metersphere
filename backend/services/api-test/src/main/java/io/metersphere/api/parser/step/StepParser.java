package io.metersphere.api.parser.step;

import io.metersphere.api.constants.ApiScenarioStepRefType;
import io.metersphere.api.domain.ApiScenarioStepBlob;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.dto.scenario.ApiScenarioStepDetailRequest;
import io.metersphere.api.grpc.util.GrpcProtocolUtils;
import io.metersphere.api.mapper.ApiScenarioStepBlobMapper;
import io.metersphere.api.utils.ApiDataUtils;
import io.metersphere.plugin.api.spi.AbstractMsTestElement;
import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author: jianxing
 * @CreateTime: 2024-01-20  15:43
 */
public abstract class StepParser {

    /**
     * 将步骤详情解析为 MsTestElement
     *
     * @param step         步骤
     * @param resourceBlob 关联的资源详情
     * @param stepDetail   步骤详情
     * @return
     */
    public abstract AbstractMsTestElement parseTestElement(ApiScenarioStepCommonDTO step, String resourceBlob, String stepDetail);

    /**
     * 将步骤解析为步骤详情
     * 场景步骤，返回 ScenarioConfig
     * 其余返回 MsTestElement
     *
     * @param step
     * @return
     */
    public abstract Object parseDetail(ApiScenarioStepDetailRequest step);


    protected boolean isRef(String refType) {
        return StringUtils.equalsAny(refType, ApiScenarioStepRefType.REF.name(), ApiScenarioStepRefType.PARTIAL_REF.name());
    }

    protected static AbstractMsTestElement parse2MsTestElement(String blobContent) {
        if (StringUtils.isBlank(blobContent)) {
            return null;
        }
        // gRPC 等内置协议步骤的 blob 不是多态 MsTestElement（缺少 polymorphicName 判别字段，
        // 例如导入 gRPC 用例复制后的空对象 "{}" 或自定义 gRPC 配置）。直接反序列化为
        // AbstractMsTestElement 会抛 InvalidTypeIdException，导致步骤/报告详情接口 500。
        // 无判别字段时安全返回 null，详情由上层按协议单独渲染。
        if (!hasPolymorphicDiscriminator(blobContent)) {
            return null;
        }
        return ApiDataUtils.parseObject(blobContent, AbstractMsTestElement.class);
    }

    /**
     * 解析场景步骤 blob 为详情对象。MsTestElement 优先；gRPC 自定义步骤返回原始 JSON Map 供前端回显。
     */
    protected static Object parseDetailFromBlob(String blobContent) {
        if (StringUtils.isBlank(blobContent)) {
            return null;
        }
        AbstractMsTestElement element = parse2MsTestElement(blobContent);
        if (element != null) {
            return element;
        }
        if (isGrpcScenarioStepBlob(blobContent)) {
            return JSON.parseObject(blobContent, LinkedHashMap.class);
        }
        return null;
    }

    protected static boolean isGrpcScenarioStepBlob(String blobContent) {
        try {
            Map<?, ?> map = JSON.parseObject(blobContent, Map.class);
            if (map == null || map.isEmpty()) {
                return false;
            }
            Object protocol = map.get("protocol");
            if (protocol != null && GrpcProtocolUtils.isGrpcProtocol(String.valueOf(protocol))) {
                return true;
            }
            Object method = map.get("method");
            if (method != null && GrpcProtocolUtils.isGrpcProtocol(String.valueOf(method))) {
                return true;
            }
            if (map.get("grpcCaseConfig") != null) {
                return true;
            }
            Object grpc = map.get("grpc");
            if (grpc instanceof Map<?, ?> grpcMap) {
                if (hasGrpcExecutableFields(grpcMap)) {
                    return true;
                }
            }
            return hasGrpcExecutableFields(map);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasGrpcExecutableFields(Map<?, ?> map) {
        return map.containsKey("serverAddress")
                || map.containsKey("serviceName")
                || map.containsKey("methodName")
                || map.containsKey("requestJson")
                || map.containsKey("descriptorContent")
                || map.containsKey("protoContent");
    }

    private static boolean hasPolymorphicDiscriminator(String blobContent) {
        try {
            Map<?, ?> map = JSON.parseObject(blobContent, Map.class);
            return map != null && map.get("polymorphicName") != null
                    && StringUtils.isNotBlank(String.valueOf(map.get("polymorphicName")));
        } catch (Exception e) {
            return false;
        }
    }

    public static Object parseStoredStepDetail(String stepId) {
        ApiScenarioStepBlobMapper apiScenarioStepBlobMapper = CommonBeanFactory.getBean(ApiScenarioStepBlobMapper.class);
        ApiScenarioStepBlob apiScenarioStepBlob = apiScenarioStepBlobMapper.selectByPrimaryKey(stepId);
        if (apiScenarioStepBlob == null) {
            return null;
        }
        return parseDetailFromBlob(new String(apiScenarioStepBlob.getContent()));
    }

    protected String getStepBlobString(String stepId) {
        ApiScenarioStepBlobMapper apiScenarioStepBlobMapper = CommonBeanFactory.getBean(ApiScenarioStepBlobMapper.class);
        ApiScenarioStepBlob apiScenarioStepBlob = apiScenarioStepBlobMapper.selectByPrimaryKey(stepId);
        if (apiScenarioStepBlob == null) {
            return null;
        }
        return new String(apiScenarioStepBlob.getContent());
    }

    public <T extends AbstractMsTestElement> T parseConfig2TestElement(Object config, Class<T> clazz) {
        if (config != null && config instanceof Map confiMap) {
            confiMap.put("polymorphicName", clazz.getSimpleName());
            return JSON.parseObject(JSON.toJSONString(confiMap), clazz);
        }
        return null;
    }
    public <T extends AbstractMsTestElement> T parseConfig2TestElement(ApiScenarioStepCommonDTO step, Class<T> clazz) {
        AbstractMsTestElement testElement = parseConfig2TestElement(step.getConfig(), clazz);
        if (testElement == null) {
            return null;
        }
        testElement.setName(step.getName());
        testElement.setEnable(step.getEnable());
        testElement.setStepId(step.getId());
        testElement.setProjectId(step.getProjectId());
        return (T) testElement;
    }
}
