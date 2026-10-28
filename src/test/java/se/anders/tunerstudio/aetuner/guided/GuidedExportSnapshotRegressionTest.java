package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateEvidenceBasis;
import java.util.EnumMap;
import se.anders.tunerstudio.aetuner.recovery.EvidenceRecoverySnapshot;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Package consistency while capture, settings and method identity change. */
public final class GuidedExportSnapshotRegressionTest {
    public static void main(String[] args) throws Exception {
        probeSerializationRetainsOneRevision(false);
        probeSerializationRetainsOneRevision(true);
        staleAndForeignRequestsAreRejected();
        historicalStagingInvalidatesQueuedRequests();
        mapSettingsCannotReplaceCapturedDraft();
        blendLedgerSnapshotSurvivesDiscardAndReset();
        blendRecoveryWaitsForTheWholeModelUpdate();
        System.out.println("GuidedExportSnapshotRegressionTest passed");
    }

    private static void probeSerializationRetainsOneRevision(boolean switchMethod) throws Exception {
        GuidedMethodProbeSession session = GuidedReviewPerformanceCharacterization.fixture(3);
        session.finish();
        String expectedCsv = session.csvText();
        String expectedReport = session.reportText("test"), expectedDraft = session.copyPasteBlock();
        List<LiveSample> rows = session.evidenceSnapshot();
        set(session, "cachedCsv", null);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        set(session, "cachedEvidenceSnapshot", new AbstractList<LiveSample>() {
            @Override public int size() { return rows.size(); }
            @Override public LiveSample get(int i) {
                entered.countDown();
                try { require(release.await(5, TimeUnit.SECONDS), "CSV release timed out"); }
                catch (InterruptedException ex) { throw new AssertionError(ex); }
                return rows.get(i);
            }
        });
        GuidedMethodProbeSession.ExportRequest request = session.requestExport();
        GuidedMethodProbeSession.ExportSnapshot exported = session.exportSnapshot("test", request);
        require(session.exportStillCurrent(request), "fresh export request is stale");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> csv = workers.submit(exported::csvText);
            require(entered.await(5, TimeUnit.SECONDS), "CSV did not start formatting detached rows");
            workers.submit(() -> {
                if (switchMethod) {
                    session.reset();
                    session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.INSTANT_FUEL));
                } else {
                    session.start(request.module, null, 1, 3, 115.0);
                }
                session.accept(GuidedReviewPerformanceCharacterization.sample(99.0, false));
            }).get(5, TimeUnit.SECONDS);
            String newerCsv = session.csvText();
            require(!session.exportStillCurrent(request), "old completion can mark newer evidence exported");
            release.countDown();
            require(csv.get(5, TimeUnit.SECONDS).equals(expectedCsv), "package CSV mixed capture revisions");
            require(exported.sampleCount == 3 && exported.report.equals(expectedReport)
                            && exported.draft.equals(expectedDraft)
                            && exported.request.module.recipe() == GuidedTuningRecipe.MAP_PREDICT,
                    "package context changed with live capture/method");
            require(session.csvText() == newerCsv, "old package replaced newer CSV cache");
        } finally { release.countDown(); workers.shutdownNow(); }
    }

    private static void staleAndForeignRequestsAreRejected() {
        GuidedMethodProbeSession session = GuidedReviewPerformanceCharacterization.fixture(3);
        GuidedMethodProbeSession other = GuidedReviewPerformanceCharacterization.fixture(3);
        reject(session, other.requestExport());
        GuidedMethodProbeSession.ExportRequest active = session.requestExport();
        long evidence = session.evidenceRevision();
        session.finish();
        require(session.evidenceRevision() == evidence, "Finish changed evidence revision");
        reject(session, active);
        GuidedMethodProbeSession.ExportRequest completed = session.requestExport();
        session.reset();
        reject(session, completed);
        require(session.requestExport() == null && session.exportSnapshot("test") == null,
                "empty capture created an export package");
    }

    private static void reject(GuidedMethodProbeSession session, GuidedMethodProbeSession.ExportRequest request) {
        try {
            session.exportSnapshot("test", request);
            throw new AssertionError("stale or foreign export request was accepted");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("changed before export"), "missing export retry guidance");
        }
    }

    private static void historicalStagingInvalidatesQueuedRequests() throws Exception {
        GuidedMethodProbeSession session = GuidedReviewPerformanceCharacterization.fixture(3);
        session.finish();
        set(session, "module", GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION));
        se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext.clear();
        try {
            GuidedMethodProbeSession.ExportRequest beforeStage = session.requestExport();
            java.lang.reflect.Method stage = GuidedHistoricalRecoveryLifecycleRegressionTest.class
                    .getDeclaredMethod("stageHistorical");
            stage.setAccessible(true);
            AeProjectSnapshot tune = (AeProjectSnapshot) stage.invoke(null);
            set(session, "projectSnapshot", tune);
            reject(session, beforeStage);
            GuidedMethodProbeSession.ExportSnapshot frozen = session.exportSnapshot("historical-test");
            String report = frozen.report;
            se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext.clear();
            reject(session, frozen.request);
            require(frozen.report.equals(report) && frozen.sampleCount == 3,
                    "clearing historical staging mutated a detached package");
        } finally {
            se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext.clear();
            se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultContext.clear();
            GuidedV019HistoricalReplayContext.clear();
            ((se.anders.tunerstudio.aetuner.guided.method.DecelDetectionMethodModule)
                    GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION)).clearHistoricalReview();
        }
    }

    private static void mapSettingsCannotReplaceCapturedDraft() {
        AeProjectSnapshot baseline =
                new AeProjectSnapshot("package-test",
                    new double[]{1}, new double[]{20}, new double[][]{{0}},
                    new double[]{1000}, new double[]{1}, 0, 0, new double[0], new double[0],
                    false, false, "none", false, true, false, false,
                    new double[0][0], new double[0][0], new double[]{1500, 3000},
                    new double[]{10, 20}, new double[][]{{45, 50}, {55, 58}},
                    new double[]{1500, 3000}, new double[]{.10, .20});
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.configureMapEstimateFocus(baseline, 3, 115);
        GuidedFocusHub.mapEstimateConfigurationListener().onEvidenceBasisRequested(
                MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY);
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE), baseline, 1, 3, 115);
        for (int i = 0; i < 20; i++) {
            EnumMap<ChannelRole, Double> values =
                    new EnumMap<ChannelRole, Double>(
                            ChannelRole.class);
            for (ChannelRole role :
                    ChannelRole.values()) values.put(role, 0.0);
            values.put(ChannelRole.TPS, 20.0);
            values.put(ChannelRole.RPM, 1500.0);
            values.put(ChannelRole.MAP, 60.0);
            double seconds = 1 + i * .05;
            session.accept(new LiveSample(Math.round(seconds * 1e9), seconds, values, 0, 0));
        }
        session.finish();
        GuidedMethodProbeSession.ExportSnapshot frozen = session.exportSnapshot("test");
        require(!frozen.draft.isEmpty() && session.reviewedWritePlan() != null,
                "MAP package fixture has no proposal/draft");
        long evidence = session.evidenceRevision();
        session.updateMapEstimateReviewSettings(100, 130);
        require(session.evidenceRevision() == evidence && !session.exportStillCurrent(frozen.request),
                "settings-only change was not detected before export completion");
        reject(session, frozen.request);
        require(session.copyPasteBlock().isEmpty() && session.reviewedWritePlan() == null
                        && frozen.report.contains("Minimum samples/direct anchor: 3")
                        && frozen.report.contains("115.0 kPa") && !frozen.draft.isEmpty()
                        && frozen.csvText().split("\n").length == 21,
                "MAP package mixed old evidence/settings/draft with a later review");
    }

    private static void blendLedgerSnapshotSurvivesDiscardAndReset() {
        GuidedEvidenceRecorder recorder = new GuidedEvidenceRecorder();
        recorder.startAdaptive(new double[]{2000, 3000}, 20, 5, "manual", 123L);
        recorder.record(outcome(), null);
        GuidedEvidenceRecorder.ExportSnapshot frozen = recorder.exportSnapshot();
        String report = recorder.reportText("test", null, "proposal", "draft");
        String csv = recorder.csvText("test"), diagnostics = recorder.diagnosticsCsvText("");
        recorder.discardLastAccepted();
        require(frozen.csvText("test").equals(csv), "discard changed frozen measurement disposition");
        recorder.record(outcome(), null);
        recorder.reset();
        recorder.startAdaptive(4000, 40, 9, "new session", 456L);
        require(frozen.recordCount == 1 && frozen.sessionId.equals("guided-7b")
                        && frozen.reportText("test", null, "proposal", "draft").equals(report)
                        && frozen.csvText("test").equals(csv)
                        && frozen.diagnosticsCsvText("").equals(diagnostics),
                "Blend package changed across event append/reset/new baseline");
    }

    private static void blendRecoveryWaitsForTheWholeModelUpdate() throws Exception {
        AtomicReference<GuidedCapturePanel> panelRef = new AtomicReference<GuidedCapturePanel>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new GuidedCapturePanel()));
        GuidedCapturePanel panel = panelRef.get();
        BlendDurationGuidedSession session = (BlendDurationGuidedSession) get(panel, "session");
        GuidedEvidenceRecorder recorder = (GuidedEvidenceRecorder) get(panel, "evidence");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicReference<Thread> thread = new AtomicReference<Thread>();
        CountDownLatch entered = new CountDownLatch(1);
        Future<EvidenceRecoverySnapshot.Guided> future;
        try {
            synchronized (session) {
                recorder.startAdaptive(2000, 20, 5, "manual", 123L);
                future = worker.submit(() -> {
                    thread.set(Thread.currentThread());
                    entered.countDown();
                    return panel.recoverySnapshot();
                });
                require(entered.await(5, TimeUnit.SECONDS), "recovery worker did not start");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (!future.isDone() && System.nanoTime() < deadline) {
                    for (StackTraceElement frame : thread.get().getStackTrace()) {
                        if (thread.get().getState() == Thread.State.BLOCKED
                                && frame.getMethodName().equals("blendExportSnapshot")) waiting = true;
                    }
                    if (waiting) break;
                    Thread.yield();
                }
                require(waiting && !future.isDone(), "recovery read a partial Blend model transaction");
                recorder.record(outcome(), session.snapshot());
                session.finish();
                recorder.finish(session.snapshot());
            }
            EvidenceRecoverySnapshot.Guided exported = future.get(5, TimeUnit.SECONDS);
            require(exported != null && ((Integer) get(exported, "recordCount")) == 1
                            && ((String) get(exported, "reportText")).contains("Recorded guided outcomes: 1")
                            && ((String) get(exported, "csvText")).split("\n").length == 2,
                    "Blend recovery count/report/CSV do not share the completed transaction");
        } finally {
            worker.shutdownNow();
            SwingUtilities.invokeAndWait(panel::disposePanel);
        }
    }

    private static GuidedOutcome outcome() {
        return new GuidedOutcome(GuidedOutcome.Decision.VALID, 10, .5, 1, "A", 1,
                "original event", "original trace");
    }
    private static Object get(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
