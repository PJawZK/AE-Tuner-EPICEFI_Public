package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.EnumMap;

/**
 * Pins the first production use of the physically validated broad write surface:
 * one evidence-reviewed TPS AE proposal may contain several table cells and is
 * still unavailable until Finish/Review.
 */
public final class GuidedTpsAeMultiCellProposalRegressionTest {
    private GuidedTpsAeMultiCellProposalRegressionTest() { }

    public static void main(String[] args) {
        reviewedTpsAeDraftBecomesOneMultiCellPlan();
        System.out.println("GuidedTpsAeMultiCellProposalRegressionTest passed");
    }

    private static void reviewedTpsAeDraftBecomesOneMultiCellPlan() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.TPS_AE);
        session.start(module, tpsSnapshot(), 3, 20, 115.0);

        addTpsAeEvent(session, 10.00, 20.0);
        addTpsAeEvent(session, 11.40, 20.0);
        addTpsAeEvent(session, 12.80, 20.0);

        require(session.reviewedWritePlan() == null,
                "TPS AE exposed a write plan before Finish/Review");
        session.finish();

        ProposalWritePlan plan = session.reviewedWritePlan();
        require(plan != null,
                "reviewed TPS AE draft did not expose the guarded write plan");
        require(plan.changeCount() > 1,
                "TPS AE remained artificially limited to one changed cell per reviewed plan");
        require("tps-test".equals(plan.getConfigurationName()),
                "TPS AE plan lost the current working-tune configuration identity");

        int previous = -1;
        for (ProposalWritePlan.Change change : plan.getChanges()) {
            require(change.kind == ProposalWritePlan.Kind.ARRAY_CELL,
                    "TPS AE reviewed plan contains a non-table change");
            require(AeParameterNames.TPS_AE_CYCLE_VALUES.equals(change.parameterName),
                    "TPS AE reviewed plan escaped the evidence-derived fuel table surface: "
                            + change.parameterName);
            require(change.flatIndex > previous,
                    "TPS AE changed-cell plan is not stable row-major order");
            require(change.proposedValue != change.expectedValue,
                    "TPS AE reviewed plan contains a no-op cell");
            previous = change.flatIndex;
        }

        String review = session.reviewText();
        require(review.contains("TPS AE TABLE REVIEW")
                        && review.contains("Guarded working-tune Apply/readback/Restore is available")
                        && review.contains("multi-cell Apply/Restore"),
                "TPS AE review does not visibly expose its guarded multi-cell Apply capability");
        require(session.copyPasteBlock().length() > 0,
                "promoting TPS AE Apply removed the independent Copy/Export path");
    }

    private static void addTpsAeEvent(GuidedMethodProbeSession session,
                                      double baseSeconds, double tpsTo) {
        session.accept(tpsAeSample(baseSeconds, 8.0, tpsTo, 0.0, 1.00, false));
        session.accept(tpsAeSample(baseSeconds + 0.05, 14.0, tpsTo, 2.0, 1.18, true));
        session.accept(tpsAeSample(baseSeconds + 0.10, 18.0, tpsTo, 4.0, 1.16, true));
        session.accept(tpsAeSample(baseSeconds + 0.20, 20.0, tpsTo, 6.0, 1.02, true));
        session.accept(tpsAeSample(baseSeconds + 0.30, 20.0, tpsTo, 10.0, 1.02, true));
        session.accept(tpsAeSample(baseSeconds + 0.45, 20.0, tpsTo, 12.0, 1.01, false));
        session.accept(tpsAeSample(baseSeconds + 0.70, 20.0, tpsTo, 12.0, 1.01, false));
        session.accept(tpsAeSample(baseSeconds + 0.90, 20.0, tpsTo, 12.0, 1.01, false));
    }

    private static AeProjectSnapshot tpsSnapshot() {
        return new AeProjectSnapshot(
                "tps-test",
                new double[]{2.0, 4.0, 6.0, 10.0, 12.0},
                new double[]{10.0, 20.0, 30.0},
                new double[][]{
                        {1.00, 1.00, 0.90, 0.70, 0.50},
                        {1.00, 1.00, 0.90, 0.70, 0.50},
                        {1.00, 1.00, 0.90, 0.70, 0.50}
                },
                new double[]{1000.0, 4000.0}, new double[]{1.0, 1.5},
                0.0, 0.0, new double[0], new double[0],
                true, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static LiveSample tpsAeSample(double seconds, double tps,
                                          double tpsTo, double cycle,
                                          double lambda, boolean active) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2500.0);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, active ? 75.0 : 50.0);
        values.put(ChannelRole.LAMBDA, lambda);
        values.put(ChannelRole.TARGET_LAMBDA, 1.0);
        values.put(ChannelRole.PW, active ? 4.5 : 3.0);
        values.put(ChannelRole.TPS_FROM, 8.0);
        values.put(ChannelRole.TPS_TO, tpsTo);
        values.put(ChannelRole.DELTA_TPS, active ? tpsTo - 8.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, active ? 2.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, active ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ADD_MS, active ? 0.80 : 0.0);
        values.put(ChannelRole.EXTRA_FUEL, 0.0);
        values.put(ChannelRole.TPS_AE_CYCLE_MULT, active ? 1.0 : 0.0);
        values.put(ChannelRole.TPS_AE_CYCLE_CNT, cycle);
        values.put(ChannelRole.WALL_WETTING_PW, 0.0);
        values.put(ChannelRole.INSTANT_PULSE_PW, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.FALLBACK_MAP, active ? 75.0 : 50.0);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.COOLANT, 80.0);
        values.put(ChannelRole.IAT, 25.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, active ? 80.0 : 0.0, active ? 100.0 : 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
