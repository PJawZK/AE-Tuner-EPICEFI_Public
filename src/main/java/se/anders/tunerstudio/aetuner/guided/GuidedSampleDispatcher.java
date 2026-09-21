package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Bounded single-worker handoff from the TunerStudio live callback path to
 * Guided processing.
 *
 * offer() never invokes Guided code and never waits for the worker. Under
 * backlog, ordinary SETTLING/READY samples may be coalesced while samples that
 * belong to an active/potential acceleration or deceleration transient are
 * retained in order as far as the bounded queue permits. Exhaustion is visible
 * in diagnostics rather than turning into an unbounded queue or blocking the
 * ECU callback.
 */
public final class GuidedSampleDispatcher implements AutoCloseable {
    interface Listener {
        GuidedCaptureState onGuidedSample(LiveSample sample);
        default boolean transientCritical() { return false; }
    }

    static final int CAPACITY = 96;
    static final int COALESCE_THRESHOLD = 24;
    static final long TRANSIENT_PROTECT_NS = 2000000000L;

    static final class Diagnostics {
        final long offered, delivered, coalesced, dropped, criticalDropped,
                suspendedCleared, listenerFailures;
        final int queueDepth, highWaterMark;
        final boolean accepting, closed;

        Diagnostics(long offered, long delivered, long coalesced,
                    long dropped, long criticalDropped,
                    long suspendedCleared, long listenerFailures,
                    int queueDepth, int highWaterMark,
                    boolean accepting, boolean closed) {
            this.offered = offered;
            this.delivered = delivered;
            this.coalesced = coalesced;
            this.dropped = dropped;
            this.criticalDropped = criticalDropped;
            this.suspendedCleared = suspendedCleared;
            this.listenerFailures = listenerFailures;
            this.queueDepth = queueDepth;
            this.highWaterMark = highWaterMark;
            this.accepting = accepting;
            this.closed = closed;
        }

        String summary() {
            return "queue=" + queueDepth + "/" + CAPACITY
                    + " high=" + highWaterMark
                    + " offered=" + offered
                    + " delivered=" + delivered
                    + " coalesced=" + coalesced
                    + " dropped=" + dropped
                    + " criticalDropped=" + criticalDropped
                    + " cleared=" + suspendedCleared
                    + " failures=" + listenerFailures;
        }
    }

    /** Public immutable projection used by bounded performance diagnostics. */
    public static final class RuntimeStats {
        public final long offered, delivered, coalesced, dropped, criticalDropped,
                suspendedCleared, listenerFailures;
        public final int queueDepth, highWaterMark;
        public final boolean accepting, closed;

        RuntimeStats(Diagnostics diagnostics) {
            this.offered = diagnostics.offered;
            this.delivered = diagnostics.delivered;
            this.coalesced = diagnostics.coalesced;
            this.dropped = diagnostics.dropped;
            this.criticalDropped = diagnostics.criticalDropped;
            this.suspendedCleared = diagnostics.suspendedCleared;
            this.listenerFailures = diagnostics.listenerFailures;
            this.queueDepth = diagnostics.queueDepth;
            this.highWaterMark = diagnostics.highWaterMark;
            this.accepting = diagnostics.accepting;
            this.closed = diagnostics.closed;
        }
    }

    private static final class Entry {
        final LiveSample sample;
        final boolean critical;
        Entry(LiveSample sample, boolean critical) {
            this.sample = sample;
            this.critical = critical;
        }
    }

    private final Object lock = new Object();
    private final ArrayDeque<Entry> queue = new ArrayDeque<Entry>();
    private final Listener listener;
    private Thread worker;
    private volatile boolean criticalMode;
    private long transientProtectUntilNano;
    private boolean accepting;
    private boolean closed;
    private long offered, delivered, coalesced, dropped, criticalDropped,
            suspendedCleared, listenerFailures;
    private int highWaterMark;

    GuidedSampleDispatcher(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        this.listener = listener;
    }

    void resume() {
        synchronized (lock) {
            if (closed) return;
            ensureWorkerLocked();
            accepting = true;
            lock.notifyAll();
        }
    }

    private void ensureWorkerLocked() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(new Runnable() {
            @Override public void run() { runWorker(); }
        }, "AE-Tuner-Guided-worker");
        worker.setDaemon(true);
        worker.start();
    }

    void suspend() {
        synchronized (lock) {
            accepting = false;
            criticalMode = false;
            transientProtectUntilNano = 0L;
            suspendedCleared += queue.size();
            queue.clear();
            lock.notifyAll();
        }
    }

    /** Constant-time producer handoff except for a bounded <=96 entry scan. */
    public boolean offer(LiveSample sample) {
        if (sample == null) return false;
        synchronized (lock) {
            if (!accepting || closed) return false;
            offered++;
            boolean marker = looksTransient(sample);
            if (marker) {
                transientProtectUntilNano = Math.max(transientProtectUntilNano,
                        sample.getNanoTime() + TRANSIENT_PROTECT_NS);
            }
            boolean critical = criticalMode || marker
                    || sample.getNanoTime() <= transientProtectUntilNano;

            if (!critical && queue.size() >= COALESCE_THRESHOLD) {
                Entry tail = queue.peekLast();
                if (tail != null && !tail.critical) {
                    queue.removeLast();
                    queue.addLast(new Entry(sample, false));
                    coalesced++;
                    lock.notifyAll();
                    return true;
                }
            }

            if (queue.size() >= CAPACITY) {
                if (critical && removeOldestNonCritical()) {
                    dropped++;
                } else {
                    dropped++;
                    if (critical) criticalDropped++;
                    return false;
                }
            }

            queue.addLast(new Entry(sample, critical));
            highWaterMark = Math.max(highWaterMark, queue.size());
            lock.notifyAll();
            return true;
        }
    }

    Diagnostics diagnostics() {
        synchronized (lock) {
            return new Diagnostics(offered, delivered, coalesced, dropped,
                    criticalDropped, suspendedCleared, listenerFailures,
                    queue.size(), highWaterMark, accepting, closed);
        }
    }

    public RuntimeStats runtimeStats() { return new RuntimeStats(diagnostics()); }

    boolean workerAliveForTest() {
        synchronized (lock) { return worker != null && worker.isAlive(); }
    }

    @Override
    public void close() {
        Thread workerToInterrupt;
        synchronized (lock) {
            if (closed) return;
            accepting = false;
            closed = true;
            criticalMode = false;
            transientProtectUntilNano = 0L;
            suspendedCleared += queue.size();
            queue.clear();
            workerToInterrupt = worker;
            lock.notifyAll();
        }
        if (workerToInterrupt != null) workerToInterrupt.interrupt();
    }

    private boolean removeOldestNonCritical() {
        Iterator<Entry> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!entry.critical) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private void runWorker() {
        while (true) {
            Entry entry;
            synchronized (lock) {
                while (!closed && (!accepting || queue.isEmpty())) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                        if (closed) return;
                    }
                }
                if (closed) return;
                entry = queue.pollFirst();
            }
            if (entry == null) continue;
            try {
                listener.onGuidedSample(entry.sample);
                criticalMode = listener.transientCritical();
            } catch (RuntimeException ex) {
                criticalMode = false;
                synchronized (lock) { listenerFailures++; }
                System.err.println("AE Tuner Guided worker failed: " + ex.getMessage());
                ex.printStackTrace(System.err);
            } finally {
                synchronized (lock) { delivered++; }
            }
        }
    }

    private static boolean looksTransient(LiveSample sample) {
        if (sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                || sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)
                || sample.bool(ChannelRole.TPS_DECEL_ACTIVE)) return true;
        double change = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double limit = sample.get(ChannelRole.ACCEL_THRESHOLD);
        if (Double.isFinite(change) && Double.isFinite(limit)
                && limit > 0.0 && Math.abs(change) > Math.abs(limit)) return true;
        return Double.isFinite(sample.getTpsDot()) && Math.abs(sample.getTpsDot()) >= 5.0;
    }
}
