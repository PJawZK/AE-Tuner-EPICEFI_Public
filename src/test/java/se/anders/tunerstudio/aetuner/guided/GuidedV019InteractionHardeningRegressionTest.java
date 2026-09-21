package se.anders.tunerstudio.aetuner.guided;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Static architecture gate for the two video-confirmed v0.19 interaction failures. */
public final class GuidedV019InteractionHardeningRegressionTest {
    private GuidedV019InteractionHardeningRegressionTest() { }

    public static void main(String[] args) throws Exception {
        periodicRefreshNeverDisableCyclesActiveButton();
        activeLifecycleControlsUseSessionAuthority();
        completeLifecycleDoesNotImplyReviewReady();
        focusReadinessPresentationUsesEvidenceAuthority();
        System.out.println("GuidedV019InteractionHardeningRegressionTest passed");
    }

    private static String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019WorkspacePanel.java")),
                StandardCharsets.UTF_8);
    }

    private static void periodicRefreshNeverDisableCyclesActiveButton() throws Exception {
        String source = source();
        int start = source.indexOf("private void refreshButtons()");
        int end = source.indexOf("private boolean evidenceReady", start);
        require(start >= 0 && end > start, "could not isolate refreshButtons");
        String method = source.substring(start, end);
        require(!method.contains("button.setEnabled(false)"),
                "200 ms refresh again disables every button before recomputing authority");
        require(method.contains("if (button.isEnabled() != shouldEnable) button.setEnabled(shouldEnable)"),
                "active button state is not applied only when its desired value changes");
    }

    private static void activeLifecycleControlsUseSessionAuthority() throws Exception {
        String focus = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019FocusBase.java")),
                StandardCharsets.UTF_8);
        require(focus.contains("boolean active = capture.isCaptureInProgress();"),
                "Focus controls again recognize only CAPTURING/PAUSED instead of the whole live session");

        String availability = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedTaskAvailabilityAdapter.java")),
                StandardCharsets.UTF_8);
        require(availability.contains("if (liveState.isCaptureInProgress()"),
                "task navigation again stops owning Blend settling/outcome/re-arm states");
    }

    private static void completeLifecycleDoesNotImplyReviewReady() throws Exception {
        String source = source();
        require(source.contains("bridge.evidenceReady(task)"),
                "workspace no longer delegates evidence quality to production session authority");
        require(!source.contains("return state == GuidedCaptureState.COMPLETE\n                ||"),
                "COMPLETE lifecycle state again became a blanket evidence-ready shortcut");
        require(source.contains("Capture ended — more evidence needed")
                        && source.contains("Continue Capture"),
                "incomplete stopped capture lost its explicit continue-capture presentation");
    }


    private static void focusReadinessPresentationUsesEvidenceAuthority() throws Exception {
        String[] paths = new String[]{
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019TpsAeFocus.java",
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019WallWettingFocus.java",
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019InstantFuelFocus.java",
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019BoundEvidenceFocus.java"
        };
        for (String path : paths) {
            String focus = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            require(focus.contains("GuidedFocusHub.isActiveEvidenceReviewReady()"),
                    path + " does not use production evidence-readiness authority for its READY presentation");
            require(!focus.contains("review.setEnabled(complete)"),
                    path + " again gives the Focus subclass its own COMPLETE-only Review authority");
            require(focus.contains("MORE EVIDENCE NEEDED"),
                    path + " does not visibly distinguish stopped incomplete evidence from review-ready evidence");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
