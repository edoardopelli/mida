package it.mida.imports;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class MonitorState {

    public enum State {
        STARTING,
        ACTIVE,
        ERROR,
        DISABLED
    }

    private volatile State state = State.STARTING;
    private volatile Instant lastScanAt;
    private volatile String lastError;
    private volatile boolean directoryReadable;

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Instant getLastScanAt() {
        return lastScanAt;
    }

    public void setLastScanAt(Instant lastScanAt) {
        this.lastScanAt = lastScanAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public boolean isDirectoryReadable() {
        return directoryReadable;
    }

    public void setDirectoryReadable(boolean directoryReadable) {
        this.directoryReadable = directoryReadable;
    }
}
