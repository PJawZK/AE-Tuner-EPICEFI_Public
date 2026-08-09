package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Immutable UI/evidence snapshot of the current Guided Blend Duration session. */
final class GuidedSessionSnapshot {
    final GuidedCaptureState state;
    final String headline;
    final String instruction;
    final String checks;
    final String result;
    final int accepted;
    final String attemptTrace;

    GuidedSessionSnapshot(GuidedCaptureState state, String headline,
                          String instruction, String checks, String result,
                          int accepted, String attemptTrace) {
        this.state = state;
        this.headline = headline;
        this.instruction = instruction;
        this.checks = checks;
        this.result = result;
        this.accepted = accepted;
        this.attemptTrace = attemptTrace == null ? "" : attemptTrace;
    }
}
