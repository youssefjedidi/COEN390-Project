package com.coen390.smartexit;

final class DepartureReminderCoordinator {

    interface Scheduler {
        void schedule(Runnable task, long delayMillis);

        void cancel();
    }

    interface SnapshotStore {
        void save(DisconnectSnapshot snapshot);
    }

    interface ReminderSink {
        void show(DisconnectSnapshot snapshot);
    }

    private final long gracePeriodMillis;
    private final Scheduler scheduler;
    private final SnapshotStore snapshotStore;
    private final ReminderSink reminderSink;
    private DisconnectSnapshot latestSnapshot;
    private DisconnectSnapshot pendingSnapshot;
    private boolean armed;

    DepartureReminderCoordinator(
            long gracePeriodMillis,
            Scheduler scheduler,
            SnapshotStore snapshotStore,
            ReminderSink reminderSink
    ) {
        this.gracePeriodMillis = gracePeriodMillis;
        this.scheduler = scheduler;
        this.snapshotStore = snapshotStore;
        this.reminderSink = reminderSink;
    }

    void onFreshSnapshot(DisconnectSnapshot snapshot) {
        latestSnapshot = snapshot;
        armed = true;
    }

    void onLinkLost() {
        if (!armed || latestSnapshot == null) {
            return;
        }

        armed = false;
        snapshotStore.save(latestSnapshot);
        if (latestSnapshot.getPresentItemNames().isEmpty()) {
            return;
        }

        pendingSnapshot = latestSnapshot;
        scheduler.schedule(this::sendPendingReminder, gracePeriodMillis);
    }

    void onReconnected() {
        clearPendingReminder();
    }

    void cancelDeparture() {
        armed = false;
        clearPendingReminder();
    }

    private void sendPendingReminder() {
        DisconnectSnapshot snapshot = pendingSnapshot;
        pendingSnapshot = null;
        if (snapshot != null) {
            reminderSink.show(snapshot);
        }
    }

    private void clearPendingReminder() {
        scheduler.cancel();
        pendingSnapshot = null;
    }
}
