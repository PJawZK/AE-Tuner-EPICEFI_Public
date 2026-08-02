package se.anders.tunerstudio.aetuner;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Builds a conservative Predictive Map Blend Duration draft from captured events. */
final class MapBlendSuggestion {
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final double MIN_GAP_KPA = 4.0;
    private static final double TARGET_FRACTION = 0.90;
    private static final double MIN_DURATION = 0.08;
    private static final double MAX_DURATION = 0.80;
    private static final int MIN_EVENTS_PER_BIN = 2;
    private static final double MERGE_TRIGGER_GAP_SECONDS = 0.08;
    private static final double MAX_TPS_DROP_DURING_CATCHUP = 5.0;
    private static final double MAX_CATCHUP_SECONDS = 1.50;

    private final boolean available;
    private final String displayText;
    private final String copyPasteBlock;

    private MapBlendSuggestion(boolean available, String displayText, String copyPasteBlock) {
        this.available = available;
        this.displayText = displayText;
        this.copyPasteBlock = copyPasteBlock;
    }

    static MapBlendSuggestion build(AeProjectSnapshot snapshot, List<EventSummary> events) {
        if (snapshot == null || !snapshot.hasBlendDurationCurve()) {
            return unavailable("Predictive Map Blend Duration draft unavailable: project curve was not found.");
        }
        double[] rpmBins = snapshot.getBlendDurationRpmBins();
        double[] current = snapshot.getBlendDurationValues();
        @SuppressWarnings("unchecked")
        List<Double>[] observations = new List[rpmBins.length];
        for (int i = 0; i < observations.length; i++) {
            observations[i] = new ArrayList<Double>();
        }

        int predictionEvents = 0;
        int singleBurstEvents = 0;
        int usableEvents = 0;
        int unresolvedCatchup = 0;
        int tpsHoldEnded = 0;
        int multipleBurstEvents = 0;
        int resetDiscontinuityEvents = 0;
        int eventsWithCounterIncrements = 0;

        for (EventSummary event : events) {
            List<LiveSample> samples = event.getSamples();
            int firstPrediction = firstPredictionIndex(samples);
            if (firstPrediction < 0) {
                continue;
            }
            predictionEvents++;

            CounterMath.Result resetMetrics = event.getPredictionResetMetrics();
            if (resetMetrics.hasDiscontinuity()) {
                resetDiscontinuityEvents++;
                continue;
            }
            if (resetMetrics.getIncrements() > 0) {
                eventsWithCounterIncrements++;
            }

            List<IndexRange> triggerBursts = triggerBursts(samples);
            if (triggerBursts.size() != 1) {
                multipleBurstEvents++;
                continue;
            }
            singleBurstEvents++;

            IndexRange burst = triggerBursts.get(0);
            int anchor = Math.max(burst.end, lastCounterChangeIndex(samples,
                    Math.max(0, burst.start - 1), Math.min(samples.size() - 1, burst.end + 12)));
            if (anchor < firstPrediction) {
                anchor = firstPrediction;
            }

            LiveSample anchorSample = samples.get(anchor);
            double realStart = anchorSample.get(ChannelRole.MAP);
            double predictedStart = anchorSample.get(ChannelRole.FALLBACK_MAP);
            if (!Double.isFinite(realStart) || !Double.isFinite(predictedStart)
                    || predictedStart - realStart < MIN_GAP_KPA) {
                continue;
            }

            double threshold = realStart + TARGET_FRACTION * (predictedStart - realStart);
            double anchorTps = anchorSample.get(ChannelRole.TPS);
            int catchIndex = -1;
            boolean holdEnded = false;
            for (int i = anchor + 1; i < samples.size(); i++) {
                LiveSample sample = samples.get(i);
                double elapsed = secondsBetween(sample, anchorSample);
                if (elapsed > MAX_CATCHUP_SECONDS) {
                    break;
                }
                double tps = sample.get(ChannelRole.TPS);
                if (Double.isFinite(anchorTps) && Double.isFinite(tps)
                        && tps < anchorTps - MAX_TPS_DROP_DURING_CATCHUP) {
                    holdEnded = true;
                    break;
                }
                double realMap = sample.get(ChannelRole.MAP);
                if (Double.isFinite(realMap) && realMap >= threshold) {
                    catchIndex = i;
                    break;
                }
            }
            if (catchIndex < 0) {
                if (holdEnded) {
                    tpsHoldEnded++;
                } else {
                    unresolvedCatchup++;
                }
                continue;
            }

            double duration = secondsBetween(samples.get(catchIndex), anchorSample);
            if (duration <= 0.0 || duration > MAX_CATCHUP_SECONDS) {
                continue;
            }
            duration = clamp(duration + 0.02, MIN_DURATION, MAX_DURATION);
            double rpm = medianRpm(samples, anchor, catchIndex);
            if (!Double.isFinite(rpm)) {
                continue;
            }
            observations[nearest(rpmBins, rpm)].add(duration);
            usableEvents++;
        }

        double[] proposed = current.clone();
        int changed = 0;
        StringBuilder perBin = new StringBuilder();
        for (int i = 0; i < rpmBins.length; i++) {
            List<Double> values = observations[i];
            if (values.size() >= MIN_EVENTS_PER_BIN) {
                double median = median(values);
                proposed[i] = round2(clamp(median, MIN_DURATION, MAX_DURATION));
                if (Math.abs(proposed[i] - current[i]) >= 0.01) {
                    changed++;
                }
                perBin.append(F2.format(rpmBins[i])).append(" RPM: ")
                        .append(values.size()).append(" single-opening event(s), median post-trigger catch-up ")
                        .append(F2.format(median)).append(" s, ")
                        .append(F2.format(current[i])).append(" -> ")
                        .append(F2.format(proposed[i])).append(" s.\n");
            } else {
                perBin.append(F2.format(rpmBins[i])).append(" RPM: ")
                        .append(values.size()).append(" usable single-opening event(s), unchanged at ")
                        .append(F2.format(current[i])).append(" s.\n");
            }
        }

        if (usableEvents == 0) {
            return unavailable("Predictive Map Blend Duration draft unavailable: no single detector-burst event reached 90% of its post-trigger measured-MAP to fallbackMap gap while throttle remained held. Counter increments inside one continuous TPS-change burst are expected and are no longer treated as repeated pedal stabs. Collect several single, held tip-ins per RPM region after refining the MAP Estimate table.");
        }

        StringBuilder copy = new StringBuilder();
        for (int i = 0; i < proposed.length; i++) {
            if (i > 0) {
                copy.append('\n');
            }
            copy.append(F2.format(proposed[i]));
        }

        StringBuilder report = new StringBuilder();
        report.append("Predictive Map Blend Duration draft copied to clipboard.\n")
                .append("Values are in ascending RPM-bin order for direct TunerStudio paste.\n")
                .append("Method: one continuous TPS-change detector burst only. The measurement starts after the final reset/above-threshold sample, then measures until real MAP reaches 90% of the remaining real-MAP to fallbackMap gap, plus a conservative 0.02 s margin.\n")
                .append("Important: multiple predTimerResetCnt increments during one continuous detector burst are normal firmware behavior and are not classified as repeated stabs. Separate detector bursts inside the same captured event are excluded from the base curve.\n")
                .append("Prediction events found: ").append(predictionEvents)
                .append(" | one-burst events: ").append(singleBurstEvents)
                .append(" | usable catch-up events: ").append(usableEvents)
                .append(" | multiple-burst/repeated-stab events: ").append(multipleBurstEvents)
                .append(" | throttle released before catch-up: ").append(tpsHoldEnded)
                .append(" | unresolved catch-up: ").append(unresolvedCatchup)
                .append(" | events with normal counter increments: ").append(eventsWithCounterIncrements)
                .append(" | reset-counter discontinuities: ").append(resetDiscontinuityEvents)
                .append(" | changed bins: ").append(changed).append(".\n\n")
                .append(perBin)
                .append("\nReview before pasting. Tune the MAP Estimate table first: if fallbackMap is too high for the held throttle position, extending Blend Duration would preserve an incorrect airmass estimate rather than fix it.");
        return new MapBlendSuggestion(true, report.toString(), copy.toString());
    }

    private static List<IndexRange> triggerBursts(List<LiveSample> samples) {
        List<IndexRange> raw = new ArrayList<IndexRange>();
        int start = -1;
        for (int i = 0; i < samples.size(); i++) {
            if (aboveThreshold(samples.get(i))) {
                if (start < 0) {
                    start = i;
                }
            } else if (start >= 0) {
                raw.add(new IndexRange(start, i - 1));
                start = -1;
            }
        }
        if (start >= 0) {
            raw.add(new IndexRange(start, samples.size() - 1));
        }
        if (raw.size() <= 1) {
            return raw;
        }

        List<IndexRange> merged = new ArrayList<IndexRange>();
        IndexRange current = raw.get(0);
        for (int i = 1; i < raw.size(); i++) {
            IndexRange next = raw.get(i);
            double gap = secondsBetween(samples.get(next.start), samples.get(current.end));
            if (gap <= MERGE_TRIGGER_GAP_SECONDS) {
                current = new IndexRange(current.start, next.end);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static boolean aboveThreshold(LiveSample sample) {
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) {
            return true;
        }
        double smoothed = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        return Double.isFinite(smoothed) && Double.isFinite(threshold)
                && threshold > 0.0 && smoothed >= threshold;
    }

    private static int lastCounterChangeIndex(List<LiveSample> samples, int start, int end) {
        int last = start;
        Integer previous = null;
        for (int i = Math.max(0, start); i <= end && i < samples.size(); i++) {
            double raw = samples.get(i).get(ChannelRole.MAP_PRED_RESET_CNT);
            if (!Double.isFinite(raw)) {
                continue;
            }
            int current = normalizeCounter(raw);
            if (previous != null && current != previous.intValue()) {
                last = i;
            }
            previous = Integer.valueOf(current);
        }
        return last;
    }

    private static int normalizeCounter(double value) {
        int rounded = (int) Math.round(value);
        int normalized = rounded % 256;
        return normalized < 0 ? normalized + 256 : normalized;
    }

    private static int firstPredictionIndex(List<LiveSample> samples) {
        for (int i = 0; i < samples.size(); i++) {
            if (samples.get(i).bool(ChannelRole.MAP_PRED_ACTIVE)) {
                return i;
            }
        }
        return -1;
    }

    private static double medianRpm(List<LiveSample> samples, int start, int end) {
        double[] values = new double[Math.max(0, end - start + 1)];
        int count = 0;
        for (int i = start; i <= end && i < samples.size(); i++) {
            double rpm = samples.get(i).get(ChannelRole.RPM);
            if (Double.isFinite(rpm) && rpm >= 400.0) {
                values[count++] = rpm;
            }
        }
        if (count == 0) {
            return Double.NaN;
        }
        Arrays.sort(values, 0, count);
        return count % 2 == 1 ? values[count / 2] : (values[count / 2 - 1] + values[count / 2]) / 2.0;
    }

    private static double median(List<Double> values) {
        List<Double> copy = new ArrayList<Double>(values);
        Collections.sort(copy);
        int middle = copy.size() / 2;
        return copy.size() % 2 == 1 ? copy.get(middle) : (copy.get(middle - 1) + copy.get(middle)) / 2.0;
    }

    private static int nearest(double[] values, double value) {
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            double distance = Math.abs(values[i] - value);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double secondsBetween(LiveSample newer, LiveSample older) {
        return Math.max(0.0, (newer.getNanoTime() - older.getNanoTime()) / 1000000000.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static MapBlendSuggestion unavailable(String reason) {
        return new MapBlendSuggestion(false, reason, "");
    }

    boolean isAvailable() { return available; }
    String getDisplayText() { return displayText; }
    String getCopyPasteBlock() { return copyPasteBlock; }

    private static final class IndexRange {
        final int start;
        final int end;

        IndexRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
