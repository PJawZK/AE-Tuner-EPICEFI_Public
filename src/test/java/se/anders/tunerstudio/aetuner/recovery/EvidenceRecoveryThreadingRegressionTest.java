package se.anders.tunerstudio.aetuner.recovery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Architecture regression: heavy Guided recovery serialization must stay off the EDT marshal. */
public final class EvidenceRecoveryThreadingRegressionTest {
    private EvidenceRecoveryThreadingRegressionTest() { }

    public static void main(String[] args) throws Exception {
        guidedRecoveryIsOutsideSwingMarshal();
        System.out.println("EvidenceRecoveryThreadingRegressionTest passed");
    }

    private static void guidedRecoveryIsOutsideSwingMarshal() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/recovery/EvidenceRecoveryManager.java")),
                StandardCharsets.UTF_8);
        int methodStart = source.indexOf("private EvidenceRecoverySnapshot captureSnapshot()");
        int methodEnd = source.indexOf("private static Path recoveryRoot()", methodStart);
        require(methodStart >= 0 && methodEnd > methodStart,
                "could not isolate EvidenceRecoveryManager.captureSnapshot");
        String method = source.substring(methodStart, methodEnd);

        int passiveRunnable = method.indexOf("Runnable capturePassive");
        int marshal = method.indexOf("SwingUtilities.invokeAndWait(capturePassive)");
        int guided = method.indexOf("guidedPanel.recoverySnapshot()");
        require(passiveRunnable >= 0 && marshal > passiveRunnable && guided > marshal,
                "Guided recovery serialization moved back inside/before the EDT passive-state marshal");

        String edtRunnable = method.substring(passiveRunnable, marshal);
        require(!edtRunnable.contains("guidedPanel.recoverySnapshot()"),
                "Guided report/CSV generation is still executed inside the Swing capture runnable");
        require(method.contains("passivePanel.recoverySnapshot()"),
                "passive Swing-owned recovery snapshot was accidentally removed from the EDT marshal");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
