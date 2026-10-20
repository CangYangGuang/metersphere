package io.metersphere.api.grpc.service;

import io.metersphere.project.dto.environment.EnvironmentConfig;
import io.metersphere.project.dto.environment.EnvironmentInfoDTO;
import io.metersphere.project.dto.environment.variables.CommonVariables;
import io.metersphere.project.service.EnvironmentService;
import io.metersphere.sdk.util.LogUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class GrpcTestPlanServerAddressResolver {

    @Resource
    private EnvironmentService environmentService;

    public String resolve(String environmentId) {
        if (StringUtils.isBlank(environmentId)) {
            return null;
        }
        try {
            EnvironmentInfoDTO environmentInfo = environmentService.get(environmentId);
            if (environmentInfo == null || environmentInfo.getConfig() == null) {
                return null;
            }
            EnvironmentConfig environmentConfig = environmentInfo.getConfig();
            if (CollectionUtils.isEmpty(environmentConfig.getCommonVariables())) {
                return null;
            }
            String serverAddress = null;
            for (CommonVariables variable : environmentConfig.getCommonVariables()) {
                if (variable == null || BooleanUtils.isFalse(variable.getEnable()) || StringUtils.isBlank(variable.getKey())) {
                    continue;
                }
                if (StringUtils.equalsAnyIgnoreCase(variable.getKey(), "grpcServerAddress", "serverAddress")) {
                    serverAddress = variable.getValue();
                }
            }
            return serverAddress;
        } catch (Exception exception) {
            LogUtils.warn("读取 gRPC 环境地址失败: {}", environmentId, exception);
            return null;
        }
    }
}
