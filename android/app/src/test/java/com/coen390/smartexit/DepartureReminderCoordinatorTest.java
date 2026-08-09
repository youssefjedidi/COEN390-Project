package com.coen390.smartexit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

public class DepartureReminderCoordinatorTest {
    private static final long GRACE_PERIOD_MS = 10_000L;

    private final ItemProfile keys = new ItemProfile("keys", "Keys", 35.0, 45.0);
    private FakeScheduler scheduler;
    private SnapshotRecorder snapshotRecorder;
    private ReminderRecorder reminderRecorder;
    private DepartureReminderCoordinator coordinator;

    @Before
    public void setUp() {
        scheduler = new FakeScheduler();
        snapshotRecorder = new SnapshotRecorder();
        reminderRecorder = new ReminderRecorder();
        coordinator = new DepartureReminderCoordinator(
                GRACE_PERIOD_MS,
                scheduler,
                snapshotRecorder,
                reminderRecorder
        );
    }

    @Test
    public void linkLossSavesLatestCompleteSnapshotAndStartsGracePeriod() {
        DisconnectSnapshot snapshot = snapshotWithKeys(100L);
        coordinator.onFreshSnapshot(snapshot);

        coordinator.onLinkLost();

        assertSame(snapshot, snapshotRecorder.lastSaved);
        assertEquals(GRACE_PERIOD_MS, scheduler.delayMillis);
        assertTrue(scheduler.hasPendingTask());
        assertEquals(0, reminderRecorder.count);
    }

    @Test
    public void reconnectDuringGracePeriodCancelsReminder() {
        coordinator.onFreshSnapshot(snapshotWithKeys(100L));
        coordinator.onLinkLost();

        coordinator.onReconnected();
        scheduler.runPendingTask();

        assertFalse(scheduler.hasPendingTask());
        assertEquals(0, reminderRecorder.count);
    }

    @Test
    public void prolongedDisconnectSendsExactlyOneReminder() {
        DisconnectSnapshot snapshot = snapshotWithKeys(100L);
        coordinator.onFreshSnapshot(snapshot);
        coordinator.onLinkLost();

        scheduler.runPendingTask();
        coordinator.onLinkLost();

        assertEquals(1, reminderRecorder.count);
        assertSame(snapshot, reminderRecorder.lastSnapshot);
        assertFalse(scheduler.hasPendingTask());
    }

    @Test
    public void freshCycleRearmsReminderAfterReconnection() {
        coordinator.onFreshSnapshot(snapshotWithKeys(100L));
        coordinator.onLinkLost();
        scheduler.runPendingTask();
        coordinator.onReconnected();

        coordinator.onLinkLost();
        assertFalse(scheduler.hasPendingTask());

        coordinator.onFreshSnapshot(snapshotWithKeys(200L));
        coordinator.onLinkLost();
        assertTrue(scheduler.hasPendingTask());
    }

    @Test
    public void stopOrPauseCancelsPendingDeparture() {
        coordinator.onFreshSnapshot(snapshotWithKeys(100L));
        coordinator.onLinkLost();

        coordinator.cancelDeparture();
        scheduler.runPendingTask();

        assertEquals(0, reminderRecorder.count);
    }

    @Test
    public void missingOrEmptySnapshotNeverSchedulesReminder() {
        coordinator.onLinkLost();
        assertFalse(scheduler.hasPendingTask());

        coordinator.onFreshSnapshot(
                DisconnectSnapshot.from(
                        100L,
                        Collections.singletonList(TrackedItemState.missing(keys))
                )
        );
        coordinator.onLinkLost();

        assertFalse(scheduler.hasPendingTask());
        assertEquals(0, reminderRecorder.count);
    }

    private DisconnectSnapshot snapshotWithKeys(long timestampMillis) {
        return DisconnectSnapshot.from(
                timestampMillis,
                Collections.singletonList(TrackedItemState.present(keys, 1))
        );
    }

    private static final class FakeScheduler
            implements DepartureReminderCoordinator.Scheduler {
        private Runnable pendingTask;
        private long delayMillis;

        @Override
        public void schedule(Runnable task, long delayMillis) {
            pendingTask = task;
            this.delayMillis = delayMillis;
        }

        @Override
        public void cancel() {
            pendingTask = null;
        }

        boolean hasPendingTask() {
            return pendingTask != null;
        }

        void runPendingTask() {
            Runnable task = pendingTask;
            pendingTask = null;
            if (task != null) {
                task.run();
            }
        }
    }

    private static final class SnapshotRecorder
            implements DepartureReminderCoordinator.SnapshotStore {
        private DisconnectSnapshot lastSaved;

        @Override
        public void save(DisconnectSnapshot snapshot) {
            lastSaved = snapshot;
        }
    }

    private static final class ReminderRecorder
            implements DepartureReminderCoordinator.ReminderSink {
        private int count;
        private DisconnectSnapshot lastSnapshot;

        @Override
        public void show(DisconnectSnapshot snapshot) {
            count++;
            lastSnapshot = snapshot;
        }
    }
}
