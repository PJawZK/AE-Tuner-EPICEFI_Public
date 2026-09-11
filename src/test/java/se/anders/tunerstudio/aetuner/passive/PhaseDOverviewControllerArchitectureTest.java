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
        require(countOccurrences(source, "SessionReview.build(") == 1,
                "overview refresh reintroduced duplicate SessionReview event scans");
        require(!source.contains("countPredictionEvents(")
                        && !source.contains("countRepeatedResetEvents(")
                        && !source.contains("for (TransientEvent event : events)")
                        && !source.contains("for (TransientEvent summary : events)"),
                "overview controller reintroduced direct full-event refresh scans");
        require(source.contains("review.predictionEvents()")
                        && source.contains("review.repeatedResetEvents()")
                        && source.contains("review.wallActiveEvents()")
                        && source.contains("review.tpsAeFuelProvedEvents()"),
                "overview controller does not consume the revision-cached SessionReview summary");
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(token, at)) >= 0) {
            count++;
            at += token.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
