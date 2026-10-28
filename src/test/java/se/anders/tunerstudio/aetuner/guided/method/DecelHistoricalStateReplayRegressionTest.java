package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class DecelHistoricalStateReplayRegressionTest {
    private DecelHistoricalStateReplayRegressionTest() { }

    public static void main(String[] args) {
        auditsEdgeAccelCancelHoldAndZeroDisable();
        missingHoldWithholdsCounterConsistency();
        contradictoryDirectCannotEraseTwinEdge();
        System.out.println("DecelHistoricalStateReplayRegressionTest passed");
    }

    private static void auditsEdgeAccelCancelHoldAndZeroDisable() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(0.00, 2000, -0.80, -0.80, 0.50, false, 0, true, 1.0));
        samples.add(sample(0.02, 2000, -0.10, -0.10, 0.50, false, 2, true, 1.0));
        samples.add(sample(0.04, 2000, -0.90, -0.90, 0.50, true, 0, false, 1.0));
        samples.add(sample(0.06, 4000, -5.00, -5.00, 0.00, false, 0, false, 1.0));
        samples.add(sample(0.08, 2000, -0.10, -0.10, 0.50, false, 10, true, 0.98));

        MlgEmbeddedTuneMetadata embedded = metadataWithHold();
        DecelHistoricalStateReplay.Result result =
                DecelHistoricalStateReplay.replay(samples, snapshot(), embedded);

        require(!result.exactStateReplay,
                "logged cycle counter was incorrectly presented as independent full-state replay");
        require(result.counterConsistencyAvailable,
                "complete logged state did not enable counter/state consistency audit");
        require(result.historicalEdgeSamples == 2,
                "historical threshold edge count ignored zero-disable or detector edge");
        require(result.accelCancelledEdgeSamples == 1,
                "simultaneous Accel did not cancel one Decel edge");
        require(result.holdOnlyExpectedActiveSamples == 1,
                "counter-based Hold-only sample was not separated from instantaneous edge");
        require(result.expectedActiveSamples == 2,
                "counter/rule-consistent active count did not follow firmware edge/cancel/Hold order");
        require(result.observedActiveSamples == 3 && result.activeStateMismatches == 1,
                "logged Decel Active mismatch was not audited");
        require(result.fuelEnleanmentSamples == 1,
                "fuel multiplier diagnostic did not detect non-unity enleanment");
        require(result.currentCounterfactualEdgeSamples == 3,
                "current Working Tune counterfactual was not kept separate from as-recorded zero-disable");
        require(result.reportText.contains("NOT AN INDEPENDENT HOLD REPLAY"),
                "state report did not disclose logged-counter dependence");
    }

    private static void missingHoldWithholdsCounterConsistency() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(0.00, 2000, -0.80, -0.80, 0.50, false, 0, true, 1.0));
        MlgEmbeddedTuneMetadata embedded = MlgEmbeddedTuneMetadata.parse(
                "<msq><constant name=\"tpsAeDetectMode\">\"Dual stride, newest\"</constant>"
                + "<constant name=\"tpsDecelThresholdRpmBins\">1000 2000</constant>"
                + "<constant name=\"tpsDecelThresholdValue\">0.5 0.5</constant></msq>");
        DecelHistoricalStateReplay.Result result =
                DecelHistoricalStateReplay.replay(samples, snapshot(), embedded);
        require(!result.counterConsistencyAvailable
                        && result.reportText.contains("EDGE-ONLY / WITHHELD"),
                "missing historical Hold still produced a logged-counter state claim");
    }

    private static void contradictoryDirectCannotEraseTwinEdge() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        // Mirrors the real .4 report failure: direct output says zero while the
        // configured Dual-stride/newest falling twin crosses the threshold.
        samples.add(sample(0.00, 2400, 0.00, -1.40, 0.831, false, 0, true, 1.0));
        DecelHistoricalStateReplay.Result result =
                DecelHistoricalStateReplay.replay(samples, snapshot(), metadataWithHold());
        require(result.historicalEdgeSamples == 1,
                "contradictory direct decelTps erased a valid mode-twin edge");
        require(result.holdOnlyExpectedActiveSamples == 0,
                "valid twin edge was mislabeled as Hold-only state");
    }

    private static MlgEmbeddedTuneMetadata metadataWithHold() {
        return MlgEmbeddedTuneMetadata.parse(
                "<msq><constant name=\"tpsAeDetectMode\">\"Dual stride, newest\"</constant>"
                + "<constant name=\"tpsDecelHoldCycles\">4</constant>"
                + "<constant name=\"tpsDecelThresholdRpmBins\">1000 2000 3000 4000</constant>"
                + "<constant name=\"tpsDecelThresholdValue\">0.5 0.5 0.5 0.0</constant></msq>");
    }

    private static LiveSample sample(double seconds, double rpm,
                                     double directDecel, double newestFall,
                                     double threshold, boolean accelActive,
                                     double cycle, boolean decelActive,
                                     double fuelMult) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, 20.0);
        values.put(ChannelRole.DECEL_DELTA_TPS, directDecel);
        values.put(ChannelRole.AE_DELTA_NEWEST_FALL, newestFall);
        values.put(ChannelRole.DECEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, accelActive ? 1.0 : 0.0);
        values.put(ChannelRole.TPS_DECEL_CYCLE_CNT, cycle);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, decelActive ? 1.0 : 0.0);
        values.put(ChannelRole.DECEL_FUEL_MULT, fuelMult);
        return new LiveSample((long)(seconds * 1.0e9), seconds, values, -10.0, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        double[] rpm = new double[]{1000, 2000, 3000, 4000};
        double[] threshold = new double[]{0.5, 0.5, 0.5, 0.5};
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 40.0, 0.06, true, true, 1.0,
                rpm, threshold, 4.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
