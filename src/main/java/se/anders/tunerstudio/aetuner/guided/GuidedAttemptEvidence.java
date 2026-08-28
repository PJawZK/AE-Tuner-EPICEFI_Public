package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.List;

/** Bounded per-attempt samples. Session gear recognition belongs to the READY baseline. */
final class GuidedAttemptEvidence {
    private static final int MAX_SAMPLES = 320;

    private final List<LiveSample> samples = new ArrayList<LiveSample>();
    private int captureSamples;

    void reset() {
        samples.clear();
        captureSamples = 0;
    }

    void add(LiveSample sample) {
        if (sample == null) return;
        captureSamples++;
        record(sample);
    }

    void replaceSamplesForTrace(List<LiveSample> replacement, LiveSample outcome) {
        samples.clear();
        if (replacement != null) {
            for (LiveSample sample : replacement) {
                record(sample);
            }
        }
        if (outcome != null && (samples.isEmpty()
                || samples.get(samples.size() - 1).getNanoTime()
                != outcome.getNanoTime())) {
            record(outcome);
        }
    }

    List<LiveSample> samples() {
        return samples;
    }

    int sampleCount() {
        return samples.size();
    }

    BlendDurationAttempt buildAttempt(int number,
                                      RoadBaselineTracker.Baseline baseline,
                                      LiveSample measurementAnchor,
                                      LiveSample holdAnchor,
                                      LiveSample end,
                                      double duration,
                                      BlendDurationCaptureConfig settings) {
        int sessionDetectedGear = baseline == null
                ? 0 : baseline.sessionDetectedGear();
        GuidedEventGearEvidence.Result eventGear =
                settings != null && settings.automaticGear
                ? GuidedEventGearEvidence.evaluate(samples, sessionDetectedGear)
                : GuidedEventGearEvidence.Result.unavailable(sessionDetectedGear);
        return BlendDurationAttempt.build(number,
                baseline.rpm, baseline.map, baseline.tps,
                measurementAnchor, holdAnchor, end, duration, settings,
                sessionDetectedGear, 0, 0, captureSamples, eventGear);
    }

    private void record(LiveSample sample) {
        if (samples.size() >= MAX_SAMPLES) {
            samples.remove(0);
        }
        samples.add(sample);
    }
}
