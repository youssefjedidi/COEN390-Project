package com.coen390.smartexit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

final class BluetoothPermissionHelper {
    private static final String PREFERENCES_NAME = "bluetooth_permission_state";
    private static final String REQUEST_ATTEMPTED_KEY = "request_attempted";

    private BluetoothPermissionHelper() {
    }

    static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        }

        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    static boolean hasRequiredPermissions(Context context) {
        for (String permission : requiredPermissions()) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    static void recordPermissionRequest(Context context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(REQUEST_ATTEMPTED_KEY, true)
                .apply();
    }

    static boolean wasPermissionRequested(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(REQUEST_ATTEMPTED_KEY, false);
    }

    static boolean supportsBle(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }
}
