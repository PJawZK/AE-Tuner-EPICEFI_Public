package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Bounded single-worker handoff from the passive TunerStudio callback path to
 * Guided processing.
 *
 * offer() never invokes Guided code and never waits for the worker. Under
 * backlog, ordinary SETTLING/READY samples may be coalesced while samples that
 * belong to an active/potential acceleration opening are retained in order as
 * far as the bounded queue permits. Exhaustion is visible in diagnostics
 * rather than turning into an unbounded queue or blocking the ECU callback.
 */
public final class GuidedSampleDispatcher implements AutoCloseable {
    interface Listener {
        GuidedCaptureState onGuidedSample(LiveSample sample);
    }

    static final int CAPACITY = 96;
    static final int COALESCE_THRESHOLD = 24;
    static final long TRANSIENT_PROTECT_NS = 2000000000L;

    static final class Diagnostics {
        final long offered;
        final long delivered;
        final long coalesced;
        final long dropped;
        final long criticalDropped;
        final long suspendedCleared;
        final long listenerFailures;
        final int queueDepth;
        final int highWaterMark;
        final boolean accepting;
        final boolean closed;

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
    private final Thread worker;

    private volatile boolean criticalMode;
    private long transientProtectUntilNano;
    private boolean accepting;
    private boolean closed;
    private long offered;
    private long delivered;
    private long coalesced;
    private long dropped;
    private long criticalDropped;
    private long suspendedCleared;
    private long listenerFailures;
    private int highWaterMark;

    GuidedSampleDispatcher(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        this.listener = listener;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runWorker();
            }
        }, "AE-Tuner-Guided-worker");
        worker.setDaemon(true);
        worker.start();
    }

    void resume() {
        synchronized (lock) {
            if (closed) return;
            accepting = true;
            lock.notifyAll();
        }
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

    boolean workerAliveForTest() {
        return worker.isAlive();
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            accepting = false;
            closed = true;
            criticalMode = false;
            transientProtectUntilNano = 0L;
            suspendedCleared += queue.size();
            queue.clear();
            lock.notifyAll();
        }
        worker.interrupt();
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
                GuidedCaptureState next = listener.onGuidedSample(entry.sample);
                criticalMode = next == GuidedCaptureState.OPENING_PENDING
                        || next == GuidedCaptureState.CAPTURING;
            } catch (RuntimeException ex) {
                synchronized (lock) {
                    listenerFailures++;
                }
                System.err.println("AE Tuner Guided worker failed: " + ex.getMessage());
                ex.printStackTrace(System.err);
            } finally {
                synchronized (lock) {
                    delivered++;
                }
            }
        }
    }

    private static boolean looksTransient(LiveSample sample) {
        if (sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                || sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) {
            return true;
        }
        double change = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double limit = sample.get(ChannelRole.ACCEL_THRESHOLD);
        if (Double.isFinite(change) && Double.isFinite(limit)
                && limit > 0.0 && change > limit) {
            return true;
        }
        return Double.isFinite(sample.getTpsDot()) && sample.getTpsDot() >= 5.0;
    }
}
