package io.metersphere.api.parser.step;

import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.scenario.ApiScenarioStepCommonDTO;
import io.metersphere.api.dto.scenario.ApiScenarioStepDetailRequest;
import io.metersphere.api.grpc.scenario.GrpcScenarioStepSupport;
import io.metersphere.plugin.api.spi.AbstractMsTestElement;

/**
 * 默认的步骤解析器
 * 逻辑控制器等步骤，直接将步骤详情解析为 MsTestElement
 * @Author: jianxing
 * @CreateTime: 2024-01-20  15:43
 */
public class DefaultStepParser extends StepParser {

    @Override
    public AbstractMsTestElement parseTestElement(ApiScenarioStepCommonDTO step, String resourceBlob, String stepDetail) {
        if (GrpcScenarioStepSupport.isGrpcScenarioStep(step)
                && ApiScenarioStepType.CUSTOM_REQUEST.name().equals(step.getStepType())) {
            return null;
        }
        return parse2MsTestElement(stepDetail);
    }

    @Override
    public Object parseDetail(ApiScenarioStepDetailRequest step) {
        return parseDetailFromBlob(getStepBlobString(step.getId()));
    }
}
