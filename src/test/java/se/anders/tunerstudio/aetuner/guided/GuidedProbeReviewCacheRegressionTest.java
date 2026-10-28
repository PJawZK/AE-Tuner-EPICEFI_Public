package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.EnumMap;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateEvidenceBasis;
import java.util.concurrent.*;

/** Behavioral gates for shared, revision-scoped Review/Proposal/export results. */
public final class GuidedProbeReviewCacheRegressionTest {
    public static void main(String[] args) throws Exception {
        completedReviewsBelongToTheirSession();
        sharedResultsAvoidRepeatedWork(false);
        sharedResultsAvoidRepeatedWork(true);
        lifecycleAndBaselineInvalidateResults();
        mapSettingsInvalidateReview();
        workingTuneReadWithholdsOldPlan();
        historicalChangesInvalidateCompletedPresentation();
        oldCsvCannotReplaceNewRevision();
        csvFormattingMatchesOriginalFormatter();
        System.out.println("GuidedProbeReviewCacheRegressionTest passed");
    }

    private static void completedReviewsBelongToTheirSession() {
        CountingModule module = new CountingModule(false);
        GuidedMethodProbeSession first = ready(module), second = ready(module);
        first.reviewedWritePlan();
        second.reviewedWritePlan();
        require(first.reviewText().contains("evaluation 1")
                        && second.reviewText().contains("evaluation 2"),
                "a shared method module replaced another session's completed review");
    }

    private static void sharedResultsAvoidRepeatedWork(boolean nullPlan) throws Exception {
        CountingModule module = new CountingModule(nullPlan);
        GuidedMethodProbeSession session = ready(module);
        String result = session.resultText(), review = session.reviewText();
        String report = session.reportText("v1"), csv = session.csvText();
        ProposalWritePlan plan = session.reviewedWritePlan();
        require((plan == null) == nullPlan, "fixture lost expected plan availability");
        final int[] reads = {0};
        Field field = field("samples");
        @SuppressWarnings("unchecked") final List<LiveSample> retained = (List<LiveSample>) field.get(session);
        field.set(session, new AbstractList<LiveSample>() {
            @Override public int size() { return retained.size(); }
            @Override public LiveSample get(int i) { reads[0]++; return retained.get(i); }
        });
        try {
            for (int i = 0; i < 20; i++) {
                require(session.snapshot().result.equals(result), "snapshot result changed during repeated refresh");
                require(session.resultText() == result && session.reviewText() == review,
                        "completed presentation was rebuilt");
                require(session.reportText("v1") == report && session.csvText() == csv,
                        "unchanged export/recovery rebuilt its payload");
                require(session.reviewedWritePlan() == plan, "review and Apply received different plans");
                require(session.copyPasteBlock().isEmpty(), "generic fixture invented a clipboard draft");
            }
            require(reads[0] == 0, "warm Review/Proposal/export rescanned retained rows: " + reads[0]);
            require(module.evaluations == 1, "unchanged evidence repeatedly evaluated a recommendation");
            String updatedVersion = session.reportText("v2");
            require(updatedVersion.contains("Plugin version: v2") && !updatedVersion.equals(report),
                    "report version was hidden by caching");
            require(module.evaluations == 1, "version-only export change repeated tuning evaluation");
        } finally { field.set(session, retained); }
    }

    private static void lifecycleAndBaselineInvalidateResults() {
        CountingModule module = new CountingModule(false);
        GuidedMethodProbeSession session = ready(module);
        String oldCsv = session.csvText();
        session.reviewText();
        long revision = session.evidenceRevisionForTest();
        AeProjectSnapshot baseline = null;
        session.start(module, baseline, 1000, 3, 115.0);
        require(session.evidenceRevisionForTest() == revision, "continuation unexpectedly discarded evidence");
        require(session.reviewedWritePlan() == null, "resuming exposed a stale completed plan");
        session.finish();
        require(session.reviewedWritePlan() == null && session.reviewText().contains("EVIDENCE INCOMPLETE"),
                "new evidence target reused old review authority");
        session.start(module, baseline, 1, 3, 115.0);
        session.finish();
        session.reviewText();
        require(module.evaluations == 2 && module.lastBaseline == baseline,
                "same evidence with a new target reused an earlier recommendation");
        session.start(module, baseline, 1, 3, 115.0);
        session.accept(GuidedReviewPerformanceCharacterization.sample(100.0, false));
        session.togglePause();
        require(session.reviewedWritePlan() == null, "paused capture reused a completed plan");
        session.togglePause();
        session.closeForLifecycle();
        require(!session.csvText().equals(oldCsv), "new evidence reused old CSV");
        session.reviewText();
        require(module.evaluations == 3, "lifecycle finish did not refresh recommendation");
        session.reset();
        require(session.reviewedWritePlan() == null && !session.hasEvidence(), "reset retained review authority");
        require(session.csvText().equals("sample_index,dt_s\n"), "reset retained old exported rows");
        CountingModule other = new CountingModule(true);
        session.start(other, null, 1, 3, 115.0);
        session.accept(GuidedReviewPerformanceCharacterization.sample(200.0, true));
        session.finish();
        session.reviewText();
        require(other.evaluations == 1 && session.reviewedWritePlan() == null,
                "new method inherited the earlier proposal");
    }

    private static void workingTuneReadWithholdsOldPlan() {
        CountingModule module = new CountingModule(false);
        GuidedMethodProbeSession session = ready(module);
        require(session.reviewedWritePlan() != null, "fixture has no initial plan");
        String csv = session.csvText();
        AeProjectSnapshot reread = mapSnapshot();
        session.noteWorkingTuneRead(reread, 3, 115.0);
        require(session.reviewedWritePlan() == null && !session.reviewReady(),
                "Working Tune reread reused authority from the captured baseline");
        require(session.reviewText().contains("WORKING TUNE RE-READ"), "reread gate not explained");
        require(session.csvText() == csv && session.hasEvidence(), "reread discarded retained evidence");
        session.start(module, reread, 1, 3, 115.0);
        require(!session.hasEvidence(), "new baseline appended old calibration evidence");
        session.accept(GuidedReviewPerformanceCharacterization.sample(200.0, true));
        session.finish();
        require(session.reviewedWritePlan() != null && module.lastBaseline == reread,
                "fresh capture did not use the reread baseline");
    }

    private static void historicalChangesInvalidateCompletedPresentation() throws Exception {
        se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext.clear();
        GuidedMethodProbeSession session = ready(new CountingModule(true));
        field("module").set(session, GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION));
        java.lang.reflect.Method stage = GuidedHistoricalRecoveryLifecycleRegressionTest.class
                .getDeclaredMethod("stageHistorical");
        stage.setAccessible(true);
        try {
            String before = session.reviewText();
            String beforeReport = session.reportText("test");
            AeProjectSnapshot tune = (AeProjectSnapshot) stage.invoke(null);
            field("projectSnapshot").set(session, tune);
            String staged = session.reviewText();
            require(!staged.equals(before) && staged.contains("CLASSIFICATION-BOUNDARY"),
                    "staged historical review stayed hidden by completed cache");
            require(!session.reportText("test").equals(beforeReport), "report retained pre-stage text");
            se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext.clear();
            String cleared = session.reviewText();
            require(!cleared.equals(staged) && !cleared.contains("CLASSIFICATION-BOUNDARY TARGETS READY"),
                    "cleared historical staging survived in completed review");
        } finally {
            se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext.clear();
            se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultContext.clear();
            GuidedV019HistoricalReplayContext.clear();
            ((se.anders.tunerstudio.aetuner.guided.method.DecelDetectionMethodModule)
                    GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION)).clearHistoricalReview();
        }
    }

    private static void mapSettingsInvalidateReview() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        AeProjectSnapshot baseline = mapSnapshot();
        session.configureMapEstimateFocus(baseline, 3, 115.0);
        GuidedFocusHub.mapEstimateConfigurationListener().onEvidenceBasisRequested(
                MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY);
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE),
                baseline, 1, 3, 115.0);
        for (int i = 0; i < 20; i++) {
            double seconds = 1.0 + i * .05;
            EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
            for (ChannelRole role : ChannelRole.values()) values.put(role, 0.0);
            values.put(ChannelRole.TPS, 20.0);
            values.put(ChannelRole.RPM, 1500.0);
            values.put(ChannelRole.MAP, 60.0);
            session.accept(new LiveSample(Math.round(seconds * 1e9), seconds, values, 0.0, 0.0));
        }
        session.finish();
        session.reviewText();
        String oldDraft = session.copyPasteBlock();
        String csv = session.csvText();
        long revision = session.evidenceRevisionForTest();
        require(session.reviewReady() && session.reviewedWritePlan() != null && !oldDraft.isEmpty(),
                "MAP fixture did not produce the initial completed proposal");
        session.updateMapEstimateReviewSettings(100, 130.0);
        require(!session.reviewReady() && session.reviewText().contains("EVIDENCE INCOMPLETE"),
                "raised MAP threshold left completed review ready");
        require(session.resultText().contains("Minimum samples/direct anchor: 100")
                        && session.resultText().contains("130.0 kPa")
                        && session.reportText("test").contains("Minimum samples/direct anchor: 100"),
                "MAP result/export retained earlier review settings");
        require(session.reviewedWritePlan() == null && session.copyPasteBlock().isEmpty(),
                "raised evidence threshold retained the earlier plan/draft");
        session.configureMapEstimateFocus(baseline, 3, 125.0);
        require(session.reviewReady() && session.reviewedWritePlan() != null
                        && !session.copyPasteBlock().isEmpty()
                        && session.reportText("test").contains("125.0 kPa"),
                "same-baseline settings did not restore consistent review authority");
        session.noteWorkingTuneRead(baseline, 1, 200.0);
        require(session.resultText().contains("Minimum samples/direct anchor: 3")
                        && session.reportText("test").contains("180.0 kPa"),
                "working-tune read did not share normalized controller settings");
        session.updateMapEstimateReviewSettings(1, 50.0);
        require(session.reviewReady() && session.reportText("test").contains("90.0 kPa"),
                "lower cap bound was not reflected in the report");
        require(session.evidenceRevisionForTest() == revision && session.csvText() == csv,
                "review settings changed retained evidence");
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE),
                baseline, 1, 3, 115.0);
        session.updateMapEstimateReviewSettings(100, 130.0);
        session.configureMapEstimateFocus(baseline, 100, 130.0);
        session.finish();
        require(session.resultText().contains("Minimum samples/direct anchor: 3")
                        && session.resultText().contains("115.0 kPa"),
                "active capture accepted an editable review-settings update");
    }

    private static void oldCsvCannotReplaceNewRevision() throws Exception {
        GuidedMethodProbeSession session = GuidedReviewPerformanceCharacterization.fixture(3);
        final List<LiveSample> oldEvidence = session.evidenceSnapshot();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        field("cachedEvidenceSnapshot").set(session, new AbstractList<LiveSample>() {
            @Override public int size() { return oldEvidence.size(); }
            @Override public LiveSample get(int i) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("CSV blocker timed out"); }
                catch (InterruptedException ex) { throw new AssertionError(ex); }
                return oldEvidence.get(i);
            }
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> old = workers.submit(session::csvText);
            require(entered.await(5, TimeUnit.SECONDS), "CSV did not start serializing outside the capture lock");
            workers.submit(() -> session.accept(GuidedReviewPerformanceCharacterization.sample(50.0, false)))
                    .get(5, TimeUnit.SECONDS);
            String current = session.csvText();
            release.countDown();
            String previous = old.get(5, TimeUnit.SECONDS);
            require(!previous.equals(current), "fixture did not change exported evidence");
            require(session.csvText() == current, "old CSV serialization replaced the current revision cache");
            require(previous.split("\n").length == 4 && current.split("\n").length == 5,
                    "concurrent export mixed old and new sample rows");
        } finally { release.countDown(); workers.shutdownNow(); }
    }

    private static void csvFormattingMatchesOriginalFormatter() {
        java.util.Locale previous = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.GERMANY);
        try {
            GuidedMethodProbeSession session = new GuidedMethodProbeSession();
            session.start(new CountingModule(true));
            double[] numbers = {0.0, -0.0, 1.2345675, -1.2345675,
                    Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY};
            for (int i = 0; i < numbers.length; i++) {
                EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
                values.put(ChannelRole.RPM, numbers[i]);
                values.put(ChannelRole.TPS, 0.0);
                session.accept(new LiveSample(i * 10000000L, i * .01, values, 0.0, 0.0));
            }
            String[] lines = session.csvText().split("\n");
            for (int i = 0; i < numbers.length; i++) {
                String expected = i + "," + String.format(java.util.Locale.US, "%.6f", i * .01)
                        + "," + (Double.isFinite(numbers[i])
                            ? String.format(java.util.Locale.US, "%.6f", numbers[i]) : "") + ",0.000000";
                require(lines[i + 1].equals(expected), "CSV formatting changed at edge case " + i);
            }
        } finally { java.util.Locale.setDefault(previous); }
    }

    private static GuidedMethodProbeSession ready(CountingModule module) {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(module, null, 1, 3, 115.0);
        for (int i = 0; i < 100; i++) session.accept(GuidedReviewPerformanceCharacterization.sample(1.0 + i * .01, i < 5));
        session.finish();
        require(session.reviewReady(), "fixture is not review ready");
        return session;
    }

    private static final class CountingModule implements GuidedAeMethodModule {
        final boolean nullPlan;
        int evaluations;
        AeProjectSnapshot lastBaseline;
        CountingModule(boolean nullPlan) { this.nullPlan = nullPlan; }
        @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.MAP_PREDICT; }
        @Override public CaptureMode captureMode() { return CaptureMode.READ_ONLY_PROBE; }
        @Override public String setupTitle() { return "test"; }
        @Override public String setupGuidance() { return "test"; }
        @Override public String captureGoal() { return "test"; }
        @Override public ChannelRole[] requiredRoles() { return new ChannelRole[]{ChannelRole.RPM}; }
        @Override public ChannelRole[] contextRoles() { return new ChannelRole[]{ChannelRole.TPS}; }
        @Override public String operatorInputs(AeProjectSnapshot snapshot) { return "test"; }
        @Override public String accumulationPlan() { return "test"; }
        @Override public String reviewOutputs() { return "evaluation " + evaluations; }
        @Override public String currentTuneContext(AeProjectSnapshot snapshot) { return "test"; }
        @Override public boolean activityObserved(LiveSample sample) { return sample.bool(ChannelRole.MAP_PRED_ACTIVE); }
        @Override public ProposalWritePlan reviewedWritePlan(AeProjectSnapshot snapshot, List<LiveSample> evidence) {
            evaluations++;
            lastBaseline = snapshot;
            return nullPlan ? null : new ProposalWritePlan("test", "test", "cfg", "test",
                    Arrays.asList(ProposalWritePlan.Change.scalar("test", 3.0, 6.0, "test", "")));
        }
    }

    private static AeProjectSnapshot mapSnapshot() {
        return new AeProjectSnapshot("cache-test",
                new double[]{1.0}, new double[]{20.0}, new double[][]{{0.0}},
                new double[]{1000.0}, new double[]{1.0},
                0.0, 0.0, new double[0], new double[0],
                false, false, "none", false, true, false, false,
                new double[0][0], new double[0][0],
                new double[]{1500.0, 3000.0}, new double[]{10.0, 20.0},
                new double[][]{{45.0, 50.0}, {55.0, 58.0}},
                new double[]{1500.0, 3000.0}, new double[]{0.10, 0.20});
    }

    private static Field field(String name) throws Exception {
        Field field = GuidedMethodProbeSession.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
