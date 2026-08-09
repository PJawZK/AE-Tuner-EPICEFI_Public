package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.List;

/** Compatibility alias retained while historical tests/tools transition to TransientEvent. */
final class EventSummary extends TransientEvent {
    EventSummary(int index, boolean accepted, String eventClass, String reason,
                 List<LiveSample> samples) {
        super(index, accepted, eventClass, reason, samples);
    }

    EventSummary(int index, boolean accepted, String eventClass, String reason,
                 List<LiveSample> samples, boolean mapPredictWorkflow) {
        super(index, accepted, eventClass, reason, samples, mapPredictWorkflow);
    }
}
