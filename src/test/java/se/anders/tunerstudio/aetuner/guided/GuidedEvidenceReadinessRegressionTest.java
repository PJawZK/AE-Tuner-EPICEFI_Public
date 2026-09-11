package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

/** Video regression: stopping capture must not manufacture review-ready evidence. */
public final class GuidedEvidenceReadinessRegressionTest {
    private GuidedEvidenceReadinessRegressionTest() { }

    public static void main(String[] args) {
        zeroEvidenceFinishIsIncomplete();
        sufficientEventsUnlockReviewAfterFinish();
        System.out.println("GuidedEvidenceReadinessRegressionTest passed");
    }

    private static GuidedAeMethodModule module() {
        return new GuidedAeMethodModule() {
            @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.WALL_WETTING; }
            @Override public CaptureMode captureMode() { return CaptureMode.READ_ONLY_PROBE; }
            @Override public String setupTitle() { return "readiness test"; }
            @Override public String setupGuidance() { return "test"; }
            @Override public String captureGoal() { return "test"; }
            @Override public ChannelRole[] requiredRoles() { return new ChannelRole[0]; }
            @Override public ChannelRole[] contextRoles() { return new ChannelRole[0]; }
            @Override public String operatorInputs(AeProjectSnapshot snapshot) { return "test"; }
            @Override public String accumulationPlan() { return "test"; }
            @Override public String reviewOutputs() { return "test"; }
            @Override public String currentTuneContext(AeProjectSnapshot snapshot) { return "test"; }
            @Override public boolean activityObserved(LiveSample sample) {
                return sample != null && sample.get(ChannelRole.TPS) > 0.5;
            }
            @Override public ProposalWritePlan reviewedWritePlan(
                    AeProjectSnapshot snapshot, List<LiveSample> evidence) {
                return new ProposalWritePlan("ready-test", "Ready test", "cfg", "test",
                        Arrays.asList(ProposalWritePlan.Change.scalar(
                                "wallTau", 1.0, 1.1, "test", "")));
            }
        };
    }

    private static void zeroEvidenceFinishIsIncomplete() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(module(), null, 2, 3, 115.0);
        session.finish();
        require(session.state() == GuidedCaptureState.COMPLETE,
                "Finish should still end the collection lifecycle");
        require(!session.reviewReady(),
                "zero evidence was incorrectly promoted to review-ready");
        require(session.reviewedWritePlan() == null,
                "zero evidence exposed an evidence-derived write plan");
        require(session.reviewText().contains("EVIDENCE INCOMPLETE")
                        && session.snapshot().headline.contains("MORE EVIDENCE NEEDED"),
                "stopped zero-evidence capture is not visibly distinguished from a valid review");
    }

    private static void sufficientEventsUnlockReviewAfterFinish() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(module(), null, 2, 3, 115.0);
        session.accept(sample(1.00, 1.0));
        session.accept(sample(1.30, 0.0));
        session.accept(sample(1.60, 1.0));
        session.finish();
        require(session.activityEventCount() == 2,
                "fixture did not create the two distinct method events");
        require(session.reviewReady(),
                "target event evidence did not unlock Review after Finish");
        require(session.reviewedWritePlan() != null,
                "review-ready evidence did not expose the module's guarded plan");
    }

    private static LiveSample sample(double seconds, double tps) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.TPS, tps);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
