package io.metersphere.dashboard.request;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DashboardFrontPageRequestTest {

    @Test
    void shouldReturnNullTimeRangeForAllTimeRequest() {
        DashboardFrontPageRequest request = new DashboardFrontPageRequest();
        request.setDayNumber(0);

        Assertions.assertNull(request.getToStartTime());
        Assertions.assertNull(request.getToEndTime());

        request.setDayNumber(null);
        Assertions.assertNull(request.getToStartTime());
        Assertions.assertNull(request.getToEndTime());
    }

    @Test
    void shouldPreserveCustomTimeRange() {
        DashboardFrontPageRequest request = new DashboardFrontPageRequest();
        request.setDayNumber(null);
        request.setStartTime(1000L);
        request.setEndTime(2000L);

        Assertions.assertEquals(1000L, request.getToStartTime());
        Assertions.assertEquals(2000L, request.getToEndTime());
    }

    @Test
    void shouldCalculateRecentTimeRange() {
        DashboardFrontPageRequest request = new DashboardFrontPageRequest();
        request.setDayNumber(3);

        long before = System.currentTimeMillis();
        Long startTime = request.getToStartTime();
        Long endTime = request.getToEndTime();
        long after = System.currentTimeMillis();

        Assertions.assertNotNull(startTime);
        Assertions.assertNotNull(endTime);
        Assertions.assertTrue(startTime < before);
        Assertions.assertTrue(endTime >= before);
        Assertions.assertTrue(endTime <= after);
        Assertions.assertTrue(endTime >= startTime);
    }
}
