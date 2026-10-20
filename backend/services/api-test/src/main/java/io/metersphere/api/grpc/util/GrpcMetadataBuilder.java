package io.metersphere.api.grpc.util;

import io.grpc.Metadata;
import io.metersphere.api.grpc.dto.GrpcMetadataItemDTO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public final class GrpcMetadataBuilder {

    private GrpcMetadataBuilder() {
    }

    public static Metadata buildMetadata(List<GrpcMetadataItemDTO> metadataItems) {
        Metadata metadata = new Metadata();
        if (CollectionUtils.isEmpty(metadataItems)) {
            return metadata;
        }
        for (GrpcMetadataItemDTO item : metadataItems) {
            if (item == null || Boolean.FALSE.equals(item.getEnabled())) {
                continue;
            }
            if (StringUtils.isBlank(item.getKey()) || item.getValue() == null) {
                continue;
            }
            Metadata.Key<String> key = Metadata.Key.of(item.getKey().trim(), Metadata.ASCII_STRING_MARSHALLER);
            metadata.put(key, item.getValue());
        }
        return metadata;
    }
}
