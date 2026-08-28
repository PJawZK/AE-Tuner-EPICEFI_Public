package se.anders.tunerstudio.aetuner.recovery;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodic local evidence recovery without touching ECU state.
 *
 * Recovery is deliberately outside the host/controller critical path. Disk
 * retention work runs only on the low-priority worker, and final close never
 * waits on a worker that can in turn wait for the Swing EDT.
 */
public final class EvidenceRecoveryManager {
    private static final long PERIOD_SECONDS = 60L;
    private static final long DIRTY_DELAY_SECONDS = 2L;
    private static final int RETAIN_RUNS = 8;
    private static final long NON_EDT_CLOSE_WAIT_SECONDS = 3L;

    private final AeTunerPanel passivePanel;
    private final GuidedCapturePanel guidedPanel;
    private final EvidenceRecoveryStore store;
    private ScheduledExecutorService executor;
    private boolean startupFinalizationScheduled;
    private boolean cleanupScheduled;
    private final AtomicBoolean writing = new AtomicBoolean();
    private final Object scheduleLock = new Object();
    private ScheduledFuture<?> dirtyFuture;
    private volatile Path startupRecovery;
    private volatile String status = "Automatic recovery waiting for evidence.";
    private volatile String passiveSession = "";
    private volatile int passiveEventCount;
    private volatile String guidedSession = "";
    private volatile int guidedRecordCount;

    public EvidenceRecoveryManager(AeTunerPanel passivePanel,
                            GuidedCapturePanel guidedPanel) {
        this(passivePanel, guidedPanel, recoveryRoot());
    }

    public EvidenceRecoveryManager(AeTunerPanel passivePanel,
                            GuidedCapturePanel guidedPanel,
                            Path root) {
        this.passivePanel = passivePanel;
        this.guidedPanel = guidedPanel;
        String runId = "run-" + new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.US).format(new Date())
                + "-" + Long.toHexString(System.nanoTime());
        this.store = new EvidenceRecoveryStore(root, runId);
        // Discovery is intentionally read-only. Potentially expensive retention
        // deletion is deferred to the worker in resume().
        this.startupRecovery = EvidenceRecoveryStore.newestUndismissedRecovery(
                root, store.runDirectory());
    }

    public synchronized void resume() {
        if (executor != null && !executor.isShutdown()) return;
        executor = newExecutor();

        if (!cleanupScheduled) {
            cleanupScheduled = true;
            final Path root = store.root();
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    EvidenceRecoveryStore.cleanup(root, RETAIN_RUNS);
                }
            });
        }

        if (startupRecovery != null && !startupFinalizationScheduled) {
            startupFinalizationScheduled = true;
            final Path recovered = startupRecovery;
            status = "Finalizing evidence recovered from the previous plugin session.";
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        EvidenceRecoveryStore.finalizeRun(recovered);
                        status = "Previous recovery is ready; open or dismiss the notice.";
                    } catch (IOException ex) {
                        status = "Previous recovery finalization failed: "
                                + safeMessage(ex);
                    }
                }
            });
        }

        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                checkpoint("periodic");
            }
        }, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
        if (startupRecovery == null) {
            status = "Automatic recovery waiting for evidence.";
        }
    }

    private ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "ae-tuner-evidence-recovery");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        });
    }

    public void requestCheckpoint() {
        synchronized (scheduleLock) {
            ScheduledExecutorService active = executor;
            if (active == null || active.isShutdown()) return;
            if (dirtyFuture != null && !dirtyFuture.isDone()) return;
            dirtyFuture = active.schedule(new Runnable() {
                @Override
                public void run() {
                    checkpoint("evidence changed");
                }
            }, DIRTY_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void cancelDirtyCheckpoint() {
        synchronized (scheduleLock) {
            if (dirtyFuture != null && !dirtyFuture.isDone()) {
                dirtyFuture.cancel(false);
            }
            dirtyFuture = null;
        }
    }

    void checkpoint(String reason) {
        EvidenceRecoverySnapshot snapshot;
        try {
            snapshot = captureSnapshot();
        } catch (Exception ex) {
            status = "Automatic recovery failed: " + safeMessage(ex);
            return;
        }
        writeSnapshot(snapshot, reason);
    }

    private void writeSnapshot(EvidenceRecoverySnapshot snapshot, String reason) {
        if (!writing.compareAndSet(false, true)) return;
        try {
            if (snapshot == null || !snapshot.hasEvidence()) {
                status = "Automatic recovery waiting for evidence.";
                return;
            }
            int passiveCount = 0;
            int guidedCount = 0;
            if (snapshot.passive != null) {
                if (!snapshot.passive.sessionKey.equals(passiveSession)) {
                    passiveSession = snapshot.passive.sessionKey;
                    passiveEventCount = 0;
                }
                store.writePassive(snapshot.passive, passiveEventCount);
                passiveEventCount = snapshot.passive.events.size();
                passiveCount = passiveEventCount;
            }
            if (snapshot.guided != null) {
                if (!snapshot.guided.sessionKey.equals(guidedSession)) {
                    guidedSession = snapshot.guided.sessionKey;
                    guidedRecordCount = 0;
                }
                store.writeGuided(snapshot.guided);
                guidedRecordCount = snapshot.guided.recordCount;
                guidedCount = guidedRecordCount;
            }
            store.writeRunInfo(reason, passiveCount, guidedCount);
            status = "Automatic recovery saved locally at "
                    + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        } catch (Exception ex) {
            status = "Automatic recovery failed: " + safeMessage(ex);
        } finally {
            writing.set(false);
        }
    }

    /**
     * Final recovery shutdown.
     *
     * The old vehicle-test.9 path could leave a two-second dirty checkpoint in
     * the scheduled executor, then block the Swing EDT in awaitTermination().
     * That dirty task used invokeAndWait() to get back to the same EDT, forming
     * a wait cycle until the 12-second timeout. We cancel the deferred task
     * first, capture the final model snapshot before shutdown, and never block
     * the EDT waiting for the worker.
     */
    public void flushAndClose() {
        final ScheduledExecutorService active;
        synchronized (this) {
            active = executor;
        }
        if (active == null || active.isShutdown()) return;

        cancelDirtyCheckpoint();

        EvidenceRecoverySnapshot finalSnapshot = null;
        try {
            finalSnapshot = captureSnapshot();
        } catch (Exception ex) {
            status = "Close-time recovery capture failed: " + safeMessage(ex);
        }
        final EvidenceRecoverySnapshot captured = finalSnapshot;
        active.submit(new Runnable() {
            @Override
            public void run() {
                writeSnapshot(captured, "plugin close");
                try {
                    store.finalizePassiveCsvFiles();
                } catch (IOException ex) {
                    status = "Recovery finalization failed: " + safeMessage(ex);
                }
            }
        });
        active.shutdown();

        // ApplicationPlugin.close() and Swing hierarchy callbacks can run on
        // the EDT. Never wait there: the recovery worker must be free to finish
        // without creating an EDT<->worker dependency.
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                if (!active.awaitTermination(
                        NON_EDT_CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    active.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                active.shutdownNow();
            }
        }

        synchronized (this) {
            if (executor == active) executor = null;
        }
    }

    public synchronized boolean isRunningForTest() {
        return executor != null && !executor.isShutdown();
    }

    public Path startupRecoveryDirectory() {
        return startupRecovery;
    }

    public Path activeRecoveryDirectory() {
        return store.runDirectory();
    }

    public String statusText() {
        return status;
    }

    public void dismissStartupRecovery() {
        Path recovery = startupRecovery;
        if (recovery == null) return;
        try {
            EvidenceRecoveryStore.dismiss(recovery);
            startupRecovery = null;
        } catch (IOException ex) {
            status = "Could not dismiss recovery notice: " + safeMessage(ex);
        }
    }

    private EvidenceRecoverySnapshot captureSnapshot() throws Exception {
        final AtomicReference<EvidenceRecoverySnapshot> result =
                new AtomicReference<EvidenceRecoverySnapshot>();
        final AtomicReference<RuntimeException> failure =
                new AtomicReference<RuntimeException>();
        Runnable capture = new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(new EvidenceRecoverySnapshot(
                            passivePanel.recoverySnapshot(),
                            guidedPanel.recoverySnapshot()));
                } catch (RuntimeException ex) {
                    failure.set(ex);
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            capture.run();
        } else {
            SwingUtilities.invokeAndWait(capture);
        }
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    private static Path recoveryRoot() {
        String override = System.getProperty("ae.tuner.recovery.dir");
        if (override != null && override.trim().length() > 0) {
            return Paths.get(override.trim());
        }
        return SessionExportSupport.lastSessionDirectory().toPath();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().length() == 0
                ? throwable == null ? "unknown error"
                : throwable.getClass().getSimpleName()
                : message.replace('\n', ' ').replace('\r', ' ');
    }
}
