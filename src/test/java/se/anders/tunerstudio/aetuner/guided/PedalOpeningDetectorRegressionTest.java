package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.EnumMap;
import java.util.List;

public final class PedalOpeningDetectorRegressionTest {
    private PedalOpeningDetectorRegressionTest() { }

    public static void main(String[] args) {
        smallMovementEntersPendingThenCanAbortSilently();
        detectorOrUsableLocalRiseConfirmsPendingOpening();
        smallNonTriggeredTimeoutAbortsSilentlyButSafetyReturnsToBaseline();
        System.out.println("PedalOpeningDetectorRegressionTest passed");
    }

    private static void smallMovementEntersPendingThenCanAbortSilently() {
        PedalOpeningDetector detector = new PedalOpeningDetector();
        RoadBaselineTracker.Baseline baseline =
                new RoadBaselineTracker.Baseline(2600.0, 50.0, 8.0);
        LiveSample first = sample(1.00, 2600.0, 50.2, 9.2, false, false, true);
        require(detector.movementStarted(first, baseline.tps),
                "small +1.2 TPS movement did not cross pending threshold");
        require(!detector.localTipInStarted(first, baseline.tps, 10.0),
                "small movement was confirmed as local tip-in too early");
        detector.beginPending(first);
        LiveSample returned = sample(1.05, 2600.0, 50.1, 8.3,
                false, false, true);
        PedalOpeningDetector.Decision decision = detector.observePending(
                returned, baseline, 2600.0, GuidedVehicleTestLimits.defaults(false));
        require(decision.type == PedalOpeningDetector.DecisionType.ABORT_TO_READY,
                "returned small pedal movement did not silently restore READY");
    }

    private static void detectorOrUsableLocalRiseConfirmsPendingOpening() {
        RoadBaselineTracker.Baseline baseline =
                new RoadBaselineTracker.Baseline(2600.0, 50.0, 8.0);
        PedalOpeningDetector detector = new PedalOpeningDetector();
        LiveSample first = sample(2.00, 2600.0, 50.2, 9.2, false, false, true);
        detector.beginPending(first);
        LiveSample ecu = sample(2.05, 2610.0, 52.0, 10.0, true, true, true);
        require(detector.observePending(ecu, baseline, 2600.0,
                        GuidedVehicleTestLimits.defaults(false)).type
                        == PedalOpeningDetector.DecisionType.CONFIRM,
                "ECU detector/prediction evidence did not confirm opening");
        List<LiveSample> early = detector.consumePendingSamples();
        require(early.size() == 2,
                "pending pre-confirmation samples were not retained");

        detector.beginPending(first);
        LiveSample tooSmall = sample(2.05, 2610.0, 51.0, 17.9,
                false, false, true);
        require(detector.observePending(tooSmall, baseline, 2600.0,
                        GuidedVehicleTestLimits.defaults(false)).type
                        == PedalOpeningDetector.DecisionType.WAIT,
                "sub-10 TPS local movement confirmed a Guided opening");

        LiveSample local = sample(2.08, 2610.0, 51.5, 18.1,
                false, false, true);
        require(detector.observePending(local, baseline, 2600.0,
                        GuidedVehicleTestLimits.defaults(false)).type
                        == PedalOpeningDetector.DecisionType.CONFIRM,
                "usable +10 TPS local rise did not confirm pending opening");
    }

    private static void smallNonTriggeredTimeoutAbortsSilentlyButSafetyReturnsToBaseline() {
        RoadBaselineTracker.Baseline baseline =
                new RoadBaselineTracker.Baseline(2600.0, 50.0, 8.0);
        PedalOpeningDetector detector = new PedalOpeningDetector();
        detector.beginPending(sample(3.00, 2600.0, 50.0, 9.2,
                false, false, true));
        PedalOpeningDetector.Decision timeout = detector.observePending(
                sample(3.60, 2600.0, 50.2, 9.3, false, false, true),
                baseline, 2600.0, GuidedVehicleTestLimits.defaults(false));
        require(timeout.type == PedalOpeningDetector.DecisionType.ABORT_TO_READY,
                "small non-triggered road correction became a failed Guided outcome");
        require(timeout.reason.length() == 0,
                "silent small-movement abort unexpectedly carried a failure reason");

        detector.reset();
        detector.beginPending(sample(4.00, 2600.0, 50.0, 9.2,
                false, false, true));
        PedalOpeningDetector.Decision unsafe = detector.observePending(
                sample(4.05, 2600.0, 50.0, 9.4, false, false, false),
                baseline, 2600.0, GuidedVehicleTestLimits.defaults(false));
        require(unsafe.type == PedalOpeningDetector.DecisionType.RETURN_TO_BASELINE,
                "unsafe running state did not cancel pending opening");
    }

    private static LiveSample sample(double seconds, double rpm,
                                     double map, double tps,
                                     boolean detector, boolean prediction,
                                     boolean running) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, map);
        values.put(ChannelRole.ENGINE_RUNNING, running ? 1.0 : 0.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
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
