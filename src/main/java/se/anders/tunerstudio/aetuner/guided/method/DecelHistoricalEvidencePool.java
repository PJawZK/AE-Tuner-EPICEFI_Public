package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Combines already-classified, threshold-independent physical Decel events from
 * separately qualified historical sessions. Raw samples are deliberately not
 * concatenated before event extraction: each session keeps its own quiet-noise
 * calibration and physical classification, then independent events are pooled.
 */
public final class DecelHistoricalEvidencePool {
    private static final int MIN_EVENTS_PER_CLASS = 3;
    private static final double NOISE_GUARD = 1.25;
    private static final double RELEASE_GUARD = 0.80;
    private static final double MIN_SEPARATION = 0.02;

    private static final class MutableBin {
        final List<Double> normalPeaks = new ArrayList<Double>();
        final List<Double> releasePeaks = new ArrayList<Double>();
        final List<DecelHistoricalEvidenceAnalyzer.Event> events =
                new ArrayList<DecelHistoricalEvidenceAnalyzer.Event>();
        int normalCrossings;
        int releaseMisses;
        int ambiguous;
    }

    private DecelHistoricalEvidencePool() { }

    public static DecelHistoricalEvidenceAnalyzer.Result combine(
            AeProjectSnapshot snapshot,
            List<DecelHistoricalEvidenceAnalyzer.Result> sessions) {
        if (snapshot == null || !snapshot.hasDecelThresholdCurve()) {
            return unavailable("Working Tune Decel threshold curve is unavailable.");
        }
        if (sessions == null || sessions.size() < 2) {
            return unavailable("At least two qualified historical analyses are required for pooling.");
        }

        double[] rpmBins = snapshot.getDecelThresholdRpmBins();
        MutableBin[] mutable = new MutableBin[rpmBins.length];
        for (int i = 0; i < mutable.length; i++) mutable[i] = new MutableBin();

        int validSamples = 0;
        int quietSamples = 0;
        double quietDuration = 0.0;
        double quietP95 = Double.NaN;
        double quietP99 = Double.NaN;
        double moveFloor = Double.NaN;
        double releaseFloor = Double.NaN;
        boolean quietLocked = true;
        int movementEvents = 0;
        int normalCount = 0;
        int releaseCount = 0;
        int ambiguousCount = 0;

        for (DecelHistoricalEvidenceAnalyzer.Result session : sessions) {
            if (session == null || !session.quietCalibrationLocked) {
                quietLocked = false;
                continue;
            }
            validSamples += session.validSamples;
            quietSamples += session.quietSamples;
            quietDuration += session.quietDurationSeconds;
            quietP95 = maxFinite(quietP95, session.quietRateP95);
            quietP99 = maxFinite(quietP99, session.quietRateP99);
            moveFloor = maxFinite(moveFloor, session.movementRateFloor);
            releaseFloor = maxFinite(releaseFloor, session.releaseRateFloor);

            for (DecelHistoricalEvidenceAnalyzer.BinSummary sourceBin : session.bins) {
                if (sourceBin == null || sourceBin.events == null) continue;
                for (DecelHistoricalEvidenceAnalyzer.Event event : sourceBin.events) {
                    if (event == null || !Double.isFinite(event.startRpm)
                            || !Double.isFinite(event.peakMagnitude)) continue;
                    MutableBin target = mutable[binForRpm(rpmBins, event.startRpm)];
                    target.events.add(event);
                    movementEvents++;
                    if (DecelHistoricalEvidenceAnalyzer.CLASS_NORMAL_CORRECTION.equals(event.eventClass)) {
                        target.normalPeaks.add(Double.valueOf(event.peakMagnitude));
                        normalCount++;
                        if (event.currentThresholdCrossed) target.normalCrossings++;
                    } else if (DecelHistoricalEvidenceAnalyzer.CLASS_DECEL_RELEASE.equals(event.eventClass)) {
                        target.releasePeaks.add(Double.valueOf(event.peakMagnitude));
                        releaseCount++;
                        if (Double.isFinite(event.currentThreshold) && event.currentThreshold > 0.0
                                && !event.currentThresholdCrossed) target.releaseMisses++;
                    } else {
                        target.ambiguous++;
                        ambiguousCount++;
                    }
                }
            }
        }

        if (!quietLocked) {
            return unavailable("Every pooled session must have a locked per-session quiet calibration before its physical events can be combined.");
        }

        List<DecelHistoricalEvidenceAnalyzer.BinSummary> bins =
                new ArrayList<DecelHistoricalEvidenceAnalyzer.BinSummary>();
        int separatedBins = 0;
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < mutable.length; i++) {
            MutableBin bin = mutable[i];
            double normalP95 = percentile(bin.normalPeaks, 0.95);
            double releaseP25 = percentile(bin.releasePeaks, 0.25);
            double low = Double.isFinite(normalP95) ? normalP95 * NOISE_GUARD : Double.NaN;
            double high = Double.isFinite(releaseP25) ? releaseP25 * RELEASE_GUARD : Double.NaN;
            boolean separated = bin.normalPeaks.size() >= MIN_EVENTS_PER_CLASS
                    && bin.releasePeaks.size() >= MIN_EVENTS_PER_CLASS
                    && separated(low, high);
            if (separated) separatedBins++;

            DecelHistoricalEvidenceAnalyzer.BinSummary summary =
                    new DecelHistoricalEvidenceAnalyzer.BinSummary(
                            rpmBins[i], bin.normalPeaks.size(), bin.releasePeaks.size(),
                            bin.ambiguous, bin.normalCrossings, bin.releaseMisses,
                            normalP95, releaseP25, low, high, separated, bin.events);
            bins.add(summary);
            if (!bin.events.isEmpty()) {
                rows.append("  ").append(Math.round(rpmBins[i])).append(" RPM: normal ")
                        .append(bin.normalPeaks.size()).append(", release ")
                        .append(bin.releasePeaks.size()).append(", ambiguous ")
                        .append(bin.ambiguous).append(", normal crossings ")
                        .append(bin.normalCrossings).append(", release misses ")
                        .append(bin.releaseMisses).append(", pooled normal p95 ")
                        .append(fmt(normalP95)).append(", pooled release p25 ")
                        .append(fmt(releaseP25)).append(", safe window ")
                        .append(fmt(low)).append("..").append(fmt(high))
                        .append(" | ").append(separated ? "SEPARATED" : "WITHHOLD")
                        .append('\n');
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("DECEL CROSS-LOG PHYSICAL-EVENT POOL — READ ONLY\n")
                .append("Sessions pooled: ").append(sessions.size()).append('\n')
                .append("Each session was classified independently with its own locked quiet baseline. Raw samples were not concatenated before classification. Only the resulting independent physical events are combined.\n")
                .append("Conservative per-session quiet summary (max p95/p99): ")
                .append(fmt(quietP95)).append(" / ").append(fmt(quietP99)).append(" %/s\n")
                .append("Conservative movement/release floor summary (max): ")
                .append(fmt(moveFloor)).append(" / ").append(fmt(releaseFloor)).append(" %/s\n")
                .append("Combined physical events: ").append(movementEvents)
                .append(" | normal corrections: ").append(normalCount)
                .append(" | clear releases: ").append(releaseCount)
                .append(" | ambiguous: ").append(ambiguousCount).append("\n\n")
                .append("POOLED RPM-BIN EVIDENCE\n").append(rows)
                .append("Separated bins: ").append(separatedBins).append(" / ")
                .append(rpmBins.length).append(".\n")
                .append("Boundary: pooled evidence remains read-only. It creates no ProposalWritePlan and gains no write authority until the normal explicit historical Review boundary is passed.");

        return new DecelHistoricalEvidenceAnalyzer.Result(
                report.toString(), validSamples, true, quietSamples, quietDuration,
                quietP95, quietP99, moveFloor, releaseFloor,
                movementEvents, normalCount, releaseCount, ambiguousCount,
                separatedBins, bins);
    }

    private static int binForRpm(double[] bins, double rpm) {
        int best = 0;
        double distance = Math.abs(rpm - bins[0]);
        for (int i = 1; i < bins.length; i++) {
            double next = Math.abs(rpm - bins[i]);
            if (next < distance) { best = i; distance = next; }
        }
        return best;
    }

    private static boolean separated(double low, double high) {
        if (!Double.isFinite(low) || !Double.isFinite(high)) return false;
        double required = Math.max(MIN_SEPARATION, Math.max(0.0, low) * 0.15);
        return high - low >= required;
    }

    private static double percentile(List<Double> values, double q) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>();
        for (Double value : values) {
            if (value != null && Double.isFinite(value.doubleValue())) copy.add(value);
        }
        if (copy.isEmpty()) return Double.NaN;
        Collections.sort(copy);
        if (copy.size() == 1) return copy.get(0).doubleValue();
        double position = Math.max(0.0, Math.min(1.0, q)) * (copy.size() - 1);
        int low = (int)Math.floor(position);
        int high = (int)Math.ceil(position);
        if (low == high) return copy.get(low).doubleValue();
        double fraction = position - low;
        return copy.get(low).doubleValue()
                + (copy.get(high).doubleValue() - copy.get(low).doubleValue()) * fraction;
    }

    private static double maxFinite(double a, double b) {
        if (!Double.isFinite(a)) return b;
        if (!Double.isFinite(b)) return a;
        return Math.max(a, b);
    }

    private static DecelHistoricalEvidenceAnalyzer.Result unavailable(String reason) {
        return new DecelHistoricalEvidenceAnalyzer.Result(
                "DECEL CROSS-LOG PHYSICAL-EVENT POOL — READ ONLY\n"
                        + (reason == null ? "Pooled evidence is unavailable." : reason)
                        + "\nNo ProposalWritePlan is produced and no Working Tune value is changed.",
                0, false, 0, 0.0, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, 0, 0, 0, 0, 0,
                Collections.<DecelHistoricalEvidenceAnalyzer.BinSummary>emptyList());
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
