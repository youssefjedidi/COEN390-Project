package com.coen390.smartexit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class MonitoringLifecycleTest {
    private MonitoringLifecycle lifecycle;

    @Before
    public void setUp() {
        lifecycle = new MonitoringLifecycle();
    }

    @Test
    public void monitoringStartsOnlyAfterTheStationConnects() {
        lifecycle.start();
        assertEquals(MonitoringLifecycle.State.STARTING, lifecycle.getState());

        lifecycle.connected();
        assertEquals(MonitoringLifecycle.State.MONITORING, lifecycle.getState());
    }

    @Test
    public void linkLossStartsReconnectingAndRequestsGracePeriod() {
        lifecycle.start();
        lifecycle.connected();

        boolean shouldScheduleReminder = lifecycle.disconnect(
                MonitoringLifecycle.DisconnectCause.LINK_LOSS
        );

        assertEquals(MonitoringLifecycle.State.RECONNECTING, lifecycle.getState());
        assertTrue(shouldScheduleReminder);
    }

    @Test
    public void repeatedLinkLossDoesNotScheduleAnotherGracePeriod() {
        lifecycle.start();
        lifecycle.connected();
        lifecycle.disconnect(MonitoringLifecycle.DisconnectCause.LINK_LOSS);

        assertFalse(lifecycle.disconnect(MonitoringLifecycle.DisconnectCause.LINK_LOSS));
    }

    @Test
    public void manualStopNeverLooksLikeDeparture() {
        lifecycle.start();
        lifecycle.connected();

        boolean shouldScheduleReminder = lifecycle.disconnect(
                MonitoringLifecycle.DisconnectCause.MANUAL_STOP
        );

        assertEquals(MonitoringLifecycle.State.STOPPED, lifecycle.getState());
        assertFalse(shouldScheduleReminder);
    }

    @Test
    public void unavailableBluetoothPausesWithoutReminder() {
        lifecycle.start();
        lifecycle.connected();

        boolean shouldScheduleReminder = lifecycle.disconnect(
                MonitoringLifecycle.DisconnectCause.BLUETOOTH_OFF
        );

        assertEquals(MonitoringLifecycle.State.PAUSED, lifecycle.getState());
        assertEquals(
                MonitoringLifecycle.PauseReason.BLUETOOTH_OFF,
                lifecycle.getPauseReason()
        );
        assertFalse(shouldScheduleReminder);
    }

    @Test
    public void unavailablePermissionPausesWithoutReminder() {
        lifecycle.start();
        lifecycle.connected();

        boolean shouldScheduleReminder = lifecycle.disconnect(
                MonitoringLifecycle.DisconnectCause.PERMISSION_UNAVAILABLE
        );

        assertEquals(MonitoringLifecycle.State.PAUSED, lifecycle.getState());
        assertEquals(
                MonitoringLifecycle.PauseReason.PERMISSION_UNAVAILABLE,
                lifecycle.getPauseReason()
        );
        assertFalse(shouldScheduleReminder);
    }
}
