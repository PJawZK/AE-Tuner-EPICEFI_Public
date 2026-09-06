package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedValidationVerdict;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Method-owned A/B validation for Foundation 2 threshold proposals.
 *
 * vehicle-test.16 retains the physically accepted same-event validator from
 * .15 and clarifies the operator/reporting contract using Archive46 evidence.
 * The question remains: for this exact B pedal movement, what would the old
 * effective threshold have done versus the verified applied threshold?
 */
public final class FoundationThresholdValidation {
    private static final double EPSILON = 0.000001;
    private static final double MIN_INFORMATIVE_EFFECTIVE_SHIFT = 0.002;

    private FoundationThresholdValidation() { }

    public static final class Baseline {
        private final ProposalWritePlan plan;
        private final List<Target> targets;
        private final String text;
        private final String configurationName;
        private final double[] rpmBins;
        private final double[] thresholdValues;
        private final boolean dynamicEnabled;
        private final boolean averageStatic;
        private final double smoothingAlpha;
        private final double deltaWindowMs;
        private final double sampleLengthSeconds;

        private Baseline(ProposalWritePlan plan, List<Target> targets, String text,
                         AeProjectSnapshot snapshot) {
            this.plan = plan;
            this.targets = Collections.unmodifiableList(new ArrayList<Target>(targets));
            this.text = text == null ? "" : text;
            this.configurationName = snapshot.getConfigurationName();
            this.rpmBins = snapshot.getThresholdRpmBins();
            this.thresholdValues = snapshot.getThresholdValues();
            this.dynamicEnabled = snapshot.isDynamicThresholdEnabled();
            this.averageStatic = snapshot.isDynamicThresholdAverageStatic();
            this.smoothingAlpha = snapshot.getDeltaTpsAverageAlpha();
            this.deltaWindowMs = snapshot.getEngagementDeltaWindowMs();
            this.sampleLengthSeconds = snapshot.getEngagementSampleLengthSeconds();
        }

        public ProposalWritePlan plan() { return plan; }

        public String instructions() {
            StringBuilder out = new StringBuilder();
            out.append("VALIDATION B — test only the changed Foundation 2 region(s)");
            for (Target target : targets) {
                out.append("\n  ").append(target.regionLabel)
                        .append(": ").append(fmt(target.beforeValue))
                        .append(" -> ").append(fmt(target.appliedValue));
            }
            out.append("\nStart with a fresh continuous quiet calibration lock: at least 80 qualifying quiet samples and 1.5 continuous seconds. Then collect 3 Normal Corrections + 3 Acceleration Openings in each listed region.")
                    .append("\nNormal Correction = a small natural pedal adjustment without trying to accelerate.")
                    .append("\nAcceleration Opening = a clear normal quick throttle opening to accelerate; do not slowly roll into the pedal.")
                    .append("\nThe validator compares OLD vs APPLIED effective threshold on the same B maneuvers; unrelated/high-RPM regions are not prerequisites.");
            return out.toString();
        }

        String baselineText() { return text; }
    }

    public static final class Result {
        public final GuidedValidationVerdict verdict;
        public final String text;

        private Result(GuidedValidationVerdict verdict, String text) {
            this.verdict = verdict == null ? GuidedValidationVerdict.INCONCLUSIVE : verdict;
            this.text = text == null ? "" : text;
        }
    }

    private static final class Target {
        final int index;
        final String regionLabel;
        final double beforeValue;
        final double appliedValue;
        final int baselineNormalCrossings;
        final int baselineAccelerationMisses;
        final double baselineNormalRatioP95;
        final double baselineAccelerationRatioP25;

        Target(int index, String regionLabel, double beforeValue, double appliedValue,
               int baselineNormalCrossings, int baselineAccelerationMisses,
               double baselineNormalRatioP95, double baselineAccelerationRatioP25) {
            this.index = index;
            this.regionLabel = regionLabel;
            this.beforeValue = beforeValue;
            this.appliedValue = appliedValue;
            this.baselineNormalCrossings = baselineNormalCrossings;
            this.baselineAccelerationMisses = baselineAccelerationMisses;
            this.baselineNormalRatioP95 = baselineNormalRatioP95;
            this.baselineAccelerationRatioP25 = baselineAccelerationRatioP25;
        }
    }

    private static final class SameEventStats {
        final List<Double> oldNormalRatios = new ArrayList<Double>();
        final List<Double> newNormalRatios = new ArrayList<Double>();
        final List<Double> oldAccelerationRatios = new ArrayList<Double>();
        final List<Double> newAccelerationRatios = new ArrayList<Double>();
        final List<Double> effectiveShifts = new ArrayList<Double>();
        int newlyTriggeredNormalCorrections;
        int lostAccelerationHits;
        int accelerationMissToHit;
        int normalHitToSafe;

        boolean complete() {
            return !oldNormalRatios.isEmpty() && !newNormalRatios.isEmpty()
                    && !oldAccelerationRatios.isEmpty() && !newAccelerationRatios.isEmpty()
                    && !effectiveShifts.isEmpty();
        }
    }

    public static Baseline arm(AeProjectSnapshot snapshot, List<LiveSample> evidence,
                               ProposalWritePlan plan) {
        if (snapshot == null || plan == null
                || !"foundation-threshold-curve".equals(plan.getRecipeId())) return null;
        FoundationThresholdRecommendation.Result baseline =
                FoundationThresholdRecommendation.evaluate(snapshot, evidence);
        if (baseline.plan == null || !baseline.summary.calibrationFrozen) return null;

        List<Target> targets = new ArrayList<Target>();
        for (ProposalWritePlan.Change change : plan.getChanges()) {
            if (change.kind != ProposalWritePlan.Kind.ARRAY_CELL
                    || !AeParameterNames.TPS_AE_THRESHOLD_VALUES.equals(change.parameterName)
                    || change.flatIndex < 0
                    || change.flatIndex >= baseline.summary.bins.size()) {
                return null;
            }
            FoundationThresholdRecommendation.BinDecision bin =
                    baseline.summary.bins.get(change.flatIndex);
            if (!bin.effectiveValidated
                    || bin.incidentalEvents < FoundationThresholdRecommendation.MIN_INCIDENTAL_EVENTS_PER_BIN
                    || bin.intentEvents < FoundationThresholdRecommendation.MIN_INTENT_EVENTS_PER_BIN) {
                return null;
            }
            if (Math.abs(bin.current - change.expectedValue)
                    > Math.max(0.002, Math.abs(change.expectedValue) * 0.001)) {
                return null;
            }
            targets.add(new Target(change.flatIndex, bin.regionLabel,
                    change.expectedValue, change.proposedValue,
                    bin.ordinaryCrossingEvents, bin.deliberateBelowThresholdEvents,
                    bin.incidentalRatioP95, bin.intentRatioP25));
        }
        if (targets.isEmpty()) return null;

        StringBuilder text = new StringBuilder();
        text.append("FOUNDATION 2 A BASELINE / PROPOSAL AUTHORITY\n")
                .append("Baseline calibration: LOCKED\n")
                .append("A is retained for proposal authority and context integrity. B validation uses same-event OLD-vs-APPLIED counterfactuals rather than requiring the driver to reproduce A amplitudes exactly.\n")
                .append("Changed cells: ").append(targets.size()).append('\n');
        for (Target target : targets) {
            text.append("  ").append(target.regionLabel)
                    .append(" | static ").append(fmt(target.beforeValue))
                    .append(" -> ").append(fmt(target.appliedValue))
                    .append(" | A Normal-correction ratio p95 ").append(fmt(target.baselineNormalRatioP95))
                    .append(" | A Acceleration-opening ratio p25 ").append(fmt(target.baselineAccelerationRatioP25))
                    .append(" | A Normal crossings ").append(target.baselineNormalCrossings)
                    .append(" | A Acceleration misses ").append(target.baselineAccelerationMisses)
                    .append('\n');
        }
        return new Baseline(plan, targets, text.toString(), snapshot);
    }

    /**
     * Returns an empty string when the post-Apply working tune differs from A
     * only by the exact cells in the verified plan. Any other relevant context
     * drift blocks B validation and requires a fresh A baseline.
     */
    public static String contextMismatch(Baseline baseline, AeProjectSnapshot afterSnapshot) {
        if (baseline == null || afterSnapshot == null) return "missing Foundation 2 A/B context";
        if (!safeEquals(baseline.configurationName, afterSnapshot.getConfigurationName())) {
            return "controller configuration changed from A baseline";
        }
        double[] afterBins = afterSnapshot.getThresholdRpmBins();
        double[] afterValues = afterSnapshot.getThresholdValues();
        if (!sameArray(baseline.rpmBins, afterBins, 0.001)) {
            return "TPS AE threshold RPM-axis changed after A baseline";
        }
        if (afterValues.length != baseline.thresholdValues.length) {
            return "TPS AE threshold curve shape changed after A baseline";
        }
        for (int i = 0; i < afterValues.length; i++) {
            double expected = expectedAfterValue(baseline, i);
            if (!close(expected, afterValues[i], Math.max(0.002, Math.abs(expected) * 0.001))) {
                return "non-target Foundation context changed: TPS AE threshold @ "
                        + Math.round(afterBins[i]) + " RPM expected " + fmt(expected)
                        + " but fresh Working Tune is " + fmt(afterValues[i]);
            }
        }
        if (baseline.dynamicEnabled != afterSnapshot.isDynamicThresholdEnabled()) {
            return "Dynamic Threshold enabled state changed after A baseline";
        }
        if (baseline.averageStatic != afterSnapshot.isDynamicThresholdAverageStatic()) {
            return "Dynamic/static averaging state changed after A baseline";
        }
        if (!closeIfFinite(baseline.smoothingAlpha, afterSnapshot.getDeltaTpsAverageAlpha(), 0.0001)) {
            return "Delta TPS smoothing alpha changed after A baseline";
        }
        if (!closeIfFinite(baseline.deltaWindowMs, afterSnapshot.getEngagementDeltaWindowMs(), 0.001)) {
            return "TPS AE Delta Window changed after A baseline";
        }
        if (!closeIfFinite(baseline.sampleLengthSeconds, afterSnapshot.getEngagementSampleLengthSeconds(), 0.0001)) {
            return "TPS AE Sample Length changed after A baseline";
        }
        return "";
    }

    public static Result compare(Baseline baseline, AeProjectSnapshot afterSnapshot,
                                 List<LiveSample> afterEvidence) {
        if (baseline == null || afterSnapshot == null || afterEvidence == null
                || afterEvidence.isEmpty()) {
            return new Result(GuidedValidationVerdict.INCONCLUSIVE,
                    "INCONCLUSIVE — fresh post-Apply Foundation 2 evidence is required.");
        }

        String contextMismatch = contextMismatch(baseline, afterSnapshot);
        if (contextMismatch.length() > 0) {
            return new Result(GuidedValidationVerdict.INCONCLUSIVE,
                    "FOUNDATION 2 METHOD-OWNED A/B VALIDATION\n"
                    + "VALIDATION BLOCKED — CONTROLLER CONTEXT CHANGED\n"
                    + contextMismatch + "\n"
                    + "The B experiment no longer isolates the reviewed Apply. Restore or establish a fresh A baseline before judging the threshold step.");
        }

        FoundationThresholdRecommendation.Result after =
                FoundationThresholdRecommendation.evaluate(afterSnapshot, afterEvidence);
        StringBuilder out = new StringBuilder();
        out.append("FOUNDATION 2 METHOD-OWNED A/B VALIDATION\n")
                .append("Comparison model: same-event OLD vs APPLIED effective threshold on fresh B maneuvers. A/B driver amplitude matching is not required.\n");
        if (baseline.targets.size() > 1) {
            out.append("Multi-cell interpretation: each result below is a REGION VERDICT UNDER THE COMPLETE APPLIED CURVE. It validates the combined reviewed Apply in each sampled region and does not claim isolated single-cell causality.\n");
        }
        boolean anyRestore = false;
        boolean anyInconclusive = false;

        for (Target target : baseline.targets) {
            if (target.index < 0 || target.index >= after.summary.bins.size()) {
                anyInconclusive = true;
                out.append("  ").append(target.regionLabel)
                        .append(": INCONCLUSIVE — target bin is unavailable after Apply.\n");
                continue;
            }
            FoundationThresholdRecommendation.BinDecision bin = after.summary.bins.get(target.index);
            double appliedDirection = target.appliedValue - target.beforeValue;
            boolean appliedValuePresent = Math.abs(bin.current - target.appliedValue)
                    <= Math.max(0.002, Math.abs(target.appliedValue) * 0.001);
            int accelerationHits = Math.max(0, bin.intentEvents - bin.deliberateBelowThresholdEvents);

            out.append("\n  ").append(target.regionLabel)
                    .append(" | static ").append(fmt(target.beforeValue)).append(" -> ")
                    .append(fmt(target.appliedValue))
                    .append(" | fresh Normal Corrections / Acceleration Openings ")
                    .append(bin.incidentalEvents).append('/').append(bin.intentEvents)
                    .append(" | separation ").append(bin.effectiveValidated ? "VALID" : "OPEN")
                    .append(" | gap ").append(fmtSigned(bin.separationGap))
                    .append(" / required +").append(fmt(bin.requiredSeparation))
                    .append('\n')
                    .append("    B classification: Normal crossings ").append(bin.ordinaryCrossingEvents)
                    .append(" | Acceleration hits ").append(accelerationHits)
                    .append(" | Acceleration misses ").append(bin.deliberateBelowThresholdEvents)
                    .append(" | near-threshold Acceleration Openings ").append(bin.nearThresholdDeliberateEvents)
                    .append('\n');

            if (!appliedValuePresent) {
                anyInconclusive = true;
                out.append("    INCONCLUSIVE — fresh Working Tune does not contain the verified applied target value.\n");
                continue;
            }
            if (!after.summary.calibrationFrozen
                    || bin.incidentalEvents < FoundationThresholdRecommendation.MIN_INCIDENTAL_EVENTS_PER_BIN
                    || bin.intentEvents < FoundationThresholdRecommendation.MIN_INTENT_EVENTS_PER_BIN) {
                anyInconclusive = true;
                out.append("    INCONCLUSIVE — collect a fresh continuous quiet lock plus 3 Normal Corrections + 3 Acceleration Openings in this changed region only.\n");
                if (bin.lastRejectedReason.length() > 0) {
                    out.append("    Last rejected maneuver: ").append(bin.lastRejectedReason)
                            .append(" | rate ").append(fmt(bin.lastRejectedRate)).append(" %/s")
                            .append(" | TPS excursion ").append(fmt(bin.lastRejectedExcursion)).append("%")
                            .append(" | detector delta ").append(fmt(bin.lastRejectedDelta))
                            .append(" | ratio ").append(fmt(bin.lastRejectedRatio)).append(".\n");
                }
                continue;
            }
            if (!bin.effectiveValidated) {
                anyInconclusive = true;
                out.append("    INCONCLUSIVE — event counts are complete, but Normal Corrections and Acceleration Openings still overlap. ")
                        .append("Current gap ").append(fmtSigned(bin.separationGap))
                        .append("; required +").append(fmt(bin.requiredSeparation)).append(". ")
                        .append("Repeat the requested maneuver class instead of accumulating arbitrary extra events.\n");
                continue;
            }

            SameEventStats stats = sameEventStats(baseline, afterSnapshot, bin);
            if (!stats.complete()) {
                anyInconclusive = true;
                out.append("    INCONCLUSIVE — same-event counterfactual could not be calculated for the accepted B maneuvers.\n");
                continue;
            }

            double medianShift = median(stats.effectiveShifts);
            double oldNormalMedian = median(stats.oldNormalRatios);
            double newNormalMedian = median(stats.newNormalRatios);
            double oldAccelerationMedian = median(stats.oldAccelerationRatios);
            double newAccelerationMedian = median(stats.newAccelerationRatios);
            double normalDirectionGain = oldNormalMedian - newNormalMedian;
            double accelerationDirectionGain = newAccelerationMedian - oldAccelerationMedian;

            out.append("    SAME-EVENT B COUNTERFACTUAL\n")
                    .append("      median effective-threshold shift |OLD-APPLIED|: ").append(fmt(medianShift)).append('\n')
                    .append("      Normal Correction median ratio OLD -> APPLIED: ")
                    .append(fmt(oldNormalMedian)).append(" -> ").append(fmt(newNormalMedian)).append('\n')
                    .append("      Acceleration Opening median ratio OLD -> APPLIED: ")
                    .append(fmt(oldAccelerationMedian)).append(" -> ").append(fmt(newAccelerationMedian)).append('\n')
                    .append("      newly triggered Normal Corrections: ").append(stats.newlyTriggeredNormalCorrections).append('\n')
                    .append("      lost Acceleration Opening threshold hits: ").append(stats.lostAccelerationHits).append('\n')
                    .append("      Acceleration miss -> hit conversions: ").append(stats.accelerationMissToHit).append('\n')
                    .append("      Normal hit -> safe conversions: ").append(stats.normalHitToSafe).append('\n');

            if (!Double.isFinite(medianShift) || medianShift < MIN_INFORMATIVE_EFFECTIVE_SHIFT) {
                anyInconclusive = true;
                out.append("    INCONCLUSIVE — the applied cell had too little effective-threshold authority at the sampled RPM to make this B set informative. Repeat closer to the changed region's useful center.\n");
                continue;
            }

            if (stats.newlyTriggeredNormalCorrections > 0 || stats.lostAccelerationHits > 0) {
                anyRestore = true;
                out.append("    REGION VERDICT UNDER APPLIED CURVE: RESTORE — the exact same B maneuver set shows a threshold-crossing regression caused by the applied step.\n");
                continue;
            }

            boolean keep;
            if (appliedDirection < -EPSILON) {
                keep = Double.isFinite(accelerationDirectionGain)
                        && accelerationDirectionGain > EPSILON;
                if (keep) {
                    out.append("    REGION VERDICT UNDER APPLIED CURVE: KEEP — lowering the threshold moved the same Acceleration Opening maneuvers toward AE activation while no Normal Correction gained a new threshold crossing.\n");
                }
            } else if (appliedDirection > EPSILON) {
                keep = Double.isFinite(normalDirectionGain)
                        && normalDirectionGain > EPSILON;
                if (keep) {
                    out.append("    REGION VERDICT UNDER APPLIED CURVE: KEEP — raising the threshold moved the same Normal Correction maneuvers away from AE activation while no Acceleration Opening lost a threshold hit.\n");
                }
            } else {
                keep = false;
            }

            if (!keep) {
                anyInconclusive = true;
                out.append("    INCONCLUSIVE — no crossing regression was found, but the same-event effect did not move measurably in the applied direction.\n");
            }
        }

        GuidedValidationVerdict verdict = anyRestore
                ? GuidedValidationVerdict.RESTORE
                : (anyInconclusive ? GuidedValidationVerdict.INCONCLUSIVE
                                   : GuidedValidationVerdict.KEEP);
        out.append("\nVERDICT: ").append(verdict.name()).append('\n');
        if (verdict == GuidedValidationVerdict.KEEP) {
            out.append("The applied incremental step passed same-event method-owned validation. KEEP accepts only this tested incremental step; it does not declare any region fully converged. KEEP records the decision only and does not Burn. Any new proposal generated from Validation B is a NEXT INCREMENTAL PROPOSAL — NOT YET APPLIED.");
        } else if (verdict == GuidedValidationVerdict.RESTORE) {
            out.append("Use the existing verified Restore Previous Apply path. No Burn.");
        } else {
            out.append("Do not KEEP yet. Follow the specific local reason above; unrelated RPM regions are not prerequisites and arbitrary extra events are not the default remedy.");
        }
        return new Result(verdict, out.toString());
    }

    private static SameEventStats sameEventStats(Baseline baseline,
                                                 AeProjectSnapshot afterSnapshot,
                                                 FoundationThresholdRecommendation.BinDecision bin) {
        SameEventStats stats = new SameEventStats();
        for (FoundationThresholdRecommendation.EventObservation event : bin.normalCorrectionEvents) {
            addSameEvent(stats, baseline, afterSnapshot, event, true);
        }
        for (FoundationThresholdRecommendation.EventObservation event : bin.accelerationOpeningEvents) {
            addSameEvent(stats, baseline, afterSnapshot, event, false);
        }
        return stats;
    }

    private static void addSameEvent(SameEventStats stats, Baseline baseline,
                                     AeProjectSnapshot afterSnapshot,
                                     FoundationThresholdRecommendation.EventObservation event,
                                     boolean normalCorrection) {
        if (event == null || !Double.isFinite(event.peakDelta)
                || !Double.isFinite(event.peakThreshold) || event.peakThreshold <= 0.000001
                || !Double.isFinite(event.peakRpm)) return;
        double oldThreshold = oldEffectiveThreshold(baseline, afterSnapshot, event);
        if (!Double.isFinite(oldThreshold) || oldThreshold <= 0.000001) return;
        double oldRatio = event.peakDelta / oldThreshold;
        double newRatio = event.peakDelta / event.peakThreshold;
        if (!Double.isFinite(oldRatio) || !Double.isFinite(newRatio)) return;
        stats.effectiveShifts.add(Double.valueOf(Math.abs(event.peakThreshold - oldThreshold)));
        if (normalCorrection) {
            stats.oldNormalRatios.add(Double.valueOf(oldRatio));
            stats.newNormalRatios.add(Double.valueOf(newRatio));
            if (oldRatio <= 1.0 && newRatio > 1.0) stats.newlyTriggeredNormalCorrections++;
            if (oldRatio > 1.0 && newRatio <= 1.0) stats.normalHitToSafe++;
        } else {
            stats.oldAccelerationRatios.add(Double.valueOf(oldRatio));
            stats.newAccelerationRatios.add(Double.valueOf(newRatio));
            if (oldRatio >= 1.0 && newRatio < 1.0) stats.lostAccelerationHits++;
            if (oldRatio < 1.0 && newRatio >= 1.0) stats.accelerationMissToHit++;
        }
    }

    private static double oldEffectiveThreshold(Baseline baseline,
                                                AeProjectSnapshot afterSnapshot,
                                                FoundationThresholdRecommendation.EventObservation event) {
        double oldStatic = FoundationThresholdRecommendation.interpolateCurve(
                event.peakRpm, baseline.rpmBins, baseline.thresholdValues);
        double newStatic = FoundationThresholdRecommendation.interpolateCurve(
                event.peakRpm, afterSnapshot.getThresholdRpmBins(), afterSnapshot.getThresholdValues());
        if (!Double.isFinite(oldStatic) || !Double.isFinite(newStatic)) return Double.NaN;
        if (!baseline.dynamicEnabled) return oldStatic;
        if (baseline.averageStatic) {
            // Firmware authority: Effective=(Static+Dynamic)/2. The exact B event
            // gives Effective_new. Holding its inferred Dynamic component fixed
            // lets us calculate the old static-curve counterfactual for that same event.
            return event.peakThreshold + (oldStatic - newStatic) / 2.0;
        }
        return event.peakThreshold;
    }

    private static double expectedAfterValue(Baseline baseline, int index) {
        for (ProposalWritePlan.Change change : baseline.plan.getChanges()) {
            if (AeParameterNames.TPS_AE_THRESHOLD_VALUES.equals(change.parameterName)
                    && change.kind == ProposalWritePlan.Kind.ARRAY_CELL
                    && change.flatIndex == index) {
                return change.proposedValue;
            }
        }
        return baseline.thresholdValues[index];
    }

    private static boolean sameArray(double[] a, double[] b, double tolerance) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!close(a[i], b[i], tolerance)) return false;
        }
        return true;
    }

    private static boolean close(double a, double b, double tolerance) {
        return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= tolerance;
    }

    private static boolean closeIfFinite(double a, double b, double tolerance) {
        if (!Double.isFinite(a) && !Double.isFinite(b)) return true;
        return close(a, b, tolerance);
    }

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        double[] copy = new double[values.size()];
        for (int i = 0; i < values.size(); i++) copy[i] = values.get(i).doubleValue();
        Arrays.sort(copy);
        int middle = copy.length / 2;
        return copy.length % 2 == 0 ? (copy[middle - 1] + copy[middle]) / 2.0 : copy[middle];
    }

    private static String fmtSigned(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%+.3f", value) : "n/a";
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
