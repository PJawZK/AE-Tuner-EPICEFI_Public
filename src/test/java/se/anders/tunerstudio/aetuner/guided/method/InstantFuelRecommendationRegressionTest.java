package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class InstantFuelRecommendationRegressionTest {
    private InstantFuelRecommendationRegressionTest() { }

    public static void main(String[] args) {
        repeatableEarlyLeanMovesOnlyMultiplier();
        zeroMultiplierNeverAutoEnables();
        sustainedErrorIsRejected();
        timerBelongsToSeparateSetupInhibitAuthority();
        System.out.println("InstantFuelRecommendationRegressionTest passed");
    }

    private static void repeatableEarlyLeanMovesOnlyMultiplier() {
        AeProjectSnapshot snapshot = snapshot(true, 1.00, 4.0);
        InstantFuelRecommendation.Result result =
                InstantFuelRecommendation.evaluate(snapshot, evidence(3, 0.055, 0.005));
        require(result.plan != null && result.plan.changeCount() == 1,
                "three clean residual-lean pulse events should create one multiplier plan");
        ProposalWritePlan.Change change = result.plan.getChanges().get(0);
        require(AeParameterNames.TPS_EXTRA_SHOT_MULT.equals(change.parameterName),
                "Instant recommendation targeted a setting other than tpsExtraShotMult");
        requireClose(1.00, change.expectedValue, "Instant multiplier stale baseline changed");
        requireClose(1.10, change.proposedValue,
                "Instant multiplier did not use bounded 10 percent step");
        require(result.reviewText.contains("timer 4.00 engine cycle(s)")
                        && result.reviewText.contains("handled by the setup/inhibit task"),
                "review did not route timer ownership to the separate setup/inhibit task");
    }

    private static void zeroMultiplierNeverAutoEnables() {
        InstantFuelRecommendation.Result result = InstantFuelRecommendation.evaluate(
                snapshot(true, 0.0, 4.0), evidence(4, 0.06, 0.0));
        require(result.plan == null, "zero multiplier was automatically enabled");
        require(result.reviewText.contains("Automatic enabling/seeding is prohibited"),
                "zero-multiplier safety boundary is not visible");
    }

    private static void sustainedErrorIsRejected() {
        InstantFuelRecommendation.Result result = InstantFuelRecommendation.evaluate(
                snapshot(true, 1.0, 4.0), evidence(4, 0.06, 0.06));
        require(result.plan == null,
                "sustained same-direction error was incorrectly hidden with Instant multiplier");
        require(result.sustainedRejected >= 3,
                "sustained-error gate did not reject the synthetic events");
    }

    private static void timerBelongsToSeparateSetupInhibitAuthority() {
        InstantFuelRecommendation.Result result = InstantFuelRecommendation.evaluate(
                snapshot(true, 1.0, 9.0), evidence(3, -0.055, 0.0));
        require(result.plan != null,
                "fixture did not create rich-response multiplier plan");
        for (ProposalWritePlan.Change change : result.plan.getChanges()) {
            require(!AeParameterNames.TPS_EXTRA_SHOT_TIMER.equals(change.parameterName),
                    "Instant multiplier recommendation attempted to tune timer in the same authority");
        }
        require(result.reviewText.contains("timer handled by the setup/inhibit task")
                        || result.reviewText.contains("timer 9.00 engine cycle(s)"),
                "review did not preserve separate timer ownership");
    }

    private static List<LiveSample> evidence(int events, double earlyError,
                                             double lateError) {
        List<LiveSample> out = new ArrayList<LiveSample>();
        double t = 0.0;
        double counter = 10.0;
        for (int e = 0; e < events; e++) {
            counter += 1.0;
            out.add(sample(t, counter, 0.8, 1.00));
            for (int i = 1; i <= 9; i++) {
                double dt = i * 0.10;
                out.add(sample(t + dt, counter, 0.0,
                        1.00 + earlyError));
            }
            for (int i = 10; i <= 18; i++) {
                double dt = i * 0.10;
                out.add(sample(t + dt, counter, 0.0,
                        1.00 + lateError));
            }
            t += 2.5;
        }
        return out;
    }

    private static LiveSample sample(double seconds, double counter,
                                     double instantPw, double lambda) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 3000.0);
        values.put(ChannelRole.TPS, 35.0);
        values.put(ChannelRole.MAP, 100.0);
        values.put(ChannelRole.LAMBDA, lambda);
        values.put(ChannelRole.TARGET_LAMBDA, 1.00);
        values.put(ChannelRole.PW, 6.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS,
                instantPw > 0 ? 0.4 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 0.2);
        values.put(ChannelRole.AE_EVENT_JUST_OCCURRED,
                instantPw > 0 ? 1.0 : 0.0);
        values.put(ChannelRole.INSTANT_PULSE_PW, instantPw);
        values.put(ChannelRole.INSTANT_PULSE_CNT, counter);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        return new LiveSample((long)(seconds * 1.0e9), seconds, values,
                instantPw > 0 ? 20.0 : 0.0, 0.0);
    }

    private static AeProjectSnapshot snapshot(boolean enabled,
                                               double multiplier,
                                               double timer) {
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                multiplier, timer, new double[0], new double[0],
                false, false, "Basic", enabled, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static void requireClose(double expected, double actual,
                                     String message) {
        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected "
                    + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
