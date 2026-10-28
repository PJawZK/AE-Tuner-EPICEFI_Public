package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedWorkingTuneSurfaceCache;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** A retiring recommendation must not hold the working-tune monitor hostage on EDT. */
public final class RecommendationCacheConcurrencyRegressionTest {
    public static void main(String[] args) throws Exception {
        cleanupWaitingForRecommendationDoesNotBlockEdtReads();
        System.out.println("RecommendationCacheConcurrencyRegressionTest passed");
    }

    private static void cleanupWaitingForRecommendationDoesNotBlockEdtReads() throws Exception {
        AtomicReference<Thread> cleanerThread = new AtomicReference<Thread>();
        ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cache-retirement-test");
            thread.setDaemon(true);
            cleanerThread.set(thread);
            return thread;
        });
        CompletableFuture<String> edtRead = new CompletableFuture<String>();
        Future<?> cleanup;
        boolean responsive = false;
        try {
            synchronized (BaselineSurfaceRecommendation.class) {
                cleanup = worker.submit(GuidedWorkingTuneSurfaceCache::clear);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean blocked = false;
                while (!cleanup.isDone() && System.nanoTime() < deadline) {
                    Thread thread = cleanerThread.get();
                    if (thread != null && thread.getState() == Thread.State.BLOCKED) {
                        for (StackTraceElement frame : thread.getStackTrace()) {
                            if (frame.getClassName().equals(BaselineSurfaceRecommendation.class.getName())
                                    && frame.getMethodName().equals("clearCache")) blocked = true;
                        }
                    }
                    if (blocked) break;
                    Thread.yield();
                }
                require(blocked, "cleanup did not wait for the recommendation monitor");
                SwingUtilities.invokeLater(() -> edtRead.complete(GuidedWorkingTuneSurfaceCache.configurationName()));
                try { edtRead.get(2, TimeUnit.SECONDS); responsive = true; }
                catch (TimeoutException expectedOldLockInversion) { /* release before failing */ }
            }
            cleanup.get(5, TimeUnit.SECONDS);
            edtRead.get(5, TimeUnit.SECONDS);
            require(responsive, "cleanup retained working-tune lock while waiting for recommendation; EDT read blocked");
        } finally { worker.shutdownNow(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
