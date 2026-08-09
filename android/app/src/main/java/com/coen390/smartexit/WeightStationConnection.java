package com.coen390.smartexit;

import java.util.UUID;

final class WeightStationConnection {
    private static final long COMMAND_TIMEOUT_MS = 10_000;

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
        WRITE_FAILED,
        STATION_REJECTED,
        TIMED_OUT,
        DISCONNECTED,
        IN_PROGRESS
    }

    enum ConnectionMode {
        DIRECT,
        AUTO_RECONNECT
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
                ConnectionMode mode,
                ConnectionEvents events
        );

        void writeCommand(String command, CommandEvents events);

        void scheduleCommandTimeout(Runnable timeout, long delayMillis);

        void cancelCommandTimeout();

        void disconnect();
    }

    interface ScanEvents {
        void onDeviceFound(DeviceCandidate device);

        void onScanFailed(Failure failure);
    }

    interface ConnectionEvents {
        void onReady(boolean commandSupported);

        void onPayloadReceived(String payload);

        void onCommandResponse(String response);

        void onDisconnected();

        void onConnectionFailed(Failure failure);
    }

    interface CommandEvents {
        void onCommandWritten();

        void onCommandWriteFailed();
    }

    interface CommandCallback {
        void onCommandSucceeded();

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
    private volatile CommandCallback pendingCommand;
    private DeviceCandidate pendingDevice;
    private String connectedStationAddress;

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

    String getConnectedStationAddress() {
        return connectedStationAddress;
    }

    boolean canRequestTare() {
        return state == State.CONNECTED && commandSupported && pendingCommand == null;
    }

    void connect() {
        if (isConnectionInProgress()) {
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

    void connectKnown(String address) {
        if (isConnectionInProgress()) {
            return;
        }

        beginGattConnection(
                new DeviceCandidate(address, DEVICE_NAME),
                ConnectionMode.AUTO_RECONNECT
        );
    }

    void disconnect() {
        finishPendingCommand(CommandFailure.DISCONNECTED);
        transport.stopScan();
        transport.disconnect();
        commandSupported = false;
        pendingDevice = null;
        connectedStationAddress = null;
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
        if (pendingCommand != null) {
            callback.onCommandFailed(CommandFailure.IN_PROGRESS);
            return;
        }

        pendingCommand = callback;
        transport.writeCommand("TARE", new CommandEvents() {
            @Override
            public void onCommandWritten() {
                if (pendingCommand != null) {
                    transport.scheduleCommandTimeout(
                            () -> finishPendingCommand(CommandFailure.TIMED_OUT),
                            COMMAND_TIMEOUT_MS
                    );
                }
            }

            @Override
            public void onCommandWriteFailed() {
                finishPendingCommand(CommandFailure.WRITE_FAILED);
            }
        });
    }

    void close() {
        finishPendingCommand(CommandFailure.DISCONNECTED);
        transport.stopScan();
        transport.disconnect();
        state = State.IDLE;
        failure = null;
        commandSupported = false;
        pendingDevice = null;
        connectedStationAddress = null;
    }

    private void handleDeviceFound(DeviceCandidate device) {
        if (state != State.SCANNING) {
            return;
        }

        transport.stopScan();
        beginGattConnection(device, ConnectionMode.DIRECT);
    }

    private void beginGattConnection(DeviceCandidate device, ConnectionMode mode) {
        pendingDevice = device;
        connectedStationAddress = null;
        changeState(State.CONNECTING, null);
        transport.connect(
                device,
                SERVICE_UUID,
                WEIGHT_CHARACTERISTIC_UUID,
                COMMAND_CHARACTERISTIC_UUID,
                mode,
                new ConnectionEvents() {
                    @Override
                    public void onReady(boolean commandSupported) {
                        WeightStationConnection.this.commandSupported = commandSupported;
                        connectedStationAddress = pendingDevice.address;
                        changeState(State.CONNECTED, null);
                    }

                    @Override
                    public void onPayloadReceived(String payload) {
                        handlePayload(payload);
                    }

                    @Override
                    public void onCommandResponse(String response) {
                        handleCommandResponse(response);
                    }

                    @Override
                    public void onDisconnected() {
                        commandSupported = false;
                        pendingDevice = null;
                        connectedStationAddress = null;
                        finishPendingCommand(CommandFailure.DISCONNECTED);
                        changeState(State.DISCONNECTED, null);
                    }

                    @Override
                    public void onConnectionFailed(Failure failure) {
                        handleFailure(failure);
                    }
                }
        );
    }

    private boolean isConnectionInProgress() {
        return state == State.SCANNING || state == State.CONNECTING || state == State.CONNECTED;
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

    private void handleCommandResponse(String response) {
        if (pendingCommand == null || response == null) {
            return;
        }

        String result = response.trim();
        if ("TARE_OK".equals(result)) {
            CommandCallback callback = takePendingCommand();
            if (callback != null) {
                callback.onCommandSucceeded();
            }
        } else if ("TARE_FAILED".equals(result)) {
            finishPendingCommand(CommandFailure.STATION_REJECTED);
        }
    }

    private void finishPendingCommand(CommandFailure failure) {
        CommandCallback callback = takePendingCommand();
        if (callback != null) {
            callback.onCommandFailed(failure);
        }
    }

    private synchronized CommandCallback takePendingCommand() {
        CommandCallback callback = pendingCommand;
        if (callback != null) {
            pendingCommand = null;
            transport.cancelCommandTimeout();
        }
        return callback;
    }

    private void handleFailure(Failure failure) {
        finishPendingCommand(CommandFailure.DISCONNECTED);
        transport.stopScan();
        transport.disconnect();
        commandSupported = false;
        pendingDevice = null;
        connectedStationAddress = null;
        changeState(State.FAILED, failure);
    }

    private void changeState(State nextState, Failure failure) {
        state = nextState;
        this.failure = failure;
        listener.onStateChanged(nextState, failure);
    }
}
