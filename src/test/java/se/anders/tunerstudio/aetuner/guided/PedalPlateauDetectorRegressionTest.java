package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class PedalPlateauDetectorRegressionTest {
    private PedalPlateauDetectorRegressionTest() { }

    public static void main(String[] args) {
        acceptsStableNaturalPlateauWithinGenericRoadBounds();
        rejectsTooSmallOrTooLargeNaturalStep();
        targetStepPolicyLivesInCaptureConfig();
        rejectsMovingPedalWindow();
        System.out.println("PedalPlateauDetectorRegressionTest passed");
    }

    private static void acceptsStableNaturalPlateauWithinGenericRoadBounds() {
        List<LiveSample> samples = flat(30.0);
        PedalPlateauDetector.Result result = PedalPlateauDetector.evaluate(
                samples, 8.0, nanos(1.20));
        require(result.usable, "stable +22 natural plateau was not accepted by the generic detector");
        close(result.step, 22.0, 0.001, "relative TPS step changed");
    }

    private static void rejectsTooSmallOrTooLargeNaturalStep() {
        PedalPlateauDetector.Result small = PedalPlateauDetector.evaluate(
                flat(16.0), 8.0, nanos(1.20));
        require(!small.usable && small.step < PedalPlateauDetector.MIN_USABLE_STEP,
                "sub-10-point natural opening became usable");
        PedalPlateauDetector.Result large = PedalPlateauDetector.evaluate(
                flat(50.0), 8.0, nanos(1.20));
        require(!large.usable && large.step > PedalPlateauDetector.MAX_USABLE_STEP,
                "over-40-point natural opening became usable");
    }

    private static void targetStepPolicyLivesInCaptureConfig() {
        BlendDurationCaptureConfig twenty =
                new BlendDurationCaptureConfig(2000.0, 20.0, 5, 2, false);
        require(twenty.acceptsTpsStep(10.0) && twenty.acceptsTpsStep(20.0)
                        && twenty.acceptsTpsStep(30.0)
                        && !twenty.acceptsTpsStep(9.9)
                        && !twenty.acceptsTpsStep(30.1),
                "+20 controlled target window no longer matches dev5 +10..+30 tolerance");
        BlendDurationCaptureConfig forty =
                new BlendDurationCaptureConfig(2000.0, 40.0, 5, 2, false);
        require(forty.acceptsTpsStep(30.0) && forty.acceptsTpsStep(40.0)
                        && !forty.acceptsTpsStep(29.9)
                        && !forty.acceptsTpsStep(40.1),
                "+40 controlled target window no longer clamps to +30..+40");
    }

    private static void rejectsMovingPedalWindow() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1.00, 25.0));
        samples.add(sample(1.05, 28.0));
        samples.add(sample(1.10, 31.0));
        samples.add(sample(1.15, 34.0));
        samples.add(sample(1.20, 37.0));
        PedalPlateauDetector.Result result =
                PedalPlateauDetector.evaluate(samples, 8.0, nanos(1.20));
        require(!result.usable, "moving pedal window was treated as a plateau");
        require(result.range > PedalPlateauDetector.RANGE_LIMIT,
                "moving-window range evidence changed");
    }

    private static List<LiveSample> flat(double tps) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1.00, tps));
        samples.add(sample(1.05, tps + 0.2));
        samples.add(sample(1.10, tps - 0.1));
        samples.add(sample(1.15, tps + 0.1));
        samples.add(sample(1.20, tps));
        return samples;
    }

    private static LiveSample sample(double seconds, double tps) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.TPS, tps);
        return new LiveSample(nanos(seconds), seconds, values, 0.0, 0.0);
    }

    private static long nanos(double seconds) {
        return Math.round(seconds * 1000000000.0);
    }

    private static void close(double actual, double expected,
                              double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " vs " + expected);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
