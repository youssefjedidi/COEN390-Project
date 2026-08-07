package com.coen390.smartexit;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

final class NotificationPermissionHelper {

    static final int REQUEST_CODE = 1003;
    private static final String PREFERENCES_NAME = "notification_permission_state";
    private static final String REQUEST_ATTEMPTED_KEY = "request_attempted";

    enum State {
        ALLOWED,
        NOT_REQUESTED,
        DENIED
    }

    private NotificationPermissionHelper() {
    }

    static void requestIfNeeded(Activity activity, boolean hasTrackedItems) {
        if (!hasTrackedItems
                || getState(activity) != State.NOT_REQUESTED) {
            return;
        }

        request(activity);
    }

    static void request(Activity activity) {
        activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(REQUEST_ATTEMPTED_KEY, true)
                .apply();
        activity.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE
        );
    }

    static State getState(Context context) {
        boolean runtimePermissionRequired = Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU;
        boolean permissionGranted = !runtimePermissionRequired
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return determineState(
                runtimePermissionRequired,
                permissionGranted,
                wasRequested(context)
        );
    }

    static State determineState(
            boolean runtimePermissionRequired,
            boolean permissionGranted,
            boolean requestAttempted
    ) {
        if (!runtimePermissionRequired || permissionGranted) {
            return State.ALLOWED;
        }
        return requestAttempted ? State.DENIED : State.NOT_REQUESTED;
    }

    private static boolean wasRequested(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(REQUEST_ATTEMPTED_KEY, false);
    }
}
