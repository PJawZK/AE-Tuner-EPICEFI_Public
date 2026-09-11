package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class DecelDetectionRecommendationRegressionTest {
    private static final double[] RPM_BINS =
            new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500};

    private DecelDetectionRecommendationRegressionTest() { }

    public static void main(String[] args) {
        twoSidedFallingEvidenceChangesOnlyObservedBins();
        zeroDisabledCellNeverAutoEnables();
        holdCyclesRemainReadOnly();
        openingDirectionCannotBecomeDecelEvidence();
        insufficientTwoSidedEvidenceWithholdsPlan();
        System.out.println("DecelDetectionRecommendationRegressionTest passed");
    }

    private static void twoSidedFallingEvidenceChangesOnlyObservedBins() {
        AeProjectSnapshot snapshot = snapshot(
                new double[]{0.30, 1.00, 0.30, 0.30, 1.00, 0.30, 0.30, 0.30}, 12.0);
        DecelDetectionRecommendation.Result result =
                DecelDetectionRecommendation.evaluate(snapshot,
                        evidenceForBins(1500.0, 3000.0));
        ProposalWritePlan plan = result.plan;
        require(plan != null && plan.changeCount() == 2,
                "two observed nonzero bins should create exactly two threshold changes");
        require(result.eligibleBins == 2 && result.changedBins == 2,
                "recommendation did not report exact evidence-backed bin counts");
        ProposalWritePlan.Change first = plan.getChanges().get(0);
        ProposalWritePlan.Change second = plan.getChanges().get(1);
        require(AeParameterNames.TPS_DECEL_THRESHOLD_VALUES.equals(first.parameterName)
                        && AeParameterNames.TPS_DECEL_THRESHOLD_VALUES.equals(second.parameterName),
                "Decel recommendation targeted a setting outside tpsDecelThresholdValue");
        require(first.flatIndex == 1 && second.flatIndex == 4,
                "Decel recommendation extrapolated into an unobserved RPM bin");
        requireClose(0.75, first.proposedValue,
                "Decel recommendation did not obey conservative 25% movement cap");
        require(result.reviewText.contains("Normal Corrections 3 event(s), Decel Releases 3 event(s)")
                        && result.reviewText.contains("Proposed threshold changes: 2")
                        && result.reviewText.contains("signed closing movement")
                        && result.reviewText.contains("threshold 0 disables detection"),
                "review did not preserve falling-TPS event classes or firmware semantics");
    }

    private static void zeroDisabledCellNeverAutoEnables() {
        double[] thresholds = new double[]{0.30, 0.0, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30};
        DecelDetectionRecommendation.Result result =
                DecelDetectionRecommendation.evaluate(snapshot(thresholds, 9.0),
                        evidenceForBins(1500.0));
        DecelDetectionRecommendation.BinDecision bin = decisionAt(result, 1500.0);
        require(bin != null && bin.validated,
                "zero-disabled bin could not retain event separation evidence");
        require(bin.currentDisabled && !bin.eligible && !bin.changed,
                "zero-disabled bin unexpectedly gained automatic proposal authority");
        require(result.plan == null,
                "zero-disabled bin was automatically enabled by a ProposalWritePlan");
        require(result.reviewText.contains("never auto-enabled")
                        || result.reviewText.contains("automatic enabling is withheld"),
                "review did not explain the zero/disabled safety boundary");
    }

    private static void holdCyclesRemainReadOnly() {
        DecelDetectionRecommendation.Result result =
                DecelDetectionRecommendation.evaluate(
                        snapshot(new double[]{0.30,1.00,0.30,0.30,1.00,0.30,0.30,0.30}, 17.0),
                        evidenceForBins(1500.0, 3000.0));
        require(result.plan != null, "fixture did not produce threshold proposal");
        for (ProposalWritePlan.Change change : result.plan.getChanges()) {
            require(!AeParameterNames.TPS_DECEL_HOLD_CYCLES.equals(change.parameterName),
                    "first Decel pass attempted to tune hold cycles together with threshold");
        }
        require(result.reviewText.contains("Decel hold: 17.000 engine cycles — READ ONLY")
                        && result.reviewText.contains("threshold and hold are intentionally not tuned together"),
                "review did not preserve read-only hold-cycle boundary");
    }

    private static void openingDirectionCannotBecomeDecelEvidence() {
        AeProjectSnapshot snapshot = snapshot(new double[]{1,1,1,1,1,1,1,1}, 12.0);
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = appendQuiet(samples, 0.0, 1500.0, 90);
        for (int i = 0; i < 12; i++) {
            samples.add(sample(seconds, 1500.0, 10.0 + i * 0.3,
                    0.60, 18.0, false));
            seconds += 0.30;
        }
        DecelDetectionRecommendation.Result result =
                DecelDetectionRecommendation.evaluate(snapshot, samples);
        require(result.summary.calibrationFrozen,
                "fixture did not freeze quiet calibration");
        require(result.summary.movementEvents == 0,
                "positive/opening TPS movement leaked into falling-TPS event evidence");
        require(result.plan == null,
                "positive/opening TPS movement created a Decel threshold proposal");
    }

    private static void insufficientTwoSidedEvidenceWithholdsPlan() {
        AeProjectSnapshot snapshot = snapshot(new double[]{1,1,1,1,1,1,1,1}, 12.0);
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = appendQuiet(samples, 0.0, 1500.0, 90);
        seconds = appendNormal(samples, seconds, 1500.0, 0);
        seconds = appendNormal(samples, seconds, 1500.0, 1);
        seconds = appendNormal(samples, seconds, 1500.0, 2);
        seconds = appendRelease(samples, seconds, 1500.0, 0);
        DecelDetectionRecommendation.Result result =
                DecelDetectionRecommendation.evaluate(snapshot, samples);
        DecelDetectionRecommendation.BinDecision bin = decisionAt(result, 1500.0);
        require(bin != null && bin.normalEvents == 3 && bin.releaseEvents == 1,
                "fixture did not preserve one-sided evidence counts");
        require(!bin.validated && result.plan == null,
                "one-sided Decel evidence created a threshold proposal");
    }

    private static List<LiveSample> evidenceForBins(double... rpms) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = appendQuiet(samples, 0.0, rpms[0], 90);
        for (int event = 0; event < 3; event++) {
            for (double rpm : rpms) seconds = appendNormal(samples, seconds, rpm, event);
        }
        for (int event = 0; event < 3; event++) {
            for (double rpm : rpms) seconds = appendRelease(samples, seconds, rpm, event);
        }
        return samples;
    }

    private static double appendQuiet(List<LiveSample> samples, double seconds,
                                      double rpm, int count) {
        for (int i = 0; i < count; i++) {
            samples.add(sample(seconds, rpm, 20.0,
                    (i % 2 == 0 ? 0.020 : -0.020), 0.20, false));
            seconds += 0.020;
        }
        return seconds + 0.25;
    }

    private static double appendNormal(List<LiveSample> samples, double seconds,
                                       double rpm, int event) {
        double baseline = 20.0 + event * 0.2;
        samples.add(sample(seconds, rpm, baseline, 0.020, 0.20, false));
        seconds += 0.25;
        samples.add(sample(seconds, rpm, baseline - 0.18,
                -(0.100 + event * 0.004), -3.0, false));
        return seconds + 0.30;
    }

    private static double appendRelease(List<LiveSample> samples, double seconds,
                                        double rpm, int event) {
        double baseline = 24.0 + event * 0.3;
        samples.add(sample(seconds, rpm, baseline, 0.020, 0.20, false));
        seconds += 0.25;
        samples.add(sample(seconds, rpm, baseline - 0.60,
                -(0.42 + event * 0.01), -18.0, false));
        samples.add(sample(seconds + 0.05, rpm, baseline - 1.10,
                -(0.58 + event * 0.01), -4.0, false));
        samples.add(sample(seconds + 0.10, rpm, baseline - 1.15,
                -(0.54 + event * 0.01), -1.0, false));
        return seconds + 0.40;
    }

    private static LiveSample sample(double seconds, double rpm, double tps,
                                     double delta, double tpsRate,
                                     boolean decelActive) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, decelActive ? 1.0 : 0.0);
        return new LiveSample((long) (seconds * 1.0e9), seconds, values, tpsRate, 0.0);
    }

    private static DecelDetectionRecommendation.BinDecision decisionAt(
            DecelDetectionRecommendation.Result result, double rpm) {
        for (DecelDetectionRecommendation.BinDecision bin : result.summary.bins) {
            if (Math.abs(bin.rpm - rpm) < 0.001) return bin;
        }
        return null;
    }

    private static AeProjectSnapshot snapshot(double[] thresholds, double holdCycles) {
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 30.0, 0.05,
                false, true, 0.35,
                RPM_BINS, thresholds, holdCycles);
    }

    private static void requireClose(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
