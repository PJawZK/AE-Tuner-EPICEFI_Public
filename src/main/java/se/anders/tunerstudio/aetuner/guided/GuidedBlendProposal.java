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
 * Review adapter for a controlled Guided Blend Duration series.
 *
 * Archive19 source-data validation retired the old largest-gap/T90 -> duration
 * conversion as a writable proposal rule. The corrected Guided capture now
 * measures physical catch-up to EPICEFI's final upward-latched fallbackMap
 * target. Those durations may be statistically summarized, but no numerical
 * Blend Duration write plan is exposed until the firmware-faithful conversion
 * is separately validated against ECU Effective MAP/log evidence.
 */
final class GuidedBlendProposal {
    private static final DecimalFormat F0 = new DecimalFormat("0");
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");

    private final boolean available;
    private final String displayText;
    private final String copyPasteBlock;
    private final ProposalWritePlan writePlan;

    private GuidedBlendProposal(boolean available, String displayText,
                                String copyPasteBlock,
                                ProposalWritePlan writePlan) {
        this.available = available;
        this.displayText = displayText;
        this.copyPasteBlock = copyPasteBlock;
        this.writePlan = writePlan;
    }

    boolean isAvailable() {
        return available;
    }

    boolean hasWritePlan() {
        return writePlan != null;
    }

    String getDisplayText() {
        return displayText;
    }

    String getCopyPasteBlock() {
        return copyPasteBlock;
    }

    ProposalWritePlan getWritePlan() {
        return writePlan;
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
            return unavailable("Guided Blend Duration model review unavailable: "
                    + "read the current project curve first.");
        }
        double[] rpm = snapshot.getBlendDurationRpmBins();
        double[] current = snapshot.getBlendDurationValues();
        if (pointIndex < 0 || pointIndex >= rpm.length) {
            return unavailable("Guided Blend Duration model review unavailable: "
                    + "select one actual table RPM point.");
        }

        List<Double> raw = acceptedDurations == null
                ? Collections.<Double>emptyList()
                : new ArrayList<Double>(acceptedDurations);
        BlendDurationPolicy.Evaluation evaluation =
                BlendDurationPolicy.evaluate(raw);
        BlendDurationPolicy.Stats stats = evaluation.stats;
        BlendDurationPolicy.Confidence repeatability = evaluation.confidence;

        PointChoice point = new PointChoice(pointIndex, rpm[pointIndex],
                current[pointIndex], lowerBoundary(rpm, pointIndex),
                upperBoundary(rpm, pointIndex));

        StringBuilder text = new StringBuilder();
        text.append("CONTROLLED GUIDED BLEND DURATION MODEL REVIEW\n")
                .append("Selected actual table point: ")
                .append(F0.format(point.rpm)).append(" RPM\n")
                .append("Capture target: actual ").append(F0.format(point.rpm))
                .append(" RPM bin | READY ±")
                .append(F0.format(RoadBaselineTracker.RPM_ACQUIRE_TOLERANCE))
                .append(" RPM | active capture ±")
                .append(F0.format(RoadBaselineTracker.RPM_CAPTURE_TOLERANCE))
                .append(" RPM\n")
                .append("Current curve value: ").append(F2.format(point.currentValue))
                .append(" s\n")
                .append("Comparable final-target events: ").append(raw.size())
                .append(" raw / ").append(stats.retainedCount).append(" retained")
                .append(" | statistical outliers: ").append(stats.outlierCount)
                .append("\n");

        if (stats.retainedCount > 0) {
            text.append("Physical final-target catch-up: median ")
                    .append(F3.format(stats.median)).append(" s, mean ")
                    .append(F3.format(stats.mean)).append(" s, range ")
                    .append(F3.format(stats.minimum)).append("-")
                    .append(F3.format(stats.maximum)).append(" s, IQR ")
                    .append(F3.format(stats.iqr)).append(" s, SD ")
                    .append(F3.format(stats.standardDeviation)).append(" s\n");
        } else {
            text.append("Physical final-target catch-up: no retained events.\n");
        }

        text.append("Measurement repeatability: ").append(repeatability.label).append("\n")
                .append("Numerical proposal eligibility: WITHHELD BY DESIGN\n")
                .append("Decision: the previous largest-gap / 90%-catch-up conversion is retired. "
                        + "These corrected final-target durations are evidence for firmware-model validation, not direct Blend Duration values.\n")
                .append("No Apply Current Proposal write plan or TunerStudio copy/paste block is generated in this correction stage. "
                        + "The working curve remains unchanged.\n")
                .append("Next: validate the corrected measurement against EPICEFI Effective MAP / prediction-counter behavior, then define and physically test a numerical conversion rule before re-enabling Blend Duration Apply.");

        return new GuidedBlendProposal(false, text.toString(), "", null);
    }

    private static GuidedBlendProposal unavailable(String reason) {
        return new GuidedBlendProposal(false, reason, "", null);
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

        /** Retained only for passive/interpolation diagnostics; writable Guided capture is bin-centered. */
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

        String startGuidance(double ignoredStartRpm) {
            String high = rpm >= 5000.0
                    ? " Upper-RPM point: use only a controlled safe test environment with adequate headroom before the limiter."
                    : "";
            return "Guided capture is locked to this actual table bin: "
                    + F0.format(rpm) + " RPM. READY requires ±"
                    + F0.format(RoadBaselineTracker.RPM_ACQUIRE_TOLERANCE)
                    + " RPM and active capture may drift ±"
                    + F0.format(RoadBaselineTracker.RPM_CAPTURE_TOLERANCE)
                    + " RPM. Midpoint/interpolation regions do not reassign writable evidence to this cell."
                    + high;
        }

        @Override
        public String toString() {
            return F0.format(rpm) + " RPM | current "
                    + F2.format(currentValue) + " s | actual-bin capture target";
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
            String prefix = "CONTROLLED COMPARABILITY GROUPING\n"
                    + "Measurement group: " + group + " | " + selected.size()
                    + " valid event(s) combined | " + other
                    + " valid event(s) retained in other group(s) and not mixed.\n"
                    + "Grouping uses baseline RPM/MAP, controlled relative TPS step, final target-anchor gap, RPM trend, and automatic detected gear when Automatic mode is selected.\n\n";
            return new GuidedBlendProposal(false,
                    prefix + base.displayText, "", null);
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
