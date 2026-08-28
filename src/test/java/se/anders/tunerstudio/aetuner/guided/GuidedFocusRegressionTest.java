package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.passive.MapEstimateCollector;

import java.util.EnumMap;

/** Regression checks for the presentation-only Guided Focus layer. */
public final class GuidedFocusRegressionTest {
    private GuidedFocusRegressionTest() { }

    public static void main(String[] args) {
        acceptedSecondsExcludeRejectedGap();
        incompleteRequiredDataPausesFocusProgress();
        focusHubFollowsMapEstimateSession();
        heatMapExposesLoadedAxesAndTextStates();
        plannedTaskCoachExplainsRealControlFamilyAndWorkflow();
        engagementFocusIsCoachingFirst();
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
        require(focus.countAt(1, 0) == 3,
                "rejected MAP Predict sample changed the stable-cell count");
        double seconds = focus.acceptedSecondsAt(1, 0);
        require(seconds >= 0.12 && seconds < 0.16,
                "accepted-seconds display credited rejected gap time: " + seconds);
        require(focus.isComplete(1, 0),
                "three clean stable samples did not satisfy the test minimum");
    }

    private static void incompleteRequiredDataPausesFocusProgress() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE),
                mapSnapshot(), 5, 3, 115.0);
        session.accept(mapSample(2.00, false, false));
        session.accept(mapSample(2.05, false, true));

        MapEstimateFocusSnapshot focus = session.mapEstimateFocusSnapshot(null);
        require(focus.countAt(1, 0) == 1,
                "required-incomplete sample entered MAP Estimate evidence");
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
        require(state.recipe == GuidedTuningRecipe.MAP_ESTIMATE,
                "focus hub did not identify MAP Estimate");
        require(state.captureState == GuidedCaptureState.CAPTURING,
                "focus hub did not follow active capture state");
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
        require(panel.tableForTest().getRowCount() == 2,
                "heat map did not expose both TPS rows");
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
                        && instant.contains("latched")
                        && instant.contains("small corrections"),
                "Instant Fuel Delta TPS task lost the current firmware semantics");

        String wall = GuidedTaskFocusCatalog.focusText(
                GuidedTuningRecipe.WALL_WETTING_ADVANCED, "test");
        require(wall.contains("wwTauMapTable")
                        && wall.contains("wwBetaMapTable")
                        && wall.contains("coolant"),
                "advanced Wall Wetting task omitted the current condition-dependent tau/beta surfaces");
    }

    private static void engagementFocusIsCoachingFirst() {
        EngagementDetectionWriteSelection.resetForTest();
        EngagementDetectionWriteSelection.observeWorkingTune(detectorSnapshot());
        EngagementDetectionGuidedFocusPanel panel = new EngagementDetectionGuidedFocusPanel();
        panel.updateModel(EngagementFocusModel.setupFromWorkingTune(GuidedCaptureState.IDLE));
        String text = panel.guidanceTextForTest();
        require(text.contains("START A BASELINE CAPTURE")
                        && text.contains("DELTA WINDOW A-B")
                        && text.contains("change only Delta Window")
                        && text.contains("READY")
                        && text.contains("TARGET")
                        && text.contains("recorded channels remain the evidence")
                        && !panel.hasRootScrollForTest(),
                "TPS Movement Guided Focus lost the visual/audio Delta Window coaching contract");
        panel.setDriverView(true);
        require(!panel.settingsToggleVisibleForTest()
                        && !panel.settingsPanelVisibleForTest(),
                "Driver view still makes detector setting controls primary");
        panel.setDriverView(false);
        require(panel.settingsToggleVisibleForTest()
                        && !panel.settingsPanelVisibleForTest(),
                "secondary detector setting experiment controls are not collapsed outside Driver view");
    }

    private static AeProjectSnapshot detectorSnapshot() {
        return new AeProjectSnapshot(
                "focus-detector-test",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0,
                new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static AeProjectSnapshot mapSnapshot() {
        return new AeProjectSnapshot(
                "focus-test",
                new double[]{1.0}, new double[]{20.0}, new double[][]{{0.0}},
                new double[]{1000.0}, new double[]{1.0},
                0.0, 0.0, new double[0], new double[0],
                false, false, "none", false, true, false, false,
                new double[0][0], new double[0][0],
                new double[]{1500.0, 3000.0},
                new double[]{10.0, 20.0},
                new double[][]{{45.0, 50.0}, {55.0, 58.0}},
                new double[]{1500.0, 3000.0}, new double[]{0.10, 0.20});
    }

    private static LiveSample mapSample(double seconds,
                                        boolean mapPredictActive,
                                        boolean omitFuelCut) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 1500.0);
        values.put(ChannelRole.TPS, 20.0);
        values.put(ChannelRole.MAP, 60.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, mapPredictActive ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, 0.0);
        values.put(ChannelRole.AE_EXTRA_SHOT, 0.0);
        values.put(ChannelRole.INSTANT_PULSE_PW, 0.0);
        values.put(ChannelRole.DFCO, 0.0);
        if (!omitFuelCut) values.put(ChannelRole.FUEL_CUT, 0.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
