package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultContext;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultModel;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.EnumMap;

/**
 * Thin presentation adapter between the v0.19 UI and the existing production
 * GuidedCapturePanel lifecycle. It deliberately delegates to the already-wired
 * production controls/session methods; it owns no tuning math, proposal writer,
 * readback, Restore or Burn behavior.
 */
final class GuidedV019ProductionBridge {
    private final EnumMap<GuidedTuningRecipe, Integer> retainedProbeEvidence =
            new EnumMap<GuidedTuningRecipe, Integer>(GuidedTuningRecipe.class);

    private final GuidedCapturePanel production;
    private final JButton readWorkingTune;
    private final JButton reviewTaskSettings;
    private final JButton pause;
    private final JButton finish;
    private final JButton saveReport;
    private final JButton reset;
    private final JButton apply;
    private final JButton keepValidation;
    private final JButton restore;
    private final JButton reconnect;
    private final GuidedFocusHub.CaptureControl captureControl;
    private final Field projectSnapshotField;
    private final Field probeSessionField;
    private final Field blendSessionField;
    private GuidedProductionTask selectedTask;

    GuidedV019ProductionBridge(GuidedCapturePanel production) {
        if (production == null) throw new IllegalArgumentException("production");
        this.production = production;
        readWorkingTune = button("readProject");
        reviewTaskSettings = button("reviewTaskSettings");
        pause = button("pause");
        finish = button("finish");
        saveReport = button("saveReport");
        reset = button("reset");
        apply = button("applyProposal");
        keepValidation = button("acceptValidationKeep");
        restore = button("restoreProposal");
        reconnect = button("reconnect");
        try {
            projectSnapshotField = field("projectSnapshot");
            probeSessionField = field("probeSession");
            blendSessionField = field("session");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not bind production Guided lifecycle", ex);
        }
        captureControl = new GuidedFocusHub.CaptureControl() {
            @Override public GuidedCaptureState finishCapture() { return finishActiveCapture(); }
            @Override public GuidedCaptureState togglePause() {
                clickSynchronous(pause);
                return directSelectedCaptureState();
            }
            @Override public GuidedCaptureState continueCapture() {
                production.startSelectedTaskForTest();
                return directSelectedCaptureState();
            }
            @Override public GuidedCaptureState captureState() { return directSelectedCaptureState(); }
            @Override public GuidedTuningRecipe activeRecipe() {
                GuidedMethodProbeSession probe = probeSession();
                GuidedAeMethodModule module = probe == null ? null : probe.module();
                GuidedCaptureState probeState = probe == null ? GuidedCaptureState.IDLE : probe.state();
                if (module != null && (probeState == GuidedCaptureState.CAPTURING
                        || probeState == GuidedCaptureState.PAUSED
                        || probeState == GuidedCaptureState.COMPLETE)) {
                    return module.recipe();
                }
                BlendDurationGuidedSession blend = blendSession();
                if (blend != null && blend.snapshot().state != GuidedCaptureState.IDLE) {
                    return GuidedTuningRecipe.BLEND_DURATION;
                }
                return selectedTask == null ? null : selectedTask.productionRecipe;
            }
            @Override public boolean reviewReady() { return directSelectedEvidenceReady(); }
            @Override public boolean canContinueCapture() { return production.startCaptureEnabledForTest(); }
            @Override public boolean canExportEvidence() { return saveReport.isEnabled(); }
            @Override public void exportEvidence() { click(saveReport); }
            @Override public boolean liveDataReady() { return production.liveDataReady(); }
        };
        GuidedFocusHub.setCaptureControl(captureControl);
    }

    boolean select(GuidedProductionTask task) {
        if (task == null || task.productionRecipe == null) return false;
        if (production.taskCancelled() && selectedTask != task) return false;
        BlendDurationGuidedSession activeBlend = blendSession();
        if (activeBlend != null && activeBlend.snapshot().state.isCaptureInProgress()
                && task.productionRecipe != GuidedTuningRecipe.BLEND_DURATION) return false;
        GuidedMethodProbeSession probe = probeSession();
        GuidedAeMethodModule current = probe == null ? null : probe.module();
        if (current != null && current.recipe() != task.productionRecipe) {
            GuidedCaptureState currentState = probe.state();
            if (currentState.isCaptureInProgress()) return false;
            if (!retainCompletedProbe(probe, current)) return false;
        }
        selectedTask = task;
        production.selectTuningTaskForTest(task.productionRecipe);
        return true;
    }

    AeProjectSnapshot readWorkingTune() {
        clickSynchronous(readWorkingTune);
        AeProjectSnapshot snapshot = currentWorkingTune();
        GuidedV019HistoricalReplayContext.publish(snapshot);
        DecelHistoricalResultContext.refreshAfterWorkingTune(snapshot);
        return snapshot;
    }

    AeProjectSnapshot currentWorkingTune() {
        try {
            Object value = projectSnapshotField.get(production);
            return value instanceof AeProjectSnapshot ? (AeProjectSnapshot)value : null;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not read production Working Tune snapshot", ex);
        }
    }

    void editTaskSettings() { click(reviewTaskSettings); }
    void setBlendArmedRpmBins(double[] bins) { production.setBlendArmedRpmBinsForV019(bins); }
    double[] blendArmedRpmBins() { return production.blendArmedRpmBinsForV019(); }
    void activateFocusControl() {
        GuidedFocusHub.setCaptureControl(captureControl);
        GuidedV019HistoricalReplayContext.publish(currentWorkingTune());
    }

    void startCapture() {
        if (production.taskCancelled()) return;
        activateFocusControl();
        if (selectedTask == GuidedProductionTask.DECEL_DETECTION
                && directSelectedCaptureState() == GuidedCaptureState.IDLE) {
            if (DecelHistoricalResultContext.targetedCaptureUseful()) {
                production.startSelectedTaskForTest();
                return;
            }
            final AeProjectSnapshot snapshot = currentWorkingTune();
            final boolean liveAvailable = production.startCaptureEnabledForTest();
            GuidedV019DecelEvidenceEntryDialog.choose(production, snapshot,
                    liveAvailable, new Runnable() {
                        @Override public void run() { production.startSelectedTaskForTest(); }
                    });
            return;
        }
        production.startSelectedTaskForTest();
    }

    void cancelTask() { production.cancelTask(); }
    boolean startOverTask() { return production.startOverTask(); }
    boolean taskCancelled() { return production.taskCancelled(); }
    boolean canStartOverTask() { return production.canStartOverTask(); }
    void finishCapture() { finishActiveCapture(); }
    void togglePause() { clickSynchronous(pause); }
    void resetSession() {
        GuidedMethodProbeSession probe = probeSession();
        GuidedAeMethodModule current = probe == null ? null : probe.module();
        if (!retainCompletedProbe(probe, current)) return;
        click(reset);
        if (selectedTask == GuidedProductionTask.DECEL_DETECTION) {
            DecelHistoricalResultContext.clear();
            DecelHistoricalThresholdProposalContext.clear();
        }
    }
    void reconnect() { click(reconnect); }
    void applyReviewedProposal() { clickSynchronous(apply); }
    void acceptValidationKeep() { click(keepValidation); }
    void restorePreviousApply() { clickSynchronous(restore); }

    boolean readEnabled() { return readWorkingTune.isEnabled(); }
    boolean taskSettingsEnabled() { return reviewTaskSettings.isEnabled(); }
    boolean currentSessionExportAvailable() { return saveReport.isEnabled(); }
    void exportCurrentSession() { activateFocusControl(); click(saveReport); }

    boolean startEnabled() {
        if (production.taskCancelled()) return false;
        if (selectedTask == GuidedProductionTask.DECEL_DETECTION
                && directSelectedCaptureState() == GuidedCaptureState.IDLE) {
            AeProjectSnapshot snapshot = currentWorkingTune();
            return snapshot != null && snapshot.hasDecelDetectionSettings();
        }
        return production.startCaptureEnabledForTest();
    }

    String startText() {
        if (selectedTask == GuidedProductionTask.DECEL_DETECTION
                && directSelectedCaptureState() == GuidedCaptureState.IDLE) {
            if (DecelHistoricalResultContext.targetedCaptureUseful()) {
                return "Capture Missing Regions";
            }
            return "Analyze Existing Log…";
        }
        return production.startCaptureTextForTest();
    }

    boolean applyEnabled() { return production.applyCurrentProposalEnabledForTest(); }
    boolean restoreEnabled() { return production.restorePreviousApplyEnabledForTest(); }
    boolean saveEnabled() { return saveReport.isEnabled(); }
    String connectionText() { return production.connectionTextForTest(); }
    String workflowText() { return production.workflowStageTextForTest(); }
    String headlineText() { return production.headlineTextForTest(); }
    String checksText() { return production.checksTextForTest(); }
    String proposalText() { return production.proposalTextForTest(); }
    int retainedProbeSessionCount() { return retainedProbeEvidence.size(); }

    DecelHistoricalResultModel decelHistoricalResult() {
        return DecelHistoricalResultContext.latest();
    }
    boolean decelHistoricalResultReady() { return DecelHistoricalResultContext.reviewReady(); }
    boolean decelTargetedCaptureUseful() { return DecelHistoricalResultContext.targetedCaptureUseful(); }
    String decelTargetedRpmText() { return DecelHistoricalResultContext.targetedRpmText(); }

    GuidedFocusHub.State focusState(GuidedProductionTask task) {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        if (task == null || task.productionRecipe == null || state == null) return state;
        return state.recipe == task.productionRecipe ? state : null;
    }

    GuidedCaptureState captureState(GuidedProductionTask task) {
        if (task == null || task.productionRecipe == null) return GuidedCaptureState.IDLE;
        GuidedMethodProbeSession probe = probeSession();
        GuidedAeMethodModule module = probe == null ? null : probe.module();
        if (module != null && module.recipe() == task.productionRecipe) return probe.state();
        if (task.productionRecipe == GuidedTuningRecipe.BLEND_DURATION) {
            BlendDurationGuidedSession blend = blendSession();
            if (blend != null) {
                GuidedCaptureState state = blend.snapshot().state;
                if (state != GuidedCaptureState.IDLE) return state;
            }
        }
        GuidedFocusHub.State state = focusState(task);
        return state == null ? GuidedCaptureState.IDLE : state.captureState;
    }

    boolean evidenceReady(GuidedProductionTask task) {
        if (production.taskCancelled()) return false;
        if (task == null || task.productionRecipe == null) return false;
        GuidedMethodProbeSession probe = probeSession();
        GuidedAeMethodModule module = probe == null ? null : probe.module();
        if (module != null && module.recipe() == task.productionRecipe
                && probe.state() != GuidedCaptureState.IDLE) {
            return probe.reviewReady();
        }
        if (task == GuidedProductionTask.DECEL_DETECTION
                && DecelHistoricalResultContext.reviewReady()) {
            return true;
        }
        if (task.productionRecipe == GuidedTuningRecipe.BLEND_DURATION) {
            BlendDurationGuidedSession blend = blendSession();
            return blend != null && blend.snapshot().state == GuidedCaptureState.COMPLETE;
        }
        return false;
    }

    private boolean directSelectedEvidenceReady() {
        if (production.taskCancelled()) return false;
        GuidedMethodProbeSession probe = probeSession();
        if (probe != null && probe.module() != null
                && probe.state() != GuidedCaptureState.IDLE) return probe.reviewReady();
        BlendDurationGuidedSession blend = blendSession();
        if (blend != null && blend.snapshot().state != GuidedCaptureState.IDLE) {
            return blend.snapshot().state == GuidedCaptureState.COMPLETE;
        }
        return selectedTask != null && evidenceReady(selectedTask);
    }

    private GuidedCaptureState directSelectedCaptureState() {
        GuidedMethodProbeSession probe = probeSession();
        if (probe != null && probe.module() != null
                && probe.state() != GuidedCaptureState.IDLE) return probe.state();
        BlendDurationGuidedSession blend = blendSession();
        if (blend != null && blend.snapshot().state != GuidedCaptureState.IDLE) {
            return blend.snapshot().state;
        }
        return selectedTask == null ? GuidedCaptureState.IDLE : captureState(selectedTask);
    }

    private GuidedCaptureState finishActiveCapture() {
        clickSynchronous(finish);
        return directSelectedCaptureState();
    }

    private boolean retainCompletedProbe(GuidedMethodProbeSession probe,
                                      GuidedAeMethodModule current) {
        if (!production.allowProbeRetirement()) return false;
        if (probe == null || current == null || probe.state() != GuidedCaptureState.COMPLETE) return true;
        if (probe.reviewReady()) {
            retainedProbeEvidence.put(current.recipe(), Integer.valueOf(probe.sampleCount()));
            production.markProbeEvidenceExportedForTest();
        }
        probe.reset();
        return true;
    }

    private GuidedMethodProbeSession probeSession() {
        try {
            Object value = probeSessionField.get(production);
            return value instanceof GuidedMethodProbeSession ? (GuidedMethodProbeSession)value : null;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not read production probe session", ex);
        }
    }

    private BlendDurationGuidedSession blendSession() {
        try {
            Object value = blendSessionField.get(production);
            return value instanceof BlendDurationGuidedSession ? (BlendDurationGuidedSession)value : null;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not read production Blend Duration session", ex);
        }
    }

    private Field field(String fieldName) throws ReflectiveOperationException {
        Field field = GuidedCapturePanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private JButton button(String fieldName) {
        try {
            Field field = GuidedCapturePanel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(production);
            if (!(value instanceof JButton)) {
                throw new IllegalStateException("Guided production field is not a JButton: " + fieldName);
            }
            return (JButton)value;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not bind existing Guided production action: " + fieldName, ex);
        }
    }

    private static void click(final JButton button) {
        if (button == null || !button.isEnabled()) return;
        if (SwingUtilities.isEventDispatchThread()) button.doClick();
        else SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { if (button.isEnabled()) button.doClick(); }
        });
    }

    private static void clickSynchronous(final JButton button) {
        if (button == null || !button.isEnabled()) return;
        if (SwingUtilities.isEventDispatchThread()) {
            button.doClick();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() { if (button.isEnabled()) button.doClick(); }
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Could not invoke existing Guided production action", ex);
        }
    }
}
