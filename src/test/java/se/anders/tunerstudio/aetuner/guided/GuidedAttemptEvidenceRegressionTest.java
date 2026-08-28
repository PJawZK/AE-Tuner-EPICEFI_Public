package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class GuidedAttemptEvidenceRegressionTest {
    private GuidedAttemptEvidenceRegressionTest() { }

    public static void main(String[] args) {
        boundsAttemptSamples();
        automaticSessionGearRejectsSpikesAndLatchesOnce();
        automaticSessionGearWaitsForDominantEvidence();
        automaticSessionGearRequiresReadyAndResetsForNewSession();
        manualGearDoesNotDependOnDetectedGearOrVss();
        traceReplacementKeepsBoundedOrder();
        System.out.println("GuidedAttemptEvidenceRegressionTest passed");
    }

    private static void boundsAttemptSamples() {
        GuidedAttemptEvidence evidence = new GuidedAttemptEvidence();
        for (int i = 0; i < 330; i++) evidence.add(sample(i * 0.01, 2.0, 40.0));
        require(evidence.sampleCount() == 320,
                "attempt sample buffer is no longer bounded at 320");
        require(Math.abs(evidence.samples().get(0).getSeconds() - 0.10) < 0.0001,
                "bounded attempt buffer no longer drops oldest samples");
    }

    private static void automaticSessionGearRejectsSpikesAndLatchesOnce() {
        RoadBaselineTracker tracker = new RoadBaselineTracker();
        tracker.add(sample(0.00, 5.0, 80.0));
        tracker.add(sample(0.05, 5.0, 80.2));
        tracker.add(sample(0.10, 0.0, 579.0));
        tracker.add(sample(0.15, 5.0, 80.6));
        tracker.add(sample(0.20, 2.0, 633.0));
        tracker.add(sample(0.25, 5.0, 81.0));
        tracker.add(sample(0.30, 5.0, 81.2));
        tracker.add(sample(0.35, 2.0, 81.4));
        tracker.add(sample(0.40, 5.0, 81.6));
        tracker.add(sample(0.45, 5.0, 81.8));
        tracker.add(sample(0.50, 5.0, 82.0));
        tracker.add(sample(0.55, 0.0, 82.2));
        tracker.add(sample(0.60, 5.0, 82.4));
        tracker.add(sample(0.65, 5.0, 82.6));
        tracker.add(sample(0.70, 5.0, 82.8));

        RoadBaselineTracker.Baseline baseline = tracker.baseline(false);
        GuidedAttemptEvidence evidence = new GuidedAttemptEvidence();
        for (int i = 0; i < 12; i++) {
            // Deliberately contradictory per-attempt raw gear. Automatic mode
            // must use the already-established READY/session latch instead.
            evidence.add(sample(1.00 + i * 0.05, 2.0, 85.0 + i * 0.1));
        }
        BlendDurationAttempt first = evidence.buildAttempt(1, baseline,
                measurement(), hold(), end(), 0.50,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 0, true));
        require(first.detectedGearLatched && first.detectedGear == 5,
                "automatic session gear did not choose the dominant trusted 5th-gear evidence");
        require(!first.gearReliabilityWarning(),
                "rejected VSS spikes incorrectly degraded a successful session gear latch");
        require(first.gearText().contains("session latched 5"),
                "automatic gear text does not expose the session-latched gear");
        require(baseline.sessionGearEvidence().contains("rejected VSS spike/dropout samples 2"),
                "session gear evidence did not retain rejected VSS corruption diagnostics");

        // After the first commit the recognizer must stop. Even a long clean
        // run of another detected gear cannot replace the session gear.
        for (int i = 0; i < 30; i++) {
            tracker.add(sample(2.00 + i * 0.05, 2.0, 90.0 + i * 0.1));
        }
        RoadBaselineTracker.Baseline laterBaseline = tracker.baseline(false);
        evidence.reset();
        for (int i = 0; i < 8; i++) {
            evidence.add(sample(4.00 + i * 0.05, 2.0, 95.0));
        }
        BlendDurationAttempt later = evidence.buildAttempt(2, laterBaseline,
                measurement(), hold(), end(), 0.50,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 0, true));
        require(later.detectedGear == 5,
                "a later detected gear replaced the one-way automatic session latch");
    }

    private static void automaticSessionGearWaitsForDominantEvidence() {
        RoadBaselineTracker tracker = new RoadBaselineTracker();
        for (int i = 0; i < 16; i++) {
            tracker.add(sample(i * 0.05, i % 2 == 0 ? 4.0 : 5.0,
                    70.0 + i * 0.1));
        }
        RoadBaselineTracker.Baseline baseline = tracker.baseline(false);
        GuidedAttemptEvidence evidence = new GuidedAttemptEvidence();
        for (int i = 0; i < 8; i++) evidence.add(sample(1.0 + i * 0.05, 5.0, 72.0));
        BlendDurationAttempt attempt = evidence.buildAttempt(1, baseline,
                measurement(), hold(), end(), 0.50,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 0, true));
        require(!attempt.detectedGearLatched && attempt.gearReliabilityWarning(),
                "mixed startup gear evidence incorrectly forced an automatic session latch");
    }

    private static void automaticSessionGearRequiresReadyAndResetsForNewSession() {
        RoadBaselineTracker tracker = new RoadBaselineTracker();

        // Stable gear before READY must not become the session gear merely
        // because a detector pulse occurred while the driver was still getting
        // into test conditions.
        for (int i = 0; i < 16; i++) {
            tracker.add(sample(i * 0.05, 2.0, 40.0 + i * 0.1));
        }
        tracker.add(triggerSample(0.80, 2.0, 41.6));

        // Let the old evidence age out, establish READY in 5th, then trigger
        // the first real controlled opening. This is the gear that must latch.
        LiveSample readySample = null;
        for (int i = 0; i < 20; i++) {
            readySample = sample(2.00 + i * 0.05, 5.0, 80.0 + i * 0.1);
            tracker.add(readySample);
        }
        RoadBaselineTracker.AcquireCheck ready = tracker.acquireCheck(
                readySample, 2000.0, 0L, 1.50);
        require(ready.ready, "stable 5th-gear road window did not establish READY");
        tracker.add(triggerSample(3.00, 5.0, 82.0));

        GuidedAttemptEvidence evidence = new GuidedAttemptEvidence();
        BlendDurationAttempt firstSession = evidence.buildAttempt(1,
                tracker.baseline(false), measurement(), hold(), end(), 0.50,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 0, true));
        require(firstSession.detectedGear == 5,
                "pre-READY gear incorrectly won over the READY/opening session gear");

        // Starting a new Guided session calls clear(); the previous session
        // latch must disappear and a new gear must be independently acquired.
        tracker.clear();
        readySample = null;
        for (int i = 0; i < 20; i++) {
            readySample = sample(10.00 + i * 0.05, 3.0, 55.0 + i * 0.1);
            tracker.add(readySample);
        }
        ready = tracker.acquireCheck(readySample, 2000.0, 0L, 1.50);
        require(ready.ready, "new-session 3rd-gear road window did not establish READY");
        tracker.add(triggerSample(11.00, 3.0, 57.0));

        evidence.reset();
        BlendDurationAttempt secondSession = evidence.buildAttempt(1,
                tracker.baseline(false), measurement(), hold(), end(), 0.50,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 0, true));
        require(secondSession.detectedGear == 3,
                "new Guided session did not clear the previous session gear latch");
    }

    private static void manualGearDoesNotDependOnDetectedGearOrVss() {
        RoadBaselineTracker tracker = new RoadBaselineTracker();
        for (int i = 0; i < 14; i++) {
            tracker.add(sample(i * 0.05, 3.0, 40.0 + i * 0.1));
        }
        RoadBaselineTracker.Baseline baseline = tracker.baseline(false);
        GuidedAttemptEvidence evidence = new GuidedAttemptEvidence();
        for (int i = 0; i < 12; i++) evidence.add(sample(1.0 + i * 0.05, 3.0, Double.NaN));
        BlendDurationAttempt attempt = evidence.buildAttempt(1, baseline,
                measurement(), hold(), end(), 0.50,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false));
        require(!attempt.gearReliabilityWarning(),
                "manual gear mode incorrectly depends on ECU detected gear/VSS quality");
        require(attempt.gearText().contains("manual 2")
                        && attempt.gearText().contains("ECU detected 3")
                        && attempt.gearText().contains("informational mismatch"),
                "manual gear result must preserve operator authority while exporting ECU detection as information");
    }

    private static void traceReplacementKeepsBoundedOrder() {
        GuidedAttemptEvidence evidence = new GuidedAttemptEvidence();
        List<LiveSample> pending = new ArrayList<LiveSample>();
        pending.add(sample(1.00, 2.0, 40.0));
        pending.add(sample(1.05, 2.0, 40.0));
        LiveSample outcome = sample(1.10, 2.0, 40.0);
        evidence.replaceSamplesForTrace(pending, outcome);
        require(evidence.sampleCount() == 3,
                "return-to-baseline trace sample assembly changed");
        require(evidence.samples().get(2) == outcome,
                "return-to-baseline outcome sample order changed");
    }

    private static LiveSample measurement() {
        return detailed(2.0, 2000.0, 55.0, 20.0, 78.0, 2.0, 40.0, false);
    }
    private static LiveSample hold() {
        return detailed(2.2, 2040.0, 65.0, 30.0, 80.0, 2.0, 40.0, false);
    }
    private static LiveSample end() {
        return detailed(2.5, 2100.0, 80.0, 30.0, 80.0, 2.0, 40.0, false);
    }
    private static LiveSample sample(double seconds, double gear, double vss) {
        return detailed(seconds, 2000.0, 50.0, 8.0, 50.0, gear, vss, false);
    }
    private static LiveSample triggerSample(double seconds, double gear, double vss) {
        return detailed(seconds, 2000.0, 50.0, 8.0, 50.0, gear, vss, true);
    }
    private static LiveSample detailed(double seconds, double rpm, double map,
                                       double tps, double fallback,
                                       double gear, double vss,
                                       boolean detector) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.GEAR, gear);
        values.put(ChannelRole.VSS, vss);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, detector ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values,
                detector ? 60.0 : 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
