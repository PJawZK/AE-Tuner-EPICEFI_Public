package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds conservative per-RPM Predictive Map Blend Duration evidence. */
public final class MapBlendSuggestion {
    private static final DecimalFormat F0 = new DecimalFormat("0");
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final double MIN_GAP_KPA = 4.0;
    private static final double TARGET_FRACTION = 0.90;
    private static final double MERGE_TRIGGER_GAP_SECONDS = 0.08;
    private static final double MAX_TPS_DROP_DURING_CATCHUP = 5.0;
    private static final double MAX_CATCHUP_SECONDS = 1.50;

    private final boolean available;
    private final String displayText;
    private final String copyPasteBlock;

    private MapBlendSuggestion(boolean available, String displayText,
                               String copyPasteBlock) {
        this.available = available;
        this.displayText = displayText;
        this.copyPasteBlock = copyPasteBlock;
    }

    public static MapBlendSuggestion build(AeProjectSnapshot snapshot,
                                    List<TransientEvent> events) {
        if (snapshot == null || !snapshot.hasBlendDurationCurve()) {
            return unavailable("Predictive Map Blend Duration evidence unavailable: project curve was not found.");
        }

        double[] rpmPoints = snapshot.getBlendDurationRpmBins();
        double[] current = snapshot.getBlendDurationValues();
        BinEvidence[] bins = new BinEvidence[rpmPoints.length];
        for (int i = 0; i < bins.length; i++) {
            bins[i] = new BinEvidence(rpmPoints[i]);
        }

        int predictionEvents = 0;
        int singleBurstEvents = 0;
        int usableEvents = 0;
        int multipleBurstEvents = 0;
        int resetDiscontinuityEvents = 0;
        int eventsWithCounterIncrements = 0;
        int missingRpmEvents = 0;

        for (TransientEvent event : events) {
            List<LiveSample> samples = event.getSamples();
            int firstPrediction = firstPredictionIndex(samples);
            if (firstPrediction < 0) {
                continue;
            }
            predictionEvents++;

            double eventRpm = event.getMedianPredictionRpm();
            if (!Double.isFinite(eventRpm)) {
                eventRpm = medianRpm(samples, firstPrediction,
                        samples.size() - 1);
            }
            int binIndex = Double.isFinite(eventRpm)
                    ? nearest(rpmPoints, eventRpm) : -1;
            BinEvidence bin = binIndex >= 0 ? bins[binIndex] : null;
            if (bin == null) {
                missingRpmEvents++;
            } else {
                bin.predictionEvents++;
                bin.predictionRpms.add(Double.valueOf(eventRpm));
            }

            CounterMath.Result resetMetrics = event.getPredictionResetMetrics();
            if (resetMetrics.hasDiscontinuity()) {
                resetDiscontinuityEvents++;
                reject(bin, "reset-counter discontinuity");
                continue;
            }
            if (resetMetrics.getIncrements() > 0) {
                eventsWithCounterIncrements++;
            }

            List<IndexRange> triggerBursts = triggerBursts(samples);
            if (triggerBursts.isEmpty()) {
                reject(bin, "no detector burst");
                continue;
            }
            if (triggerBursts.size() != 1) {
                multipleBurstEvents++;
                reject(bin, "multiple detector bursts / repeated stab");
                continue;
            }
            singleBurstEvents++;

            IndexRange burst = triggerBursts.get(0);
            int anchor = Math.max(burst.end, lastCounterChangeIndex(samples,
                    Math.max(0, burst.start - 1),
                    Math.min(samples.size() - 1, burst.end + 12)));
            if (anchor < firstPrediction) {
                anchor = firstPrediction;
            }

            LiveSample anchorSample = samples.get(anchor);
            double realStart = anchorSample.get(ChannelRole.MAP);
            double predictedStart = anchorSample.get(ChannelRole.FALLBACK_MAP);
            if (!Double.isFinite(realStart) || !Double.isFinite(predictedStart)
                    || predictedStart - realStart < MIN_GAP_KPA) {
                reject(bin, "insufficient measured-MAP to fallbackMap gap");
                continue;
            }

            double threshold = realStart
                    + TARGET_FRACTION * (predictedStart - realStart);
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
                reject(bin, holdEnded
                        ? "throttle released before catch-up"
                        : "catch-up unresolved within 1.50 s");
                continue;
            }

            double duration = secondsBetween(samples.get(catchIndex),
                    anchorSample);
            if (duration <= 0.0 || duration > MAX_CATCHUP_SECONDS) {
                reject(bin, "invalid catch-up duration");
                continue;
            }
            double rpm = medianRpm(samples, anchor, catchIndex);
            if (!Double.isFinite(rpm)) {
                reject(bin, "missing RPM during catch-up");
                missingRpmEvents++;
                continue;
            }

            BinEvidence actualBin = bin;
            if (actualBin == null) {
                actualBin = bins[nearest(rpmPoints, rpm)];
                actualBin.predictionEvents++;
                actualBin.predictionRpms.add(Double.valueOf(rpm));
            }
            actualBin.durations.add(Double.valueOf(duration));
            actualBin.usableRpms.add(Double.valueOf(rpm));
            usableEvents++;
        }

        double[] proposed = current.clone();
        int eligiblePoints = 0;
        int highConfidencePoints = 0;
        int changedPoints = 0;
        int outlierCount = 0;
        StringBuilder perPoint = new StringBuilder();

        for (int i = 0; i < rpmPoints.length; i++) {
            BinEvidence bin = bins[i];
            BlendDurationPolicy.Evaluation evaluation =
                    BlendDurationPolicy.evaluate(bin.durations);
            BlendDurationPolicy.Stats stats = evaluation.stats;
            BlendDurationPolicy.Confidence confidence = evaluation.confidence;
            outlierCount += stats.outlierCount;
            boolean eligible = evaluation.eligible;
            if (eligible) {
                eligiblePoints++;
                if (confidence == BlendDurationPolicy.Confidence.HIGH) {
                    highConfidencePoints++;
                }
                proposed[i] = evaluation.proposedValue;
                if (Math.abs(proposed[i] - current[i]) >= 0.01) {
                    changedPoints++;
                }
            }

            perPoint.append(formatPointEvidence(rpmPoints, i, bin, stats,
                    confidence, current[i], proposed[i], eligible));
        }

        StringBuilder report = new StringBuilder();
        report.append("Predictive Map Blend Duration per-RPM evidence.\n")
                .append("Method: usable events must contain one continuous TPS-change detector burst, a held throttle, coherent reset-counter data, and measured MAP reaching 90% of the post-trigger measured-MAP to fallbackMap gap. Catch-up statistics use measured durations; a 0.02 s conservative margin and the 0.08-0.80 s table bounds apply only to an eligible final proposed value.\n")
                .append("Axis handling: each usable event is assigned to the nearest actual Predictive Map Blend Duration table RPM point. No interpolation or smoothing is applied. Unsupported or ineligible RPM points remain exactly unchanged.\n")
                .append("Eligibility: at least ")
                .append(BlendDurationPolicy.MIN_EVENTS_FOR_PROPOSAL)
                .append(" retained single-held-opening events at one RPM point, with duration range <= ")
                .append(F2.format(BlendDurationPolicy.MAX_ELIGIBLE_RANGE_SECONDS))
                .append(" s, IQR <= ")
                .append(F2.format(BlendDurationPolicy.MAX_ELIGIBLE_IQR_SECONDS))
                .append(" s, and standard deviation <= ")
                .append(F2.format(BlendDurationPolicy.MAX_ELIGIBLE_STDDEV_SECONDS))
                .append(" s. High confidence additionally requires ")
                .append(BlendDurationPolicy.MIN_EVENTS_HIGH_CONFIDENCE)
                .append(" retained events and tighter spread.\n")
                .append("Repeated-stab policy: multiple detector bursts remain visible diagnostically but never define the base curve. Normal predTimerResetCnt increments inside one continuous detector burst are allowed.\n\n")
                .append("Prediction events: ").append(predictionEvents)
                .append(" | one-burst events: ").append(singleBurstEvents)
                .append(" | usable held-opening events: ").append(usableEvents)
                .append(" | repeated-stab/multiple-burst events: ")
                .append(multipleBurstEvents)
                .append(" | reset discontinuities: ")
                .append(resetDiscontinuityEvents)
                .append(" | events with normal counter increments: ")
                .append(eventsWithCounterIncrements)
                .append(" | missing-RPM events: ").append(missingRpmEvents)
                .append(" | statistical outliers excluded: ")
                .append(outlierCount)
                .append(" | eligible RPM points: ").append(eligiblePoints)
                .append(" | high-confidence RPM points: ")
                .append(highConfidencePoints)
                .append(" | changed RPM points: ").append(changedPoints)
                .append(".\n\n")
                .append(perPoint);

        if (eligiblePoints == 0) {
            report.append("\nNo paste-ready Blend Duration proposal is available. Collect controlled single held tip-ins at the actual table RPM points. One event cannot create a medium- or high-confidence proposal.");
            return new MapBlendSuggestion(false, report.toString(), "");
        }

        StringBuilder copy = new StringBuilder();
        for (int i = 0; i < proposed.length; i++) {
            if (i > 0) {
                copy.append('\n');
            }
            copy.append(F2.format(proposed[i]));
        }

        report.append("\nPaste-ready values are available only for eligible RPM points; every unsupported or ineligible point is preserved from the current curve. Review the complete per-RPM evidence before pasting. Tune MAP Estimate first when fallbackMap is implausible for the held throttle position.");
        return new MapBlendSuggestion(true, report.toString(), copy.toString());
    }

    private static String formatPointEvidence(
            double[] rpmPoints,
            int index,
            BinEvidence bin,
            BlendDurationPolicy.Stats stats,
            BlendDurationPolicy.Confidence confidence,
            double current,
            double proposed,
            boolean eligible) {
        StringBuilder text = new StringBuilder();
        text.append(F0.format(rpmPoints[index])).append(" RPM point")
                .append(" (region ").append(regionText(rpmPoints, index))
                .append("):\n")
                .append("  Prediction events in region: ")
                .append(bin.predictionEvents)
                .append(" | usable held openings: ")
                .append(bin.durations.size())
                .append(" raw / ").append(stats.retainedCount)
                .append(" retained")
                .append(" | outliers: ").append(stats.outlierCount)
                .append(".\n")
                .append("  RPM coverage: ")
                .append(coverageText(bin.usableRpms)).append(".\n");

        if (stats.retainedCount > 0) {
            text.append("  Catch-up duration: median ")
                    .append(F2.format(stats.median))
                    .append(" s, mean ").append(F2.format(stats.mean))
                    .append(" s, range ").append(F2.format(stats.minimum))
                    .append("-").append(F2.format(stats.maximum))
                    .append(" s, IQR ").append(F2.format(stats.iqr))
                    .append(" s, SD ")
                    .append(F2.format(stats.standardDeviation)).append(" s.\n");
        } else {
            text.append("  Catch-up duration: no retained samples.\n");
        }

        text.append("  Confidence: ").append(confidence.label)
                .append(" | eligibility: ")
                .append(eligible ? "ELIGIBLE" : "WITHHELD")
                .append(" | curve value: ").append(F2.format(current))
                .append(" -> ").append(F2.format(proposed)).append(" s.\n")
                .append("  Decision: ")
                .append(decisionReason(stats, confidence, eligible))
                .append("\n")
                .append("  Rejections: ")
                .append(rejectionText(bin.rejections)).append("\n\n");
        return text.toString();
    }

    private static String decisionReason(
            BlendDurationPolicy.Stats stats,
            BlendDurationPolicy.Confidence confidence,
            boolean eligible) {
        if (eligible) {
            return confidence == BlendDurationPolicy.Confidence.HIGH
                    ? "high-confidence retained median may define this RPM point"
                    : "medium-confidence retained median may define this RPM point";
        }
        if (stats.retainedCount < BlendDurationPolicy.MIN_EVENTS_FOR_PROPOSAL) {
            return "need at least "
                    + BlendDurationPolicy.MIN_EVENTS_FOR_PROPOSAL
                    + " retained single-held-opening events";
        }
        if (stats.range > BlendDurationPolicy.MAX_ELIGIBLE_RANGE_SECONDS) {
            return "withheld because catch-up range is too wide";
        }
        if (stats.iqr > BlendDurationPolicy.MAX_ELIGIBLE_IQR_SECONDS) {
            return "withheld because catch-up IQR is too wide";
        }
        if (stats.standardDeviation
                > BlendDurationPolicy.MAX_ELIGIBLE_STDDEV_SECONDS) {
            return "withheld because catch-up standard deviation is too high";
        }
        return "withheld because evidence is insufficient";
    }

    private static String regionText(double[] rpmPoints, int index) {
        if (rpmPoints.length == 1) {
            return "all RPM";
        }
        if (index == 0) {
            return "<= " + F0.format(midpoint(rpmPoints[0], rpmPoints[1]))
                    + " RPM";
        }
        if (index == rpmPoints.length - 1) {
            return "> " + F0.format(midpoint(
                    rpmPoints[index - 1], rpmPoints[index])) + " RPM";
        }
        return "> " + F0.format(midpoint(
                rpmPoints[index - 1], rpmPoints[index]))
                + " to <= " + F0.format(midpoint(
                rpmPoints[index], rpmPoints[index + 1])) + " RPM";
    }

    private static double midpoint(double a, double b) {
        return a + (b - a) / 2.0;
    }

    private static String coverageText(List<Double> values) {
        if (values.isEmpty()) {
            return "none";
        }
        List<Double> sorted = new ArrayList<Double>(values);
        Collections.sort(sorted);
        return F0.format(sorted.get(0).doubleValue()) + "-"
                + F0.format(sorted.get(sorted.size() - 1).doubleValue())
                + " RPM, median "
                + F0.format(percentile(sorted, 0.50)) + " RPM";
    }

    private static String rejectionText(Map<String, Integer> rejections) {
        if (rejections.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : rejections.entrySet()) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return text.toString();
    }

    private static void reject(BinEvidence bin, String reason) {
        if (bin == null) {
            return;
        }
        Integer count = bin.rejections.get(reason);
        bin.rejections.put(reason, Integer.valueOf(
                count == null ? 1 : count.intValue() + 1));
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
            double gap = secondsBetween(samples.get(next.start),
                    samples.get(current.end));
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

    private static int lastCounterChangeIndex(List<LiveSample> samples,
                                              int start, int end) {
        int last = start;
        Integer previous = null;
        for (int i = Math.max(0, start);
             i <= end && i < samples.size(); i++) {
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

    private static double medianRpm(List<LiveSample> samples,
                                    int start, int end) {
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
        return count % 2 == 1
                ? values[count / 2]
                : (values[count / 2 - 1] + values[count / 2]) / 2.0;
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
        return Math.max(0.0,
                (newer.getNanoTime() - older.getNanoTime()) / 1000000000.0);
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.isEmpty()) {
            return Double.NaN;
        }
        if (sorted.size() == 1) {
            return sorted.get(0).doubleValue();
        }
        double position = (sorted.size() - 1) * fraction;
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted.get(lower).doubleValue();
        }
        double weight = position - lower;
        return sorted.get(lower).doubleValue() * (1.0 - weight)
                + sorted.get(upper).doubleValue() * weight;
    }

    private static MapBlendSuggestion unavailable(String reason) {
        return new MapBlendSuggestion(false, reason, "");
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getCopyPasteBlock() {
        return copyPasteBlock;
    }

    private static final class BinEvidence {
        final double rpmPoint;
        final List<Double> predictionRpms = new ArrayList<Double>();
        final List<Double> usableRpms = new ArrayList<Double>();
        final List<Double> durations = new ArrayList<Double>();
        final Map<String, Integer> rejections =
                new LinkedHashMap<String, Integer>();
        int predictionEvents;

        BinEvidence(double rpmPoint) {
            this.rpmPoint = rpmPoint;
        }
    }

    private static final class IndexRange {
        final int start;
        final int end;

        IndexRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
