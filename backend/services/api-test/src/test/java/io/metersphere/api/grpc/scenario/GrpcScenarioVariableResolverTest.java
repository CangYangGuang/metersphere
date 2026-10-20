package io.metersphere.api.grpc.scenario;

import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class GrpcScenarioVariableResolverTest {

    private final GrpcScenarioVariableResolver resolver = new GrpcScenarioVariableResolver();

    @Test
    void shouldResolveVariableInRequestJson() {
        GrpcScenarioVariableContext context = new GrpcScenarioVariableContext();
        context.put("name", "MeterSphere");

        String resolved = resolver.resolve("{\"name\":\"${name}\"}", context);

        Assertions.assertEquals("{\"name\":\"MeterSphere\"}", resolved);
    }

    @Test
    void shouldKeepUnresolvedPlaceholder() {
        GrpcScenarioVariableContext context = new GrpcScenarioVariableContext();

        String resolved = resolver.resolve("{\"name\":\"${missing}\"}", context);

        Assertions.assertEquals("{\"name\":\"${missing}\"}", resolved);
    }

    @Test
    void shouldResolveMetadataValues() {
        GrpcScenarioVariableContext context = new GrpcScenarioVariableContext();
        context.put("token", "abc123");

        GrpcMetadataItemDTO metadataItem = new GrpcMetadataItemDTO();
        metadataItem.setKey("authorization");
        metadataItem.setValue("Bearer ${token}");

        List<GrpcMetadataItemDTO> resolved = resolver.resolveMetadata(List.of(metadataItem), context);

        Assertions.assertEquals("Bearer abc123", resolved.getFirst().getValue());
    }
}
