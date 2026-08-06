package com.coen390.smartexit;

final class DashboardSnapshotSelector {

    private DashboardSnapshotSelector() {
    }

    static DisconnectSnapshot select(
            DisconnectSnapshot liveSnapshot,
            DisconnectSnapshot cachedSnapshot,
            boolean showDisconnectSnapshot
    ) {
        if (showDisconnectSnapshot && cachedSnapshot != null) {
            return cachedSnapshot;
        }
        return liveSnapshot != null ? liveSnapshot : cachedSnapshot;
    }
}
