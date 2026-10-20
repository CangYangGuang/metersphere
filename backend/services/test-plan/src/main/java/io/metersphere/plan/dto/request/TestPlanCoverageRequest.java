package io.metersphere.plan.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

@Data
public class TestPlanCoverageRequest {

    @Schema(description = "固定时间天数")
    private Integer dayNumber = 0;

    @Schema(description = "自定义开始时间")
    private Long startTime;

    @Schema(description = "自定义结束时间")
    private Long endTime;

    @Schema(description = "项目ID")
    @NotBlank
    private String projectId;


    public Long getStartTime() {
        if ((startTime == null || startTime == 0) && dayNumber != null && dayNumber > 0) {
            LocalDate now = LocalDate.now();
            LocalDate localDate = now.minusDays(dayNumber);
            LocalDateTime localDateTime = LocalDateTime.of(localDate, LocalTime.MIN);//当天零点
            return localDateTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } else {
            return startTime != null && startTime > 0 ? startTime : null;
        }
    }

    public Long getEndTime() {
        if ((endTime == null || endTime == 0) && dayNumber != null && dayNumber > 0) {
            LocalDateTime now = LocalDateTime.now();
            return now.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } else {
            return endTime != null && endTime > 0 ? endTime : null;
        }
    }

}
