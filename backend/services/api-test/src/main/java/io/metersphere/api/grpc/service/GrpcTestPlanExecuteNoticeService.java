package io.metersphere.api.grpc.service;

import io.metersphere.sdk.constants.KafkaTopicConstants;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.dto.api.notice.ApiNoticeDTO;
import io.metersphere.sdk.dto.api.task.ApiRunModeConfigDTO;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class GrpcTestPlanExecuteNoticeService {

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    public void publish(ApiNoticeDTO noticeDTO) {
        kafkaTemplate.send(KafkaTopicConstants.API_REPORT_TASK_TOPIC, JSON.toJSONString(noticeDTO));
    }

    public ApiNoticeDTO buildNotice(String resourceType,
                                    String resourceId,
                                    String reportId,
                                    String reportStatus,
                                    String userId,
                                    String projectId,
                                    String taskId,
                                    String taskItemId,
                                    String queueId,
                                    String setId,
                                    String parentQueueId,
                                    String parentSetId,
                                    ApiRunModeConfigDTO runModeConfig,
                                    Boolean rerun) {
        ApiNoticeDTO noticeDTO = new ApiNoticeDTO();
        noticeDTO.setResourceType(resourceType);
        noticeDTO.setResourceId(resourceId);
        noticeDTO.setReportId(reportId);
        noticeDTO.setReportStatus(reportStatus);
        noticeDTO.setUserId(userId);
        noticeDTO.setProjectId(projectId);
        noticeDTO.setTaskId(taskId);
        noticeDTO.setTaskItemId(taskItemId);
        noticeDTO.setQueueId(queueId);
        noticeDTO.setSetId(setId);
        noticeDTO.setParentQueueId(parentQueueId);
        noticeDTO.setParentSetId(parentSetId);
        noticeDTO.setRerun(rerun);
        if (runModeConfig != null) {
            noticeDTO.setRunModeConfig(runModeConfig);
        }
        return noticeDTO;
    }

    public String resolveReportStatus(boolean success) {
        return success ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name();
    }
}
