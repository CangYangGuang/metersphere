package io.metersphere.api.grpc.service;

import io.metersphere.api.domain.ApiGrpcProto;
import io.metersphere.api.grpc.dto.GrpcProtoVersionDTO;
import io.metersphere.api.mapper.ApiGrpcProtoMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GrpcProtoVersionService {

    private static final int CONTENT_HASH_SHORT_LENGTH = 8;

    @Resource
    private ApiGrpcProtoMapper apiGrpcProtoMapper;

    public GrpcProtoVersionDTO resolveVersion(ApiGrpcProto proto) {
        if (proto == null) {
            return null;
        }
        GrpcProtoVersionDTO dto = new GrpcProtoVersionDTO();
        dto.setProtoId(proto.getId());
        dto.setFileName(resolveFileKey(proto));
        dto.setContentHash(proto.getContentHash());
        dto.setContentHashShort(toShortHash(proto.getContentHash()));
        dto.setCreateTime(proto.getCreateTime());
        int versionNo = resolveVersionNo(proto);
        dto.setVersionNo(versionNo);
        dto.setVersionLabel("v" + versionNo);
        return dto;
    }

    public List<GrpcProtoVersionDTO> listVersionHistory(ApiGrpcProto proto) {
        if (proto == null || StringUtils.isBlank(proto.getProjectId())) {
            return new ArrayList<>();
        }
        String fileKey = resolveFileKey(proto);
        List<ApiGrpcProto> history = apiGrpcProtoMapper.selectByProjectIdAndFileKeyOrderByCreateTime(
                proto.getProjectId(), fileKey);
        List<GrpcProtoVersionDTO> versions = new ArrayList<>();
        for (int index = 0; index < history.size(); index++) {
            ApiGrpcProto item = history.get(index);
            GrpcProtoVersionDTO version = new GrpcProtoVersionDTO();
            version.setProtoId(item.getId());
            version.setFileName(fileKey);
            version.setContentHash(item.getContentHash());
            version.setContentHashShort(toShortHash(item.getContentHash()));
            version.setCreateTime(item.getCreateTime());
            version.setVersionNo(index + 1);
            version.setVersionLabel("v" + (index + 1));
            versions.add(version);
        }
        return versions;
    }

    private int resolveVersionNo(ApiGrpcProto proto) {
        List<ApiGrpcProto> history = apiGrpcProtoMapper.selectByProjectIdAndFileKeyOrderByCreateTime(
                proto.getProjectId(), resolveFileKey(proto));
        for (int index = 0; index < history.size(); index++) {
            if (StringUtils.equals(history.get(index).getId(), proto.getId())) {
                return index + 1;
            }
        }
        return history.isEmpty() ? 1 : history.size();
    }

    public String resolveFileKey(ApiGrpcProto proto) {
        if (StringUtils.isNotBlank(proto.getFileName())) {
            return proto.getFileName();
        }
        return StringUtils.defaultIfBlank(proto.getName(), "upload.proto");
    }

    private String toShortHash(String contentHash) {
        if (StringUtils.isBlank(contentHash)) {
            return "";
        }
        if (contentHash.length() <= CONTENT_HASH_SHORT_LENGTH) {
            return contentHash;
        }
        return contentHash.substring(0, CONTENT_HASH_SHORT_LENGTH);
    }
}
