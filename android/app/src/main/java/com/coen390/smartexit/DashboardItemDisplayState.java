package com.coen390.smartexit;

enum DashboardItemDisplayState {
    PRESENT,
    MISSING,
    UNKNOWN,
    WAS_ON_TRAY,
    WAS_NOT_ON_TRAY,
    WAS_UNKNOWN;

    static DashboardItemDisplayState from(
            TrackedItemStatus itemStatus,
            boolean cachedSnapshot
    ) {
        if (!cachedSnapshot) {
            return liveState(itemStatus);
        }
        if (itemStatus == TrackedItemStatus.PRESENT) {
            return WAS_ON_TRAY;
        }
        if (itemStatus == TrackedItemStatus.MISSING) {
            return WAS_NOT_ON_TRAY;
        }
        return WAS_UNKNOWN;
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
