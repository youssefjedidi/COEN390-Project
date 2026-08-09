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
    private DisconnectSnapshot latestCompleteSnapshot;
    private DisconnectSnapshot snapshotAwaitingReminder;
    private boolean reminderArmed;

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
        latestCompleteSnapshot = snapshot;
        reminderArmed = true;
    }

    void onLinkLost() {
        if (!reminderArmed || latestCompleteSnapshot == null) {
            return;
        }

        reminderArmed = false;
        snapshotStore.save(latestCompleteSnapshot);
        if (latestCompleteSnapshot.getPresentItemNames().isEmpty()) {
            return;
        }

        snapshotAwaitingReminder = latestCompleteSnapshot;
        scheduler.schedule(this::sendPendingReminder, gracePeriodMillis);
    }

    void onReconnected() {
        clearPendingReminder();
    }

    void cancelDeparture() {
        reminderArmed = false;
        clearPendingReminder();
    }

    private void sendPendingReminder() {
        DisconnectSnapshot snapshot = snapshotAwaitingReminder;
        snapshotAwaitingReminder = null;
        if (snapshot != null) {
            reminderSink.show(snapshot);
        }
    }

    private void clearPendingReminder() {
        scheduler.cancel();
        snapshotAwaitingReminder = null;
    }
}
