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
    public void cachedDashboardWarnsAboutItemsStillOnTheTray() {
        assertEquals(
                DashboardItemDisplayState.STILL_ON_TRAY,
                DashboardItemDisplayState.from(TrackedItemStatus.PRESENT, true)
        );
    }

    @Test
    public void cachedDashboardDoesNotCallAnAbsentItemMissing() {
        assertEquals(
                DashboardItemDisplayState.NOT_ON_TRAY,
                DashboardItemDisplayState.from(TrackedItemStatus.MISSING, true)
        );
    }

    @Test
    public void cachedDashboardKeepsUncertainItemsUnknown() {
        assertEquals(
                DashboardItemDisplayState.UNKNOWN,
                DashboardItemDisplayState.from(TrackedItemStatus.UNKNOWN, true)
        );
    }
}
