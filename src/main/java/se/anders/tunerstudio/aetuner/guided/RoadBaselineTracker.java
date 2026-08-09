package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rolling trend-aware road baseline and READY qualification for Guided capture. */
final class RoadBaselineTracker {
    static final double BASELINE_SECONDS = 0.75;
    static final double WINDOW_SECONDS = 1.10;
    static final double RPM_ACQUIRE_TOLERANCE = 300.0;
    static final double RPM_READY_RELEASE_TOLERANCE = 450.0;
    static final double RPM_RESIDUAL_RANGE = 100.0;
    static final double MAP_RESIDUAL_RANGE = 3.0;
    static final double TPS_RESIDUAL_RANGE = 1.4;
    static final double RPM_SLOPE_LIMIT = 550.0;
    static final double MAP_SLOPE_LIMIT = 12.0;
    static final double TPS_SLOPE_LIMIT = 4.5;

    private final ArrayDeque<LiveSample> window = new ArrayDeque<LiveSample>();

    void clear() {
        window.clear();
    }

    void add(LiveSample sample) {
        if (sample == null) return;
        window.addLast(sample);
        while (!window.isEmpty()
                && sample.getSeconds() - window.peekFirst().getSeconds()
                > WINDOW_SECONDS) {
            window.removeFirst();
        }
    }

    Baseline baseline(boolean excludeLast) {
        List<LiveSample> samples = recent(window, BASELINE_SECONDS, excludeLast);
        if (samples.isEmpty()) {
            return new Baseline(Double.NaN, Double.NaN, Double.NaN);
        }
        return new Baseline(trendEnd(samples, ChannelRole.RPM),
                trendEnd(samples, ChannelRole.MAP),
                trendEnd(samples, ChannelRole.TPS));
    }

    AcquireCheck acquireCheck(LiveSample sample, double startRpm,
                              long lastOutcomeNano, double recoverySeconds) {
        StringBuilder text = new StringBuilder();
        boolean valid = requiredFinite(sample);
        boolean safe = safe(sample);
        boolean quiet = !sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                && !triggered(sample);
        RoadWindowStats stats = RoadWindowStats.fromRecent(window,
                BASELINE_SECONDS, false);
        boolean rpmRegion = Double.isFinite(sample.get(ChannelRole.RPM))
                && Math.abs(sample.get(ChannelRole.RPM) - startRpm)
                <= RPM_ACQUIRE_TOLERANCE;
        boolean trendSmooth = stats.duration >= BASELINE_SECONDS * 0.95
                && stats.rpmResidualRange <= RPM_RESIDUAL_RANGE
                && stats.mapResidualRange <= MAP_RESIDUAL_RANGE
                && stats.tpsResidualRange <= TPS_RESIDUAL_RANGE
                && Math.abs(stats.rpmSlope) <= RPM_SLOPE_LIMIT
                && Math.abs(stats.mapSlope) <= MAP_SLOPE_LIMIT
                && Math.abs(stats.tpsSlope) <= TPS_SLOPE_LIMIT;
        boolean recovered = lastOutcomeNano == 0L
                || seconds(lastOutcomeNano, sample.getNanoTime()) >= recoverySeconds;

        add(text, valid, "Required RPM/TPS/MAP/fallbackMap channels valid");
        add(text, safe, "Engine running with no crank/cut/trigger fault");
        add(text, quiet, "No active acceleration detector/prediction burst");
        add(text, rpmRegion, "RPM inside road region " + f0(startRpm)
                + " ±" + f0(RPM_ACQUIRE_TOLERANCE));
        add(text, stats.duration >= BASELINE_SECONDS * 0.95,
                "Rolling baseline collected for about " + f2(BASELINE_SECONDS) + " s");
        add(text, stats.rpmResidualRange <= RPM_RESIDUAL_RANGE,
                "RPM smooth after road-grade trend removal (residual range ≤"
                        + f0(RPM_RESIDUAL_RANGE) + " RPM)");
        add(text, stats.mapResidualRange <= MAP_RESIDUAL_RANGE,
                "MAP smooth after trend removal (residual range ≤"
                        + f1(MAP_RESIDUAL_RANGE) + " kPa)");
        add(text, stats.tpsResidualRange <= TPS_RESIDUAL_RANGE,
                "TPS corrections smooth after trend removal (residual range ≤"
                        + f1(TPS_RESIDUAL_RANGE) + " points)");
        add(text, Math.abs(stats.rpmSlope) <= RPM_SLOPE_LIMIT,
                "RPM trend within road allowance");
        add(text, Math.abs(stats.mapSlope) <= MAP_SLOPE_LIMIT,
                "MAP trend within road allowance");
        add(text, Math.abs(stats.tpsSlope) <= TPS_SLOPE_LIMIT,
                "TPS trend within road allowance");
        add(text, recovered, "Short recovery interval complete");

        boolean ready = valid && safe && quiet && rpmRegion
                && trendSmooth && recovered;
        return new AcquireCheck(ready, recovered, text.toString());
    }

    ReadyCheck readyCheck(LiveSample sample, double startRpm) {
        StringBuilder text = new StringBuilder();
        boolean valid = requiredFinite(sample);
        boolean safe = safe(sample);
        boolean quiet = !sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                && !triggered(sample);
        boolean rpmHeld = Double.isFinite(sample.get(ChannelRole.RPM))
                && Math.abs(sample.get(ChannelRole.RPM) - startRpm)
                <= RPM_READY_RELEASE_TOLERANCE;
        RoadWindowStats stats = RoadWindowStats.fromRecent(window,
                BASELINE_SECONDS, true);
        boolean smooth = stats.duration >= BASELINE_SECONDS * 0.70
                && stats.rpmResidualRange <= RPM_RESIDUAL_RANGE * 1.35
                && stats.mapResidualRange <= MAP_RESIDUAL_RANGE * 1.35
                && stats.tpsResidualRange <= TPS_RESIDUAL_RANGE * 1.35
                && Math.abs(stats.rpmSlope) <= RPM_SLOPE_LIMIT * 1.15
                && Math.abs(stats.mapSlope) <= MAP_SLOPE_LIMIT * 1.15
                && Math.abs(stats.tpsSlope) <= TPS_SLOPE_LIMIT * 1.15;
        add(text, valid, "Required channels remain valid");
        add(text, safe, "Running/safety state remains valid");
        add(text, quiet, "Waiting for one acceleration opening");
        add(text, rpmHeld, "READY retained within " + f0(startRpm)
                + " ±" + f0(RPM_READY_RELEASE_TOLERANCE) + " RPM");
        add(text, smooth,
                "Rolling baseline follows gradual road/load changes without abrupt residual movement");
        if (!valid || !safe) {
            return new ReadyCheck(false, text.toString(),
                    "READY cancelled by channel or safety state. Resume smooth driving.");
        }
        if (!rpmHeld) {
            return new ReadyCheck(false, text.toString(),
                    "READY cancelled because RPM left the selected road region.");
        }
        if (!quiet) {
            return new ReadyCheck(false, text.toString(),
                    "READY cancelled by transient activity before a recognized opening.");
        }
        if (!smooth) {
            return new ReadyCheck(false, text.toString(),
                    "Road baseline became abrupt; continue normal driving until it settles again.");
        }
        return new ReadyCheck(true, text.toString(), "");
    }

    static final class Baseline {
        final double rpm;
        final double map;
        final double tps;

        Baseline(double rpm, double map, double tps) {
            this.rpm = rpm;
            this.map = map;
            this.tps = tps;
        }

        boolean valid() {
            return Double.isFinite(rpm) && Double.isFinite(map)
                    && Double.isFinite(tps);
        }
    }

    static final class AcquireCheck {
        final boolean ready;
        final boolean recovered;
        final String text;

        AcquireCheck(boolean ready, boolean recovered, String text) {
            this.ready = ready;
            this.recovered = recovered;
            this.text = text;
        }
    }

    static final class ReadyCheck {
        final boolean ready;
        final String text;
        final String instruction;

        ReadyCheck(boolean ready, String text, String instruction) {
            this.ready = ready;
            this.text = text;
            this.instruction = instruction;
        }
    }

    private static final class RoadWindowStats {
        final double duration;
        final double rpmResidualRange;
        final double mapResidualRange;
        final double tpsResidualRange;
        final double rpmSlope;
        final double mapSlope;
        final double tpsSlope;

        RoadWindowStats(double duration,
                        double rpmResidualRange,
                        double mapResidualRange,
                        double tpsResidualRange,
                        double rpmSlope, double mapSlope, double tpsSlope) {
            this.duration = duration;
            this.rpmResidualRange = rpmResidualRange;
            this.mapResidualRange = mapResidualRange;
            this.tpsResidualRange = tpsResidualRange;
            this.rpmSlope = rpmSlope;
            this.mapSlope = mapSlope;
            this.tpsSlope = tpsSlope;
        }

        static RoadWindowStats fromRecent(ArrayDeque<LiveSample> source,
                                          double seconds, boolean excludeLast) {
            List<LiveSample> samples = recent(source, seconds, excludeLast);
            if (samples.size() < 2) {
                return new RoadWindowStats(0.0,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            double duration = samples.get(samples.size() - 1).getSeconds()
                    - samples.get(0).getSeconds();
            Fit rpm = Fit.of(samples, ChannelRole.RPM);
            Fit map = Fit.of(samples, ChannelRole.MAP);
            Fit tps = Fit.of(samples, ChannelRole.TPS);
            return new RoadWindowStats(duration, rpm.residualRange,
                    map.residualRange, tps.residualRange,
                    rpm.slope, map.slope, tps.slope);
        }
    }

    private static final class Fit {
        final double slope;
        final double intercept;
        final double residualRange;

        Fit(double slope, double intercept, double residualRange) {
            this.slope = slope;
            this.intercept = intercept;
            this.residualRange = residualRange;
        }

        static Fit of(List<LiveSample> samples, ChannelRole role) {
            if (samples.isEmpty()) {
                return new Fit(Double.NaN, Double.NaN,
                        Double.POSITIVE_INFINITY);
            }
            double t0 = samples.get(0).getSeconds();
            double sumT = 0.0;
            double sumY = 0.0;
            double sumTT = 0.0;
            double sumTY = 0.0;
            int count = 0;
            for (LiveSample sample : samples) {
                double y = sample.get(role);
                if (!Double.isFinite(y)) continue;
                double t = sample.getSeconds() - t0;
                sumT += t;
                sumY += y;
                sumTT += t * t;
                sumTY += t * y;
                count++;
            }
            if (count == 0) {
                return new Fit(Double.NaN, Double.NaN,
                        Double.POSITIVE_INFINITY);
            }
            double denominator = count * sumTT - sumT * sumT;
            double slope = Math.abs(denominator) < 1.0e-12
                    ? 0.0 : (count * sumTY - sumT * sumY) / denominator;
            double intercept = (sumY - slope * sumT) / count;
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (LiveSample sample : samples) {
                double y = sample.get(role);
                if (!Double.isFinite(y)) continue;
                double t = sample.getSeconds() - t0;
                double residual = y - (intercept + slope * t);
                min = Math.min(min, residual);
                max = Math.max(max, residual);
            }
            double range = min == Double.POSITIVE_INFINITY
                    ? Double.POSITIVE_INFINITY : max - min;
            return new Fit(slope, intercept, range);
        }
    }

    private static List<LiveSample> recent(ArrayDeque<LiveSample> source,
                                           double seconds, boolean excludeLast) {
        List<LiveSample> all = new ArrayList<LiveSample>(source);
        if (excludeLast && !all.isEmpty()) all.remove(all.size() - 1);
        if (all.isEmpty()) return all;
        double end = all.get(all.size() - 1).getSeconds();
        List<LiveSample> recent = new ArrayList<LiveSample>();
        for (LiveSample sample : all) {
            if (end - sample.getSeconds() <= seconds + 1.0e-9) {
                recent.add(sample);
            }
        }
        return recent;
    }

    private static double trendEnd(List<LiveSample> samples, ChannelRole role) {
        Fit fit = Fit.of(samples, role);
        if (!Double.isFinite(fit.slope) || !Double.isFinite(fit.intercept)) {
            return Double.NaN;
        }
        double duration = samples.get(samples.size() - 1).getSeconds()
                - samples.get(0).getSeconds();
        return fit.intercept + fit.slope * duration;
    }

    private static boolean triggered(LiveSample sample) {
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) return true;
        double change = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double limit = sample.get(ChannelRole.ACCEL_THRESHOLD);
        return Double.isFinite(change) && Double.isFinite(limit)
                && limit > 0.0 && change > limit;
    }

    private static boolean requiredFinite(LiveSample sample) {
        return Double.isFinite(sample.get(ChannelRole.RPM))
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

    private static void add(StringBuilder text, boolean pass, String label) {
        text.append(pass ? "✓ " : "✗ ").append(label).append('\n');
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }

    private static String f0(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.0f", value) : "n/a";
    }

    private static String f1(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.1f", value) : "n/a";
    }

    private static String f2(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.2f", value) : "n/a";
    }
}
