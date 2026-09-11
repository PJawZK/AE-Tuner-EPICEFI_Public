package se.anders.tunerstudio.aetuner.guided;

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
 *
 * GuidedCapturePanel predates the structural UI port and still encapsulates its
 * action buttons/private Working Tune snapshot. Until that controller/presentation
 * monolith is split, this bridge binds those existing production controls once.
 * Capture state is read directly from the production session so the v0.19 stage
 * machine cannot lag behind a capture that has already started.
 */
final class GuidedV019ProductionBridge {
    /*
     * Completed probe payloads are already owned by the production evidence /
     * export and recovery paths. The v0.19 bridge only needs to remember that a
     * recipe had a completed retained session; keeping a second full report,
     * CSV and copy/paste draft here duplicated large strings that were never
     * read. Store the bounded sample-count marker only.
     */
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
        GuidedFocusHub.setCaptureControl(new GuidedFocusHub.CaptureControl() {
            @Override public GuidedCaptureState finishCapture() {
                return finishActiveCapture();
            }
            @Override public GuidedCaptureState togglePause() {
                clickSynchronous(pause);
                return directSelectedCaptureState();
            }
            @Override public GuidedCaptureState continueCapture() {
                production.startSelectedTaskForTest();
                return directSelectedCaptureState();
            }
            @Override public GuidedCaptureState captureState() {
                return directSelectedCaptureState();
            }
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
            @Override public boolean reviewReady() {
                return directSelectedEvidenceReady();
            }
            @Override public boolean canContinueCapture() {
                return production.startCaptureEnabledForTest();
            }
            @Override public boolean canExportEvidence() {
                return saveReport.isEnabled();
            }
            @Override public void exportEvidence() {
                click(saveReport);
            }
        });
    }

    void select(GuidedProductionTask task) {
        if (task == null || task.productionRecipe == null) return;
        GuidedMethodProbeSession probe = probeSession();
        GuidedAeMethodModule current = probe == null ? null : probe.module();
        if (current != null && current.recipe() != task.productionRecipe) {
            GuidedCaptureState currentState = probe.state();
            if (currentState == GuidedCaptureState.CAPTURING
                    || currentState == GuidedCaptureState.PAUSED) {
                return;
            }
            retainCompletedProbe(probe, current);
        }
        selectedTask = task;
        production.selectTuningTaskForTest(task.productionRecipe);
    }

    AeProjectSnapshot readWorkingTune() {
        clickSynchronous(readWorkingTune);
        return currentWorkingTune();
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
    void startCapture() { production.startSelectedTaskForTest(); }
    void finishCapture() { finishActiveCapture(); }
    void togglePause() { clickSynchronous(pause); }
    void resetSession() {
        GuidedMethodProbeSession probe = probeSession();
        GuidedAeMethodModule current = probe == null ? null : probe.module();
        retainCompletedProbe(probe, current);
        click(reset);
    }
    void reconnect() { click(reconnect); }
    void applyReviewedProposal() { clickSynchronous(apply); }
    void acceptValidationKeep() { click(keepValidation); }
    void restorePreviousApply() { clickSynchronous(restore); }

    boolean readEnabled() { return readWorkingTune.isEnabled(); }
    boolean taskSettingsEnabled() { return reviewTaskSettings.isEnabled(); }
    boolean startEnabled() { return production.startCaptureEnabledForTest(); }
    String startText() { return production.startCaptureTextForTest(); }
    boolean applyEnabled() { return production.applyCurrentProposalEnabledForTest(); }
    boolean restoreEnabled() { return production.restorePreviousApplyEnabledForTest(); }
    boolean saveEnabled() { return saveReport.isEnabled(); }
    String connectionText() { return production.connectionTextForTest(); }
    String workflowText() { return production.workflowStageTextForTest(); }
    String headlineText() { return production.headlineTextForTest(); }
    String checksText() { return production.checksTextForTest(); }
    String proposalText() { return production.proposalTextForTest(); }
    int retainedProbeSessionCount() { return retainedProbeEvidence.size(); }

    GuidedFocusHub.State focusState(GuidedProductionTask task) {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        if (task == null || task.productionRecipe == null || state == null) return state;
        return state.recipe == task.productionRecipe ? state : null;
    }

    GuidedCaptureState captureState(GuidedProductionTask task) {
        if (task == null || task.productionRecipe == null) return GuidedCaptureState.IDLE;

        GuidedMethodProbeSession probe = probeSession();
        GuidedAeMethodModule module = probe == null ? null : probe.module();
        if (module != null && module.recipe() == task.productionRecipe) {
            return probe.state();
        }

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

    /**
     * Capture COMPLETE means only that collection has stopped. Review readiness
     * is a separate evidence-quality decision and must never be inferred from
     * the lifecycle state alone.
     */
    boolean evidenceReady(GuidedProductionTask task) {
        if (task == null || task.productionRecipe == null) return false;
        GuidedMethodProbeSession probe = probeSession();
        GuidedAeMethodModule module = probe == null ? null : probe.module();
        if (module != null && module.recipe() == task.productionRecipe) {
            return probe.reviewReady();
        }
        if (task.productionRecipe == GuidedTuningRecipe.BLEND_DURATION) {
            GuidedFocusHub.State focus = focusState(task);
            return focus != null && focus.captureState == GuidedCaptureState.COMPLETE
                    && focus.blendDuration != null
                    && focus.blendDuration.matchingEvents >= focus.blendDuration.targetEvents;
        }
        return false;
    }

    private boolean directSelectedEvidenceReady() {
        if (selectedTask != null) return evidenceReady(selectedTask);
        GuidedMethodProbeSession probe = probeSession();
        if (probe != null && probe.module() != null) return probe.reviewReady();
        BlendDurationGuidedSession blend = blendSession();
        if (blend == null) return false;
        GuidedFocusHub.State focus = GuidedFocusHub.snapshot();
        return blend.snapshot().state == GuidedCaptureState.COMPLETE
                && focus != null && focus.blendDuration != null
                && focus.blendDuration.matchingEvents >= focus.blendDuration.targetEvents;
    }

    private GuidedCaptureState directSelectedCaptureState() {
        if (selectedTask != null) return captureState(selectedTask);
        GuidedMethodProbeSession probe = probeSession();
        if (probe != null && probe.module() != null) return probe.state();
        BlendDurationGuidedSession blend = blendSession();
        return blend == null ? GuidedCaptureState.IDLE : blend.snapshot().state;
    }

    private GuidedCaptureState finishActiveCapture() {
        clickSynchronous(finish);
        return directSelectedCaptureState();
    }

    private void retainCompletedProbe(GuidedMethodProbeSession probe,
                                      GuidedAeMethodModule current) {
        if (probe == null || current == null || probe.state() != GuidedCaptureState.COMPLETE) return;
        if (probe.reviewReady()) {
            retainedProbeEvidence.put(current.recipe(), Integer.valueOf(probe.sampleCount()));
            production.markProbeEvidenceExportedForTest();
        }
        probe.reset();
    }

    private GuidedMethodProbeSession probeSession() {
        try {
            Object value = probeSessionField.get(production);
            return value instanceof GuidedMethodProbeSession
                    ? (GuidedMethodProbeSession)value : null;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not read production probe session", ex);
        }
    }

    private BlendDurationGuidedSession blendSession() {
        try {
            Object value = blendSessionField.get(production);
            return value instanceof BlendDurationGuidedSession
                    ? (BlendDurationGuidedSession)value : null;
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
