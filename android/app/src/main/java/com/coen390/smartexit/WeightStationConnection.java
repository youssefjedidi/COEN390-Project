package com.coen390.smartexit;

import java.util.UUID;

final class WeightStationConnection {
    static final String DEVICE_NAME = "SmartExit-Station";
    static final UUID SERVICE_UUID = UUID.fromString("05442887-a14c-4c36-906c-0fe1af039f9f");
    static final UUID WEIGHT_CHARACTERISTIC_UUID = UUID.fromString("e3abbc63-b985-4c8e-8e38-d423ce320106");
    static final UUID COMMAND_CHARACTERISTIC_UUID =
            UUID.fromString("e3abbc63-b985-4c8e-8e38-d423ce320107");

    enum State {
        IDLE,
        SCANNING,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        FAILED
    }

    enum Failure {
        SCAN_UNAVAILABLE,
        STATION_NOT_FOUND,
        SCAN_FAILED,
        CONNECTION_FAILED,
        SERVICE_MISSING,
        CHARACTERISTIC_MISSING,
        NOTIFICATION_SETUP_FAILED
    }

    enum CommandFailure {
        NOT_CONNECTED,
        NOT_SUPPORTED,
        WRITE_FAILED
    }

    interface Listener {
        void onStateChanged(State state, Failure failure);

        void onReadingReceived(BluetoothReading reading);

        void onInvalidPayload();
    }

    interface Transport {
        void startScan(UUID serviceUuid, ScanEvents events);

        void stopScan();

        void connect(
                DeviceCandidate device,
                UUID serviceUuid,
                UUID characteristicUuid,
                UUID commandCharacteristicUuid,
                ConnectionEvents events
        );

        void writeCommand(String command, CommandEvents events);

        void disconnect();
    }

    interface ScanEvents {
        void onDeviceFound(DeviceCandidate device);

        void onScanFailed(Failure failure);
    }

    interface ConnectionEvents {
        void onReady(boolean commandSupported);

        void onPayloadReceived(String payload);

        void onDisconnected();

        void onConnectionFailed(Failure failure);
    }

    interface CommandEvents {
        void onCommandWritten();

        void onCommandWriteFailed();
    }

    interface CommandCallback {
        void onCommandSent();

        void onCommandFailed(CommandFailure failure);
    }

    static final class DeviceCandidate {
        final String address;
        final String name;

        DeviceCandidate(String address, String name) {
            this.address = address;
            this.name = name;
        }
    }

    private final Transport transport;
    private final Listener listener;
    private State state = State.IDLE;
    private Failure failure;
    private boolean commandSupported;

    WeightStationConnection(Transport transport, Listener listener) {
        this.transport = transport;
        this.listener = listener;
    }

    State getState() {
        return state;
    }

    Failure getFailure() {
        return failure;
    }

    boolean canRequestTare() {
        return state == State.CONNECTED && commandSupported;
    }

    void connect() {
        if (state == State.SCANNING || state == State.CONNECTING || state == State.CONNECTED) {
            return;
        }

        changeState(State.SCANNING, null);
        transport.startScan(SERVICE_UUID, new ScanEvents() {
            @Override
            public void onDeviceFound(DeviceCandidate device) {
                handleDeviceFound(device);
            }

            @Override
            public void onScanFailed(Failure failure) {
                handleFailure(failure);
            }
        });
    }

    void disconnect() {
        transport.stopScan();
        transport.disconnect();
        commandSupported = false;
        changeState(State.DISCONNECTED, null);
    }

    void requestTare(CommandCallback callback) {
        if (state != State.CONNECTED) {
            callback.onCommandFailed(CommandFailure.NOT_CONNECTED);
            return;
        }
        if (!commandSupported) {
            callback.onCommandFailed(CommandFailure.NOT_SUPPORTED);
            return;
        }

        transport.writeCommand("TARE", new CommandEvents() {
            @Override
            public void onCommandWritten() {
                callback.onCommandSent();
            }

            @Override
            public void onCommandWriteFailed() {
                callback.onCommandFailed(CommandFailure.WRITE_FAILED);
            }
        });
    }

    void close() {
        transport.stopScan();
        transport.disconnect();
        state = State.IDLE;
        failure = null;
        commandSupported = false;
    }

    private void handleDeviceFound(DeviceCandidate device) {
        if (state != State.SCANNING) {
            return;
        }

        transport.stopScan();
        changeState(State.CONNECTING, null);
        transport.connect(
                device,
                SERVICE_UUID,
                WEIGHT_CHARACTERISTIC_UUID,
                COMMAND_CHARACTERISTIC_UUID,
                new ConnectionEvents() {
                    @Override
                    public void onReady(boolean commandSupported) {
                        WeightStationConnection.this.commandSupported = commandSupported;
                        changeState(State.CONNECTED, null);
                    }

                    @Override
                    public void onPayloadReceived(String payload) {
                        handlePayload(payload);
                    }

                    @Override
                    public void onDisconnected() {
                        changeState(State.DISCONNECTED, null);
                    }

                    @Override
                    public void onConnectionFailed(Failure failure) {
                        handleFailure(failure);
                    }
                }
        );
    }

    private void handlePayload(String payload) {
        if (state != State.CONNECTED) {
            return;
        }

        BluetoothPayloadParser.ParseResult result = BluetoothPayloadParser.parse(payload);
        if (result.isValid()) {
            listener.onReadingReceived(result.getReading());
        } else {
            listener.onInvalidPayload();
        }
    }

    private void handleFailure(Failure failure) {
        transport.stopScan();
        transport.disconnect();
        commandSupported = false;
        changeState(State.FAILED, failure);
    }

    private void changeState(State nextState, Failure failure) {
        state = nextState;
        this.failure = failure;
        listener.onStateChanged(nextState, failure);
    }
}
