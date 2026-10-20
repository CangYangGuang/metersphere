package io.metersphere.api.mapper;

import io.metersphere.api.domain.ApiGrpcCaseConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ApiGrpcCaseConfigMapper {

    int insertSelective(ApiGrpcCaseConfig record);

    ApiGrpcCaseConfig selectByPrimaryKey(String id);

    ApiGrpcCaseConfig selectByApiCaseId(@Param("apiCaseId") String apiCaseId);

    List<ApiGrpcCaseConfig> selectDeletedByApiCaseId(@Param("apiCaseId") String apiCaseId);

    int updateByPrimaryKeySelective(ApiGrpcCaseConfig record);

    int deleteByApiCaseIds(@Param("apiCaseIds") List<String> apiCaseIds);

    List<ApiGrpcCaseConfig> selectActiveByProtoId(@Param("protoId") String protoId);
}
