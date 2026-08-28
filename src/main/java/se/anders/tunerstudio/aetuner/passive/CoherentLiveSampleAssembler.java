package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.model.ChannelRole;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Reassembles individual TunerStudio output-channel callbacks into coherent
 * callback batches before a LiveSample is created.
 *
 * OutputChannelClient is invoked one channel at a time. Reading the shared
 * latest-value map from an arbitrary TPS/RPM/TIME callback can therefore mix
 * values from adjacent ECU updates. This assembler waits until a callback-burst
 * boundary (a repeated role or a quiet gap) and only emits the completed batch
 * when every resolved coherence-critical role was observed in that batch.
 */
final class CoherentLiveSampleAssembler {
    static final long DEFAULT_QUIET_GAP_NS = 4000000L;

    private static final ChannelRole[] CRITICAL_IF_RESOLVED = {
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.MAP,
            ChannelRole.FALLBACK_MAP,
            ChannelRole.EFFECTIVE_MAP,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.MAP_PRED_RESET_CNT,
            ChannelRole.MAP_PRED_EVENT_OVER,
            ChannelRole.SMOOTHED_DELTA_TPS,
            ChannelRole.ACCEL_THRESHOLD,
            ChannelRole.GEAR,
            ChannelRole.VSS
    };

    static final class Frame {
        final long nanoTime;
        final long firstCallbackNano;
        final long lastCallbackNano;
        final EnumMap<ChannelRole, Double> values;

        Frame(long nanoTime, long firstCallbackNano, long lastCallbackNano,
              EnumMap<ChannelRole, Double> values) {
            this.nanoTime = nanoTime;
            this.firstCallbackNano = firstCallbackNano;
            this.lastCallbackNano = lastCallbackNano;
            this.values = values;
        }

        long burstSpanNano() {
            return Math.max(0L, lastCallbackNano - firstCallbackNano);
        }
    }

    private final long quietGapNs;
    private final EnumMap<ChannelRole, Double> batchValues =
            new EnumMap<ChannelRole, Double>(ChannelRole.class);
    private final EnumSet<ChannelRole> seen = EnumSet.noneOf(ChannelRole.class);
    private EnumSet<ChannelRole> required = EnumSet.of(
            ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP);

    private long firstCallbackNano;
    private long lastCallbackNano;
    private long emittedFrames;
    private long incompleteFrames;
    private long duplicateBoundaries;
    private long quietGapBoundaries;
    private long maxBurstSpanNano;

    CoherentLiveSampleAssembler() {
        this(DEFAULT_QUIET_GAP_NS);
    }

    CoherentLiveSampleAssembler(long quietGapNs) {
        this.quietGapNs = Math.max(1L, quietGapNs);
    }

    void configureResolvedRoles(Set<ChannelRole> resolved) {
        EnumSet<ChannelRole> next = EnumSet.noneOf(ChannelRole.class);
        if (resolved != null) {
            for (ChannelRole role : CRITICAL_IF_RESOLVED) {
                if (resolved.contains(role)) next.add(role);
            }
        }
        // RPM/TPS/MAP are the minimum usable LiveSample contract. If one is
        // unresolved we intentionally cannot emit complete frames.
        next.add(ChannelRole.RPM);
        next.add(ChannelRole.TPS);
        next.add(ChannelRole.MAP);
        required = next;
        clearBatch();
    }

    Frame accept(ChannelRole role, double value, long nowNano) {
        if (role == null) return null;
        Frame completed = null;
        if (!seen.isEmpty()) {
            boolean duplicate = seen.contains(role);
            boolean quietGap = lastCallbackNano > 0L
                    && nowNano - lastCallbackNano > quietGapNs;
            if (duplicate || quietGap) {
                if (duplicate) duplicateBoundaries++;
                else quietGapBoundaries++;
                completed = finishBatch();
            }
        }
        if (seen.isEmpty()) firstCallbackNano = nowNano;
        batchValues.put(role, value);
        seen.add(role);
        lastCallbackNano = nowNano;
        return completed;
    }

    Frame flush() {
        return seen.isEmpty() ? null : finishBatch();
    }

    void reset() {
        clearBatch();
        emittedFrames = 0L;
        incompleteFrames = 0L;
        duplicateBoundaries = 0L;
        quietGapBoundaries = 0L;
        maxBurstSpanNano = 0L;
    }

    long emittedFrames() { return emittedFrames; }
    long incompleteFrames() { return incompleteFrames; }
    long duplicateBoundaries() { return duplicateBoundaries; }
    long quietGapBoundaries() { return quietGapBoundaries; }
    long maxBurstSpanNano() { return maxBurstSpanNano; }

    String requiredRolesText() {
        return required.toString();
    }

    private Frame finishBatch() {
        long first = firstCallbackNano;
        long last = lastCallbackNano;
        long span = Math.max(0L, last - first);
        if (span > maxBurstSpanNano) maxBurstSpanNano = span;

        Frame frame = null;
        if (seen.containsAll(required)) {
            emittedFrames++;
            frame = new Frame(last, first, last,
                    new EnumMap<ChannelRole, Double>(batchValues));
        } else {
            incompleteFrames++;
        }
        clearBatch();
        return frame;
    }

    private void clearBatch() {
        batchValues.clear();
        seen.clear();
        firstCallbackNano = 0L;
        lastCallbackNano = 0L;
    }
}
