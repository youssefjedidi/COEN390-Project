package com.coen390.smartexit;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

final class NotificationPermissionHelper {

    private static final int REQUEST_CODE = 1002;
    private static final String PREFERENCES_NAME = "notification_permission_state";
    private static final String REQUEST_ATTEMPTED_KEY = "request_attempted";

    private NotificationPermissionHelper() {
    }

    static void requestIfNeeded(Activity activity, boolean hasTrackedItems) {
        if (!hasTrackedItems
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
                || wasRequested(activity)) {
            return;
        }

        activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(REQUEST_ATTEMPTED_KEY, true)
                .apply();
        activity.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE
        );
    }

    private static boolean wasRequested(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(REQUEST_ATTEMPTED_KEY, false);
    }
}
