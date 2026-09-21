package se.anders.tunerstudio.aetuner.guided;

import java.util.Locale;

/**
 * Per-event automatic Blend Duration RPM-bin latch.
 *
 * The operator arms 1..4 actual table bins for the session. While no event is
 * active, live RPM may establish a candidate only inside the acquisition window.
 * The same candidate must remain inside that window for a dwell period before
 * it can be latched. Once latched, this class never follows RPM until the caller
 * explicitly releases it after the event/outcome. That prevents a rising-RPM
 * transient from migrating to a different table bin mid-measurement.
 */
final class BlendDurationRpmBinLatch {
    static final double ACQUIRE_TOLERANCE_RPM = BlendDurationGuidedSession.ENTRY_RPM_TOLERANCE;
    static final double CANDIDATE_RELEASE_TOLERANCE_RPM = 375.0;
    static final double LATCH_DWELL_SECONDS = 0.65;

    private double[] armed = new double[0];
    private double candidate = Double.NaN;
    private long candidateInsideSince;
    private double latched = Double.NaN;

    void configure(double[] bins) {
        armed = bins == null ? new double[0] : bins.clone();
        release();
    }

    void release() {
        candidate = Double.NaN;
        candidateInsideSince = 0L;
        latched = Double.NaN;
    }

    double observe(double rpm, long nanoTime) {
        if (Double.isFinite(latched)) return latched;
        if (!Double.isFinite(rpm) || armed.length == 0) {
            clearCandidate();
            return Double.NaN;
        }

        if (Double.isFinite(candidate)) {
            double error = Math.abs(rpm - candidate);
            if (error <= ACQUIRE_TOLERANCE_RPM) {
                if (candidateInsideSince == 0L) candidateInsideSince = nanoTime;
                return candidate;
            }
            if (error <= CANDIDATE_RELEASE_TOLERANCE_RPM) {
                // Hysteresis preserves candidate identity but the latch dwell
                // must restart because the strict acquisition window was left.
                candidateInsideSince = 0L;
                return candidate;
            }
        }

        double next = nearestWithin(rpm, ACQUIRE_TOLERANCE_RPM);
        if (!Double.isFinite(next)) {
            clearCandidate();
            return Double.NaN;
        }
        if (!same(next, candidate)) {
            candidate = next;
            candidateInsideSince = nanoTime;
        } else if (candidateInsideSince == 0L) {
            candidateInsideSince = nanoTime;
        }
        return candidate;
    }

    boolean candidateStable(long nanoTime) {
        return Double.isFinite(candidate)
                && candidateInsideSince != 0L
                && seconds(candidateInsideSince, nanoTime) >= LATCH_DWELL_SECONDS;
    }

    double latch(long nanoTime) {
        if (!candidateStable(nanoTime)) return Double.NaN;
        latched = candidate;
        return latched;
    }

    double candidateRpm() { return candidate; }
    double latchedRpm() { return latched; }
    boolean isLatched() { return Double.isFinite(latched); }
    double displayTargetRpm() { return isLatched() ? latched : candidate; }

    double nearestArmed(double rpm) {
        return nearestWithin(rpm, ACQUIRE_TOLERANCE_RPM);
    }

    String statusText(double liveRpm, long nanoTime) {
        if (isLatched()) {
            return "RPM bin latch: LATCHED " + f0(latched)
                    + " RPM for this event; live RPM may rise after the opening.";
        }
        if (!Double.isFinite(candidate)) {
            return "RPM bin latch: waiting near armed bin(s) " + armedText()
                    + " (±" + f0(ACQUIRE_TOLERANCE_RPM) + " RPM).";
        }
        double dwell = candidateInsideSince == 0L
                ? 0.0 : seconds(candidateInsideSince, nanoTime);
        return "RPM bin latch: candidate " + f0(candidate) + " RPM | hold steady "
                + f2(Math.min(LATCH_DWELL_SECONDS, dwell)) + "/"
                + f2(LATCH_DWELL_SECONDS) + " s"
                + (Double.isFinite(liveRpm)
                    ? " | live " + f0(liveRpm) + " RPM" : "");
    }

    String armedText() {
        if (armed.length == 0) return "none";
        StringBuilder out = new StringBuilder();
        for (double rpm : armed) {
            if (out.length() > 0) out.append(" / ");
            out.append(f0(rpm));
        }
        return out.toString();
    }

    private double nearestWithin(double rpm, double tolerance) {
        double best = Double.NaN;
        double distance = Double.POSITIVE_INFINITY;
        for (double bin : armed) {
            if (!Double.isFinite(bin)) continue;
            double next = Math.abs(rpm - bin);
            if (next <= tolerance && next < distance) {
                best = bin;
                distance = next;
            }
        }
        return best;
    }

    private void clearCandidate() {
        candidate = Double.NaN;
        candidateInsideSince = 0L;
    }

    private static boolean same(double a, double b) {
        return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= 0.5;
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }

    private static String f0(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.0f", value) : "n/a";
    }
    private static String f2(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "n/a";
    }
}
