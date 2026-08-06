package com.coen390.smartexit;

enum DashboardItemDisplayState {
    PRESENT,
    MISSING,
    UNKNOWN,
    STILL_ON_TRAY,
    NOT_ON_TRAY;

    static DashboardItemDisplayState from(
            TrackedItemStatus itemStatus,
            boolean cachedSnapshot
    ) {
        if (!cachedSnapshot) {
            return liveState(itemStatus);
        }
        if (itemStatus == TrackedItemStatus.PRESENT) {
            return STILL_ON_TRAY;
        }
        if (itemStatus == TrackedItemStatus.MISSING) {
            return NOT_ON_TRAY;
        }
        return UNKNOWN;
    }

    private static DashboardItemDisplayState liveState(TrackedItemStatus itemStatus) {
        if (itemStatus == TrackedItemStatus.PRESENT) {
            return PRESENT;
        }
        if (itemStatus == TrackedItemStatus.MISSING) {
            return MISSING;
        }
        return UNKNOWN;
    }
}
