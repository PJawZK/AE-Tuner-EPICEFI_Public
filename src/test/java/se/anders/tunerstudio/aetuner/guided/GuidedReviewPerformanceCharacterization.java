package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import javax.swing.SwingUtilities;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.EnumMap;

/** Optional characterization, not a timing gate. Run after build + compile-tests. */
public final class GuidedReviewPerformanceCharacterization {
    private static volatile int sink;

    public static void main(String[] args) throws Exception {
        for (int size : new int[]{100, 6000, 24000}) {
            GuidedMethodProbeSession session = fixture(size);
            SwingUtilities.invokeAndWait(() -> measure("active-" + size, 100, () -> refresh(session)));
            session.finish();
            SwingUtilities.invokeAndWait(() -> cold("cold-review-" + size, () -> refresh(session)));
            SwingUtilities.invokeAndWait(() -> measure("review-" + size, 100, () -> refresh(session)));
            cold("cold-export-" + size, () -> {
                sink = session.reportText("characterization").length() + session.csvText().length();
            });
            measure("export-" + size, 3, () -> {
                sink = session.reportText("characterization").length() + session.csvText().length();
            });
        }
        backlog();
    }

    private static void refresh(GuidedMethodProbeSession session) {
        sink = session.snapshot().result.length() + session.reviewText().length()
                + session.copyPasteBlock().length();
        session.reviewedWritePlan();
    }

    private static void cold(String label, Runnable work) {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean allocation = bean.isThreadAllocatedMemorySupported();
        if (allocation) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long bytes = allocation ? bean.getThreadAllocatedBytes(thread) : 0;
        long start = System.nanoTime();
        work.run();
        long elapsed = System.nanoTime() - start;
        System.out.println(label + " us=" + elapsed / 1000.0 + " bytes="
                + (allocation ? bean.getThreadAllocatedBytes(thread) - bytes : -1));
    }

    private static void measure(String label, int iterations, Runnable work) {
        for (int i = 0; i < 5; i++) work.run();
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean allocation = bean.isThreadAllocatedMemorySupported();
        if (allocation) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long[] times = new long[5];
        long bytes = 0;
        for (int trial = 0; trial < times.length; trial++) {
            long beforeBytes = allocation ? bean.getThreadAllocatedBytes(thread) : 0;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) work.run();
            times[trial] = (System.nanoTime() - start) / iterations;
            if (allocation) bytes += bean.getThreadAllocatedBytes(thread) - beforeBytes;
        }
        Arrays.sort(times);
        System.out.println(label + " median-us/op=" + times[2] / 1000.0
                + " bytes/op=" + (allocation ? bytes / (5L * iterations) : -1));
    }

    private static void backlog() throws Exception {
        GuidedMethodProbeSession session = fixture(6000);
        final long[] offeredAt = new long[400], latency = new long[400];
        GuidedSampleDispatcher dispatcher = new GuidedSampleDispatcher(new GuidedSampleDispatcher.Listener() {
            @Override public GuidedCaptureState onGuidedSample(LiveSample sample) {
                int index = (int) Math.round((sample.getSeconds() - 100.0) / .005);
                session.accept(sample);
                latency[index] = System.nanoTime() - offeredAt[index];
                return session.state();
            }
        });
        dispatcher.resume();
        try {
            long start = System.nanoTime();
            for (int i = 0; i < 400; i++) {
                offeredAt[i] = System.nanoTime();
                dispatcher.offer(sample(100.0 + i * .005, i % 100 < 5));
                if (i % 30 == 0) SwingUtilities.invokeAndWait(() -> refresh(session));
                long remaining = start + (i + 1) * 5000000L - System.nanoTime();
                if (remaining > 0) java.util.concurrent.locks.LockSupport.parkNanos(remaining);
            }
            long deadline = System.nanoTime() + 2000000000L;
            while (dispatcher.runtimeStats().delivered < 400 && System.nanoTime() < deadline) {
                Thread.yield();
            }
            GuidedSampleDispatcher.RuntimeStats stats = dispatcher.runtimeStats();
            Arrays.sort(latency);
            System.out.println("active-200Hz offered=" + stats.offered + " delivered=" + stats.delivered
                    + " coalesced=" + stats.coalesced + " dropped=" + stats.dropped
                    + " highWater=" + stats.highWaterMark + " failures=" + stats.listenerFailures
                    + " p95-offer-to-accept-us=" + latency[379] / 1000.0);
        } finally { dispatcher.close(); }
    }

    static GuidedMethodProbeSession fixture(int size) {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(size > 6000
                ? GuidedTuningRecipe.FOUNDATION_THRESHOLD : GuidedTuningRecipe.MAP_PREDICT));
        for (int i = 0; i < size; i++) session.accept(sample(10.0 + i * .01, i % 100 < 5));
        return session;
    }

    static LiveSample sample(double seconds, boolean active) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        for (ChannelRole role : ChannelRole.values()) values.put(role, 0.0);
        values.put(ChannelRole.RPM, 2200.0);
        values.put(ChannelRole.TPS, active ? 20.0 : 8.0);
        values.put(ChannelRole.MAP, active ? 65.0 : 50.0);
        values.put(ChannelRole.FALLBACK_MAP, active ? 82.0 : 50.0);
        values.put(ChannelRole.EFFECTIVE_MAP, active ? 78.0 : 50.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, active ? 1.0 : 0.0);
        values.put(ChannelRole.DELTA_TPS, active ? 2.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, active ? 2.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        values.put(ChannelRole.LAMBDA, 1.0);
        values.put(ChannelRole.TARGET_LAMBDA, 1.0);
        return new LiveSample(Math.round(seconds * 1e9), seconds, values,
                active ? 60.0 : 0.0, active ? 100.0 : 0.0);
    }
}
