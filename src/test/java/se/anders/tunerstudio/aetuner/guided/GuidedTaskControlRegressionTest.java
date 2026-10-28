package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.*;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import javax.swing.*;
import java.lang.reflect.*;
import java.util.concurrent.CancellationException;

public final class GuidedTaskControlRegressionTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                cancelAndStartOverAtEveryStage();
                exportProtectsEvidenceWhileResetIsPending();
                rejectedTaskSwitchKeepsSelection();
                missingDataNeverRequestsPedalMovement();
                decelResultExplainsEvidenceGate();
                finishReconcilesMainWorkflow();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        Thread.currentThread().interrupt();
        try {
            DecelHistoricalLogImporter.load(null);
            throw new AssertionError("analysis ignored interruption");
        } catch (CancellationException expected) {
            require(Thread.currentThread().isInterrupted(), "analysis swallowed interruption");
        } finally { Thread.interrupted(); }
        System.out.println("GuidedTaskControlRegressionTest passed");
    }
    private static void cancelAndStartOverAtEveryStage() throws Exception {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        GuidedV019WorkspacePanel workspace = new GuidedV019WorkspacePanel(panel);
        GuidedMethodProbeSession probe = (GuidedMethodProbeSession) get(panel, "probeSession");
        try {
            for (GuidedV019WorkspacePanel.Stage stage : GuidedV019WorkspacePanel.Stage.values()) {
                seed(probe);
                set(workspace, "stage", stage);
                workspace.cancelTask();
                require(panel.taskCancelled() && probe.sampleCount() == 3
                                && probe.state() == GuidedCaptureState.COMPLETE,
                        "cancel did not stop and retain evidence at " + stage);
                require(!probe.reviewReady() && probe.reportText("test").contains("TASK CANCELLED"), "cancelled export retained review authority");
                panel.acceptProbeSampleForTest(GuidedReviewPerformanceCharacterization.sample(99, true));
                require(probe.sampleCount() == 3, "cancelled capture accepted another sample");
                require(!((JButton) get(panel, "applyProposal")).isEnabled(), "cancel retained Apply authority");
                require(get(workspace, "stage") == GuidedV019WorkspacePanel.Stage.PREPARE,
                        "cancel left workflow stranded");
                Method stageHistorical = GuidedHistoricalRecoveryLifecycleRegressionTest.class.getDeclaredMethod("stageHistorical");
                stageHistorical.setAccessible(true);
                stageHistorical.invoke(null);
                workspace.startOverTask();
                require(!panel.connectionTextForTest().contains("Task cancelled"), "Start Over left a stale cancellation message");
                require(!panel.taskCancelled() && !probe.hasEvidence() && probe.state() == GuidedCaptureState.IDLE,
                        "Start Over did not produce a fresh session");
                require(!DecelHistoricalThresholdProposalContext.active()
                                && !DecelHistoricalResultContext.available(), "Start Over retained historical authority");
            }
        } finally { panel.disposePanel(); }
    }
    private static void exportProtectsEvidenceWhileResetIsPending() throws Exception {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            GuidedMethodProbeSession probe = (GuidedMethodProbeSession) get(panel, "probeSession");
            seed(probe);
            set(panel, "exportInProgress", true);
            require(!panel.startOverTask() && probe.sampleCount() == 3, "reset discarded a pending export");
            panel.cancelTask();
            require(probe.sampleCount() == 3, "cancel discarded pending export evidence");
            set(panel, "exportInProgress", false);
            require(panel.startOverTask() && !probe.hasEvidence(), "reset stayed blocked after export");
        } finally { panel.disposePanel(); }
    }
    private static void rejectedTaskSwitchKeepsSelection() throws Exception {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        GuidedV019WorkspacePanel workspace = new GuidedV019WorkspacePanel(panel);
        try {
            GuidedMethodProbeSession probe = (GuidedMethodProbeSession) get(panel, "probeSession");
            probe.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.WALL_WETTING));
            Object original = get(workspace, "task");
            GuidedV019ProductionBridge bridge = (GuidedV019ProductionBridge)get(workspace,"bridge");
            require(!bridge.select(GuidedProductionTask.MAP_ESTIMATE), "bridge allowed a task switch during capture");
            Method snapshot = GuidedProbeReviewCacheRegressionTest.class.getDeclaredMethod("mapSnapshot");
            snapshot.setAccessible(true);
            workspace.setCurrentTune((AeProjectSnapshot) snapshot.invoke(null));
            Method select = GuidedV019WorkspacePanel.class.getDeclaredMethod("selectTask", GuidedProductionTask.class);
            select.setAccessible(true); select.invoke(workspace, GuidedProductionTask.MAP_ESTIMATE);
            require(get(workspace, "task") == original, "blocked switch changed selected task card");
        } finally { panel.disposePanel(); }
    }
    private static void missingDataNeverRequestsPedalMovement() throws Exception {
        Method snapshot = GuidedProbeReviewCacheRegressionTest.class.getDeclaredMethod("mapSnapshot");
        snapshot.setAccessible(true);
        EngagementFocusModel model = EngagementFocusModel.build((AeProjectSnapshot) snapshot.invoke(null),
                null, GuidedCaptureState.CAPTURING, 0, 5, 0, 0);
        require(!model.nextActionText().contains("MAKE ONE COMFORTABLE"), "missing channels requested movement");
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            require(!panel.liveDataReady(), "absent data presented as live-ready");
            panel.showLiveSampleForTest(GuidedReviewPerformanceCharacterization.sample(1, true));
            set(panel, "lastSharedSampleReceivedNano", System.nanoTime() - 3000000000L);
            require(!panel.liveDataReady(), "stale data presented as live-ready");
        } finally { panel.disposePanel(); }
    }
    private static void decelResultExplainsEvidenceGate() throws Exception {
        Method stage = GuidedHistoricalRecoveryLifecycleRegressionTest.class.getDeclaredMethod("stageHistorical");
        stage.setAccessible(true); stage.invoke(null);
        try {
            DecelHistoricalResultModel model = DecelHistoricalResultContext.latest();
            JComponent view = GuidedV019DecelResultView.create(model);
            require(hasText(view, "Why: " + model.bins.get(0).reason),
                    "Decel result does not expose the per-bin evidence gate");
        } finally { DecelHistoricalResultContext.clear(); DecelHistoricalThresholdProposalContext.clear(); }
    }
    private static boolean hasText(java.awt.Container root, String text) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JTextArea && ((JTextArea)child).getText().contains(text)) return true;
            if (child instanceof java.awt.Container && hasText((java.awt.Container)child,text)) return true;
        }
        return false;
    }
    private static void finishReconcilesMainWorkflow() throws Exception {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        GuidedV019WorkspacePanel workspace = new GuidedV019WorkspacePanel(panel);
        try {
            GuidedMethodProbeSession probe = (GuidedMethodProbeSession)get(panel,"probeSession");
            set(workspace,"task",GuidedProductionTask.WALL_FILM);
            set(workspace,"stage",GuidedV019WorkspacePanel.Stage.GUIDED_FOCUS);
            probe.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.WALL_WETTING),null,1,3,115);
            probe.finish();
            Method refresh = GuidedV019WorkspacePanel.class.getDeclaredMethod("refreshAll");
            refresh.setAccessible(true); refresh.invoke(workspace);
            require(get(workspace,"stage") == GuidedV019WorkspacePanel.Stage.CAPTURE,
                    "incomplete Finish stranded main workflow in Focus");
            probe.reset();
            probe.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.WALL_WETTING),null,1,3,115);
            java.util.EnumMap<se.anders.tunerstudio.aetuner.model.ChannelRole,Double> values =
                    new java.util.EnumMap<>(se.anders.tunerstudio.aetuner.model.ChannelRole.class);
            for(se.anders.tunerstudio.aetuner.model.ChannelRole role:se.anders.tunerstudio.aetuner.model.ChannelRole.values()) values.put(role,1.0);
            probe.accept(new se.anders.tunerstudio.aetuner.model.LiveSample(1000000000L,1,values,10,0));
            probe.finish();
            require(probe.reviewReady(),"ready lifecycle fixture did not meet its evidence-count target");
            set(workspace,"stage",GuidedV019WorkspacePanel.Stage.GUIDED_FOCUS);
            refresh.invoke(workspace);
            require(get(workspace,"stage") == GuidedV019WorkspacePanel.Stage.REVIEW,
                    "ready Finish stranded main workflow in Focus");
        } finally { panel.disposePanel(); }
    }
    private static void seed(GuidedMethodProbeSession probe) {
        probe.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.INSTANT_FUEL));
        for (int i=0;i<3;i++) probe.accept(GuidedReviewPerformanceCharacterization.sample(1+i*.01, true));
    }
    private static Object get(Object owner, String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner,value);
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
