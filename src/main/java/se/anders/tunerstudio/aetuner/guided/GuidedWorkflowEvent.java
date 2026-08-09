package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Typed Guided Capture workflow events. Audio and audit consumers use
 * these directly instead of polling rendered Swing text. */
enum GuidedWorkflowEvent {
    SESSION_STARTED("SESSION STARTED",
            "A Guided Session has begun; drive smoothly in the selected RPM region while the rolling baseline establishes."),
    READY_ENTERED("READY - MAKE ONE MODERATE OPENING",
            "The rolling baseline is suitable and the driver may begin one moderate throttle opening when safe."),
    OPENING_PENDING("OPENING PENDING",
            "TPS has begun moving, but the ECU detector or local-onset threshold has not confirmed the opening yet."),
    TARGET_ACQUIRED("PEDAL HOLD ACQUIRED",
            "The natural pedal plateau has been detected; hold approximately steady while MAP catch-up completes."),
    EVENT_ACCEPTED("VALID EVENT - RETURN TO NORMAL THROTTLE",
            "A physically valid Guided measurement completed and was retained in its adaptive comparability group."),
    EVENT_EXCLUDED("EVENT EXCLUDED - RETURN TO NORMAL THROTTLE",
            "The measurement itself was invalid; return to normal throttle and review the reason when safe."),
    RETURN_TO_BASELINE("RETURN TO NORMAL THROTTLE",
            "A partial or invalid pre-trigger movement ended; resume normal light throttle while the rolling baseline reacquires."),
    SERIES_COMPLETE("SERIES COMPLETE",
            "One adaptive comparability group has reached the requested number of valid events."),
    PAUSED("PAUSED",
            "Guided Capture was paused and all current or queued sounds must stop."),
    SESSION_ENDED("SESSION ENDED",
            "The Guided Session was reset, finished, hidden, or closed.");

    final String displayName;
    final String triggerDescription;

    GuidedWorkflowEvent(String displayName, String triggerDescription) {
        this.displayName = displayName;
        this.triggerDescription = triggerDescription;
    }

    interface Listener {
        void onGuidedWorkflowEvent(GuidedWorkflowEvent event,
                                   String detail,
                                   long nanoTime);
    }

    static final Listener NONE = new Listener() {
        @Override
        public void onGuidedWorkflowEvent(GuidedWorkflowEvent event,
                                          String detail,
                                          long nanoTime) { }
    };
}
