package com.coen390.smartexit;

final class MonitoringLifecycle {

    enum State {
        STARTING,
        MONITORING,
        RECONNECTING,
        PAUSED,
        STOPPED
    }

    enum PauseReason {
        BLUETOOTH_OFF,
        PERMISSION_UNAVAILABLE,
        CONNECTION_UNAVAILABLE
    }

    enum DisconnectCause {
        LINK_LOSS,
        MANUAL_STOP,
        BLUETOOTH_OFF,
        PERMISSION_UNAVAILABLE
    }

    private State state = State.STOPPED;
    private PauseReason pauseReason;
    private boolean reminderGracePending;

    State getState() {
        return state;
    }

    PauseReason getPauseReason() {
        return pauseReason;
    }

    void start() {
        state = State.STARTING;
        pauseReason = null;
        reminderGracePending = false;
    }

    void connected() {
        state = State.MONITORING;
        pauseReason = null;
        reminderGracePending = false;
    }

    /**
     * A lost radio link begins departure handling only after a real connection.
     * User actions and unavailable phone hardware always stop or pause quietly.
     */
    boolean disconnect(DisconnectCause cause) {
        switch (cause) {
            case LINK_LOSS:
                return beginReconnecting();
            case BLUETOOTH_OFF:
                pause(PauseReason.BLUETOOTH_OFF);
                return false;
            case PERMISSION_UNAVAILABLE:
                pause(PauseReason.PERMISSION_UNAVAILABLE);
                return false;
            case MANUAL_STOP:
            default:
                stop();
                return false;
        }
    }

    void pause(PauseReason reason) {
        state = State.PAUSED;
        pauseReason = reason;
        reminderGracePending = false;
    }

    void stop() {
        state = State.STOPPED;
        pauseReason = null;
        reminderGracePending = false;
    }

    private boolean beginReconnecting() {
        if (state != State.MONITORING && state != State.RECONNECTING) {
            return false;
        }

        state = State.RECONNECTING;
        if (reminderGracePending) {
            return false;
        }

        reminderGracePending = true;
        return true;
    }
}
