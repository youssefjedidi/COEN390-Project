package com.coen390.smartexit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DashboardItemDisplayStateTest {

    @Test
    public void liveDashboardKeepsRecognitionStateWording() {
        assertEquals(
                DashboardItemDisplayState.PRESENT,
                DashboardItemDisplayState.from(TrackedItemStatus.PRESENT, false)
        );
        assertEquals(
                DashboardItemDisplayState.MISSING,
                DashboardItemDisplayState.from(TrackedItemStatus.MISSING, false)
        );
        assertEquals(
                DashboardItemDisplayState.UNKNOWN,
                DashboardItemDisplayState.from(TrackedItemStatus.UNKNOWN, false)
        );
    }

    @Test
    public void savedDashboardKeepsPreviouslyPresentItemsDistinctFromLiveData() {
        assertEquals(
                DashboardItemDisplayState.WAS_ON_TRAY,
                DashboardItemDisplayState.from(TrackedItemStatus.PRESENT, true)
        );
    }

    @Test
    public void savedDashboardKeepsPreviouslyAbsentItemsDistinctFromLiveData() {
        assertEquals(
                DashboardItemDisplayState.WAS_NOT_ON_TRAY,
                DashboardItemDisplayState.from(TrackedItemStatus.MISSING, true)
        );
    }

    @Test
    public void savedDashboardKeepsUncertainItemsDistinctFromLiveData() {
        assertEquals(
                DashboardItemDisplayState.WAS_UNKNOWN,
                DashboardItemDisplayState.from(TrackedItemStatus.UNKNOWN, true)
        );
    }
}
