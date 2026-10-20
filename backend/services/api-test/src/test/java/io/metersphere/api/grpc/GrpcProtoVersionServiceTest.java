package io.metersphere.api.grpc;

import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.grpc.service.GrpcProtoVersionService;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcProtoVersionServiceTest {

    private static final String PROJECT_ID = "project-id";

    @Mock
    private ApiGrpcProtoMapper apiGrpcProtoMapper;

    private GrpcProtoVersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = new GrpcProtoVersionService();
        ReflectionTestUtils.setField(versionService, "apiGrpcProtoMapper", apiGrpcProtoMapper);
    }

    @Test
    void shouldResolveFirstProtoVersion() {
        ApiGrpcProto proto = buildProto("proto-v1", "abc123456789", 1000L);
        when(apiGrpcProtoMapper.selectByProjectIdAndFileKeyOrderByCreateTime(eq(PROJECT_ID), eq("helloworld.proto")))
                .thenReturn(List.of(proto));

        GrpcProtoVersionDTO version = versionService.resolveVersion(proto);

        Assertions.assertEquals(1, version.getVersionNo());
        Assertions.assertEquals("v1", version.getVersionLabel());
        Assertions.assertEquals("abc12345", version.getContentHashShort());
        Assertions.assertEquals("proto-v1", version.getProtoId());
    }

    @Test
    void shouldResolveSecondProtoVersionAfterContentChange() {
        ApiGrpcProto protoV1 = buildProto("proto-v1", "hash-v1", 1000L);
        ApiGrpcProto protoV2 = buildProto("proto-v2", "hash-v2", 2000L);
        when(apiGrpcProtoMapper.selectByProjectIdAndFileKeyOrderByCreateTime(eq(PROJECT_ID), eq("helloworld.proto")))
                .thenReturn(List.of(protoV1, protoV2));

        GrpcProtoVersionDTO version = versionService.resolveVersion(protoV2);

        Assertions.assertEquals(2, version.getVersionNo());
        Assertions.assertEquals("v2", version.getVersionLabel());
    }

    @Test
    void shouldListVersionHistoryInCreateOrder() {
        ApiGrpcProto protoV1 = buildProto("proto-v1", "hash-v1", 1000L);
        ApiGrpcProto protoV2 = buildProto("proto-v2", "hash-v2", 2000L);
        when(apiGrpcProtoMapper.selectByProjectIdAndFileKeyOrderByCreateTime(eq(PROJECT_ID), eq("helloworld.proto")))
                .thenReturn(List.of(protoV1, protoV2));

        List<GrpcProtoVersionDTO> history = versionService.listVersionHistory(protoV2);

        Assertions.assertEquals(2, history.size());
        Assertions.assertEquals("v1", history.get(0).getVersionLabel());
        Assertions.assertEquals("proto-v1", history.get(0).getProtoId());
        Assertions.assertEquals("v2", history.get(1).getVersionLabel());
        Assertions.assertEquals("proto-v2", history.get(1).getProtoId());
    }

    @Test
    void shouldFallbackToNameWhenFileNameMissing() {
        ApiGrpcProto proto = buildProto("proto-v1", "hash-v1", 1000L);
        proto.setFileName(null);
        proto.setName("upload.proto");
        when(apiGrpcProtoMapper.selectByProjectIdAndFileKeyOrderByCreateTime(eq(PROJECT_ID), eq("upload.proto")))
                .thenReturn(List.of(proto));

        GrpcProtoVersionDTO version = versionService.resolveVersion(proto);

        Assertions.assertEquals("upload.proto", version.getFileName());
        Assertions.assertEquals(1, version.getVersionNo());
    }

    private ApiGrpcProto buildProto(String id, String contentHash, long createTime) {
        ApiGrpcProto proto = new ApiGrpcProto();
        proto.setId(id);
        proto.setProjectId(PROJECT_ID);
        proto.setFileName("helloworld.proto");
        proto.setName("helloworld.proto");
        proto.setContentHash(contentHash);
        proto.setCreateTime(createTime);
        return proto;
    }
}
