package com.coen390.smartexit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DisconnectNotifierTest {

    @Test
    public void reminderCannotReplaceTheForegroundServiceNotification() {
        assertNotEquals(
                StationMonitoringService.MONITORING_NOTIFICATION_ID,
                DisconnectNotifier.FORGOTTEN_ITEMS_NOTIFICATION_ID
        );
    }

    @Test
    public void deniedPermissionPreventsNotificationDelivery() {
        assertFalse(
                DisconnectNotifier.shouldNotify(
                        false,
                        Collections.singletonList("Keys")
                )
        );
    }

    @Test
    public void noRemainingItemsDoesNotNeedANotification() {
        assertFalse(DisconnectNotifier.shouldNotify(true, Collections.emptyList()));
    }

    @Test
    public void oneRemainingItemNeedsANotification() {
        assertTrue(
                DisconnectNotifier.shouldNotify(
                        true,
                        Collections.singletonList("Keys")
                )
        );
    }

    @Test
    public void severalRemainingItemsNeedOneNotification() {
        assertTrue(
                DisconnectNotifier.shouldNotify(
                        true,
                        Arrays.asList("Keys", "Wallet", "Medication")
                )
        );
    }
}
