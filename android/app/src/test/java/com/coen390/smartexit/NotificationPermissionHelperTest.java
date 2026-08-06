package com.coen390.smartexit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NotificationPermissionHelperTest {

    @Test
    public void preAndroid13DoesNotNeedRuntimePermission() {
        assertEquals(
                NotificationPermissionHelper.State.ALLOWED,
                NotificationPermissionHelper.determineState(false, false, false)
        );
    }

    @Test
    public void missingPermissionBeforeFirstRequestIsNotRequested() {
        assertEquals(
                NotificationPermissionHelper.State.NOT_REQUESTED,
                NotificationPermissionHelper.determineState(true, false, false)
        );
    }

    @Test
    public void missingPermissionAfterRequestIsDenied() {
        assertEquals(
                NotificationPermissionHelper.State.DENIED,
                NotificationPermissionHelper.determineState(true, false, true)
        );
    }

    @Test
    public void grantedRuntimePermissionIsAllowed() {
        assertEquals(
                NotificationPermissionHelper.State.ALLOWED,
                NotificationPermissionHelper.determineState(true, true, true)
        );
    }
}
