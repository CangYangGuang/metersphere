package io.metersphere.api.utils;

import io.metersphere.system.utils.PromptTemplateCache;
import org.springframework.stereotype.Component;

@Component
public class GrpcApiCasePromptTemplateCache extends PromptTemplateCache {

    public static final String TEMPLATE_FILENAME = "apiCaseGrpcAiTemplate.vm";

    @Override
    public String getCacheKey() {
        return null;
    }

    @Override
    public String getTemplateFileName() {
        return TEMPLATE_FILENAME;
    }
}
