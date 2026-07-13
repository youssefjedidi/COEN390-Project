package com.coen390.smartexit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WeightStationConnectionTest {
    private FakeTransport transport;
    private StateRecorder states;
    private WeightStationConnection connection;

    @Before
    public void setUp() {
        transport = new FakeTransport();
        states = new StateRecorder();
        connection = new WeightStationConnection(transport, states);
    }

    @Test
    public void connectStartsScanForWeightService() {
        connection.connect();

        assertEquals(WeightStationConnection.SERVICE_UUID, transport.scannedServiceUuid);
        assertEquals(WeightStationConnection.State.SCANNING, states.lastState());
    }

    @Test
    public void foundDeviceConnectsUsingFirmwareUuids() {
        connection.connect();
        transport.findStation();

        assertEquals(1, transport.stopScanCount);
        assertEquals(WeightStationConnection.SERVICE_UUID, transport.connectedServiceUuid);
        assertEquals(
                WeightStationConnection.WEIGHT_CHARACTERISTIC_UUID,
                transport.connectedCharacteristicUuid
        );
        assertEquals(WeightStationConnection.State.CONNECTING, states.lastState());
    }

    @Test
    public void readyGattConnectionReportsConnected() {
        connection.connect();
        transport.findStation();
        transport.finishConnection();

        assertEquals(WeightStationConnection.State.CONNECTED, states.lastState());
        assertNull(states.lastFailure());
    }

    @Test
    public void receivedPayloadReachesListenerAsWeightReading() {
        connection.connect();
        transport.findStation();
        transport.finishConnection();

        transport.sendPayload("211.2,OK,1149");

        assertEquals(211.2f, states.lastReading().getWeightGrams(), 0.01f);
        assertEquals(BluetoothReading.Status.OK, states.lastReading().getStatus());
        assertEquals(1149, states.lastReading().getSequence());
    }

    @Test
    public void malformedPayloadIsReportedWithoutAReading() {
        connection.connect();
        transport.findStation();
        transport.finishConnection();

        transport.sendPayload("not-a-weight");

        assertEquals(1, states.invalidPayloadCount);
        assertNull(states.lastReading());
    }

    @Test
    public void scanTimeoutReportsStationNotFound() {
        connection.connect();
        transport.failScan(WeightStationConnection.Failure.STATION_NOT_FOUND);

        assertEquals(WeightStationConnection.State.FAILED, states.lastState());
        assertEquals(WeightStationConnection.Failure.STATION_NOT_FOUND, states.lastFailure());
        assertEquals(WeightStationConnection.Failure.STATION_NOT_FOUND, connection.getFailure());
        assertEquals(1, transport.disconnectCount);
    }

    @Test
    public void missingServiceReportsSpecificFailure() {
        connection.connect();
        transport.findStation();
        transport.failConnection(WeightStationConnection.Failure.SERVICE_MISSING);

        assertEquals(WeightStationConnection.State.FAILED, states.lastState());
        assertEquals(WeightStationConnection.Failure.SERVICE_MISSING, states.lastFailure());
    }

    @Test
    public void unexpectedDisconnectIsVisible() {
        connection.connect();
        transport.findStation();
        transport.finishConnection();
        transport.dropConnection();

        assertEquals(WeightStationConnection.State.DISCONNECTED, states.lastState());
    }

    @Test
    public void manualDisconnectStopsScanAndGattConnection() {
        connection.connect();
        connection.disconnect();

        assertEquals(1, transport.stopScanCount);
        assertEquals(1, transport.disconnectCount);
        assertEquals(WeightStationConnection.State.DISCONNECTED, states.lastState());
    }

    private static final class StateRecorder implements WeightStationConnection.Listener {
        private final List<WeightStationConnection.State> stateHistory = new ArrayList<>();
        private final List<WeightStationConnection.Failure> failureHistory = new ArrayList<>();
        private final List<BluetoothReading> readings = new ArrayList<>();
        private int invalidPayloadCount;

        @Override
        public void onStateChanged(
                WeightStationConnection.State state,
                WeightStationConnection.Failure failure
        ) {
            stateHistory.add(state);
            failureHistory.add(failure);
        }

        @Override
        public void onReadingReceived(BluetoothReading reading) {
            readings.add(reading);
        }

        @Override
        public void onInvalidPayload() {
            invalidPayloadCount++;
        }

        WeightStationConnection.State lastState() {
            return stateHistory.get(stateHistory.size() - 1);
        }

        WeightStationConnection.Failure lastFailure() {
            return failureHistory.get(failureHistory.size() - 1);
        }

        BluetoothReading lastReading() {
            return readings.isEmpty() ? null : readings.get(readings.size() - 1);
        }
    }

    private static final class FakeTransport implements WeightStationConnection.Transport {
        UUID scannedServiceUuid;
        UUID connectedServiceUuid;
        UUID connectedCharacteristicUuid;
        WeightStationConnection.ScanEvents scanEvents;
        WeightStationConnection.ConnectionEvents connectionEvents;
        int stopScanCount;
        int disconnectCount;

        @Override
        public void startScan(UUID serviceUuid, WeightStationConnection.ScanEvents events) {
            scannedServiceUuid = serviceUuid;
            scanEvents = events;
        }

        @Override
        public void stopScan() {
            stopScanCount++;
        }

        @Override
        public void connect(
                WeightStationConnection.DeviceCandidate device,
                UUID serviceUuid,
                UUID characteristicUuid,
                WeightStationConnection.ConnectionEvents events
        ) {
            connectedServiceUuid = serviceUuid;
            connectedCharacteristicUuid = characteristicUuid;
            connectionEvents = events;
        }

        @Override
        public void disconnect() {
            disconnectCount++;
        }

        void findStation() {
            scanEvents.onDeviceFound(
                    new WeightStationConnection.DeviceCandidate(
                            "00:11:22:33:44:55",
                            WeightStationConnection.DEVICE_NAME
                    )
            );
        }

        void failScan(WeightStationConnection.Failure failure) {
            scanEvents.onScanFailed(failure);
        }

        void finishConnection() {
            connectionEvents.onReady();
        }

        void sendPayload(String payload) {
            connectionEvents.onPayloadReceived(payload);
        }

        void failConnection(WeightStationConnection.Failure failure) {
            connectionEvents.onConnectionFailed(failure);
        }

        void dropConnection() {
            connectionEvents.onDisconnected();
        }
    }
}
