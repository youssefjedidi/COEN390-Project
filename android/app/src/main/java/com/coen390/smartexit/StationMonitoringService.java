package com.coen390.smartexit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public final class StationMonitoringService extends Service
        implements StationConnectionManager.MonitoringListener {
    private static final String ACTION_START =
            "com.coen390.smartexit.action.START_MONITORING";
    private static final String ACTION_STOP =
            "com.coen390.smartexit.action.STOP_MONITORING";
    private static final String CHANNEL_ID = "station_monitoring";
    private static final int NOTIFICATION_ID = 2001;

    private StationConnectionManager connectionManager;

    static void startMonitoring(Context context) {
        Intent intent = new Intent(context, StationMonitoringService.class)
                .setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    static void stopMonitoring(Context context) {
        Intent intent = new Intent(context, StationMonitoringService.class)
                .setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        connectionManager = StationConnectionManager.getInstance(this);
        connectionManager.addMonitoringListener(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoringService(startId);
            return START_NOT_STICKY;
        }

        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            pauseForMissingPermission(startId);
            return START_NOT_STICKY;
        }

        startInForeground(R.string.monitoring_starting);
        connectionManager.startMonitoring();
        updateNotification(
                connectionManager.getMonitoringState(),
                connectionManager.getMonitoringPauseReason()
        );
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        connectionManager.removeMonitoringListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onMonitoringStateChanged(
            MonitoringLifecycle.State state,
            MonitoringLifecycle.PauseReason pauseReason
    ) {
        if (state == MonitoringLifecycle.State.STOPPED) {
            return;
        }
        if (state == MonitoringLifecycle.State.PAUSED
                && pauseReason == MonitoringLifecycle.PauseReason.PERMISSION_UNAVAILABLE) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        updateNotification(state, pauseReason);
    }

    private void startInForeground(int messageResId) {
        Notification notification = buildNotification(messageResId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(
            MonitoringLifecycle.State state,
            MonitoringLifecycle.PauseReason pauseReason
    ) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(notificationTextFor(state, pauseReason))
            );
        }
    }

    private int notificationTextFor(
            MonitoringLifecycle.State state,
            MonitoringLifecycle.PauseReason pauseReason
    ) {
        switch (state) {
            case MONITORING:
                return R.string.monitoring_connected;
            case RECONNECTING:
                return R.string.monitoring_reconnecting;
            case PAUSED:
                return monitoringPauseTextFor(pauseReason);
            case STARTING:
            case STOPPED:
            default:
                return R.string.monitoring_starting;
        }
    }

    private int monitoringPauseTextFor(MonitoringLifecycle.PauseReason reason) {
        if (reason == null) {
            return R.string.monitoring_paused_unavailable;
        }

        switch (reason) {
            case BLUETOOTH_OFF:
                return R.string.monitoring_paused_bluetooth;
            case PERMISSION_UNAVAILABLE:
                return R.string.monitoring_paused_permission;
            case CONNECTION_UNAVAILABLE:
            default:
                return R.string.monitoring_paused_unavailable;
        }
    }

    private void stopMonitoringService(int startId) {
        connectionManager.stopMonitoring();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf(startId);
    }

    private void pauseForMissingPermission(int startId) {
        connectionManager.pauseMonitoring(
                MonitoringLifecycle.PauseReason.PERMISSION_UNAVAILABLE
        );
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf(startId);
    }

    private Notification buildNotification(int messageResId) {
        Intent openApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_station_monitoring)
                .setContentTitle(getString(R.string.monitoring_notification_title))
                .setContentText(getString(messageResId))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.monitoring_notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.monitoring_notification_channel_detail));
        manager.createNotificationChannel(channel);
    }
}
