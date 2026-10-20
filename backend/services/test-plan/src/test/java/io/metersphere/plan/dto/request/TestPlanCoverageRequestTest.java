package io.metersphere.plan.dto.request;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestPlanCoverageRequestTest {

    @Test
    void shouldReturnNullTimeRangeForAllPlans() {
        TestPlanCoverageRequest request = new TestPlanCoverageRequest();

        Assertions.assertNull(request.getStartTime());
        Assertions.assertNull(request.getEndTime());
    }

    @Test
    void shouldPreserveCustomTimeRange() {
        TestPlanCoverageRequest request = new TestPlanCoverageRequest();
        request.setStartTime(1000L);
        request.setEndTime(2000L);

        Assertions.assertEquals(1000L, request.getStartTime());
        Assertions.assertEquals(2000L, request.getEndTime());
    }
}
