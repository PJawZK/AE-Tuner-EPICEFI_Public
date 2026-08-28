package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.BlendDurationPolicy;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;

/**
 * Test-only bridge for the already-proven M6B Blend Duration write contract.
 * Production GuidedBlendProposal intentionally withholds numerical Blend
 * proposals during the Archive19 measurement-model correction. The write
 * gateway itself remains regression-tested without re-enabling it in production.
 */
public final class GuidedBlendProposalTestSupport {
    private static final DecimalFormat F0 = new DecimalFormat("0");

    private GuidedBlendProposalTestSupport() { }

    public static ProposalWritePlan buildWritePlan(AeProjectSnapshot snapshot,
                                                   int pointIndex,
                                                   Double... durations) {
        if (snapshot == null || !snapshot.hasBlendDurationCurve()) {
            throw new AssertionError("Blend Duration test snapshot is unavailable");
        }
        double[] rpm = snapshot.getBlendDurationRpmBins();
        double[] values = snapshot.getBlendDurationValues();
        if (pointIndex < 0 || pointIndex >= rpm.length) {
            throw new AssertionError("Blend Duration test point is outside the curve");
        }
        BlendDurationPolicy.Evaluation evaluation =
                BlendDurationPolicy.evaluate(Arrays.asList(durations));
        if (!evaluation.eligible) {
            throw new AssertionError("Write-contract fixture requires statistically eligible durations");
        }
        return new ProposalWritePlan(
                "predictive-map-blend-duration",
                "Predictive MAP Blend Duration",
                snapshot.getConfigurationName(),
                F0.format(rpm[pointIndex]) + " RPM — historical M6B write-contract fixture",
                Collections.singletonList(
                        ProposalWritePlan.Change.arrayCell(
                                AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES,
                                pointIndex,
                                values[pointIndex],
                                evaluation.proposedValue,
                                F0.format(rpm[pointIndex]) + " RPM",
                                "s")));
    }
}
