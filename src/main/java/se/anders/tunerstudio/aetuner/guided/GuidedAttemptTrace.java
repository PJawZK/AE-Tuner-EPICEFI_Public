package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.List;
import java.util.Locale;

/** Bounded, read-only compact trace formatter for one Guided attempt. */
final class GuidedAttemptTrace {
    private static final int MAX_TRACE_ROWS = 40;

    private GuidedAttemptTrace() { }

    static String build(String disposition,
                        List<LiveSample> attemptSamples,
                        BlendDurationCaptureConfig settings,
                        GuidedVehicleTestLimits.Snapshot limits,
                        double baselineSeconds,
                        double plateauWindowSeconds,
                        double plateauRange,
                        double majorPedalMove,
                        LiveSample measurementAnchor,
                        double bestGap,
                        LiveSample holdAnchor,
                        LiveSample outcome) {
        StringBuilder trace = new StringBuilder();
        trace.append("Compact attempt trace (").append(disposition).append(")\n")
                .append("adaptive_baseline_s,").append(f2(baselineSeconds)).append('\n')
                .append("desired_tps_step,").append(f1(settings.desiredTpsStep)).append('\n')
                .append("plateau_window_s,").append(f2(plateauWindowSeconds)).append('\n')
                .append("plateau_range_limit,").append(f1(plateauRange)).append('\n')
                .append("major_pedal_move,").append(f1(majorPedalMove)).append('\n')
                .append("timing_limits,").append(limits.summary()).append('\n')
                .append("dt_s,rpm,tps,map,fallbackMap,gap,tpsdot,detector,prediction\n");
        if (attemptSamples == null || attemptSamples.isEmpty()) {
            trace.append("no samples\n");
            return trace.toString();
        }
        int stride = Math.max(1,
                (int) Math.ceil(attemptSamples.size() / (double) MAX_TRACE_ROWS));
        long start = attemptSamples.get(0).getNanoTime();
        for (int i = 0; i < attemptSamples.size(); i += stride) {
            appendTraceRow(trace, attemptSamples.get(i), start);
        }
        LiveSample last = attemptSamples.get(attemptSamples.size() - 1);
        if ((attemptSamples.size() - 1) % stride != 0) {
            appendTraceRow(trace, last, start);
        }
        if (measurementAnchor != null) {
            trace.append("measurement_anchor_dt_s=")
                    .append(f3(seconds(start, measurementAnchor.getNanoTime())))
                    .append(",gap_kpa=").append(f2(bestGap)).append('\n');
        }
        if (holdAnchor != null) {
            trace.append("natural_hold_dt_s=")
                    .append(f3(seconds(start, holdAnchor.getNanoTime())))
                    .append(",tps=")
                    .append(f1(holdAnchor.get(ChannelRole.TPS))).append('\n');
        }
        if (outcome != null) {
            trace.append("outcome_dt_s=")
                    .append(f3(seconds(start, outcome.getNanoTime()))).append('\n');
        }
        return trace.toString();
    }

    private static void appendTraceRow(StringBuilder trace,
                                       LiveSample sample, long start) {
        double map = sample.get(ChannelRole.MAP);
        double fallback = sample.get(ChannelRole.FALLBACK_MAP);
        trace.append(f3(seconds(start, sample.getNanoTime()))).append(',')
                .append(f0(sample.get(ChannelRole.RPM))).append(',')
                .append(f1(sample.get(ChannelRole.TPS))).append(',')
                .append(f2(map)).append(',')
                .append(f2(fallback)).append(',')
                .append(f2(Double.isFinite(map) && Double.isFinite(fallback)
                        ? fallback - map : Double.NaN)).append(',')
                .append(f2(sample.getTpsDot())).append(',')
                .append(triggered(sample) ? '1' : '0').append(',')
                .append(sample.bool(ChannelRole.MAP_PRED_ACTIVE) ? '1' : '0')
                .append('\n');
    }

    private static boolean triggered(LiveSample sample) {
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) return true;
        double change = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double limit = sample.get(ChannelRole.ACCEL_THRESHOLD);
        return Double.isFinite(change) && Double.isFinite(limit)
                && limit > 0.0 && change > limit;
    }

    private static double seconds(long start, long end) {
        return (end - start) / 1000000000.0;
    }

    private static String f0(double value) {
        return String.format(Locale.US, "%.0f", value);
    }

    private static String f1(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String f2(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String f3(double value) {
        return String.format(Locale.US, "%.3f", value);
    }
}
