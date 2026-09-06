package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Presentation-only mirror using the same accepted samples and recommendation evaluator. */
public final class FoundationThresholdFocusBridge {
    private static final int MAX_SAMPLES = 24000;
    private static final int EVALUATION_STRIDE = 40;
    private static final List<LiveSample> samples = new ArrayList<LiveSample>();
    private static AeProjectSnapshot snapshot;
    private static LiveSample latest;
    private static FoundationThresholdFocusModel cached;
    private static int evaluatedSampleCount = -1;

    private FoundationThresholdFocusBridge() { }

    public static synchronized void observeWorkingTune(AeProjectSnapshot workingTune) {
        if (snapshot == workingTune) return;
        snapshot = workingTune;
        cached = null;
        evaluatedSampleCount = -1;
    }

    public static synchronized void observe(LiveSample sample) {
        if (sample == null) return;
        latest = sample;
        if (samples.size() >= MAX_SAMPLES) samples.remove(0);
        samples.add(sample);
    }

    public static synchronized void replaceEvidence(AeProjectSnapshot workingTune,
                                                     List<LiveSample> evidence) {
        snapshot = workingTune;
        samples.clear();
        if (evidence != null) {
            int start = Math.max(0, evidence.size() - MAX_SAMPLES);
            for (int i = start; i < evidence.size(); i++) {
                if (evidence.get(i) != null) samples.add(evidence.get(i));
            }
            latest = samples.isEmpty() ? null : samples.get(samples.size() - 1);
        }
        cached = null;
        evaluatedSampleCount = -1;
    }

    public static synchronized FoundationThresholdFocusModel snapshot(Object state) {
        String stateName = state == null ? "IDLE" : String.valueOf(state);
        boolean force = cached == null || "COMPLETE".equals(stateName)
                || Math.abs(samples.size() - evaluatedSampleCount) >= EVALUATION_STRIDE;
        if (force) {
            cached = FoundationThresholdFocusModel.build(snapshot,
                    Collections.unmodifiableList(new ArrayList<LiveSample>(samples)),
                    latest, stateName);
            evaluatedSampleCount = samples.size();
        } else {
            cached = cached.withLive(latest, stateName);
        }
        return cached;
    }

    public static synchronized void reset() {
        samples.clear();
        snapshot = null;
        latest = null;
        cached = null;
        evaluatedSampleCount = -1;
    }
}
