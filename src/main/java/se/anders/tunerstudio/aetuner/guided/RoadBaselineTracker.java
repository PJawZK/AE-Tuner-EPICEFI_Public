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
    /** Actual selected Blend Duration table bin must be approached closely before READY. */
    static final double RPM_ACQUIRE_TOLERANCE = 200.0;
    /** READY is cancelled if the driver leaves the same actual-bin acquisition window. */
    static final double RPM_READY_RELEASE_TOLERANCE = 200.0;
    /** Once the opening is confirmed, modest acceleration drift is permitted. */
    static final double RPM_CAPTURE_TOLERANCE = 300.0;
    static final double RPM_RESIDUAL_RANGE = 100.0;
    static final double MAP_RESIDUAL_RANGE = 3.0;
    static final double TPS_RESIDUAL_RANGE = 1.4;
    static final double RPM_SLOPE_LIMIT = 550.0;
    static final double MAP_SLOPE_LIMIT = 12.0;
    static final double TPS_SLOPE_LIMIT = 4.5;

    /**
     * Automatic gear is intentionally a one-time Guided-session latch. The
     * rolling recognizer is only allowed to collect evidence until one gear is
     * committed from the first controlled opening after READY. After that,
     * observe() becomes a no-op until clear() starts a new Guided session.
     */
    static final double GEAR_WINDOW_SECONDS = 1.50;
    static final double GEAR_MIN_DOMINANT_SECONDS = 0.45;
    static final int GEAR_MIN_SAMPLES = 6;
    static final double GEAR_MIN_CONFIDENCE = 0.75;
    static final double GEAR_MAX_VSS_KPH = 300.0;
    static final double GEAR_MAX_VSS_STEP_KPH = 25.0;
    static final double GEAR_VSS_CONTINUITY_SECONDS = 0.35;

    private final ArrayDeque<LiveSample> window = new ArrayDeque<LiveSample>();
    private final SessionGearLatch sessionGearLatch = new SessionGearLatch();

    void clear() {
        window.clear();
        sessionGearLatch.reset();
    }

    void add(LiveSample sample) {
        if (sample == null) return;
        window.addLast(sample);
        while (!window.isEmpty()
                && sample.getSeconds() - window.peekFirst().getSeconds()
                > WINDOW_SECONDS) {
            window.removeFirst();
        }
        sessionGearLatch.observe(sample);
        if (triggered(sample)) {
            sessionGearLatch.commitIfReady(sample.getSeconds());
        }
    }

    Baseline baseline(boolean excludeLast) {
        List<LiveSample> samples = recent(window, BASELINE_SECONDS, excludeLast);
        if (samples.isEmpty()) {
            return new Baseline(Double.NaN, Double.NaN, Double.NaN);
        }
        double now = window.isEmpty()
                ? samples.get(samples.size() - 1).getSeconds()
                : window.peekLast().getSeconds();
        SessionGearLatch.Candidate gearCandidate = sessionGearLatch.candidate(now);
        return new Baseline(trendEnd(samples, ChannelRole.RPM),
                trendEnd(samples, ChannelRole.MAP),
                trendEnd(samples, ChannelRole.TPS),
                sessionGearLatch, gearCandidate);
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
        add(text, rpmRegion, "RPM near actual selected Blend Duration bin " + f0(startRpm)
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
        sessionGearLatch.setReady(ready);
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
        add(text, rpmHeld, "READY retained near actual table bin " + f0(startRpm)
                + " ±" + f0(RPM_READY_RELEASE_TOLERANCE) + " RPM");
        add(text, smooth,
                "Rolling baseline follows gradual road/load changes without abrupt residual movement");
        if (!valid || !safe) {
            sessionGearLatch.setReady(false);
            return new ReadyCheck(false, text.toString(),
                    "READY cancelled by channel or safety state. Resume smooth driving.");
        }
        if (!rpmHeld) {
            sessionGearLatch.setReady(false);
            return new ReadyCheck(false, text.toString(),
                    "READY cancelled because RPM left the selected table-bin target.");
        }
        if (!quiet) {
            sessionGearLatch.setReady(false);
            return new ReadyCheck(false, text.toString(),
                    "READY cancelled by transient activity before a recognized opening.");
        }
        if (!smooth) {
            sessionGearLatch.setReady(false);
            return new ReadyCheck(false, text.toString(),
                    "Road baseline became abrupt; continue normal driving until it settles again.");
        }
        sessionGearLatch.setReady(true);
        return new ReadyCheck(true, text.toString(), "");
    }

    static final class Baseline {
        final double rpm;
        final double map;
        final double tps;
        private final SessionGearLatch gearLatch;
        private final SessionGearLatch.Candidate gearCandidate;

        Baseline(double rpm, double map, double tps) {
            this(rpm, map, tps, null, SessionGearLatch.Candidate.unavailable());
        }

        private Baseline(double rpm, double map, double tps,
                         SessionGearLatch gearLatch,
                         SessionGearLatch.Candidate gearCandidate) {
            this.rpm = rpm;
            this.map = map;
            this.tps = tps;
            this.gearLatch = gearLatch;
            this.gearCandidate = gearCandidate == null
                    ? SessionGearLatch.Candidate.unavailable() : gearCandidate;
        }

        boolean valid() {
            return Double.isFinite(rpm) && Double.isFinite(map)
                    && Double.isFinite(tps);
        }

        /**
         * Normally already committed at the first recognized opening after
         * READY. This fallback commits the frozen READY candidate if the local
         * opening path completed without the ECU trigger appearing first.
         */
        int sessionDetectedGear() {
            return gearLatch == null ? 0 : gearLatch.commit(gearCandidate);
        }

        String sessionGearEvidence() {
            return gearLatch == null
                    ? "session gear evidence unavailable"
                    : gearLatch.statusText(gearCandidate);
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

    /**
     * One-way session gear recognizer. VSS corruption removes a sample from the
     * vote; it never resets a candidate and can never replace a committed gear.
     */
    private static final class SessionGearLatch {
        private final ArrayDeque<GearSample> votes = new ArrayDeque<GearSample>();
        private int lockedGear;
        private double lastTrustedVss = Double.NaN;
        private double lastTrustedSeconds = Double.NaN;
        private int rejectedVssSamples;
        private int ignoredGearSamples;
        private boolean readyArmed;
        private String lockedEvidence = "";

        void reset() {
            votes.clear();
            lockedGear = 0;
            lastTrustedVss = Double.NaN;
            lastTrustedSeconds = Double.NaN;
            rejectedVssSamples = 0;
            ignoredGearSamples = 0;
            readyArmed = false;
            lockedEvidence = "";
        }

        void setReady(boolean ready) {
            if (lockedGear == 0) readyArmed = ready;
        }

        void observe(LiveSample sample) {
            if (sample == null || lockedGear != 0) return;
            double now = sample.getSeconds();
            prune(now);

            double vss = sample.get(ChannelRole.VSS);
            if (!Double.isFinite(vss) || vss <= 0.0 || vss > GEAR_MAX_VSS_KPH) {
                rejectedVssSamples++;
                return;
            }
            if (Double.isFinite(lastTrustedVss)
                    && Double.isFinite(lastTrustedSeconds)
                    && now - lastTrustedSeconds <= GEAR_VSS_CONTINUITY_SECONDS
                    && Math.abs(vss - lastTrustedVss) > GEAR_MAX_VSS_STEP_KPH) {
                rejectedVssSamples++;
                return;
            }
            lastTrustedVss = vss;
            lastTrustedSeconds = now;

            double rawGear = sample.get(ChannelRole.GEAR);
            int gear = Double.isFinite(rawGear) ? (int) Math.round(rawGear) : 0;
            if (!Double.isFinite(rawGear) || gear < 1 || gear > 8
                    || Math.abs(rawGear - gear) > 0.25) {
                ignoredGearSamples++;
                return;
            }
            votes.addLast(new GearSample(now, gear));
            prune(now);
        }

        void commitIfReady(double now) {
            if (lockedGear != 0 || !readyArmed) return;
            commit(candidate(now));
        }

        Candidate candidate(double now) {
            if (lockedGear != 0) {
                return Candidate.locked(lockedGear, lockedEvidence);
            }
            prune(now);
            if (votes.size() < GEAR_MIN_SAMPLES) {
                return Candidate.unavailable(votes.size(), rejectedVssSamples,
                        ignoredGearSamples, "too little trusted gear evidence");
            }

            int[] counts = new int[9];
            double[] first = new double[9];
            double[] last = new double[9];
            for (int i = 0; i < first.length; i++) {
                first[i] = Double.NaN;
                last[i] = Double.NaN;
            }
            for (GearSample vote : votes) {
                counts[vote.gear]++;
                if (!Double.isFinite(first[vote.gear])) first[vote.gear] = vote.seconds;
                last[vote.gear] = vote.seconds;
            }

            int dominantGear = 0;
            int dominantCount = 0;
            for (int gear = 1; gear <= 8; gear++) {
                if (counts[gear] > dominantCount) {
                    dominantGear = gear;
                    dominantCount = counts[gear];
                }
            }
            double confidence = dominantCount / (double) votes.size();
            double span = dominantGear == 0 || !Double.isFinite(first[dominantGear])
                    ? 0.0 : Math.max(0.0, last[dominantGear] - first[dominantGear]);
            boolean valid = dominantCount >= GEAR_MIN_SAMPLES
                    && confidence >= GEAR_MIN_CONFIDENCE
                    && span >= GEAR_MIN_DOMINANT_SECONDS;
            return new Candidate(valid, dominantGear, votes.size(), dominantCount,
                    confidence, span, rejectedVssSamples, ignoredGearSamples,
                    valid ? "confident READY-window dominant gear"
                            : "READY-window gear evidence not yet dominant/stable enough");
        }

        int commit(Candidate candidate) {
            if (lockedGear != 0) return lockedGear;
            if (candidate == null || !candidate.valid) return 0;
            lockedGear = candidate.gear;
            lockedEvidence = candidate.summary();
            readyArmed = false;
            votes.clear();
            return lockedGear;
        }

        String statusText(Candidate candidate) {
            if (lockedGear != 0) {
                return "session gear " + lockedGear + " latched | " + lockedEvidence;
            }
            return candidate == null ? "session gear not latched"
                    : "session gear not latched | " + candidate.summary();
        }

        private void prune(double now) {
            while (!votes.isEmpty()
                    && now - votes.peekFirst().seconds > GEAR_WINDOW_SECONDS) {
                votes.removeFirst();
            }
        }

        static final class Candidate {
            final boolean valid;
            final int gear;
            final int trustedSamples;
            final int dominantSamples;
            final double confidence;
            final double dominantSpan;
            final int rejectedVssSamples;
            final int ignoredGearSamples;
            final String reason;

            Candidate(boolean valid, int gear, int trustedSamples,
                      int dominantSamples, double confidence,
                      double dominantSpan, int rejectedVssSamples,
                      int ignoredGearSamples, String reason) {
                this.valid = valid;
                this.gear = gear;
                this.trustedSamples = trustedSamples;
                this.dominantSamples = dominantSamples;
                this.confidence = confidence;
                this.dominantSpan = dominantSpan;
                this.rejectedVssSamples = rejectedVssSamples;
                this.ignoredGearSamples = ignoredGearSamples;
                this.reason = reason == null ? "" : reason;
            }

            static Candidate unavailable() {
                return unavailable(0, 0, 0, "no session gear evidence");
            }

            static Candidate unavailable(int trusted, int rejected,
                                         int ignored, String reason) {
                return new Candidate(false, 0, trusted, 0, 0.0, 0.0,
                        rejected, ignored, reason);
            }

            static Candidate locked(int gear, String evidence) {
                return new Candidate(true, gear, 0, 0, 1.0,
                        GEAR_MIN_DOMINANT_SECONDS, 0, 0,
                        evidence == null ? "already latched" : evidence);
            }

            String summary() {
                String confidenceText = trustedSamples <= 0 ? "n/a"
                        : String.format(Locale.US, "%.0f%%", confidence * 100.0);
                return "candidate " + (gear > 0 ? Integer.toString(gear) : "n/a")
                        + " | dominant " + dominantSamples + "/" + trustedSamples
                        + " (" + confidenceText + ")"
                        + " | dominant span " + f2(dominantSpan) + " s"
                        + " | rejected VSS spike/dropout samples " + rejectedVssSamples
                        + " | ignored unavailable/non-integer gear samples " + ignoredGearSamples
                        + " | " + reason;
            }
        }

        private static final class GearSample {
            final double seconds;
            final int gear;

            GearSample(double seconds, int gear) {
                this.seconds = seconds;
                this.gear = gear;
            }
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
