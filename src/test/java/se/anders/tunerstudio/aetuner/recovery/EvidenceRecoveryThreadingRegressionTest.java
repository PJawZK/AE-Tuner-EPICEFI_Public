package se.anders.tunerstudio.aetuner.recovery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Architecture regression for low-overhead, off-EDT evidence recovery. */
public final class EvidenceRecoveryThreadingRegressionTest {
    private EvidenceRecoveryThreadingRegressionTest() { }

    public static void main(String[] args) throws Exception {
        guidedRecoveryIsOutsideSwingMarshal();
        dirtyRecoveryIsCoalescedAndDuplicateWritesAreSkipped();
        System.out.println("EvidenceRecoveryThreadingRegressionTest passed");
    }

    private static String managerSource() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/recovery/EvidenceRecoveryManager.java")),
                StandardCharsets.UTF_8);
    }

    private static void guidedRecoveryIsOutsideSwingMarshal() throws Exception {
        String source = managerSource();
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

    private static void dirtyRecoveryIsCoalescedAndDuplicateWritesAreSkipped()
            throws Exception {
        String source = managerSource();
        require(source.contains("DIRTY_DELAY_SECONDS = 5L"),
                "continuous evidence recovery lost the five-second coalescing window");
        require(source.contains("if (dirtyFuture != null && !dirtyFuture.isDone()) return;"),
                "sample-cadence dirty requests can schedule overlapping recovery work");
        require(source.contains("passiveFingerprint(snapshot.passive)")
                        && source.contains("guidedFingerprint(snapshot.guided)"),
                "recovery payload deduplication fingerprints were removed");
        require(source.contains("force || fingerprint != passiveFingerprint")
                        && source.contains("force || fingerprint != guidedFingerprint"),
                "unchanged recovery payloads no longer skip atomic disk replacement");
        require(source.contains("boolean force = \"plugin close\".equals(reason)"),
                "final plugin close no longer forces the authoritative last recovery write");
        require(source.contains("Automatic recovery already up to date."),
                "deduplicated checkpoint does not report its no-write state");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
