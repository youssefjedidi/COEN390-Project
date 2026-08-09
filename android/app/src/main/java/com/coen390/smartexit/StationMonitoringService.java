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
        implements WeightStationConnection.Listener {
    private static final String ACTION_START =
            "com.coen390.smartexit.action.START_MONITORING";
    private static final String ACTION_STOP =
            "com.coen390.smartexit.action.STOP_MONITORING";
    private static final String CHANNEL_ID = "station_monitoring";
    private static final int NOTIFICATION_ID = 2001;

    private StationConnectionManager connectionManager;

    static void start(Context context) {
        Intent intent = new Intent(context, StationMonitoringService.class)
                .setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    static void stop(Context context) {
        Intent intent = new Intent(context, StationMonitoringService.class)
                .setAction(ACTION_STOP);
        context.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        connectionManager = StationConnectionManager.getInstance(this);
        connectionManager.addListener(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground(R.string.monitoring_starting);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            connectionManager.disconnect();
            new StationMonitoringPreferences(this).setMonitoringEnabled(false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        new StationMonitoringPreferences(this).setMonitoringEnabled(true);
        connectionManager.connect();
        updateNotification(connectionManager.getState());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        connectionManager.removeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStateChanged(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        updateNotification(state);
    }

    @Override
    public void onReadingReceived(BluetoothReading reading) {
        // Complete plate cycles are stored by StationConnectionManager.
    }

    @Override
    public void onInvalidPayload() {
        // One malformed packet should not interrupt ongoing monitoring.
    }

    private void startInForeground(int message) {
        Notification notification = buildNotification(message);
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

    private void updateNotification(WeightStationConnection.State state) {
        int message = R.string.monitoring_starting;
        if (state == WeightStationConnection.State.CONNECTED) {
            message = R.string.monitoring_connected;
        } else if (state == WeightStationConnection.State.DISCONNECTED
                || state == WeightStationConnection.State.FAILED) {
            message = R.string.monitoring_reconnecting;
        }

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(message));
        }
    }

    private Notification buildNotification(int message) {
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
                .setContentText(getString(message))
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
