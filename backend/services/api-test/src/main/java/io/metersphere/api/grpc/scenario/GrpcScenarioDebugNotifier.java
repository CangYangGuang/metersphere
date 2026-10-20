package io.metersphere.api.grpc.scenario;

import io.metersphere.api.grpc.scenario.dto.GrpcScenarioRunResultDTO;
import io.metersphere.sdk.constants.MsgType;
import io.metersphere.sdk.dto.SocketMsgDTO;
import io.metersphere.sdk.dto.api.result.TaskResultDTO;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.sdk.util.WebSocketUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class GrpcScenarioDebugNotifier {

    @Resource
    private GrpcScenarioResultConverter grpcScenarioResultConverter;

    public void notifyResult(String reportId, GrpcScenarioRunResultDTO runResult) {
        if (StringUtils.isBlank(reportId)) {
            return;
        }
        TaskResultDTO taskResult = grpcScenarioResultConverter.toTaskResult(runResult);
        SocketMsgDTO message = SocketMsgDTO.builder()
                .reportId(reportId)
                .msgType(MsgType.EXEC_RESULT.name())
                .taskResult(taskResult)
                .build();
        try {
            WebSocketUtils.sendMessageSingle(message);
        } catch (Exception exception) {
            LogUtils.error("gRPC 场景调试结果推送失败: {}", reportId, exception);
        }
    }

    public void notifyEnd(String reportId) {
        if (StringUtils.isBlank(reportId)) {
            return;
        }
        SocketMsgDTO message = SocketMsgDTO.builder()
                .reportId(reportId)
                .msgType(MsgType.EXEC_END.name())
                .build();
        try {
            WebSocketUtils.sendMessageSingle(message);
        } catch (Exception exception) {
            LogUtils.error("gRPC 场景调试结束推送失败: {}", reportId, exception);
        }
    }
}
