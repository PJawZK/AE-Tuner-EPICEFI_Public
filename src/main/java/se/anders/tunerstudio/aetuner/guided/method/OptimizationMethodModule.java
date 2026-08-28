package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/** Product-review scaffold: no capture or tuning authority yet. */
public final class OptimizationMethodModule implements GuidedAeMethodModule {
    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.OPTIMIZATION; }
    @Override public CaptureMode captureMode() { return CaptureMode.ARCHITECTURE_ONLY; }
    @Override public String setupTitle() { return "Stack interaction review"; }
    @Override public String setupGuidance() {
        return "Review which transient methods are enabled, where their authority overlaps and whether every layer still has a clear purpose. This task is present to make the intended final Guided workflow visible; it does not yet create automatic enable/disable recommendations.";
    }
    @Override public String captureGoal() { return "No dedicated live capture yet."; }
    @Override public ChannelRole[] requiredRoles() { return new ChannelRole[0]; }
    @Override public ChannelRole[] contextRoles() { return new ChannelRole[0]; }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Review the current enabled-method combination and use Guided Focus to understand the intended interaction review.";
    }
    @Override public String accumulationPlan() {
        return "Future implementation should correlate a common transient timeline across every enabled AE contribution rather than tune one method in isolation.";
    }
    @Override public String reviewOutputs() {
        return "Planned outputs: method-overlap summary, attribution confidence, redundant-authority warnings and links back to the specific Guided task responsible for an unresolved error.";
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        return snapshot == null ? "Working tune not read yet." : snapshot.methodStatusText();
    }
    @Override public boolean activityObserved(LiveSample sample) { return false; }
}
