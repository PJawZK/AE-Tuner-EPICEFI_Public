package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class PhaseDOverviewControllerArchitectureTest {
    private PhaseDOverviewControllerArchitectureTest() { }

    public static void main(String[] args) throws Exception {
        hostPanelDoesNotOwnOverviewEvaluation();
        controllerOwnsRecommendationAndReviewEvaluation();
        System.out.println("PhaseDOverviewControllerArchitectureTest passed");
    }

    private static void hostPanelDoesNotOwnOverviewEvaluation() throws Exception {
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/passive/AeTunerPanel.java");
        require(source.contains("PassiveOverviewController overviewController"),
                "AeTunerPanel does not own the presentation controller");
        require(source.contains("overviewController.refresh("),
                "refreshUi does not delegate to the presentation controller");
        String[] forbidden = new String[]{
                "private void refreshOverview()",
                "private int countRepeatedResetEvents()",
                "private String buildSessionModeText()",
                "private String buildSessionGuidanceText()",
                "OverviewTextRenderer.eventProgress(",
                "recommendationHistory.observe("
        };
        for (String token : forbidden) {
            require(!source.contains(token),
                    "AeTunerPanel still owns presentation token " + token);
        }
    }

    private static void controllerOwnsRecommendationAndReviewEvaluation() throws Exception {
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/passive/PassiveOverviewController.java");
        require(source.contains("OverviewTextRenderer.eventProgress(")
                        && source.contains("recommendationHistory.observe(")
                        && source.contains("SessionReview.build("),
                "overview controller does not own status/review/recommendation evaluation");
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
