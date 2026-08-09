package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.List;

/** Pending/confirmed pedal-opening classifier for adaptive Guided capture. */
final class PedalOpeningDetector {
    static final double PENDING_RISE = 1.0;
    private static final int MAX_PENDING_SAMPLES = 320;

    enum DecisionType {
        WAIT,
        CONFIRM,
        ABORT_TO_READY,
        RETURN_TO_BASELINE
    }

    static final class Decision {
        final DecisionType type;
        final String reason;
        final String correction;

        Decision(DecisionType type, String reason, String correction) {
            this.type = type;
            this.reason = reason == null ? "" : reason;
            this.correction = correction == null ? "" : correction;
        }

        static Decision waitForMore() {
            return new Decision(DecisionType.WAIT, "", "");
        }

        static Decision confirm() {
            return new Decision(DecisionType.CONFIRM, "", "");
        }

        static Decision abortToReady() {
            return new Decision(DecisionType.ABORT_TO_READY, "", "");
        }

        static Decision returnToBaseline(String reason, String correction) {
            return new Decision(DecisionType.RETURN_TO_BASELINE, reason, correction);
        }
    }

    private final List<LiveSample> pendingSamples = new ArrayList<LiveSample>();
    private long pendingStarted;

    void reset() {
        pendingSamples.clear();
        pendingStarted = 0L;
    }

    boolean movementStarted(LiveSample sample, double baselineTps) {
        double tps = sample == null ? Double.NaN : sample.get(ChannelRole.TPS);
        return Double.isFinite(tps) && Double.isFinite(baselineTps)
                && tps - baselineTps > PENDING_RISE;
    }

    boolean localTipInStarted(LiveSample sample, double baselineTps,
                              double localTpsOnsetRise) {
        double tps = sample == null ? Double.NaN : sample.get(ChannelRole.TPS);
        return Double.isFinite(tps) && Double.isFinite(baselineTps)
                && tps - baselineTps >= localTpsOnsetRise;
    }

    void beginPending(LiveSample sample) {
        reset();
        record(sample);
        pendingStarted = sample == null ? 0L : sample.getNanoTime();
    }

    Decision observePending(LiveSample sample,
                            RoadBaselineTracker.Baseline baseline,
                            double startRpm,
                            GuidedVehicleTestLimits.Snapshot limits) {
        record(sample);
        if (baseline == null || !requiredFinite(sample)) {
            return Decision.returnToBaseline(
                    "The pending opening lost required RPM/TPS/MAP/fallbackMap evidence.",
                    "Resume normal driving and wait for READY again.");
        }
        if (!safe(sample)) {
            return Decision.returnToBaseline(
                    "The pending opening left a safe running state.",
                    "Resume only after the engine and trigger/cut states are valid.");
        }
        if (Math.abs(sample.get(ChannelRole.RPM) - startRpm)
                > RoadBaselineTracker.RPM_READY_RELEASE_TOLERANCE) {
            return Decision.returnToBaseline(
                    "RPM left the selected road region before the opening was confirmed.",
                    "Return near the selected RPM region and wait for READY.");
        }
        double rise = sample.get(ChannelRole.TPS) - baseline.tps;
        if (triggered(sample) || sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                || rise >= limits.localTpsOnsetRise) {
            return Decision.confirm();
        }
        if (rise <= PENDING_RISE * 0.5
                && !sample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
            return Decision.abortToReady();
        }
        if (pendingStarted != 0L
                && seconds(pendingStarted, sample.getNanoTime())
                > limits.detectorConfirmSeconds) {
            return Decision.returnToBaseline(
                    "A small/slow pedal movement did not become a confirmed acceleration opening within "
                            + f2(limits.detectorConfirmSeconds) + " seconds.",
                    "Return to normal throttle; the rolling baseline will reacquire automatically.");
        }
        return Decision.waitForMore();
    }

    List<LiveSample> consumePendingSamples() {
        List<LiveSample> copy = new ArrayList<LiveSample>(pendingSamples);
        reset();
        return copy;
    }

    List<LiveSample> pendingSamples() {
        return new ArrayList<LiveSample>(pendingSamples);
    }

    void clearPending() {
        reset();
    }

    private void record(LiveSample sample) {
        if (sample == null) return;
        if (pendingSamples.size() >= MAX_PENDING_SAMPLES) {
            pendingSamples.remove(0);
        }
        pendingSamples.add(sample);
    }

    private static boolean triggered(LiveSample sample) {
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) return true;
        double change = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double limit = sample.get(ChannelRole.ACCEL_THRESHOLD);
        return Double.isFinite(change) && Double.isFinite(limit)
                && limit > 0.0 && change > limit;
    }

    private static boolean requiredFinite(LiveSample sample) {
        return sample != null
                && Double.isFinite(sample.get(ChannelRole.RPM))
                && Double.isFinite(sample.get(ChannelRole.TPS))
                && Double.isFinite(sample.get(ChannelRole.MAP))
                && Double.isFinite(sample.get(ChannelRole.FALLBACK_MAP));
    }

    private static boolean safe(LiveSample sample) {
        boolean running = Double.isFinite(sample.get(ChannelRole.ENGINE_RUNNING))
                ? sample.bool(ChannelRole.ENGINE_RUNNING)
                : sample.get(ChannelRole.RPM) >= 400.0;
        return running
                && !sample.bool(ChannelRole.ENGINE_CRANKING)
                && !sample.bool(ChannelRole.FUEL_CUT)
                && !sample.bool(ChannelRole.TOTAL_SPARK_CUT)
                && !sample.bool(ChannelRole.TRIGGER_ERROR);
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }

    private static String f2(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
