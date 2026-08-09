package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only proposal adapter for a controlled Guided Capture Blend Duration
 * series. Passive and guided proposals both use {@link BlendDurationPolicy}.
 */
final class GuidedBlendProposal {
    private static final DecimalFormat F0 = new DecimalFormat("0");
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");

    private final boolean available;
    private final String displayText;
    private final String copyPasteBlock;

    private GuidedBlendProposal(boolean available, String displayText,
                                String copyPasteBlock) {
        this.available = available;
        this.displayText = displayText;
        this.copyPasteBlock = copyPasteBlock;
    }

    boolean isAvailable() {
        return available;
    }

    String getDisplayText() {
        return displayText;
    }

    String getCopyPasteBlock() {
        return copyPasteBlock;
    }

    static List<PointChoice> points(AeProjectSnapshot snapshot) {
        List<PointChoice> result = new ArrayList<PointChoice>();
        if (snapshot == null || !snapshot.hasBlendDurationCurve()) {
            return result;
        }
        double[] rpm = snapshot.getBlendDurationRpmBins();
        double[] values = snapshot.getBlendDurationValues();
        for (int i = 0; i < rpm.length; i++) {
            result.add(new PointChoice(i, rpm[i], values[i],
                    lowerBoundary(rpm, i), upperBoundary(rpm, i)));
        }
        return result;
    }

    static GuidedBlendProposal build(AeProjectSnapshot snapshot, int pointIndex,
                                     List<Double> acceptedDurations) {
        if (snapshot == null || !snapshot.hasBlendDurationCurve()) {
            return unavailable("Guided Blend Duration proposal unavailable: "
                    + "read the current project curve first.");
        }
        double[] rpm = snapshot.getBlendDurationRpmBins();
        double[] current = snapshot.getBlendDurationValues();
        if (pointIndex < 0 || pointIndex >= rpm.length) {
            return unavailable("Guided Blend Duration proposal unavailable: "
                    + "select one actual table RPM point.");
        }

        List<Double> raw = acceptedDurations == null
                ? Collections.<Double>emptyList()
                : new ArrayList<Double>(acceptedDurations);
        BlendDurationPolicy.Evaluation evaluation =
                BlendDurationPolicy.evaluate(raw);
        BlendDurationPolicy.Stats stats = evaluation.stats;
        BlendDurationPolicy.Confidence confidence = evaluation.confidence;
        boolean eligible = evaluation.eligible;

        double[] proposed = current.clone();
        if (eligible) {
            proposed[pointIndex] = evaluation.proposedValue;
        }

        PointChoice point = new PointChoice(pointIndex, rpm[pointIndex],
                current[pointIndex], lowerBoundary(rpm, pointIndex),
                upperBoundary(rpm, pointIndex));

        StringBuilder text = new StringBuilder();
        text.append("CONTROLLED GUIDED BLEND DURATION PROPOSAL\n")
                .append("Selected actual table point: ")
                .append(F0.format(point.rpm)).append(" RPM\n")
                .append("Assignment region: ").append(point.regionText()).append("\n")
                .append("Current curve value: ").append(F2.format(point.currentValue))
                .append(" s\n")
                .append("Accepted comparable guided events: ").append(raw.size())
                .append(" raw / ").append(stats.retainedCount).append(" retained")
                .append(" | statistical outliers: ").append(stats.outlierCount)
                .append("\n");

        if (stats.retainedCount > 0) {
            text.append("Measured catch-up durations: median ")
                    .append(F3.format(stats.median)).append(" s, mean ")
                    .append(F3.format(stats.mean)).append(" s, range ")
                    .append(F3.format(stats.minimum)).append("-")
                    .append(F3.format(stats.maximum)).append(" s, IQR ")
                    .append(F3.format(stats.iqr)).append(" s, SD ")
                    .append(F3.format(stats.standardDeviation)).append(" s\n");
        } else {
            text.append("Measured catch-up durations: no retained events.\n");
        }

        text.append("Confidence: ").append(confidence.label)
                .append(" | eligibility: ")
                .append(eligible ? "ELIGIBLE" : "WITHHELD").append("\n")
                .append("Decision: ").append(decision(stats, confidence)).append("\n");

        if (eligible) {
            text.append("Selected point proposal: ")
                    .append(F2.format(current[pointIndex])).append(" -> ")
                    .append(F2.format(proposed[pointIndex])).append(" s\n")
                    .append("The 0.02 s margin, 0.08-0.80 s bounds, and rounding "
                            + "were applied only to this eligible final value.\n")
                    .append("Every other table point remains exactly unchanged. "
                            + "No interpolation or smoothing was applied.\n")
                    .append("Review the guided report before copying. No ECU value "
                            + "is written or burned automatically.");
        } else {
            text.append("No paste-ready guided proposal is available. The current "
                    + "curve remains unchanged.\n")
                    .append(nextAction(stats));
        }

        String copy = eligible ? formatCurve(proposed) : "";
        return new GuidedBlendProposal(eligible, text.toString(), copy);
    }

    private static GuidedBlendProposal unavailable(String reason) {
        return new GuidedBlendProposal(false, reason, "");
    }

    private static String decision(BlendDurationPolicy.Stats stats,
                                   BlendDurationPolicy.Confidence confidence) {
        if (confidence == BlendDurationPolicy.Confidence.HIGH) {
            return "high-confidence retained median may define the selected RPM point";
        }
        if (confidence == BlendDurationPolicy.Confidence.MEDIUM) {
            return "medium-confidence retained median may define the selected RPM point";
        }
        if (stats.retainedCount < BlendDurationPolicy.MIN_EVENTS_FOR_PROPOSAL) {
            return "need at least " + BlendDurationPolicy.MIN_EVENTS_FOR_PROPOSAL
                    + " retained comparable held openings";
        }
        if (stats.range > BlendDurationPolicy.MAX_ELIGIBLE_RANGE_SECONDS) {
            return "withheld because measured duration range exceeds 0.18 s";
        }
        if (stats.iqr > BlendDurationPolicy.MAX_ELIGIBLE_IQR_SECONDS) {
            return "withheld because measured duration IQR exceeds 0.10 s";
        }
        if (stats.standardDeviation
                > BlendDurationPolicy.MAX_ELIGIBLE_STDDEV_SECONDS) {
            return "withheld because measured duration standard deviation exceeds 0.08 s";
        }
        return "withheld because evidence is insufficient";
    }

    private static String nextAction(BlendDurationPolicy.Stats stats) {
        if (stats.retainedCount < BlendDurationPolicy.MIN_EVENTS_FOR_PROPOSAL) {
            return "Collect more events only under the same guided reference profile.";
        }
        return "Do not add mixed events. Restart the guided series with tighter "
                + "starting conditions and repeat the selected table point.";
    }

    private static String formatCurve(double[] values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(F2.format(values[i]));
        }
        return text.toString();
    }

    private static double lowerBoundary(double[] rpm, int index) {
        return index == 0 ? Double.NEGATIVE_INFINITY
                : midpoint(rpm[index - 1], rpm[index]);
    }

    private static double upperBoundary(double[] rpm, int index) {
        return index == rpm.length - 1 ? Double.POSITIVE_INFINITY
                : midpoint(rpm[index], rpm[index + 1]);
    }

    private static double midpoint(double a, double b) {
        return a + (b - a) / 2.0;
    }

    static final class PointChoice {
        final int index;
        final double rpm;
        final double currentValue;
        final double lower;
        final double upper;

        PointChoice(int index, double rpm, double currentValue,
                    double lower, double upper) {
            this.index = index;
            this.rpm = rpm;
            this.currentValue = currentValue;
            this.lower = lower;
            this.upper = upper;
        }

        boolean contains(double startRpm) {
            return startRpm > lower && startRpm <= upper;
        }

        String regionText() {
            if (!Double.isFinite(lower) && !Double.isFinite(upper)) {
                return "all RPM";
            }
            if (!Double.isFinite(lower)) {
                return "<= " + F0.format(upper) + " RPM";
            }
            if (!Double.isFinite(upper)) {
                return "> " + F0.format(lower) + " RPM";
            }
            return "> " + F0.format(lower) + " to <= "
                    + F0.format(upper) + " RPM";
        }

        String startGuidance(double startRpm) {
            if (contains(startRpm)) {
                String high = rpm >= 6000.0
                        ? " High-RPM point: use only a controlled safe test environment."
                        : "";
                return "Start RPM belongs to this actual table region." + high;
            }
            return "Start RPM is outside this table region; select the matching "
                    + "point or change the start RPM.";
        }

        @Override
        public String toString() {
            return F0.format(rpm) + " RPM | current "
                    + F2.format(currentValue) + " s | " + regionText();
        }
    }

    static final class Tracker {
        private final List<GuidedOutcome> adaptiveOutcomes =
                new ArrayList<GuidedOutcome>();
        private AeProjectSnapshot snapshot;
        private int pointIndex = -1;

        synchronized void start(AeProjectSnapshot nextSnapshot, int nextPointIndex) {
            adaptiveOutcomes.clear();
            snapshot = nextSnapshot;
            pointIndex = nextPointIndex;
        }

        synchronized void reset() {
            adaptiveOutcomes.clear();
            snapshot = null;
            pointIndex = -1;
        }

        synchronized void observe(GuidedOutcome outcome) {
            if (outcome != null && outcome.isValid()
                    && Double.isFinite(outcome.durationSeconds)) {
                adaptiveOutcomes.add(outcome);
            }
        }

        synchronized void discardLastAccepted() {
            if (!adaptiveOutcomes.isEmpty()) {
                adaptiveOutcomes.remove(adaptiveOutcomes.size() - 1);
            }
        }

        synchronized GuidedBlendProposal evaluate() {
            if (adaptiveOutcomes.isEmpty()) {
                return GuidedBlendProposal.build(snapshot, pointIndex,
                        Collections.<Double>emptyList());
            }
            String group = bestAdaptiveGroup();
            List<Double> selected = adaptiveDurations(group);
            GuidedBlendProposal base = GuidedBlendProposal.build(
                    snapshot, pointIndex, selected);
            int total = adaptiveOutcomes.size();
            int other = total - selected.size();
            String prefix = "ADAPTIVE COMPARABILITY GROUPING\n"
                    + "Proposal group: " + group + " | " + selected.size()
                    + " valid event(s) combined | " + other
                    + " valid event(s) retained in other group(s) and not mixed.\n"
                    + "Grouping uses baseline RPM/MAP, relative TPS step, fallback gap and RPM trend.\n\n";
            return new GuidedBlendProposal(base.available,
                    prefix + base.displayText, base.copyPasteBlock);
        }

        synchronized int durationCount() {
            return adaptiveOutcomes.isEmpty()
                    ? 0 : adaptiveDurations(bestAdaptiveGroup()).size();
        }

        private String bestAdaptiveGroup() {
            LinkedHashMap<String, Integer> counts =
                    new LinkedHashMap<String, Integer>();
            for (GuidedOutcome outcome : adaptiveOutcomes) {
                String id = outcome.groupId.length() == 0
                        ? "UNGROUPED" : outcome.groupId;
                Integer count = counts.get(id);
                counts.put(id, count == null ? 1 : count + 1);
            }
            String best = "UNGROUPED";
            int bestCount = -1;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            return best;
        }

        private List<Double> adaptiveDurations(String group) {
            List<Double> selected = new ArrayList<Double>();
            for (GuidedOutcome outcome : adaptiveOutcomes) {
                String id = outcome.groupId.length() == 0
                        ? "UNGROUPED" : outcome.groupId;
                if (id.equals(group) && Double.isFinite(outcome.durationSeconds)) {
                    selected.add(Double.valueOf(outcome.durationSeconds));
                }
            }
            return selected;
        }
    }
}
