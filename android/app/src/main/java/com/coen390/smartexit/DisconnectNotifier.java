package com.coen390.smartexit;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;

final class DisconnectNotifier {

    private static final String CHANNEL_ID = "forgotten_items";
    static final int FORGOTTEN_ITEMS_NOTIFICATION_ID = 2002;

    private final Context appContext;
    private final NotificationManager notificationManager;

    DisconnectNotifier(Context context) {
        appContext = context.getApplicationContext();
        notificationManager = appContext.getSystemService(NotificationManager.class);
    }

    void show(DisconnectSnapshot snapshot) {
        List<String> itemNames = snapshot.getPresentItemNames();
        if (notificationManager == null || !shouldNotify(hasPermission(), itemNames)) {
            return;
        }

        ensureChannel();
        String names = String.join(", ", itemNames);
        String message = appContext.getResources().getQuantityString(
                R.plurals.disconnect_notification_message,
                itemNames.size(),
                names
        );

        Intent openApp = MainActivity.newIntentForDisconnectSnapshot(appContext)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                appContext,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(appContext.getString(R.string.disconnect_notification_title))
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        notificationManager.notify(FORGOTTEN_ITEMS_NOTIFICATION_ID, notification);
    }

    static boolean shouldNotify(boolean permissionGranted, List<String> itemNames) {
        return permissionGranted && !itemNames.isEmpty();
    }

    private boolean hasPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    void ensureChannel() {
        if (notificationManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.disconnect_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
        );
        notificationManager.createNotificationChannel(channel);
    }
}
