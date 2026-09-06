package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationTargets;
import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.host.AeTuningParameterCatalog;
import se.anders.tunerstudio.aetuner.host.GuidedControllerSettingInventory;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.List;

public final class GuidedTaskSettingsDraftRegressionTest {
    private static final String CFG = "Main Controller";

    private GuidedTaskSettingsDraftRegressionTest() { }

    public static void main(String[] args) throws Exception {
        plannedRecommendationTaskStillBuildsBroadReviewedPlan();
        multiBinAxisEditMustPreserveLiveOrdering();
        System.out.println("GuidedTaskSettingsDraftRegressionTest passed");
    }

    private static void plannedRecommendationTaskStillBuildsBroadReviewedPlan()
            throws Exception {
        GuidedControllerSettingInventory.TaskInventory inventory =
                GuidedControllerSettingInventory.find(GuidedTuningRecipe.INSTANT_FUEL_SETUP);
        require(inventory != null
                        && inventory.getProductionWriteSupport()
                        == GuidedControllerSettingInventory.ProductionWriteSupport.CURRENT,
                "Instant Fuel Setup lost current production write support");
        require(inventory.getRecommendationSupport()
                        == GuidedControllerSettingInventory.RecommendationSupport.PLANNED,
                "fixture no longer exercises a task whose recommendation logic is still planned");

        GuidedTaskSettingsDraft draft = GuidedTaskSettingsDraft.capture(
                new GuidedTaskSettingsDraft.ValueReader() {
                    @Override
                    public double read(String configurationName,
                                       AeApplyRestoreValidationTargets.Target target) {
                        return initialValue(target);
                    }
                }, CFG, GuidedTuningRecipe.INSTANT_FUEL_SETUP);

        require(draft.getEntries().size() == 3,
                "Instant Fuel Setup should expose all three validated physical settings at once");
        int changed = 0;
        for (GuidedTaskSettingsDraft.Entry entry : draft.getEntries()) {
            Double next = alternateValue(entry.getTarget(), entry.getOriginalValue());
            if (next == null) continue;
            draft.setProposedValue(entry.getTarget().identity(), next.doubleValue());
            changed++;
            if (changed == 2) break;
        }
        require(changed == 2,
                "test fixture could not stage two independent validated task settings");

        ProposalWritePlan plan = draft.buildPlan();
        require(plan != null && plan.changeCount() == 2,
                "broad task settings draft did not build one two-change ProposalWritePlan");
        require(CFG.equals(plan.getConfigurationName()),
                "task settings plan lost the captured working-tune configuration name");
        require(plan.getContext().contains("2 changed value(s) across 2 controller parameter(s)"),
                "task settings plan did not report its multi-parameter scope");
    }

    private static void multiBinAxisEditMustPreserveLiveOrdering() throws Exception {
        GuidedTaskSettingsDraft draft = GuidedTaskSettingsDraft.capture(
                new GuidedTaskSettingsDraft.ValueReader() {
                    @Override
                    public double read(String configurationName,
                                       AeApplyRestoreValidationTargets.Target target) {
                        AeTuningParameterCatalog.Parameter parameter = target.getParameter();
                        if (parameter.getShape() == AeTuningParameterCatalog.Shape.CURVE_AXIS) {
                            AeControllerDefinitionCatalog.Definition d = target.getDefinition();
                            double step = Math.max(d.getScale(), GuidedTaskSettingsDraft.editorStep(d));
                            return d.getMinimum() + (target.getFlatIndex() + 1) * step;
                        }
                        return initialValue(target);
                    }
                }, CFG, GuidedTuningRecipe.FOUNDATION_THRESHOLD);

        List<GuidedTaskSettingsDraft.Entry> axis = draft.entriesForController(
                AeParameterNames.DELTA_TPS_AVERAGE_CURVE_RPM_BINS);
        require(axis.size() >= 3,
                "threshold smoothing RPM axis did not expose enough bins for ordering regression");
        draft.setProposedValue(axis.get(1).getTarget().identity(),
                axis.get(0).getOriginalValue());
        requireThrows(() -> draft.buildPlan(),
                "task settings draft accepted a non-monotonic multi-bin curve axis");
    }

    private static double initialValue(AeApplyRestoreValidationTargets.Target target) {
        AeControllerDefinitionCatalog.Definition d = target.getDefinition();
        if (d.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
            String[] labels = d.getOptionLabels();
            for (int i = 0; i < labels.length; i++) {
                String label = labels[i] == null ? "" : labels[i].trim();
                if (label.length() > 0 && !"INVALID".equalsIgnoreCase(label)) return i;
            }
            throw new IllegalStateException("no valid option for " + target.identity());
        }
        return d.getMinimum();
    }

    private static Double alternateValue(AeApplyRestoreValidationTargets.Target target,
                                         double original) {
        AeControllerDefinitionCatalog.Definition d = target.getDefinition();
        if (d.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
            String[] labels = d.getOptionLabels();
            int current = (int) Math.rint(original);
            for (int i = 0; i < labels.length; i++) {
                String label = labels[i] == null ? "" : labels[i].trim();
                if (i != current && label.length() > 0
                        && !"INVALID".equalsIgnoreCase(label)) return Double.valueOf(i);
            }
            return null;
        }
        double step = GuidedTaskSettingsDraft.editorStep(d);
        double next = original + step;
        return next <= d.getMaximum() + 0.000001 ? Double.valueOf(next) : null;
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
