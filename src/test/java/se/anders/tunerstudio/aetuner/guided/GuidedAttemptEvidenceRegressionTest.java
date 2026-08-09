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
        preservesGearAndVssAdvisories();
        traceReplacementKeepsBoundedOrder();
        System.out.println("GuidedAttemptEvidenceRegressionTest passed");
    }

    private static void boundsAttemptSamples() {
        GuidedAttemptEvidence evidence = new GuidedAttemptEvidence();
        for (int i = 0; i < 330; i++) {
            evidence.add(sample(i * 0.01, 2.0, 40.0));
        }
        require(evidence.sampleCount() == 320,
                "attempt sample buffer is no longer bounded at 320");
        require(Math.abs(evidence.samples().get(0).getSeconds() - 0.10) < 0.0001,
                "bounded attempt buffer no longer drops oldest samples");
    }

    private static void preservesGearAndVssAdvisories() {
        GuidedAttemptEvidence evidence = new GuidedAttemptEvidence();
        for (int i = 0; i < 10; i++) {
            evidence.add(sample(i * 0.05, i < 5 ? 2.0 : 3.0,
                    i < 8 ? 40.0 : Double.NaN));
        }
        BlendDurationAttempt attempt = evidence.buildAttempt(1,
                new RoadBaselineTracker.Baseline(2000.0, 50.0, 8.0),
                measurement(), hold(), end(), 0.50,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false));
        require(attempt.gearOscillation,
                "gear oscillation advisory changed during evidence extraction");
        require(attempt.vssBad,
                "VSS bad-sample advisory threshold changed during evidence extraction");
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
        return detailed(2.0, 2000.0, 55.0, 20.0, 78.0, 2.0, 40.0);
    }

    private static LiveSample hold() {
        return detailed(2.2, 2040.0, 65.0, 30.0, 80.0, 2.0, 40.0);
    }

    private static LiveSample end() {
        return detailed(2.5, 2100.0, 78.0, 30.0, 80.0, 2.0, 40.0);
    }

    private static LiveSample sample(double seconds, double gear, double vss) {
        return detailed(seconds, 2000.0, 50.0, 8.0, 50.0, gear, vss);
    }

    private static LiveSample detailed(double seconds, double rpm, double map,
                                       double tps, double fallback,
                                       double gear, double vss) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.GEAR, gear);
        values.put(ChannelRole.VSS, vss);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
