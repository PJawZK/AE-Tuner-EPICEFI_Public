package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class WallWettingRecommendationRegressionTest {
    private WallWettingRecommendationRegressionTest() { }

    public static void main(String[] args) {
        repeatedLeanTipInMovesBetaWithoutInventingTau();
        persistenceMismatchIsDiagnosticOnlyUntilTransportAligned();
        zeroBetaNeverAutoEnables();
        advancedModelRoutesToAdvancedTask();
        instantOverlapIsRejected();
        System.out.println("WallWettingRecommendationRegressionTest passed");
    }

    private static void repeatedLeanTipInMovesBetaWithoutInventingTau() {
        AeProjectSnapshot snapshot = snapshot("Basic", true, 0.80, 0.40);
        WallWettingRecommendation.Result result =
                WallWettingRecommendation.evaluate(snapshot, evidence(3, 0.055, false));
        require(result.plan != null && result.plan.changeCount() == 1,
                "three clean repeatable lean method-owned tip-ins should create one bounded Beta plan");
        ProposalWritePlan.Change change = result.plan.getChanges().get(0);
        require(AeParameterNames.WALL_BETA.equals(change.parameterName),
                "amplitude evidence targeted a setting other than wwaeBeta");
        requireClose(0.40, change.expectedValue, "Beta stale baseline changed");
        requireClose(0.44, change.proposedValue, "Beta step was not bounded to 10 percent");
        require(result.reviewText.contains("WITHHOLD Tau automatic movement")
                        && result.reviewText.contains("transport"),
                "review did not expose the transport-delay boundary for Tau");
    }

    private static void persistenceMismatchIsDiagnosticOnlyUntilTransportAligned() {
        WallWettingRecommendation.Result result = WallWettingRecommendation.evaluate(
                snapshot("Basic", true, 0.80, 0.40), persistenceEvidence(3));
        require(result.plan != null,
                "qualified amplitude evidence should still allow a bounded Beta plan");
        ProposalWritePlan.Change beta = find(result.plan, AeParameterNames.WALL_BETA);
        ProposalWritePlan.Change tau = find(result.plan, AeParameterNames.WALL_TAU);
        require(beta != null, "qualified Wall amplitude evidence did not retain Beta authority");
        require(tau == null,
                "raw wall-decay/lambda-recovery timing created an automatic Tau write before transport alignment");
        require(Double.isFinite(result.medianPersistenceDelta),
                "raw persistence relationship was not retained as diagnostic evidence");
        require(result.reviewText.contains("diagnostic only")
                        && result.reviewText.contains("NOT Tau write authority"),
                "review did not distinguish diagnostic persistence from Tau write authority");
    }

    private static void zeroBetaNeverAutoEnables() {
        WallWettingRecommendation.Result result = WallWettingRecommendation.evaluate(
                snapshot("Basic", true, 0.80, 0.0), evidence(4, 0.06, false));
        require(result.plan == null, "zero/disabled Beta was automatically enabled");
        require(result.reviewText.contains("automatic enabling is withheld")
                        && result.reviewText.contains("Task Settings"),
                "zero-Beta safety boundary or explicit setup route is not visible");
    }

    private static void advancedModelRoutesToAdvancedTask() {
        WallWettingRecommendation.Result result = WallWettingRecommendation.evaluate(
                snapshot("Advanced", true, 0.80, 0.40), evidence(4, 0.06, false));
        require(result.plan == null, "Advanced wall model unexpectedly received Basic scalar plan");
        require(result.reviewText.contains("Advanced Tau/Beta Mapping"),
                "advanced model was not routed to its active dedicated mapping task");
    }

    private static void instantOverlapIsRejected() {
        WallWettingRecommendation.Result result = WallWettingRecommendation.evaluate(
                snapshot("Basic", true, 0.80, 0.40), evidence(4, 0.06, true));
        require(result.plan == null, "Instant-overlapped events created a Wall plan");
        require(result.rejectedInstantOverlap >= 3,
                "Instant overlap was not counted as rejected Wall evidence");
    }

    private static List<LiveSample> evidence(int events, double lambdaError, boolean instant) {
        List<LiveSample> out = new ArrayList<LiveSample>();
        double t = 0.0;
        for (int e = 0; e < events; e++) {
            out.add(sample(t, 2500, 20.0, 10.0, 0.10, 1.00, 0.08,
                    instant ? 0.5 : 0.0));
            for (int i = 1; i <= 6; i++) {
                double dt = i * 0.20;
                out.add(sample(t + dt, 2500, 22.0, 0.0, 0.08,
                        1.00 + lambdaError, 0.06, instant ? 0.2 : 0.0));
            }
            t += 2.5;
        }
        return out;
    }

    private static List<LiveSample> persistenceEvidence(int events) {
        List<LiveSample> out = new ArrayList<LiveSample>();
        double t = 0.0;
        for (int e = 0; e < events; e++) {
            out.add(sample(t, 2500, 20.0, 10.0, 0.10, 1.00, 0.10, 0.0));
            out.add(sample(t + 0.20, 2500, 22.0, 0.0, 0.08, 1.055, 0.08, 0.0));
            out.add(sample(t + 0.40, 2500, 22.0, 0.0, 0.07, 1.040, 0.07, 0.0));
            out.add(sample(t + 0.60, 2500, 22.0, 0.0, 0.06, 1.010, 0.06, 0.0));
            out.add(sample(t + 0.80, 2500, 22.0, 0.0, 0.04, 1.005, 0.04, 0.0));
            out.add(sample(t + 1.00, 2500, 22.0, 0.0, 0.01, 1.000, 0.01, 0.0));
            out.add(sample(t + 1.20, 2500, 22.0, 0.0, 0.00, 1.000, 0.00, 0.0));
            t += 2.5;
        }
        return out;
    }

    private static ProposalWritePlan.Change find(ProposalWritePlan plan, String parameter) {
        if (plan == null) return null;
        for (ProposalWritePlan.Change change : plan.getChanges()) {
            if (parameter.equals(change.parameterName)) return change;
        }
        return null;
    }

    private static LiveSample sample(double seconds, double rpm, double tps,
                                     double tpsDot, double wallCorrection,
                                     double lambda, double wallPw, double instantPw) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, 70.0);
        values.put(ChannelRole.LAMBDA, lambda);
        values.put(ChannelRole.TARGET_LAMBDA, 1.00);
        values.put(ChannelRole.PW, 4.0);
        values.put(ChannelRole.WALL_CORRECTION, wallCorrection);
        values.put(ChannelRole.WALL_WETTING_PW, wallPw);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, tpsDot > 0 ? 0.20 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 0.10);
        values.put(ChannelRole.INSTANT_PULSE_PW, instantPw);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        return new LiveSample((long)(seconds * 1.0e9), seconds, values, tpsDot, 0.0);
    }

    private static AeProjectSnapshot snapshot(String model, boolean enabled,
                                               double tau, double beta) {
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                1.0, 3.0, new double[0], new double[0],
                false, enabled, model, false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 30.0, 0.05, false, true, 0.35,
                new double[0], new double[0], Double.NaN,
                tau, beta);
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
