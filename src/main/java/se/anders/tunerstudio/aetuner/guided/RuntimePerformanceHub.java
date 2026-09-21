package se.anders.tunerstudio.aetuner.guided;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded, allocation-conscious runtime diagnostics for the current Guided
 * system. It records coarse five-second samples, not ECU-rate data, so the
 * diagnostic itself cannot become an unbounded long-session workload.
 */
public final class RuntimePerformanceHub {
    private static final long SAMPLE_INTERVAL_NS = 5000000000L;
    private static final long EXPECTED_EDT_HEARTBEAT_NS = 250000000L;
    private static final int MAX_SAMPLES = 240; // 20 minutes at 5 s cadence
    private static final ArrayDeque<Sample> samples = new ArrayDeque<Sample>();

    private static long startedNano = System.nanoTime();
    private static long lastSampleNano;
    private static long lastEdtHeartbeatNano;
    private static double maxEdtLagMillis;
    private static double recentEdtLagMillis;

    private static int dialogsCreated;
    private static int dialogsDisposed;
    private static int dialogsLive;
    private static int dialogsHighWater;

    private static long blendSnapshotBuilds;
    private static long blendSnapshotBuildNanos;
    private static long blendFocusBuilds;
    private static long blendFocusBuildNanos;

    private static double lastRecoverySnapshotMillis;
    private static double maxRecoverySnapshotMillis;
    private static double lastRecoveryWriteMillis;
    private static double maxRecoveryWriteMillis;
    private static long recoveryCheckpoints;

    private static int guidedEvidenceRecords;
    private static int guidedProbeSamples;

    private static int dispatcherQueue;
    private static int dispatcherHighWater;
    private static long dispatcherOffered;
    private static long dispatcherDelivered;
    private static long dispatcherCoalesced;
    private static long dispatcherDropped;
    private static long dispatcherCriticalDropped;

    private static double liveSampleRateHz;
    private static long liveFrames;
    private static long liveIncompleteFrames;

    private RuntimePerformanceHub() { }

    /** Call from the normal 250 ms Swing heartbeat. */
    public static synchronized void edtHeartbeat() {
        long now = System.nanoTime();
        if (lastEdtHeartbeatNano > 0L) {
            long interval = Math.max(0L, now - lastEdtHeartbeatNano);
            double lag = Math.max(0L, interval - EXPECTED_EDT_HEARTBEAT_NS) / 1000000.0;
            recentEdtLagMillis = lag;
            maxEdtLagMillis = Math.max(maxEdtLagMillis, lag);
        }
        lastEdtHeartbeatNano = now;
        maybeSample(now);
    }

    public static synchronized void noteDialogCreated() {
        dialogsCreated++;
        dialogsLive++;
        dialogsHighWater = Math.max(dialogsHighWater, dialogsLive);
    }

    public static synchronized void noteDialogDisposed() {
        dialogsDisposed++;
        dialogsLive = Math.max(0, dialogsLive - 1);
    }

    public static synchronized void noteBlendSnapshotBuild(long elapsedNanos) {
        blendSnapshotBuilds++;
        blendSnapshotBuildNanos += Math.max(0L, elapsedNanos);
    }

    public static synchronized void noteBlendFocusBuild(long elapsedNanos) {
        blendFocusBuilds++;
        blendFocusBuildNanos += Math.max(0L, elapsedNanos);
    }

    public static synchronized void noteRecoverySnapshot(double millis) {
        if (!Double.isFinite(millis)) return;
        lastRecoverySnapshotMillis = Math.max(0.0, millis);
        maxRecoverySnapshotMillis = Math.max(maxRecoverySnapshotMillis, lastRecoverySnapshotMillis);
    }

    public static synchronized void noteRecoveryWrite(double millis) {
        if (!Double.isFinite(millis)) return;
        lastRecoveryWriteMillis = Math.max(0.0, millis);
        maxRecoveryWriteMillis = Math.max(maxRecoveryWriteMillis, lastRecoveryWriteMillis);
        recoveryCheckpoints++;
    }

    public static synchronized void noteEvidenceSizes(int records, int probeSamples) {
        guidedEvidenceRecords = Math.max(0, records);
        guidedProbeSamples = Math.max(0, probeSamples);
    }

    public static synchronized void noteDispatcher(GuidedSampleDispatcher.RuntimeStats stats) {
        if (stats == null) return;
        dispatcherQueue = stats.queueDepth;
        dispatcherHighWater = Math.max(dispatcherHighWater, stats.highWaterMark);
        dispatcherOffered = stats.offered;
        dispatcherDelivered = stats.delivered;
        dispatcherCoalesced = stats.coalesced;
        dispatcherDropped = stats.dropped;
        dispatcherCriticalDropped = stats.criticalDropped;
    }

    public static synchronized void noteLiveSource(GuidedLiveSampleSource.RuntimeStats stats) {
        if (stats == null) return;
        liveSampleRateHz = stats.sampleRateHz;
        liveFrames = stats.emittedFrames;
        liveIncompleteFrames = stats.incompleteFrames;
    }

    public static synchronized String reportText() {
        maybeSample(System.nanoTime());
        Sample latest = samples.peekLast();
        StringBuilder out = new StringBuilder();
        out.append("RUNTIME PERFORMANCE\n")
                .append("===================\n")
                .append("Recorder samples retained: ").append(samples.size())
                .append("/").append(MAX_SAMPLES).append(" (5 s cadence)\n")
                .append("Uptime observed: ").append(f1((System.nanoTime() - startedNano) / 1.0e9)).append(" s\n")
                .append("EDT lag: recent ").append(f1(recentEdtLagMillis))
                .append(" ms | max ").append(f1(maxEdtLagMillis)).append(" ms\n")
                .append("Dialogs: created ").append(dialogsCreated)
                .append(" | disposed ").append(dialogsDisposed)
                .append(" | live ").append(dialogsLive)
                .append(" | high-water ").append(dialogsHighWater).append('\n')
                .append("Guided dispatcher: queue ").append(dispatcherQueue)
                .append(" | high-water ").append(dispatcherHighWater)
                .append(" | offered ").append(dispatcherOffered)
                .append(" | delivered ").append(dispatcherDelivered)
                .append(" | coalesced ").append(dispatcherCoalesced)
                .append(" | dropped ").append(dispatcherDropped)
                .append(" | critical dropped ").append(dispatcherCriticalDropped).append('\n')
                .append("Guided live source: ").append(f1(liveSampleRateHz)).append(" Hz")
                .append(" | frames ").append(liveFrames)
                .append(" | incomplete ").append(liveIncompleteFrames).append('\n')
                .append("Guided evidence: Blend records ").append(guidedEvidenceRecords)
                .append(" | probe retained samples ").append(guidedProbeSamples).append('\n')
                .append("Blend summary builds: ").append(blendSnapshotBuilds)
                .append(" | cumulative ").append(f1(blendSnapshotBuildNanos / 1.0e6)).append(" ms\n")
                .append("Blend Focus builds: ").append(blendFocusBuilds)
                .append(" | cumulative ").append(f1(blendFocusBuildNanos / 1.0e6)).append(" ms\n")
                .append("Recovery: checkpoints ").append(recoveryCheckpoints)
                .append(" | snapshot last/max ").append(f1(lastRecoverySnapshotMillis))
                .append("/").append(f1(maxRecoverySnapshotMillis)).append(" ms")
                .append(" | write last/max ").append(f1(lastRecoveryWriteMillis))
                .append("/").append(f1(maxRecoveryWriteMillis)).append(" ms\n");
        if (latest != null) {
            out.append("JVM latest: heap used ").append(latest.heapUsedMiB).append(" MiB")
                    .append(" / committed ").append(latest.heapCommittedMiB).append(" MiB")
                    .append(" / max ").append(latest.heapMaxMiB).append(" MiB")
                    .append(" | GC count/time ").append(latest.gcCount).append("/")
                    .append(latest.gcTimeMillis).append(" ms")
                    .append(" | threads ").append(latest.threadCount).append('\n');
        }
        out.append("Bounded recorder: oldest samples are discarded automatically; no ECU-rate history is retained.");
        return out.toString();
    }

    public static synchronized String csvText() {
        maybeSample(System.nanoTime());
        StringBuilder out = new StringBuilder();
        out.append("elapsed_s,heap_used_mib,heap_committed_mib,heap_max_mib,gc_count,gc_time_ms,threads,")
                .append("edt_recent_lag_ms,edt_max_lag_ms,dispatcher_queue,dispatcher_high_water,")
                .append("dispatcher_dropped,dispatcher_critical_dropped,dialogs_live,dialogs_high_water,")
                .append("live_sample_hz,live_frames,live_incomplete,evidence_records,probe_samples,\n");
        for (Sample sample : samples) {
            out.append(f3(sample.elapsedSeconds)).append(',')
                    .append(sample.heapUsedMiB).append(',')
                    .append(sample.heapCommittedMiB).append(',')
                    .append(sample.heapMaxMiB).append(',')
                    .append(sample.gcCount).append(',')
                    .append(sample.gcTimeMillis).append(',')
                    .append(sample.threadCount).append(',')
                    .append(f3(sample.edtRecentLagMillis)).append(',')
                    .append(f3(sample.edtMaxLagMillis)).append(',')
                    .append(sample.dispatcherQueue).append(',')
                    .append(sample.dispatcherHighWater).append(',')
                    .append(sample.dispatcherDropped).append(',')
                    .append(sample.dispatcherCriticalDropped).append(',')
                    .append(sample.dialogsLive).append(',')
                    .append(sample.dialogsHighWater).append(',')
                    .append(f3(sample.liveSampleRateHz)).append(',')
                    .append(sample.liveFrames).append(',')
                    .append(sample.liveIncompleteFrames).append(',')
                    .append(sample.evidenceRecords).append(',')
                    .append(sample.probeSamples).append(',').append('\n');
        }
        return out.toString();
    }

    public static synchronized void resetForPluginRetirement() {
        samples.clear();
        startedNano = System.nanoTime();
        lastSampleNano = 0L;
        lastEdtHeartbeatNano = 0L;
        maxEdtLagMillis = 0.0;
        recentEdtLagMillis = 0.0;
        dialogsCreated = dialogsDisposed = dialogsLive = dialogsHighWater = 0;
        blendSnapshotBuilds = blendSnapshotBuildNanos = 0L;
        blendFocusBuilds = blendFocusBuildNanos = 0L;
        lastRecoverySnapshotMillis = maxRecoverySnapshotMillis = 0.0;
        lastRecoveryWriteMillis = maxRecoveryWriteMillis = 0.0;
        recoveryCheckpoints = 0L;
        guidedEvidenceRecords = guidedProbeSamples = 0;
        dispatcherQueue = dispatcherHighWater = 0;
        dispatcherOffered = dispatcherDelivered = dispatcherCoalesced = 0L;
        dispatcherDropped = dispatcherCriticalDropped = 0L;
        liveSampleRateHz = 0.0;
        liveFrames = liveIncompleteFrames = 0L;
    }

    private static void maybeSample(long now) {
        if (lastSampleNano != 0L && now - lastSampleNano < SAMPLE_INTERVAL_NS) return;
        lastSampleNano = now;
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapCommitted = runtime.totalMemory();
        long heapMax = runtime.maxMemory();
        long gcCount = 0L;
        long gcTime = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            long time = bean.getCollectionTime();
            if (count > 0L) gcCount += count;
            if (time > 0L) gcTime += time;
        }
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        samples.addLast(new Sample(
                (now - startedNano) / 1.0e9,
                toMiB(heapUsed), toMiB(heapCommitted), toMiB(heapMax),
                gcCount, gcTime, threads.getThreadCount(),
                recentEdtLagMillis, maxEdtLagMillis,
                dispatcherQueue, dispatcherHighWater,
                dispatcherDropped, dispatcherCriticalDropped,
                dialogsLive, dialogsHighWater,
                liveSampleRateHz, liveFrames, liveIncompleteFrames,
                guidedEvidenceRecords, guidedProbeSamples));
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
    }

    private static long toMiB(long bytes) { return bytes < 0L ? -1L : bytes / (1024L * 1024L); }
    private static String f1(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String f3(double value) { return String.format(Locale.ROOT, "%.3f", value); }

    private static final class Sample {
        final double elapsedSeconds;
        final long heapUsedMiB, heapCommittedMiB, heapMaxMiB;
        final long gcCount, gcTimeMillis;
        final int threadCount;
        final double edtRecentLagMillis, edtMaxLagMillis;
        final int dispatcherQueue, dispatcherHighWater;
        final long dispatcherDropped, dispatcherCriticalDropped;
        final int dialogsLive, dialogsHighWater;
        final double liveSampleRateHz;
        final long liveFrames, liveIncompleteFrames;
        final int evidenceRecords, probeSamples;

        Sample(double elapsedSeconds, long heapUsedMiB, long heapCommittedMiB,
               long heapMaxMiB, long gcCount, long gcTimeMillis, int threadCount,
               double edtRecentLagMillis, double edtMaxLagMillis,
               int dispatcherQueue, int dispatcherHighWater,
               long dispatcherDropped, long dispatcherCriticalDropped,
               int dialogsLive, int dialogsHighWater, double liveSampleRateHz,
               long liveFrames, long liveIncompleteFrames,
               int evidenceRecords, int probeSamples) {
            this.elapsedSeconds = elapsedSeconds;
            this.heapUsedMiB = heapUsedMiB;
            this.heapCommittedMiB = heapCommittedMiB;
            this.heapMaxMiB = heapMaxMiB;
            this.gcCount = gcCount;
            this.gcTimeMillis = gcTimeMillis;
            this.threadCount = threadCount;
            this.edtRecentLagMillis = edtRecentLagMillis;
            this.edtMaxLagMillis = edtMaxLagMillis;
            this.dispatcherQueue = dispatcherQueue;
            this.dispatcherHighWater = dispatcherHighWater;
            this.dispatcherDropped = dispatcherDropped;
            this.dispatcherCriticalDropped = dispatcherCriticalDropped;
            this.dialogsLive = dialogsLive;
            this.dialogsHighWater = dialogsHighWater;
            this.liveSampleRateHz = liveSampleRateHz;
            this.liveFrames = liveFrames;
            this.liveIncompleteFrames = liveIncompleteFrames;
            this.evidenceRecords = evidenceRecords;
            this.probeSamples = probeSamples;
        }
    }
}
