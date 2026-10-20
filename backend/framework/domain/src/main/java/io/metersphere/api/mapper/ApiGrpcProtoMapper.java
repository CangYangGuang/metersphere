package io.metersphere.api.mapper;

import io.metersphere.api.domain.ApiGrpcProto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ApiGrpcProtoMapper {
    int insertSelective(ApiGrpcProto record);

    ApiGrpcProto selectByPrimaryKey(String id);

    ApiGrpcProto selectByProjectIdAndContentHash(@Param("projectId") String projectId, @Param("contentHash") String contentHash);

    List<ApiGrpcProto> selectByProjectIdAndFileKeyOrderByCreateTime(@Param("projectId") String projectId,
                                                                    @Param("fileKey") String fileKey);

    int updateByPrimaryKeySelective(ApiGrpcProto record);
}
