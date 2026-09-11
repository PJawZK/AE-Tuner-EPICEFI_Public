package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.ChannelRole;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Permanent route/evidence contract for the activated Decel Detection task. */
public final class DecelDetectionMethodModuleRegressionTest {
    private DecelDetectionMethodModuleRegressionTest() { }

    public static void main(String[] args) {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(
                GuidedTuningRecipe.DECEL_DETECTION);
        require(module instanceof DecelDetectionMethodModule,
                "Decel Detection did not route to its production evidence module");
        require(module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE,
                "Decel Detection capture stopped being read-only");
        requireAll(module.requiredRoles(), ChannelRole.RPM, ChannelRole.TPS,
                ChannelRole.DELTA_TPS, ChannelRole.TPS_DECEL_ACTIVE);
        requireAll(module.contextRoles(), ChannelRole.SMOOTHED_DELTA_TPS,
                ChannelRole.DFCO, ChannelRole.FUEL_CUT, ChannelRole.MAP_PRED_ACTIVE);

        String setup = module.setupGuidance();
        String operator = module.operatorInputs(null);
        String accumulation = module.accumulationPlan();
        String review = module.reviewOutputs();
        require(setup.contains("NORMAL CORRECTION")
                        && setup.contains("DECEL RELEASE")
                        && setup.contains("threshold of 0")
                        && setup.contains("never auto-enabled")
                        && setup.contains("hold remains read-only"),
                "Decel setup lost physical classes, zero-disable safety, or hold boundary");
        require(operator.contains("Do not change the decel threshold curve")
                        && operator.contains("Hold cycles are deliberately read-only"),
                "Decel operator contract permits threshold/hold mutation during capture");
        require(accumulation.contains("80 qualifying quiet samples")
                        && accumulation.contains("1.5 continuous seconds")
                        && accumulation.contains("3+3 magnitude separation")
                        && accumulation.contains("Current threshold 0 remains disabled"),
                "Decel accumulation lost calibration/separation/zero-disable evidence contract");
        require(review.contains("no ProposalWritePlan")
                        && review.contains("tpsDecelHoldCycles remains unchanged"),
                "Decel review boundary stopped withholding before evidence or hold tuning");
        require(module.explicitSettingWritePlan(null) == null,
                "Decel automatic route exposed a pre-capture explicit write plan");
        require(module.reviewedWritePlan(null, Collections.emptyList()) == null,
                "Decel automatic route created a plan without Working Tune/evidence");

        System.out.println("DecelDetectionMethodModuleRegressionTest passed");
    }

    private static void requireAll(ChannelRole[] actual, ChannelRole... expected) {
        Set<ChannelRole> roles = new HashSet<ChannelRole>();
        for (ChannelRole role : actual) roles.add(role);
        for (ChannelRole role : expected) {
            require(roles.contains(role), "Decel method evidence contract omitted " + role);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
