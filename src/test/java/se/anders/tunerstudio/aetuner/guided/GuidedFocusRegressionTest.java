package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusBridge;
import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.passive.MapEstimateCollector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Regression checks for the presentation-only Guided Focus layer. */
public final class GuidedFocusRegressionTest {
    private GuidedFocusRegressionTest() { }

    public static void main(String[] args) {
        acceptedSecondsExcludeRejectedGap();
        incompleteRequiredDataPausesFocusProgress();
        focusHubFollowsMapEstimateSession();
        heatMapExposesLoadedAxesAndTextStates();
        plannedTaskCoachExplainsRealControlFamilyAndWorkflow();
        engagementFocusIsPassiveCaptureFirst();
        foundationThresholdGetsDedicatedEvidenceFocus();
        System.out.println("GuidedFocusRegressionTest passed");
    }

    private static void acceptedSecondsExcludeRejectedGap() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE),
                mapSnapshot(), 5, 3, 115.0);
        session.accept(mapSample(1.00, false, false));
        session.accept(mapSample(1.05, false, false));
        session.accept(mapSample(1.10, true, false));
        session.accept(mapSample(1.20, false, false));
        MapEstimateFocusSnapshot focus = session.mapEstimateFocusSnapshot(null);
        require(focus.countAt(1, 0) == 3, "rejected MAP Predict sample changed the stable-cell count");
        double seconds = focus.acceptedSecondsAt(1, 0);
        require(seconds >= 0.12 && seconds < 0.16,
                "accepted-seconds display credited rejected gap time: " + seconds);
        require(focus.isComplete(1, 0), "three clean stable samples did not satisfy the test minimum");
    }

    private static void incompleteRequiredDataPausesFocusProgress() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE),
                mapSnapshot(), 5, 3, 115.0);
        session.accept(mapSample(2.00, false, false));
        session.accept(mapSample(2.05, false, true));
        MapEstimateFocusSnapshot focus = session.mapEstimateFocusSnapshot(null);
        require(focus.countAt(1, 0) == 1, "required-incomplete sample entered MAP Estimate evidence");
        require(focus.eligibility == MapEstimateCollector.LiveEligibility.MISSING_REQUIRED,
                "required-incomplete sample was not surfaced as a driver-facing pause reason");
        require(!focus.eligibility.isCollecting(),
                "required-incomplete focus state still presented itself as collecting");
    }

    private static void focusHubFollowsMapEstimateSession() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE),
                mapSnapshot(), 5, 3, 115.0);
        session.accept(mapSample(3.00, false, false));
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        require(state.recipe == GuidedTuningRecipe.MAP_ESTIMATE, "focus hub did not identify MAP Estimate");
        require(state.captureState == GuidedCaptureState.CAPTURING, "focus hub did not follow active capture state");
        require(state.mapEstimate != null && state.mapEstimate.hasTable(),
                "focus hub omitted MAP Estimate heat-map state");
        require(state.mapEstimate.liveRow == 1 && state.mapEstimate.liveCol == 0,
                "focus hub did not expose the live TPS/RPM cell");
        session.finish();
        require(GuidedFocusHub.snapshot().captureState == GuidedCaptureState.COMPLETE,
                "focus hub did not follow finish/review state");
    }

    private static void heatMapExposesLoadedAxesAndTextStates() {
        MapEstimateFocusSnapshot setup = MapEstimateFocusSnapshot.setup(mapSnapshot(), 20, null);
        MapEstimateGuidedFocusPanel panel = new MapEstimateGuidedFocusPanel();
        panel.updateSnapshot(setup, GuidedCaptureState.IDLE);
        require(panel.tableForTest().getRowCount() == 2, "heat map did not expose both TPS rows");
        require(panel.tableForTest().getColumnCount() == 3,
                "heat map did not expose TPS label plus both RPM columns");
        require(String.valueOf(panel.tableForTest().getValueAt(0, 1)).contains("—"),
                "empty heat-map cell did not expose a text state in addition to color");
    }

    private static void plannedTaskCoachExplainsRealControlFamilyAndWorkflow() {
        String decel = GuidedTaskFocusCatalog.focusText(
                GuidedTuningRecipe.DECEL_FUEL, "Working tune read; detailed decel values not mapped yet.");
        require(decel.contains("STATUS — PLANNED GUIDED SCAFFOLD")
                        && decel.contains("CURRENT EPICEFI CONTROLS")
                        && decel.contains("TPS Decel fuel multiplier")
                        && decel.contains("CLT authority")
                        && decel.contains("WHAT TO DO")
                        && decel.contains("WATCH / MEASURE")
                        && decel.contains("WHAT GOOD EVIDENCE LOOKS LIKE")
                        && decel.contains("WHEN AE TUNER SHOULD WITHHOLD")
                        && decel.contains("NEXT"),
                "planned Decel task does not expose the intended complete Guided Focus coach");
        String instant = GuidedTaskFocusCatalog.focusText(
                GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH, "test");
        require(instant.contains("tpsAeInstantDeltaTpsBins")
                        && instant.contains("latched") && instant.contains("small corrections"),
                "Instant Fuel Delta TPS task lost the current firmware semantics");
        String wall = GuidedTaskFocusCatalog.focusText(
                GuidedTuningRecipe.WALL_WETTING_ADVANCED, "test");
        require(wall.contains("wwTauMapTable") && wall.contains("wwBetaMapTable") && wall.contains("coolant"),
                "advanced Wall Wetting task omitted the current condition-dependent tau/beta surfaces");
    }

    private static void engagementFocusIsPassiveCaptureFirst() {
        EngagementPassiveCapture.reset();
        EngagementDetectionWriteSelection.resetForTest();
        EngagementDetectionWriteSelection.observeWorkingTune(detectorSnapshot());
        EngagementDetectionGuidedFocusPanel panel = new EngagementDetectionGuidedFocusPanel();
        panel.updateModel(EngagementFocusModel.setupFromWorkingTune(GuidedCaptureState.IDLE));
        String text = panel.guidanceTextForTest();
        require(text.contains("comfortable moderate positive pedal opening")
                        && text.contains("full-height visual TPS reference marker")
                        && text.contains("AE Tuner still decides comparability")
                        && text.contains("SETTLING must complete before another event can be accepted")
                        && text.contains("one optional accepted-event cue only")
                        && text.contains("SETTLING is a physical event-separation state")
                        && text.contains("no exact-target, hold or candidate-transition cues")
                        && !text.contains("HOLD / OBSERVE")
                        && panel.currentTextForTest().contains("Sample Length 50 ms")
                        && !panel.currentTextForTest().contains("Delta Window")
                        && panel.currentTextForTest().contains("capture is read-only")
                        && !panel.hasRootScrollForTest(),
                "TPS Movement Guided Focus lost the passive capture contract");
        panel.setDriverView(true);
        require(!panel.settingsToggleVisibleForTest() && !panel.settingsPanelVisibleForTest(),
                "passive Driver view exposes retired detector setting controls");
        panel.setDriverView(false);
        require(!panel.settingsToggleVisibleForTest() && !panel.settingsPanelVisibleForTest()
                        && !panel.requestedDeltaWindowEnabledForTest(),
                "passive Details view revived retired Delta Window experiment controls");
    }

    private static void foundationThresholdGetsDedicatedEvidenceFocus() {
        FoundationThresholdFocusBridge.reset();
        AeProjectSnapshot snapshot = thresholdSnapshot(false, false);
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(
                GuidedTuningRecipe.FOUNDATION_THRESHOLD);
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(module, snapshot, 5, 20, 115.0);
        for (LiveSample sample : thresholdEvidence()) session.accept(sample);
        session.finish();

        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        require(state.recipe == GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                "Foundation 2 did not own the Guided Focus hub after capture");
        require(state.foundationThreshold != null,
                "Foundation 2 still published only the generic coach payload");
        require(state.foundationThreshold.eligibleBins == 2
                        && state.foundationThreshold.recommendationPlanAvailable,
                "dedicated Foundation 2 Focus did not reuse the event-backed recommendation result");

        FoundationThresholdGuidedFocusPanel panel = new FoundationThresholdGuidedFocusPanel();
        panel.updateModel(state.foundationThreshold, "");
        require(panel.tableForTest().getRowCount() == 8,
                "Foundation 2 details did not expose every threshold RPM bin");
        require(panel.recommendationTextForTest().contains("READY FOR REVIEW")
                        && panel.reviewTextForTest().contains("RPM-BIN EVENT DECISIONS")
                        && panel.reviewTextForTest().contains("quiet p99 is diagnostic only")
                        && !panel.hasRootScrollForTest(),
                "Foundation 2 dedicated Focus lost event-level recommendation/readout/driver-view contract");

        List<LiveSample> evidence = thresholdEvidence();
        FoundationThresholdFocusModel averaged = FoundationThresholdFocusModel.build(
                thresholdSnapshot(true, true), evidence, evidence.get(evidence.size() - 1),
                GuidedCaptureState.COMPLETE);
        require(averaged.eligibleBins == 2 && averaged.recommendationPlanAvailable
                        && averaged.recommendationStatus().contains("firmware averaged-threshold inversion")
                        && averaged.reviewText.contains("Effective=(Static+Dynamic)/2")
                        && averaged.reviewText.contains("inverts the peak-event live AccelThreshold against the interpolated static curve"),
                "Foundation 2 Focus did not preserve averaged Dynamic/static recommendation authority");

        FoundationThresholdFocusModel dynamicOnly = FoundationThresholdFocusModel.build(
                thresholdSnapshot(true, false), evidence, evidence.get(evidence.size() - 1),
                GuidedCaptureState.COMPLETE);
        require(dynamicOnly.effectiveValidatedBins == 2 && dynamicOnly.eligibleBins == 0
                        && !dynamicOnly.recommendationPlanAvailable
                        && dynamicOnly.recommendationStatus().contains("zero runtime authority"),
                "Foundation 2 Focus failed to preserve the dynamic-only static-curve blocker");
    }

    private static AeProjectSnapshot detectorSnapshot() {
        return new AeProjectSnapshot(
                "focus-detector-test",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5}, 1.0, 0.0,
                new double[0], new double[0], false, false, "none",
                false, false, false, false, new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0], new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static AeProjectSnapshot mapSnapshot() {
        return new AeProjectSnapshot(
                "focus-test", new double[]{1.0}, new double[]{20.0}, new double[][]{{0.0}},
                new double[]{1000.0}, new double[]{1.0}, 0.0, 0.0, new double[0], new double[0],
                false, false, "none", false, true, false, false,
                new double[0][0], new double[0][0], new double[]{1500.0, 3000.0},
                new double[]{10.0, 20.0}, new double[][]{{45.0, 50.0}, {55.0, 58.0}},
                new double[]{1500.0, 3000.0}, new double[]{0.10, 0.20});
    }

    private static AeProjectSnapshot thresholdSnapshot(boolean dynamic, boolean averageStatic) {
        return new AeProjectSnapshot(
                "threshold-focus-test",
                new double[0], new double[0], new double[0][0],
                new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500},
                new double[]{0.30, 1.00, 0.30, 0.30, 1.00, 0.30, 0.30, 0.30},
                0.10, 0.0, new double[0], new double[0], true, false, "off", false, false,
                dynamic, averageStatic, new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0], new double[0], new double[0]);
    }

    private static List<LiveSample> thresholdEvidence() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = 0.0;
        for (int i = 0; i < 90; i++) {
            samples.add(thresholdSample(seconds, 1500.0,
                    0.020 + (i % 3) * 0.002, 1.0, 0.20));
            seconds += 0.020;
        }
        seconds += 0.25;
        for (int event = 0; event < 4; event++) {
            samples.add(thresholdSample(seconds, 1500.0,
                    0.100 + event * 0.004, 1.0, 3.0));
            seconds += 0.25;
            samples.add(thresholdSample(seconds, 3000.0,
                    0.110 + event * 0.004, 1.0, 3.2));
            seconds += 0.25;
        }
        for (int event = 0; event < 4; event++) {
            samples.add(thresholdSample(seconds, 1500.0,
                    0.42 + event * 0.01, 1.0, 18.0));
            samples.add(thresholdSample(seconds + 0.05, 1500.0,
                    0.58 + event * 0.01, 1.0, 4.0));
            samples.add(thresholdSample(seconds + 0.10, 1500.0,
                    0.54 + event * 0.01, 1.0, 1.0));
            seconds += 0.40;

            samples.add(thresholdSample(seconds, 3000.0,
                    0.45 + event * 0.01, 1.0, 20.0));
            samples.add(thresholdSample(seconds + 0.05, 3000.0,
                    0.62 + event * 0.01, 1.0, 4.5));
            samples.add(thresholdSample(seconds + 0.10, 3000.0,
                    0.57 + event * 0.01, 1.0, 1.0));
            seconds += 0.40;
        }
        return samples;
    }

    private static LiveSample thresholdSample(double seconds, double rpm,
                                              double delta, double threshold,
                                              double tpsRate) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, 12.0);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, tpsRate, 0.0);
    }

    private static LiveSample mapSample(double seconds, boolean mapPredictActive, boolean omitFuelCut) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 1500.0);
        values.put(ChannelRole.TPS, 20.0);
        values.put(ChannelRole.MAP, 60.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, mapPredictActive ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, 0.0);
        values.put(ChannelRole.AE_EXTRA_SHOT, 0.0);
        values.put(ChannelRole.INSTANT_PULSE_PW, 0.0);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        if (omitFuelCut) values.remove(ChannelRole.FUEL_CUT);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
