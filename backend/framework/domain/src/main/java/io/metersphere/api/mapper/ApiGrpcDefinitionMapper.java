package io.metersphere.api.mapper;

import io.metersphere.api.domain.ApiGrpcDefinition;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ApiGrpcDefinitionMapper {
    int insertSelective(ApiGrpcDefinition record);

    ApiGrpcDefinition selectByPrimaryKey(String id);

    List<ApiGrpcDefinition> selectByApiDefinitionId(@Param("apiDefinitionId") String apiDefinitionId);

    List<ApiGrpcDefinition> selectDeletedByApiDefinitionId(@Param("apiDefinitionId") String apiDefinitionId);

    long countByProtoId(@Param("protoId") String protoId);

    List<ApiGrpcDefinition> selectActiveByProtoId(@Param("protoId") String protoId);

    List<ApiGrpcDefinition> selectActiveByFullMethodName(@Param("fullMethodName") String fullMethodName);

    int updateByPrimaryKeySelective(ApiGrpcDefinition record);
}
