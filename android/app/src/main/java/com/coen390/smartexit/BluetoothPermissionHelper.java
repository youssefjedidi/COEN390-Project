package com.coen390.smartexit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

final class BluetoothPermissionHelper {
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

    static boolean supportsBle(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }
}
