package com.coen390.smartexit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class StationBackgroundMonitoringFlowTest {
    private static final String STATION_ADDRESS = "00:11:22:33:44:55";
    private static final int REQUIRED_SAMPLES = 3;

    @Test
    public void monitoringFlowReconnectsAndRemindsOnceWithoutAnActivity() {
        ItemProfile keys = new ItemProfile("keys", "House keys", 35.0, 45.0);
        FlowHarness flow = new FlowHarness(Collections.singletonList(keys));

        flow.startAndConnect();
        assertEquals(MonitoringLifecycle.State.MONITORING, flow.lifecycle.getState());
        assertEquals(
                WeightStationConnection.ConnectionMode.DIRECT,
                flow.transport.lastConnectionMode
        );

        flow.sendCompleteCycle(
                "1,40.0,OK",
                "2,0.0,NO_LOAD",
                "3,0.0,NO_LOAD",
                "4,0.0,NO_LOAD"
        );
        assertItemPresent(flow.snapshotStore.restore(), "keys", 1);

        flow.transport.dropConnection();
        assertEquals(MonitoringLifecycle.State.RECONNECTING, flow.lifecycle.getState());
        assertEquals(
                WeightStationConnection.ConnectionMode.AUTO_RECONNECT,
                flow.transport.lastConnectionMode
        );
        assertTrue(flow.scheduler.hasPendingTask());

        flow.transport.finishConnection();
        flow.scheduler.runPendingTask();
        assertEquals(0, flow.reminderRecorder.count);

        flow.sendCompleteCycle(
                "1,0.0,NO_LOAD",
                "2,0.0,NO_LOAD",
                "3,40.0,OK",
                "4,0.0,NO_LOAD"
        );
        assertItemPresent(flow.snapshotStore.restore(), "keys", 3);

        flow.transport.dropConnection();
        flow.scheduler.runPendingTask();
        flow.transport.dropConnection();
        flow.scheduler.runPendingTask();

        assertEquals(1, flow.reminderRecorder.count);
        assertItemPresent(flow.reminderRecorder.lastSnapshot, "keys", 3);

        flow.transport.finishConnection();
        flow.sendCompleteCycle(
                "1,0.0,NO_LOAD",
                "2,40.0,OK",
                "3,0.0,NO_LOAD",
                "4,0.0,NO_LOAD"
        );

        assertEquals(MonitoringLifecycle.State.MONITORING, flow.lifecycle.getState());
        assertItemPresent(flow.snapshotStore.restore(), "keys", 2);
    }

    @Test
    public void malformedAndPartialReadingsCannotScheduleAReminder() {
        ItemProfile keys = new ItemProfile("keys", "House keys", 35.0, 45.0);
        FlowHarness flow = new FlowHarness(Collections.singletonList(keys));
        flow.startAndConnect();

        flow.sendStablePayload("1,40.0,OK");
        flow.sendStablePayload("2,0.0,NO_LOAD");
        flow.sendStablePayload("3,0.0,NO_LOAD");
        flow.transport.sendPayload("4,not-a-weight,OK");
        flow.transport.dropConnection();

        assertEquals(1, flow.invalidPayloadCount);
        assertFalse(flow.scheduler.hasPendingTask());
        assertEquals(0, flow.reminderRecorder.count);
    }

    @Test
    public void reconnectDoesNotCompleteACycleStartedBeforeTheDisconnect() {
        ItemProfile keys = new ItemProfile("keys", "House keys", 35.0, 45.0);
        FlowHarness flow = new FlowHarness(Collections.singletonList(keys));
        flow.startAndConnect();

        flow.sendStablePayload("1,40.0,OK");
        flow.sendStablePayload("2,0.0,NO_LOAD");
        flow.sendStablePayload("3,0.0,NO_LOAD");
        flow.transport.dropConnection();
        flow.transport.finishConnection();

        flow.sendStablePayload("4,0.0,NO_LOAD");
        assertNull(flow.snapshotStore.restore());

        flow.sendStablePayload("1,40.0,OK");
        flow.sendStablePayload("2,0.0,NO_LOAD");
        flow.sendStablePayload("3,0.0,NO_LOAD");
        assertItemPresent(flow.snapshotStore.restore(), "keys", 1);
    }

    private void assertItemPresent(
            DisconnectSnapshot snapshot,
            String itemId,
            int plateNumber
    ) {
        assertNotNull(snapshot);
        DisconnectSnapshot.ItemEntry match = null;
        for (DisconnectSnapshot.ItemEntry item : snapshot.getItems()) {
            if (item.getItemId().equals(itemId)) {
                match = item;
                break;
            }
        }

        assertNotNull(match);
        assertEquals(TrackedItemStatus.PRESENT, match.getStatus());
        assertEquals(Integer.valueOf(plateNumber), match.getPlateNumber());
    }

    private static final class FlowHarness implements WeightStationConnection.Listener {
        private final FakeTransport transport = new FakeTransport();
        private final MonitoringLifecycle lifecycle = new MonitoringLifecycle();
        private final FakeScheduler scheduler = new FakeScheduler();
        private final PersistedSnapshots snapshotStore = new PersistedSnapshots();
        private final ReminderRecorder reminderRecorder = new ReminderRecorder();
        private final StationReadingProcessor processor;
        private final DepartureReminderCoordinator reminderCoordinator;
        private final WeightStationConnection connection;
        private String knownAddress;
        private long timestampMillis = 1_000L;
        private int invalidPayloadCount;

        private FlowHarness(List<ItemProfile> profiles) {
            processor = new StationReadingProcessor(profiles, 4, REQUIRED_SAMPLES, 5.0, 5.0);
            reminderCoordinator = new DepartureReminderCoordinator(
                    10_000L,
                    scheduler,
                    snapshotStore,
                    reminderRecorder
            );
            connection = new WeightStationConnection(transport, this);
        }

        void startAndConnect() {
            lifecycle.start();
            connection.connect();
            transport.findStation();
            transport.finishConnection();
        }

        void sendCompleteCycle(String plateOne, String plateTwo, String plateThree,
                               String plateFour) {
            sendStablePayload(plateOne);
            sendStablePayload(plateTwo);
            sendStablePayload(plateThree);
            sendStablePayload(plateFour);
        }

        void sendStablePayload(String payload) {
            for (int sample = 0; sample < REQUIRED_SAMPLES; sample++) {
                transport.sendPayload(payload);
            }
        }

        @Override
        public void onStateChanged(
                WeightStationConnection.State state,
                WeightStationConnection.Failure failure
        ) {
            if (state == WeightStationConnection.State.CONNECTED) {
                processor.resetCycle();
                knownAddress = connection.getConnectedStationAddress();
                reminderCoordinator.onReconnected();
                lifecycle.connected();
                return;
            }
            if (state == WeightStationConnection.State.DISCONNECTED) {
                if (lifecycle.disconnect(MonitoringLifecycle.DisconnectCause.LINK_LOSS)) {
                    reminderCoordinator.onLinkLost();
                }
                if (knownAddress != null) {
                    connection.connectKnown(knownAddress);
                }
            }
        }

        @Override
        public void onReadingReceived(BluetoothReading reading) {
            Optional<DisconnectSnapshot> completed = processor.process(reading, timestampMillis++);
            if (completed.isPresent()) {
                DisconnectSnapshot snapshot = completed.get();
                snapshotStore.save(snapshot);
                reminderCoordinator.onFreshSnapshot(snapshot);
            }
        }

        @Override
        public void onInvalidPayload() {
            invalidPayloadCount++;
        }
    }

    private static final class FakeTransport implements WeightStationConnection.Transport {
        private WeightStationConnection.ScanEvents scanEvents;
        private WeightStationConnection.ConnectionEvents connectionEvents;
        private WeightStationConnection.ConnectionMode lastConnectionMode;

        @Override
        public void startScan(UUID serviceUuid, WeightStationConnection.ScanEvents events) {
            scanEvents = events;
        }

        @Override
        public void stopScan() {
        }

        @Override
        public void connect(
                WeightStationConnection.DeviceCandidate device,
                UUID serviceUuid,
                UUID characteristicUuid,
                UUID commandCharacteristicUuid,
                WeightStationConnection.ConnectionMode mode,
                WeightStationConnection.ConnectionEvents events
        ) {
            lastConnectionMode = mode;
            connectionEvents = events;
        }

        @Override
        public void writeCommand(
                String command,
                WeightStationConnection.CommandEvents events
        ) {
        }

        @Override
        public void scheduleCommandTimeout(Runnable timeout, long delayMillis) {
        }

        @Override
        public void cancelCommandTimeout() {
        }

        @Override
        public void disconnect() {
        }

        void findStation() {
            scanEvents.onDeviceFound(
                    new WeightStationConnection.DeviceCandidate(
                            STATION_ADDRESS,
                            WeightStationConnection.DEVICE_NAME
                    )
            );
        }

        void finishConnection() {
            connectionEvents.onReady(true);
        }

        void sendPayload(String payload) {
            connectionEvents.onPayloadReceived(payload);
        }

        void dropConnection() {
            connectionEvents.onDisconnected();
        }
    }

    private static final class FakeScheduler
            implements DepartureReminderCoordinator.Scheduler {
        private Runnable pendingTask;

        @Override
        public void schedule(Runnable task, long delayMillis) {
            pendingTask = task;
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

    private static final class PersistedSnapshots
            implements DepartureReminderCoordinator.SnapshotStore {
        private String savedJson;

        @Override
        public void save(DisconnectSnapshot snapshot) {
            savedJson = DisconnectSnapshotJsonConverter.toJson(snapshot);
        }

        DisconnectSnapshot restore() {
            return DisconnectSnapshotJsonConverter.fromJson(savedJson);
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
