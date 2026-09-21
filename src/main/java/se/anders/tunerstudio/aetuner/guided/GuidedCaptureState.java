package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Lifecycle/state vocabulary shared by the current Guided Blend Duration workflow. */
enum GuidedCaptureState {
    IDLE,
    SETTLING,
    READY,
    OPENING_PENDING,
    CAPTURING,
    ACCEPTED,
    WARNING,
    EXCLUDED,
    RETURNING,
    RECOVERING,
    PAUSED,
    COMPLETE;

    /**
     * A Guided session owns capture/navigation from start until it is explicitly
     * finished. Blend Duration deliberately moves through several maneuver and
     * outcome states while that same session remains live, so CAPTURING/PAUSED
     * alone must never be used as the generic "session active" test.
     */
    boolean isCaptureInProgress() {
        return this != IDLE && this != COMPLETE;
    }
}
