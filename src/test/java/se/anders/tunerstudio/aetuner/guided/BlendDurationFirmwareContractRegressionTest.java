package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Permanent contract separating physical Blend timing from firmware prediction diagnostics. */
public final class BlendDurationFirmwareContractRegressionTest {
    private BlendDurationFirmwareContractRegressionTest() { }

    public static void main(String[] args) throws Exception {
        latestTimerResetRemainsFaithfulDiagnostic();
        predictionTargetCannotGatePhysicalValidity();
        activeCaptureHasNoPostStartRpmCeiling();
        blendUsesThreeHundredRpmEntryWindow();
        currentTuneReplayHasNoPhysicalEventAuthority();
        suggestedTpsStepHasNoCaptureAuthority();
        firmwareBlipCannotDirectlyStartSmallAttempt();
        blendFocusRequestsTwentyHertzPresentation();
        System.out.println("BlendDurationFirmwareContractRegressionTest passed");
    }

    private static void latestTimerResetRemainsFaithfulDiagnostic() {
        BlendDurationCaptureConfig config = new BlendDurationCaptureConfig(
                1500.0, 20.0, 5, 0, false,
                new double[]{1500.0, 2500.0}, new double[]{0.30, 0.25});
        MapCatchupMeasurement m = new MapCatchupMeasurement();
        m.configure(config);
        List<LiveSample> samples = new ArrayList<LiveSample>();
        LiveSample first = sample(0.00, 1550.0, 50.0, 12.0, 80.0, true, true, 10.0);
        LiveSample second = sample(0.10, 1680.0, 55.0, 20.0, 74.0, true, true, 11.0);
        LiveSample settled = sample(0.20, 1850.0, 62.0, 20.2, 74.0, true, false, 11.0);
        samples.add(first); samples.add(second); samples.add(settled);
        m.observePredictionGap(first);
        m.observePredictionGap(second);
        m.observePredictionGap(settled);
        m.beginCatchup(samples, 1.5);
        require(m.measurementAnchor() == second,
                "diagnostic final Blend timer anchor did not move to latest firmware reset");
        require(Math.abs(m.finalPredictionTarget() - 74.0) < 1.0e-9,
                "diagnostic replay retained an older/higher fallback target");
        LiveSample caught = sample(0.42, 2150.0, 74.2, 20.1, 74.0, false, false, 11.0);
        m.observePredictionGap(caught);
        m.observeCatchup(caught);
        require(m.physicalCatchSample() == caught
                        && Math.abs(m.catchupDurationSeconds() - 0.32) < 0.011,
                "firmware-shaped prediction diagnostic no longer follows latest-reset semantics");
    }

    private static void predictionTargetCannotGatePhysicalValidity() throws Exception {
        String session = read("src/main/java/se/anders/tunerstudio/aetuner/guided/BlendDurationGuidedSession.java");
        require(session.contains("PhysicalMapResponseMeasurement physicalResponse")
                        && session.contains("physicalResponse.begin(baseline.map")
                        && session.contains("physicalResponse.durationSeconds()"),
                "Blend session no longer makes measured physical MAP response primary");
        require(!session.contains("if (mapCatchup.bestGap() < MIN_GAP)")
                        && !session.contains("mapCatchup.timedOut(sample, limits.mapCatchupSeconds)"),
                "prediction target/gap still gates physical event validity");
        require(session.contains("Predictive/fallback MAP is diagnostic only")
                        && session.contains("Prediction diagnostic only:"),
                "prediction context is not explicitly diagnostic-only in capture/results");
        String physical = read("src/main/java/se/anders/tunerstudio/aetuner/guided/PhysicalMapResponseMeasurement.java");
        require(physical.contains("LOW_FRACTION = 0.20")
                        && physical.contains("HIGH_FRACTION = 0.80")
                        && !physical.contains("FALLBACK_MAP"),
                "physical Blend timing is not normalized 20->80 measured-MAP response independent of fallbackMap");
    }

    private static void activeCaptureHasNoPostStartRpmCeiling() throws Exception {
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/guided/BlendDurationGuidedSession.java");
        int start = source.indexOf("private void capture(LiveSample sample)");
        int end = source.indexOf("private void acquirePlateau", start);
        require(start >= 0 && end > start, "could not inspect Blend capture block");
        String capture = source.substring(start, end);
        require(!capture.contains("RPM_CAPTURE_TOLERANCE") && !capture.contains("RPM left the selected"),
                "Blend capture still rejects an event for post-start RPM rise");
        require(source.contains("RPM may continue rising") && source.contains("no post-start RPM ceiling"),
                "driver guidance does not explain that RPM is entry-only");
    }

    private static void blendUsesThreeHundredRpmEntryWindow() throws Exception {
        require(Math.abs(BlendDurationGuidedSession.ENTRY_RPM_TOLERANCE - 300.0) < 1.0e-9,
                "Blend Duration entry tolerance is not ±300 RPM");
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/guided/BlendDurationGuidedSession.java");
        require(source.contains("ENTRY_RPM_TOLERANCE")
                        && source.contains("double eventRpm = rpmBinLatch.latchedRpm()")
                        && source.contains("readyCheck(sample, eventRpm"),
                "Blend session is not routing READY through the immutable per-event RPM latch and entry-only tolerance");
    }

    private static void currentTuneReplayHasNoPhysicalEventAuthority() throws Exception {
        String session = read("src/main/java/se/anders/tunerstudio/aetuner/guided/BlendDurationGuidedSession.java");
        require(!session.contains("boolean modelWarning = !mapCatchup.effectiveMapModelConsistent()"),
                "current Blend Duration replay still participates in physical event warning authority");
        require(session.contains("boolean warning = candidate.gearReliabilityWarning()")
                        && session.contains("|| assignment.nearBoundary")
                        && session.contains("|| physicalResponse.usedBoundedLateWindow()"),
                "physical Blend warning authority no longer has intended non-replay sources");
        require(session.contains("Diagnostic only: current-tune Effective MAP replay")
                        && session.contains("does not change physical event validity, warning state, measured duration, or repeatability authority"),
                "capture result does not explicitly preserve replay as diagnostic-only context");
        String focus = read("src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019BlendDurationFocus.java");
        require(focus.contains("Maneuver shape — what to do")
                        && focus.contains("Existing Blend (context)")
                        && focus.contains("DIAGNOSTIC ONLY"),
                "Blend Focus presentation can again imply the existing Blend setting is a target");
    }

    private static void suggestedTpsStepHasNoCaptureAuthority() throws Exception {
        String session = read("src/main/java/se/anders/tunerstudio/aetuner/guided/BlendDurationGuidedSession.java");
        require(session.contains("if (plateau.usable) {")
                        && !session.contains("plateau.usable && settings.acceptsTpsStep"),
                "configured suggested TPS step still gates physical Blend acceptance");
        require(session.contains("coaching suggestion only")
                        && session.contains("repeatability is decided by comparable-event grouping"),
                "driver guidance does not state TPS authority boundary");
    }

    private static void firmwareBlipCannotDirectlyStartSmallAttempt() throws Exception {
        String session = read("src/main/java/se/anders/tunerstudio/aetuner/guided/BlendDurationGuidedSession.java");
        int ready = session.indexOf("if (state == GuidedCaptureState.READY)");
        int pending = session.indexOf("if (state == GuidedCaptureState.OPENING_PENDING)", ready);
        require(ready >= 0 && pending > ready, "could not inspect Blend READY routing");
        String block = session.substring(ready, pending);
        require(block.contains("openingDetector.localTipInStarted")
                        && block.contains("triggered(sample) || sample.bool(ChannelRole.MAP_PRED_ACTIVE)"),
                "READY no longer distinguishes physical opening from firmware-only pending evidence");
        String detector = read("src/main/java/se/anders/tunerstudio/aetuner/guided/PedalOpeningDetector.java");
        require(detector.contains("if (rise >= localRequired)"),
                "pending opening lets firmware evidence bypass broad local TPS threshold");
    }

    private static void blendFocusRequestsTwentyHertzPresentation() throws Exception {
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019BlendDurationFocus.java");
        require(source.contains("int refreshIntervalMillis() { return 50; }"),
                "Blend Driver Focus no longer requests ~20 Hz live presentation refresh");
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static LiveSample sample(double seconds, double rpm, double map,
                                     double tps, double fallback,
                                     boolean prediction, boolean accel,
                                     double resetCounter) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.EFFECTIVE_MAP, prediction ? fallback : map);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.MAP_PRED_RESET_CNT, resetCounter);
        values.put(ChannelRole.MAP_PRED_EVENT_OVER, 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, accel ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, accel ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
