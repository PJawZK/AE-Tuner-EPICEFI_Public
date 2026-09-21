package se.anders.tunerstudio.aetuner.recovery;

import se.anders.tunerstudio.aetuner.guided.GuidedCapturePanel;
import se.anders.tunerstudio.aetuner.guided.RuntimePerformanceHub;
import se.anders.tunerstudio.aetuner.proposal.SessionExportSupport;

import javax.swing.SwingUtilities;
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

/**
 * Periodic local recovery for current Guided evidence only.
 *
 * All evidence scanning, report/CSV construction, hashing and disk I/O run on
 * the low-priority recovery worker. No recovery checkpoint synchronously enters
 * the Swing EDT, so long Guided sessions cannot periodically stall TunerStudio.
 */
public final class EvidenceRecoveryManager {
    private static final long PERIOD_SECONDS = 60L;
    private static final long DIRTY_DELAY_SECONDS = 5L;
    private static final int RETAIN_RUNS = 8;
    private static final long NON_EDT_CLOSE_WAIT_SECONDS = 3L;

    private final GuidedCapturePanel guidedPanel;
    private final EvidenceRecoveryStore store;
    private ScheduledExecutorService executor;
    private boolean cleanupScheduled;
    private final AtomicBoolean writing = new AtomicBoolean();
    private final Object scheduleLock = new Object();
    private ScheduledFuture<?> dirtyFuture;
    private volatile Path startupRecovery;
    private volatile String status = "Automatic recovery waiting for Guided evidence.";
    private volatile String guidedSession = "";
    private volatile int guidedRecordCount;
    private volatile long guidedFingerprint = Long.MIN_VALUE;

    public EvidenceRecoveryManager(GuidedCapturePanel guidedPanel) {
        this(guidedPanel, recoveryRoot());
    }

    public EvidenceRecoveryManager(GuidedCapturePanel guidedPanel, Path root) {
        if (guidedPanel == null) throw new IllegalArgumentException("guidedPanel");
        this.guidedPanel = guidedPanel;
        String runId = "run-" + new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.US).format(new Date())
                + "-" + Long.toHexString(System.nanoTime());
        this.store = new EvidenceRecoveryStore(root, runId);
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
                @Override public void run() { EvidenceRecoveryStore.cleanup(root, RETAIN_RUNS); }
            });
        }

        executor.scheduleWithFixedDelay(new Runnable() {
            @Override public void run() { checkpoint("periodic"); }
        }, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
        status = startupRecovery == null
                ? "Automatic recovery waiting for Guided evidence."
                : "Previous Guided recovery is ready; open or dismiss the notice.";
    }

    private ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "ae-tuner-guided-recovery");
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
                @Override public void run() { checkpoint("evidence changed"); }
            }, DIRTY_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void cancelDirtyCheckpoint() {
        synchronized (scheduleLock) {
            if (dirtyFuture != null && !dirtyFuture.isDone()) dirtyFuture.cancel(false);
            dirtyFuture = null;
        }
    }

    void checkpoint(String reason) {
        long started = System.nanoTime();
        EvidenceRecoverySnapshot snapshot;
        try {
            snapshot = captureSnapshot();
            RuntimePerformanceHub.noteRecoverySnapshot(elapsedMillis(started));
        } catch (Exception ex) {
            RuntimePerformanceHub.noteRecoverySnapshot(elapsedMillis(started));
            status = "Automatic recovery failed: " + safeMessage(ex);
            return;
        }
        writeSnapshot(snapshot, reason);
    }

    private void writeSnapshot(EvidenceRecoverySnapshot snapshot, String reason) {
        if (!writing.compareAndSet(false, true)) return;
        long started = System.nanoTime();
        try {
            if (snapshot == null || !snapshot.hasEvidence()) {
                status = "Automatic recovery waiting for Guided evidence.";
                return;
            }
            boolean force = "plugin close".equals(reason);
            boolean wrote = false;
            int guidedCount = guidedRecordCount;

            if (snapshot.guided != null) {
                boolean newSession = !snapshot.guided.sessionKey.equals(guidedSession);
                if (newSession) {
                    guidedSession = snapshot.guided.sessionKey;
                    guidedRecordCount = 0;
                    guidedFingerprint = Long.MIN_VALUE;
                }
                long fingerprint = guidedFingerprint(snapshot.guided);
                if (force || fingerprint != guidedFingerprint) {
                    store.writeGuided(snapshot.guided);
                    guidedFingerprint = fingerprint;
                    wrote = true;
                }
                guidedRecordCount = snapshot.guided.recordCount;
                guidedCount = guidedRecordCount;
            }

            if (wrote || force) {
                store.writeRunInfo(reason, guidedCount);
                status = "Automatic Guided recovery saved locally at "
                        + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            } else {
                status = "Automatic Guided recovery already up to date.";
            }
        } catch (Exception ex) {
            status = "Automatic recovery failed: " + safeMessage(ex);
        } finally {
            RuntimePerformanceHub.noteRecoveryWrite(elapsedMillis(started));
            writing.set(false);
        }
    }

    private static long guidedFingerprint(EvidenceRecoverySnapshot.Guided snapshot) {
        long hash = 1469598103934665603L;
        hash = mix(hash, snapshot.sessionKey);
        hash = mix(hash, snapshot.recordCount);
        hash = mix(hash, snapshot.reportText);
        hash = mix(hash, snapshot.csvText);
        return hash;
    }

    private static long mix(long hash, int value) {
        return (hash ^ value) * 1099511628211L;
    }

    private static long mix(long hash, String value) {
        return mix(hash, value == null ? 0 : value.hashCode());
    }

    /**
     * Hide/final close only schedules the final capture on the recovery worker.
     * If called on Swing EDT it returns immediately; a non-EDT final close may
     * wait briefly for the worker to finish.
     */
    public void flushAndClose() {
        final ScheduledExecutorService active;
        synchronized (this) { active = executor; }
        if (active == null || active.isShutdown()) return;

        cancelDirtyCheckpoint();
        active.submit(new Runnable() {
            @Override public void run() { checkpoint("plugin close"); }
        });
        active.shutdown();

        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                if (!active.awaitTermination(NON_EDT_CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
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

    public Path startupRecoveryDirectory() { return startupRecovery; }
    public Path activeRecoveryDirectory() { return store.runDirectory(); }
    public String statusText() { return status; }

    public void dismissStartupRecovery() {
        Path recovery = startupRecovery;
        if (recovery == null) return;
        try {
            EvidenceRecoveryStore.dismiss(recovery);
            startupRecovery = null;
        } catch (java.io.IOException ex) {
            status = "Could not dismiss recovery notice: " + safeMessage(ex);
        }
    }

    /** Called only on the recovery worker. No Swing state is read here. */
    private EvidenceRecoverySnapshot captureSnapshot() {
        return new EvidenceRecoverySnapshot(guidedPanel.recoverySnapshot());
    }

    private static Path recoveryRoot() {
        String override = System.getProperty("ae.tuner.recovery.dir");
        if (override != null && override.trim().length() > 0) {
            return Paths.get(override.trim());
        }
        return SessionExportSupport.lastSessionDirectory().toPath();
    }

    private static double elapsedMillis(long startedNano) {
        return Math.max(0L, System.nanoTime() - startedNano) / 1000000.0;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().length() == 0
                ? throwable == null ? "unknown error"
                : throwable.getClass().getSimpleName()
                : message.replace('\n', ' ').replace('\r', ' ');
    }
}
