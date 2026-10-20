package io.metersphere.api.grpc.scenario;

import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GrpcScenarioVariableResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    public String resolve(String text, GrpcScenarioVariableContext context) {
        if (StringUtils.isBlank(text) || context == null) {
            return text;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String value = context.get(variableName);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    public List<GrpcMetadataItemDTO> resolveMetadata(List<GrpcMetadataItemDTO> metadata,
                                                     GrpcScenarioVariableContext context) {
        if (CollectionUtils.isEmpty(metadata)) {
            return new ArrayList<>();
        }
        List<GrpcMetadataItemDTO> resolved = new ArrayList<>(metadata.size());
        for (GrpcMetadataItemDTO item : metadata) {
            if (item == null) {
                continue;
            }
            GrpcMetadataItemDTO copy = new GrpcMetadataItemDTO();
            copy.setKey(item.getKey());
            copy.setValue(resolve(item.getValue(), context));
            resolved.add(copy);
        }
        return resolved;
    }
}
