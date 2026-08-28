package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

public final class GuidedMethodProbeSessionRegressionTest {
    private GuidedMethodProbeSessionRegressionTest() { }

    public static void main(String[] args) {
        mapPredictProbeCapturesRawCoherentEvidence();
        distinctActivityEventsAreAccumulated();
        inactiveFallbackGapDoesNotCountAsPredictionActivity();
        retentionCapAccountingUsesObservedDenominator();
        retainedWindowMetricsAreExplicitlyScoped();
        mapEstimateOmitsTransientActivityAccounting();
        finishUsesCommonApplyContractWithoutInventingAChange();
        genericProbeModuleCanExposeReviewedWritePlan();
        sameMethodContinuationPreservesEvidence();
        methodSwitchRequiresFreshProbeSession();
        mapEstimateReusesStableTableGenerator();
        tpsAeReusesConservativeTableGenerator();
        System.out.println("GuidedMethodProbeSessionRegressionTest passed");
    }

    private static void mapPredictProbeCapturesRawCoherentEvidence() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT);
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(module);
        session.accept(sample(1.00, 2000.0, 8.0, 50.0, 50.0, 50.0, false));
        session.accept(sample(1.02, 2020.0, 15.0, 55.0, 78.0, 76.0, true));
        session.accept(sample(1.04, 2050.0, 20.0, 62.0, 80.0, 74.0, true));
        require(session.sampleCount() == 3,
                "MAP Predict base did not retain coherent samples");
        require(session.activitySampleCount() == 2,
                "MAP Predict activity predicate did not identify active prediction evidence");
        String coverage = session.coverageText();
        require(coverage.contains("fallbackMap: 3/3 — ready")
                        && coverage.contains("effectiveMap: 3/3 — ready")
                        && coverage.contains("REQUIRED CHANNELS")
                        && coverage.contains("CONTEXT / ATTRIBUTION CHANNELS"),
                "MAP Predict coverage did not expose the required/context channel contract");
        String csv = session.csvText();
        require(csv.contains("\"fallbackMap\"")
                        && csv.contains("\"effectiveMap\"")
                        && csv.contains("\"isMapPredictionActive\""),
                "MAP Predict raw export omitted the channels needed for model/coherence comparison");
    }

    private static void distinctActivityEventsAreAccumulated() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT));
        session.accept(sample(1.00, 2000.0, 8.0, 50.0, 50.0, 50.0, false));
        session.accept(sample(1.05, 2050.0, 20.0, 60.0, 80.0, 75.0, true));
        session.accept(sample(1.10, 2070.0, 21.0, 63.0, 82.0, 76.0, true));
        session.accept(sample(1.35, 2100.0, 8.0, 50.0, 50.0, 50.0, false));
        session.accept(sample(1.45, 2150.0, 22.0, 62.0, 84.0, 78.0, true));
        require(session.activityEventCount() == 2,
                "separate prediction bursts were not counted as distinct accumulation events");
    }

    private static void inactiveFallbackGapDoesNotCountAsPredictionActivity() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT));
        session.accept(sample(1.00, 2000.0, 8.0, 50.0, 90.0, 50.0, false));
        require(session.activitySampleCount() == 0 && session.activityEventCount() == 0,
                "inactive stale fallbackMap gap was incorrectly counted as MAP Predict activity");
        session.accept(sample(1.25, 2100.0, 20.0, 62.0, 84.0, 78.0, true));
        require(session.activitySampleCount() == 1 && session.activityEventCount() == 1,
                "real predictor-active sample was not counted after stale-gap rejection");
    }

    private static void retentionCapAccountingUsesObservedDenominator() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT));
        for (int i = 0; i < 6105; i++) {
            double seconds = 10.0 + i * 0.01;
            session.accept(sample(seconds, 2000.0, 8.0, 50.0, 50.0, 50.0, false));
        }
        require(session.sampleCount() == 6000 && session.observedSampleCount() == 6105,
                "probe retention cap did not preserve observed-versus-retained accounting");
        String coverage = session.coverageText();
        require(coverage.contains("Samples observed: 6105")
                        && coverage.contains("Samples retained in export window: 6000")
                        && coverage.contains("Samples with every REQUIRED channel present (observed): 6105/6105")
                        && coverage.contains("fallbackMap: 6105/6105 — ready")
                        && coverage.contains("Dropped by probe retention cap: 105"),
                "coverage mixed lifetime counters with the retained 6000-row denominator");
    }

    private static void retainedWindowMetricsAreExplicitlyScoped() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT));
        session.accept(sample(3.00, 2000.0, 20.0, 60.0, 80.0, 75.0, true));
        String metrics = session.resultText();
        require(metrics.contains("Retained-window metrics (up to 6000 coherent samples):")
                        && metrics.contains("Prediction-active samples retained:")
                        && metrics.contains("predTimerResetCnt retained-window span:")
                        && metrics.contains("mapPredEventOver retained-window span:"),
                "method diagnostics did not make their retained-window scope explicit");
    }

    private static void mapEstimateOmitsTransientActivityAccounting() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE),
                mapSnapshot(), 5, 3, 115.0);
        session.accept(mapEstimateSample(4.00, 1500.0, 20.0, 60.0));
        String coverage = session.coverageText();
        require(coverage.contains("Stable-cell accumulation does not use transient activity-event counting.")
                        && !coverage.contains("Distinct method-activity events:"),
                "MAP Estimate still presented transient activity accounting instead of stable-cell progress");
    }

    private static void finishUsesCommonApplyContractWithoutInventingAChange() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.WALL_WETTING));
        session.accept(wallSample(2.0, 0.35));
        session.finish();
        GuidedSessionSnapshot snapshot = session.snapshot();
        require(snapshot.state == GuidedCaptureState.COMPLETE,
                "method-base finish did not enter review/complete state");
        String report = session.reportText("0.4.2-dev.test");
        require(report.contains("any explicit ProposalWritePlan may use the common guarded Apply/readback/Restore gateway")
                        && report.contains("No automatic Apply and no burn"),
                "method report did not expose the common guarded Apply contract");
        require(session.reviewedWritePlan() == null,
                "Wall Wetting invented a numerical change before its tuning rule produced one");
        require(session.reviewText().contains("No supported setting/value change is currently proposed"),
                "review did not distinguish no-current-change from a read-only product state");
    }

    private static void genericProbeModuleCanExposeReviewedWritePlan() {
        GuidedAeMethodModule module = new GuidedAeMethodModule() {
            @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.WALL_WETTING; }
            @Override public CaptureMode captureMode() { return CaptureMode.READ_ONLY_PROBE; }
            @Override public String setupTitle() { return "test"; }
            @Override public String setupGuidance() { return "test"; }
            @Override public String captureGoal() { return "test"; }
            @Override public ChannelRole[] requiredRoles() { return new ChannelRole[0]; }
            @Override public ChannelRole[] contextRoles() { return new ChannelRole[0]; }
            @Override public String operatorInputs(AeProjectSnapshot snapshot) { return "test"; }
            @Override public String accumulationPlan() { return "test"; }
            @Override public String reviewOutputs() { return "test"; }
            @Override public String currentTuneContext(AeProjectSnapshot snapshot) { return "test"; }
            @Override public boolean activityObserved(LiveSample sample) { return true; }
            @Override public ProposalWritePlan reviewedWritePlan(AeProjectSnapshot snapshot,
                                                                 List<LiveSample> evidence) {
                return new ProposalWritePlan(
                        "wall-tau-test", "Wall tau test", "cfg", "generic probe route",
                        Arrays.asList(ProposalWritePlan.Change.scalar(
                                "wallTau", 1.0, 1.1, "Wall tau", "")));
            }
        };
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(module);
        session.accept(wallSample(2.0, 0.35));
        require(session.reviewedWritePlan() == null,
                "probe exposed a write plan before Finish/Review");
        session.finish();
        ProposalWritePlan plan = session.reviewedWritePlan();
        require(plan != null && plan.changeCount() == 1
                        && "wallTau".equals(plan.getChanges().get(0).parameterName),
                "generic non-MAP probe write plan did not route through the shared session");
        require(session.reviewText().contains("Guarded working-tune Apply/readback/Restore is available"),
                "generic probe review did not expose the shared Apply path");
    }

    private static void sameMethodContinuationPreservesEvidence() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        GuidedAeMethodModule map = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT);
        session.start(map);
        session.accept(sample(2.0, 2000.0, 10.0, 50.0, 70.0, 68.0, true));
        session.finish();
        session.start(map);
        require(session.sampleCount() == 1,
                "continuing the same method discarded previously reviewed evidence");
        session.accept(sample(2.4, 2200.0, 20.0, 60.0, 80.0, 76.0, true));
        require(session.sampleCount() == 2,
                "same-method continuation did not append evidence");
    }

    private static void methodSwitchRequiresFreshProbeSession() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        GuidedAeMethodModule map = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT);
        GuidedAeMethodModule tps = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.TPS_AE);
        session.start(map);
        session.accept(sample(3.0, 2000.0, 10.0, 50.0, 70.0, 68.0, true));
        session.finish();
        session.start(tps);
        require(session.sampleCount() == 0 && session.activitySampleCount() == 0,
                "starting another isolated AE method retained evidence from the previous method");
        require(session.module() == tps,
                "new method base did not own its fresh probe session");
    }

    private static void mapEstimateReusesStableTableGenerator() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE);
        session.start(module, mapSnapshot(), 5, 3, 115.0);
        session.accept(mapEstimateSample(4.00, 1500.0, 20.0, 60.0));
        session.accept(mapEstimateSample(4.05, 1500.0, 20.0, 60.0));
        session.accept(mapEstimateSample(4.10, 1500.0, 20.0, 60.0));
        session.finish();
        require(session.resultText().contains("3 stable sample(s)")
                        && session.resultText().contains("1/4 cell(s)"),
                "Guided MAP Estimate did not expose stable table-cell accumulation progress");
        require(session.copyPasteBlock().length() > 0,
                "evidence-backed MAP Estimate cells did not produce the existing paste-ready draft");
        String mapReview = session.reviewText();
        require(mapReview.contains("MAP ESTIMATE TABLE REVIEW")
                        && mapReview.contains("CURRENT PROPOSAL")
                        && mapReview.contains("60"),
                "Guided MAP Estimate did not expose the reviewed table proposal/diff");
    }

    private static void tpsAeReusesConservativeTableGenerator() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.TPS_AE);
        session.start(module, tpsSnapshot(), 3, 20, 115.0);

        addTpsAeEvent(session, 10.00, 20.0);
        addTpsAeEvent(session, 11.40, 20.0);
        addTpsAeEvent(session, 12.80, 20.0);
        session.finish();

        require(session.tpsAeTableEventCount() == 3
                        && session.tpsAeFuelProvedEventCount() == 3,
                "Guided TPS AE did not retain three completed fuel-proved table-analysis windows");
        require(session.activityEventCount() == 3,
                "Guided TPS AE activity progress did not match the three separated test events");
        require(session.copyPasteBlock().length() > 0,
                "three repeated TPS AE fuel-proved events did not produce the existing bounded table draft");
        String review = session.reviewText();
        require(review.contains("TPS AE TABLE REVIEW")
                        && review.contains("Basis: 3 usable TPS AE fuel-proved event(s)")
                        && review.contains("TPS-to 20.00")
                        && review.contains("No supported setting/value change is currently proposed"),
                "Guided TPS AE review did not expose its table draft while preserving the common no-invented-change rule");
        require(session.reportText("0.4.2-dev.test").contains("PASTE-READY DRAFT"),
                "Guided TPS AE report/export path omitted its paste-ready draft");
    }

    private static void addTpsAeEvent(GuidedMethodProbeSession session,
                                      double baseSeconds, double tpsTo) {
        session.accept(tpsAeSample(baseSeconds, 8.0, tpsTo, 0.0, 1.00, false));
        session.accept(tpsAeSample(baseSeconds + 0.05, 14.0, tpsTo, 2.0, 1.18, true));
        session.accept(tpsAeSample(baseSeconds + 0.10, 18.0, tpsTo, 4.0, 1.16, true));
        session.accept(tpsAeSample(baseSeconds + 0.20, 20.0, tpsTo, 6.0, 1.02, true));
        session.accept(tpsAeSample(baseSeconds + 0.30, 20.0, tpsTo, 10.0, 1.02, true));
        session.accept(tpsAeSample(baseSeconds + 0.45, 20.0, tpsTo, 12.0, 1.01, false));
        session.accept(tpsAeSample(baseSeconds + 0.70, 20.0, tpsTo, 12.0, 1.01, false));
        session.accept(tpsAeSample(baseSeconds + 0.90, 20.0, tpsTo, 12.0, 1.01, false));
    }

    private static AeProjectSnapshot mapSnapshot() {
        return new AeProjectSnapshot(
                "test",
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

    private static AeProjectSnapshot tpsSnapshot() {
        return new AeProjectSnapshot(
                "tps-test",
                new double[]{2.0, 4.0, 6.0, 10.0, 12.0},
                new double[]{10.0, 20.0, 30.0},
                new double[][]{
                        {1.00, 1.00, 0.90, 0.70, 0.50},
                        {1.00, 1.00, 0.90, 0.70, 0.50},
                        {1.00, 1.00, 0.90, 0.70, 0.50}
                },
                new double[]{1000.0, 4000.0}, new double[]{1.0, 1.5},
                0.0, 0.0, new double[0], new double[0],
                true, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static LiveSample sample(double seconds, double rpm, double tps,
                                     double map, double fallback, double effective,
                                     boolean active) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.EFFECTIVE_MAP, effective);
        values.put(ChannelRole.MAP_PRED_ACTIVE, active ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, active ? 2.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        values.put(ChannelRole.MAP_PRED_RESET_CNT, 1.0);
        values.put(ChannelRole.MAP_PRED_EVENT_OVER, 0.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds, values, 0.0, 0.0);
    }

    private static LiveSample wallSample(double seconds, double correction) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2500.0);
        values.put(ChannelRole.TPS, 20.0);
        values.put(ChannelRole.MAP, 75.0);
        values.put(ChannelRole.LAMBDA, 0.92);
        values.put(ChannelRole.TARGET_LAMBDA, 0.95);
        values.put(ChannelRole.PW, 4.0);
        values.put(ChannelRole.WALL_CORRECTION, correction);
        values.put(ChannelRole.WALL_WETTING_PW, correction);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, 2.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds, values, 0.0, 0.0);
    }

    private static LiveSample mapEstimateSample(double seconds, double rpm,
                                                double tps, double map) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, 0.0);
        values.put(ChannelRole.AE_EXTRA_SHOT, 0.0);
        values.put(ChannelRole.INSTANT_PULSE_PW, 0.0);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, 0.0, 0.0);
    }

    private static LiveSample tpsAeSample(double seconds, double tps,
                                          double tpsTo, double cycle,
                                          double lambda, boolean active) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2500.0);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, active ? 75.0 : 50.0);
        values.put(ChannelRole.LAMBDA, lambda);
        values.put(ChannelRole.TARGET_LAMBDA, 1.0);
        values.put(ChannelRole.PW, active ? 4.5 : 3.0);
        values.put(ChannelRole.TPS_FROM, 8.0);
        values.put(ChannelRole.TPS_TO, tpsTo);
        values.put(ChannelRole.DELTA_TPS, active ? tpsTo - 8.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, active ? 2.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, active ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ADD_MS, active ? 0.80 : 0.0);
        values.put(ChannelRole.EXTRA_FUEL, 0.0);
        values.put(ChannelRole.TPS_AE_CYCLE_MULT, active ? 1.0 : 0.0);
        values.put(ChannelRole.TPS_AE_CYCLE_CNT, cycle);
        values.put(ChannelRole.WALL_WETTING_PW, 0.0);
        values.put(ChannelRole.INSTANT_PULSE_PW, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.FALLBACK_MAP, active ? 75.0 : 50.0);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.COOLANT, 80.0);
        values.put(ChannelRole.IAT, 25.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, active ? 80.0 : 0.0, active ? 100.0 : 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
