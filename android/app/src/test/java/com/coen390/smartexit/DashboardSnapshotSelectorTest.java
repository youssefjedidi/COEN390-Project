package com.coen390.smartexit;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertSame;

public class DashboardSnapshotSelectorTest {

    private final DisconnectSnapshot liveSnapshot = snapshotAt(200L);
    private final DisconnectSnapshot cachedSnapshot = snapshotAt(100L);

    @Test
    public void notificationLaunchShowsTheSavedDisconnectSnapshot() {
        assertSame(
                cachedSnapshot,
                DashboardSnapshotSelector.select(
                        liveSnapshot,
                        cachedSnapshot,
                        true
                )
        );
    }

    @Test
    public void normalLaunchShowsTheLatestLiveSnapshot() {
        assertSame(
                liveSnapshot,
                DashboardSnapshotSelector.select(
                        liveSnapshot,
                        cachedSnapshot,
                        false
                )
        );
    }

    @Test
    public void coldLaunchFallsBackToTheSavedDisconnectSnapshot() {
        assertSame(
                cachedSnapshot,
                DashboardSnapshotSelector.select(null, cachedSnapshot, false)
        );
    }

    @Test
    public void notificationLaunchFallsBackToLiveDataWhenNoSnapshotWasSaved() {
        assertSame(
                liveSnapshot,
                DashboardSnapshotSelector.select(liveSnapshot, null, true)
        );
    }

    private DisconnectSnapshot snapshotAt(long timestampMillis) {
        return DisconnectSnapshot.from(timestampMillis, Collections.emptyList());
    }
}
