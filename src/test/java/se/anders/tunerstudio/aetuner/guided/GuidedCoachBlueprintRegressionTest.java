package se.anders.tunerstudio.aetuner.guided;

import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;

/** Product regression for the provisional coaching-first foundation. */
public final class GuidedCoachBlueprintRegressionTest {
    private GuidedCoachBlueprintRegressionTest() { }

    public static void main(String[] args) {
        everyTaskHasAConcreteCoachProposal();
        detectorProposalMatchesCurrentProductDecision();
        temperatureTasksPlanFutureRuns();
        reviewUsesExplicitAbBeforeMemory();
        proposalPanelHasNoRootScrollingContract();
        System.out.println("GuidedCoachBlueprintRegressionTest passed");
    }

    private static void everyTaskHasAConcreteCoachProposal() {
        for (GuidedTuningRecipe recipe : GuidedTuningRecipe.values()) {
            GuidedCoachBlueprint b = GuidedCoachCatalog.forRecipe(recipe);
            require(b != null && b.recipe == recipe,
                    "missing/misrouted coach blueprint: " + recipe);
            require(nonEmpty(b.question) && nonEmpty(b.driverCue)
                            && nonEmpty(b.primaryVisual) && nonEmpty(b.audio)
                            && nonEmpty(b.evidence) && nonEmpty(b.review)
                            && nonEmpty(b.experiment) && nonEmpty(b.futureConditions),
                    "incomplete coach blueprint: " + recipe);
        }
    }

    private static void detectorProposalMatchesCurrentProductDecision() {
        GuidedCoachBlueprint b = GuidedCoachCatalog.forRecipe(
                GuidedTuningRecipe.ENGAGEMENT_DETECTION);
        String all = (b.question + " " + b.primaryVisual + " " + b.experiment
                + " " + b.futureConditions).toLowerCase();
        require(all.contains("tps movement")
                        && all.contains("accelthreshold")
                        && all.contains("delta window"),
                "detector proposal lost TPS movement / threshold / Delta Window focus");
        require(all.contains("fast callback") && all.contains("prerequisite")
                        && all.contains("200 hz"),
                "Fast Callback was not demoted to prerequisite/context information");
        require(all.contains("temporary research")
                        && !b.primaryVisual.toLowerCase().contains("five detector"),
                "temporary Engagement Model comparison leaked back into normal coaching visual");
    }

    private static void temperatureTasksPlanFutureRuns() {
        GuidedCoachBlueprint tps = GuidedCoachCatalog.forRecipe(
                GuidedTuningRecipe.TPS_AE_COMPENSATION);
        GuidedCoachBlueprint wall = GuidedCoachCatalog.forRecipe(
                GuidedTuningRecipe.WALL_WETTING_ADVANCED);
        String tpsFuture = tps.futureConditions.toLowerCase();
        String wallFuture = wall.futureConditions.toLowerCase();
        require(tpsFuture.contains("actual correction breakpoints")
                        && tpsFuture.contains("future")
                        && tpsFuture.contains("cold"),
                "TPS compensation does not tell the user how future temperature runs are planned");
        require(wallFuture.contains("actual correction axes")
                        && wallFuture.contains("future")
                        && wallFuture.contains("cold"),
                "Wall Wetting mapping lost future temperature-band planning");
    }

    private static void reviewUsesExplicitAbBeforeMemory() {
        GuidedCoachBlueprint finalReview = GuidedCoachCatalog.forRecipe(
                GuidedTuningRecipe.FINAL_SIMPLIFICATION);
        require(finalReview.review.contains("Primary authority: explicit same-protocol A/B session")
                        && finalReview.review.contains("same-calibration accumulated memory")
                        && finalReview.review.contains("reference only"),
                "final review no longer distinguishes direct A/B from accumulated/historical memory");
    }

    private static void proposalPanelHasNoRootScrollingContract() {
        GuidedCoachProposalPanel panel = new GuidedCoachProposalPanel();
        panel.updateRecipe(GuidedTuningRecipe.BLEND_DURATION);
        panel.setDriverView(true);
        require(!containsScrollPane(panel),
                "coaching proposal panel introduced a root/nested scroll pane");
        require(!panel.reviewVisibleForTest() && !panel.futureVisibleForTest(),
                "Driver View still exposes review/planning detail instead of eyes-up coaching");
        panel.setDriverView(false);
        require(panel.reviewVisibleForTest() && panel.futureVisibleForTest(),
                "non-driver view did not expose review/A-B/future-condition design detail");
    }

    private static boolean containsScrollPane(Component component) {
        if (component instanceof JScrollPane) return true;
        if (!(component instanceof Container)) return false;
        for (Component child : ((Container) component).getComponents()) {
            if (containsScrollPane(child)) return true;
        }
        return false;
    }

    private static boolean nonEmpty(String value) {
        return value != null && value.trim().length() > 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
