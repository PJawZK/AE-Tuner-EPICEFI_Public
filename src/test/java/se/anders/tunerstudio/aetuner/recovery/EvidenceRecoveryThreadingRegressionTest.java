package se.anders.tunerstudio.aetuner.recovery;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.guided.GuidedCapturePanel;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
        busyWorkerClose(false, false);
        busyWorkerClose(true, false);
        busyWorkerClose(false, true);
        busyWorkerClose(false, false, true);
        hiddenCloseStartsFinalRecoveryWorker();
        failedSaveStillReleasesSession();
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

    private static void busyWorkerClose(boolean hideFirst, boolean interruptedCaller) throws Exception {
        busyWorkerClose(hideFirst, interruptedCaller, false);
    }

    private static void busyWorkerClose(boolean hideFirst, boolean interruptedCaller,
                                        boolean timeoutCaller) throws Exception {
        CloseFixture fixture = new CloseFixture(Files.createTempDirectory("ae-close-busy"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fixture.manager.resume();
        ScheduledExecutorService worker = fixture.worker();
        worker.submit(() -> {
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        try {
            require(entered.await(5, TimeUnit.SECONDS), "recovery blocker did not start");
            if (hideFirst) SwingUtilities.invokeAndWait(fixture.manager::flushAndClose);
            if (interruptedCaller) {
                Thread.currentThread().interrupt();
                try {
                    fixture.plugin.close();
                    require(Thread.currentThread().isInterrupted(), "close swallowed caller interruption");
                } finally {
                    Thread.interrupted();
                }
            } else if (timeoutCaller) {
                fixture.plugin.close();
            } else {
                SwingUtilities.invokeAndWait(fixture.plugin::close);
            }
            // close returned while the recovery worker is still blocked:
            // close did not wait on it, and the retained model must still exist.
            require(fixture.hasEvidence(), "EDT close cleared evidence before the final checkpoint");
        } finally {
            release.countDown();
            require(worker.awaitTermination(10, TimeUnit.SECONDS), "prior recovery did not finish");
            ScheduledExecutorService finalWorker = fixture.worker();
            if (finalWorker != null) require(finalWorker.awaitTermination(10, TimeUnit.SECONDS),
                    "final recovery did not finish");
        }
        fixture.assertSavedAndReleased();
    }

    private static void hiddenCloseStartsFinalRecoveryWorker() throws Exception {
        CloseFixture fixture = new CloseFixture(Files.createTempDirectory("ae-close-hidden"));
        require(fixture.worker() == null, "hidden initialize unexpectedly started recovery");
        SwingUtilities.invokeAndWait(fixture.plugin::close);
        ScheduledExecutorService worker = fixture.worker();
        require(worker != null && worker.awaitTermination(10, TimeUnit.SECONDS),
                "hidden close did not finish its final recovery checkpoint");
        fixture.assertSavedAndReleased();
    }

    private static void failedSaveStillReleasesSession() throws Exception {
        // A regular file cannot be used as the parent recovery directory.
        CloseFixture fixture = new CloseFixture(Files.createTempFile("ae-close-failure", ".tmp"));
        SwingUtilities.invokeAndWait(fixture.plugin::close);
        ScheduledExecutorService worker = fixture.worker();
        require(worker != null && worker.awaitTermination(10, TimeUnit.SECONDS),
                "failed recovery did not complete cleanup");
        require(!fixture.hasEvidence(), "save failure prevented session release");
        require(fixture.manager.statusText().startsWith("Automatic recovery failed:"),
                "save failure was not reported: " + fixture.manager.statusText());
    }

    private static final class CloseFixture {
        AeTunerPlugin plugin;
        GuidedCapturePanel panel;
        EvidenceRecoveryManager manager;
        Object probe;
        Method hasEvidence;
        String expectedCsv;

        CloseFixture(Path root) throws Exception {
            String previous = System.getProperty("ae.tuner.recovery.dir");
            System.setProperty("ae.tuner.recovery.dir", root.toString());
            try {
                SwingUtilities.invokeAndWait(() -> {
                    plugin = new AeTunerPlugin();
                    plugin.initialize(null);
                });
            } finally {
                if (previous == null) System.clearProperty("ae.tuner.recovery.dir");
                else System.setProperty("ae.tuner.recovery.dir", previous);
            }
            panel = (GuidedCapturePanel) field(plugin, "guidedPanel");
            manager = (EvidenceRecoveryManager) field(plugin, "recoveryManager");
            probe = field(panel, "probeSession");
            Class<?> type = probe.getClass();
            Method start = type.getDeclaredMethod("start", GuidedAeMethodModule.class);
            Method accept = type.getDeclaredMethod("accept", LiveSample.class);
            hasEvidence = type.getDeclaredMethod("hasEvidence");
            start.setAccessible(true);
            accept.setAccessible(true);
            hasEvidence.setAccessible(true);
            start.invoke(probe, GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.INSTANT_FUEL));
            EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
            for (ChannelRole role : ChannelRole.values()) values.put(role, 1.0);
            for (int i = 0; i < 3; i++) {
                double seconds = 1.0 + i * 0.01;
                accept.invoke(probe, new LiveSample(Math.round(seconds * 1e9), seconds, values, 0.0, 0.0));
            }
            EvidenceRecoverySnapshot.Guided snapshot = panel.recoverySnapshot();
            require(snapshot != null && snapshot.recordCount == 3, "fixture lost probe evidence");
            expectedCsv = snapshot.csvText;
        }

        boolean hasEvidence() throws Exception { return (Boolean) hasEvidence.invoke(probe); }

        ScheduledExecutorService worker() throws Exception {
            return (ScheduledExecutorService) field(manager, "executor");
        }

        void assertSavedAndReleased() throws Exception {
            Path saved = manager.activeRecoveryDirectory().resolve("guided-guided-probe-instant_fuel");
            String csv = new String(Files.readAllBytes(saved.resolve("guided-events-recovery.csv")),
                    StandardCharsets.UTF_8);
            String report = new String(Files.readAllBytes(saved.resolve("guided-report-recovery.txt")),
                    StandardCharsets.UTF_8);
            require(csv.equals(expectedCsv), "final recovery did not preserve the complete retained CSV");
            require(report.contains("Capture state: COMPLETE"), "final recovery missed capture termination");
            require(Files.exists(manager.activeRecoveryDirectory().resolve(EvidenceRecoveryStore.INFO)),
                    "final recovery did not save run metadata");
            require(!hasEvidence(), "session was not released after final recovery");
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
