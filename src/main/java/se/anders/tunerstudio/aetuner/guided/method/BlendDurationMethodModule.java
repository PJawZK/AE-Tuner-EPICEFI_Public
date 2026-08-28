package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

public final class BlendDurationMethodModule implements GuidedAeMethodModule {
    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.BLEND_DURATION; }
    @Override public CaptureMode captureMode() { return CaptureMode.BLEND_DURATION; }
    @Override public String setupTitle() { return "Predictive MAP Blend Duration controlled capture"; }
    @Override public String setupGuidance() {
        return "Use the existing per-RPM controlled opening workflow. Numerical Apply remains withheld while the corrected firmware-faithful model is physically validated.";
    }
    @Override public String captureGoal() {
        return "Collect comparable final-target response events and validate coherent Effective MAP replay.";
    }
    @Override public ChannelRole[] requiredRoles() { return new ChannelRole[0]; }
    @Override public ChannelRole[] contextRoles() { return new ChannelRole[0]; }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Select the actual Blend Duration RPM table point, desired TPS step, comparable-event target and gear handling mode.";
    }
    @Override public String accumulationPlan() {
        return "Use the controlled opening detector, buffered prediction evidence, final upward fallback target latch and event-local gear checks already implemented for Blend Duration. Keep the real curve unchanged while validation remains in progress.";
    }
    @Override public String reviewOutputs() {
        return "Accepted/excluded outcome reasons, comparable final-target durations, coherent-channel diagnostics, event-local gear evidence and firmware replay review. Numerical Blend Duration Apply remains withheld.";
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null) return "Working tune not read yet.";
        return snapshot.hasBlendDurationCurve()
                ? "Blend Duration points loaded: " + snapshot.getBlendDurationRpmBins().length
                : "Predictive MAP Blend Duration curve not found.";
    }
    @Override public boolean activityObserved(LiveSample sample) { return false; }
}
