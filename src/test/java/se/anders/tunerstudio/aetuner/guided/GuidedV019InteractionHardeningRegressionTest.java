package se.anders.tunerstudio.aetuner.guided;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Static architecture gate for the two video-confirmed v0.19 interaction failures. */
public final class GuidedV019InteractionHardeningRegressionTest {
    private GuidedV019InteractionHardeningRegressionTest() { }

    public static void main(String[] args) throws Exception {
        periodicRefreshNeverDisableCyclesActiveButton();
        completeLifecycleDoesNotImplyReviewReady();
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
