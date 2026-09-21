package se.anders.tunerstudio.aetuner.recovery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Architecture regression for low-overhead, off-EDT Guided evidence recovery. */
public final class EvidenceRecoveryThreadingRegressionTest {
    private EvidenceRecoveryThreadingRegressionTest() { }

    public static void main(String[] args) throws Exception {
        guidedRecoveryNeverMarshalsIntoSwing();
        dirtyRecoveryIsCoalescedAndDuplicateWritesAreSkipped();
        recoveryWorkerOwnsSerializationAndIo();
        startupRecoveryNeedsNoFinalizationPass();
        System.out.println("EvidenceRecoveryThreadingRegressionTest passed");
    }

    private static String managerSource() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/recovery/EvidenceRecoveryManager.java")),
                StandardCharsets.UTF_8);
    }

    private static String storeSource() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/recovery/EvidenceRecoveryStore.java")),
                StandardCharsets.UTF_8);
    }

    private static void guidedRecoveryNeverMarshalsIntoSwing() throws Exception {
        String source = managerSource();
        require(source.contains("new EvidenceRecoverySnapshot(guidedPanel.recoverySnapshot())"),
                "Guided recovery no longer captures directly on the recovery worker");
        require(!source.contains("SwingUtilities.invokeAndWait")
                        && !source.contains("SwingUtilities.invokeLater"),
                "recovery reintroduced synchronous/asynchronous Swing marshaling");
        require(!source.contains("passivePanel")
                        && !source.contains("snapshot.passive")
                        && !source.contains("writePassive")
                        && !source.contains("passiveFingerprint"),
                "retired Passive recovery architecture reappeared");
    }

    private static void dirtyRecoveryIsCoalescedAndDuplicateWritesAreSkipped()
            throws Exception {
        String source = managerSource();
        require(source.contains("DIRTY_DELAY_SECONDS = 5L"),
                "continuous evidence recovery lost the five-second coalescing window");
        require(source.contains("if (dirtyFuture != null && !dirtyFuture.isDone()) return;"),
                "sample-cadence dirty requests can schedule overlapping recovery work");
        require(source.contains("guidedFingerprint(snapshot.guided)"),
                "Guided recovery payload deduplication fingerprint was removed");
        require(source.contains("force || fingerprint != guidedFingerprint"),
                "unchanged Guided recovery payloads no longer skip atomic disk replacement");
        require(source.contains("boolean force = \"plugin close\".equals(reason)"),
                "final plugin close no longer forces the authoritative last recovery write");
        require(source.contains("Automatic Guided recovery already up to date."),
                "deduplicated checkpoint does not report its no-write state");
    }

    private static void recoveryWorkerOwnsSerializationAndIo() throws Exception {
        String source = managerSource();
        require(source.contains("ae-tuner-guided-recovery"),
                "dedicated low-priority Guided recovery worker is missing");
        require(source.contains("thread.setPriority(Thread.MIN_PRIORITY)"),
                "Guided recovery worker lost low-priority scheduling");
        require(source.contains("store.writeGuided(snapshot.guided)"),
                "Guided persistence is no longer performed by EvidenceRecoveryManager worker flow");
        require(source.contains("RuntimePerformanceHub.noteRecoverySnapshot")
                        && source.contains("RuntimePerformanceHub.noteRecoveryWrite"),
                "bounded recovery timing diagnostics are no longer recorded");
        require(source.contains("if (!SwingUtilities.isEventDispatchThread())"),
                "close path no longer avoids blocking the Swing EDT");
    }

    private static void startupRecoveryNeedsNoFinalizationPass() throws Exception {
        String manager = managerSource();
        String store = storeSource();
        require(!manager.contains("startupFinalizationScheduled")
                        && !manager.contains("finalizeRun("),
                "obsolete startup finalization lifecycle returned to the manager");
        require(!store.contains("static void finalizeRun"),
                "no-op recovery finalizer returned to the store");
        require(manager.contains("Previous Guided recovery is ready; open or dismiss the notice."),
                "startup recovery no longer surfaces already-atomic Guided evidence directly");
        require(manager.contains("se.anders.tunerstudio.aetuner.proposal.SessionExportSupport")
                        || manager.contains("import se.anders.tunerstudio.aetuner.proposal.SessionExportSupport;"),
                "recovery no longer points directly at canonical SessionExportSupport");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
