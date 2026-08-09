package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Stable panel-facing wrapper around the operational-state-aware monitor core. */
final class SessionMonitor {
    private final SessionMonitorCore core = new SessionMonitorCore();

    synchronized void reset() { core.reset(); }

    synchronized void addSample(LiveSample sample) {
        core.addSample(sample);
    }

    synchronized Snapshot snapshot() { return new Snapshot(core.snapshot()); }

    static final class Snapshot extends SessionMonitorSnapshot {
        Snapshot(SessionMonitorSnapshot source) { super(source); }
    }
}
