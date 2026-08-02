package se.anders.tunerstudio.aetuner;

/** Stable panel-facing wrapper around the operational-state-aware monitor core. */
final class SessionMonitor {
    private final SessionMonitorCore core = new SessionMonitorCore();

    synchronized void reset() { core.reset(); }
    synchronized void addSample(LiveSample sample) { core.addSample(sample); }
    synchronized Snapshot snapshot() { return new Snapshot(core.snapshot()); }

    static final class Snapshot extends SessionMonitorSnapshot {
        Snapshot(SessionMonitorSnapshot source) { super(source); }
    }
}
