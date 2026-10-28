package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.BuildIdentity;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Operator-facing result derived from the qualified historical Decel evidence.
 * The result layer adds no write authority. Evidence-supported bins expose one
 * controller-ready classification-boundary target; unresolved bins expose no ECU value.
 * Independent physical-event counts are also reduced to a read-only evidence-strength
 * label so result coverage is visible without altering proposal authority.
 */
public final class DecelHistoricalResultModel {
    public enum OverallState {
        VALIDATED_KEEP,
        PARTIAL_VALIDATION,
        CHANGE_RECOMMENDED,
        MORE_EVIDENCE_NEEDED,
        RESULT_WITHHELD
    }

    public static final class BinResult {
        public final double rpm;
        public final double current;
        public final double safeLow;
        public final double safeHigh;
        public final DecelHistoricalThresholdDecision.Outcome outcome;
        /** Log-derived classification-boundary target for KEEP/CHANGE; NaN for WITHHOLD. */
        public final double proposed;
        public final int normalEvents;
        public final int releaseEvents;
        public final int ambiguousEvents;
        public final int releaseMisses;
        public final String operatorState;
        public final DecelHistoricalEvidenceStrength.Level evidenceStrength;
        public final String evidenceStrengthReason;
        public final String reason;

        BinResult(double rpm, double current, double safeLow, double safeHigh,
                  DecelHistoricalThresholdDecision.Outcome outcome, double proposed,
                  int normalEvents, int releaseEvents, int ambiguousEvents,
                  int releaseMisses, String operatorState,
                  DecelHistoricalEvidenceStrength.Level evidenceStrength,
                  String evidenceStrengthReason, String reason) {
            this.rpm = rpm;
            this.current = current;
            this.safeLow = safeLow;
            this.safeHigh = safeHigh;
            this.outcome = outcome;
            this.proposed = proposed;
            this.normalEvents = normalEvents;
            this.releaseEvents = releaseEvents;
            this.ambiguousEvents = ambiguousEvents;
            this.releaseMisses = releaseMisses;
            this.operatorState = operatorState == null ? "" : operatorState;
            this.evidenceStrength = evidenceStrength == null
                    ? DecelHistoricalEvidenceStrength.Level.NONE : evidenceStrength;
            this.evidenceStrengthReason = evidenceStrengthReason == null ? "" : evidenceStrengthReason;
            this.reason = reason == null ? "" : reason;
        }
    }

    public final OverallState state;
    public final String sourceName;
    public final String headline;
    public final String recommendation;
    public final String explanation;
    public final String compatibility;
    public final int validatedBins;
    public final int proposedBins;
    public final int missingBins;
    public final int totalBins;
    public final int strongBins;
    public final int goodBins;
    public final int minimumBins;
    public final int totalEvents;
    public final int normalEvents;
    public final int releaseEvents;
    public final int ambiguousEvents;
    public final int releaseMisses;
    public final boolean proposalAvailable;
    public final boolean targetedCaptureUseful;
    public final List<BinResult> bins;
    public final double[] missingRpmBins;
    public final String technicalReport;

    private DecelHistoricalResultModel(OverallState state, String sourceName,
                                       String headline, String recommendation,
                                       String explanation, String compatibility,
                                       int validatedBins, int proposedBins,
                                       int missingBins, int totalBins,
                                       int strongBins, int goodBins, int minimumBins,
                                       int totalEvents, int normalEvents,
                                       int releaseEvents, int ambiguousEvents,
                                       int releaseMisses, boolean proposalAvailable,
                                       boolean targetedCaptureUseful,
                                       List<BinResult> bins, double[] missingRpmBins,
                                       String technicalReport) {
        this.state = state;
        this.sourceName = sourceName == null ? "historical log" : sourceName;
        this.headline = headline == null ? "" : headline;
        this.recommendation = recommendation == null ? "" : recommendation;
        this.explanation = explanation == null ? "" : explanation;
        this.compatibility = compatibility == null ? "" : compatibility;
        this.validatedBins = validatedBins;
        this.proposedBins = proposedBins;
        this.missingBins = missingBins;
        this.totalBins = totalBins;
        this.strongBins = strongBins;
        this.goodBins = goodBins;
        this.minimumBins = minimumBins;
        this.totalEvents = totalEvents;
        this.normalEvents = normalEvents;
        this.releaseEvents = releaseEvents;
        this.ambiguousEvents = ambiguousEvents;
        this.releaseMisses = releaseMisses;
        this.proposalAvailable = proposalAvailable;
        this.targetedCaptureUseful = targetedCaptureUseful;
        this.bins = Collections.unmodifiableList(new ArrayList<BinResult>(bins));
        this.missingRpmBins = missingRpmBins == null ? new double[0] : missingRpmBins.clone();
        this.technicalReport = technicalReport == null ? "" : technicalReport;
    }

    public static DecelHistoricalResultModel build(File source,
                                                   AeProjectSnapshot snapshot,
                                                   DecelHistoricalReplayService.Result replay,
                                                   DecelHistoricalThresholdProposal.Eligibility eligibility) {
        String sourceName = source == null ? "historical log" : source.getName();
        if (replay == null || replay.thresholdDecision == null) {
            return unavailable(sourceName,
                    "Historical replay did not produce a threshold decision.",
                    replay == null ? "" : replay.reportText);
        }
        String technical = replay.reportText + (eligibility != null && eligibility.reviewText.length() > 0
                ? "\n\n" + eligibility.reviewText : "");
        return fromDecision(sourceName, replay.thresholdDecision, replay,
                eligibility == null ? null : eligibility.state,
                eligibility != null && eligibility.promotable,
                withReportIdentity(technical), null);
    }

    public static DecelHistoricalResultModel refreshAfterWorkingTune(
            DecelHistoricalResultModel previous,
            DecelHistoricalThresholdProposal.Result proposalResult) {
        if (previous == null || proposalResult == null || proposalResult.decision == null) {
            return previous;
        }
        return fromDecision(previous.sourceName, proposalResult.decision, null,
                proposalResult.state, proposalResult.plan != null,
                previous.technicalReport + "\n\nPOST-APPLY / FRESH WORKING TUNE RESULT\n"
                        + proposalResult.reviewText, previous);
    }

    private static DecelHistoricalResultModel fromDecision(
            String sourceName,
            DecelHistoricalThresholdDecision.Result decision,
            DecelHistoricalReplayService.Result replay,
            DecelHistoricalThresholdProposal.State proposalState,
            boolean proposalAvailable,
            String technical,
            DecelHistoricalResultModel previous) {
        boolean compatibilityActionable = decision.compatibility != null
                && decision.compatibility.actionable;
        List<BinResult> bins = new ArrayList<BinResult>();
        List<Double> missing = new ArrayList<Double>();
        int releaseMissesTotal = 0;
        int strong = 0;
        int good = 0;
        int minimum = 0;

        for (int i = 0; i < decision.bins.size(); i++) {
            DecelHistoricalThresholdDecision.BinDecision d = decision.bins.get(i);
            int misses = 0;
            if (replay != null && replay.analysis != null && replay.analysis.bins != null
                    && i < replay.analysis.bins.size()) {
                misses = replay.analysis.bins.get(i).releaseMisses;
            } else if (previous != null && i < previous.bins.size()) {
                misses = previous.bins.get(i).releaseMisses;
            }
            releaseMissesTotal += misses;

            String operatorState;
            if (d.outcome == DecelHistoricalThresholdDecision.Outcome.KEEP) {
                operatorState = "KEEP";
            } else if (d.outcome == DecelHistoricalThresholdDecision.Outcome.PROPOSE) {
                operatorState = "CHANGE";
            } else if (compatibilityActionable) {
                operatorState = "NEED EVIDENCE";
                missing.add(Double.valueOf(d.rpm));
            } else {
                operatorState = "WITHHELD";
            }

            DecelHistoricalEvidenceStrength.Assessment strength =
                    DecelHistoricalEvidenceStrength.assess(d);
            if (strength.level == DecelHistoricalEvidenceStrength.Level.STRONG) strong++;
            else if (strength.level == DecelHistoricalEvidenceStrength.Level.GOOD) good++;
            else if (strength.level == DecelHistoricalEvidenceStrength.Level.MINIMUM) minimum++;

            bins.add(new BinResult(d.rpm, d.current, d.safeLow, d.safeHigh,
                    d.outcome, d.ecuResultValue(), d.normalEvents, d.releaseEvents,
                    d.ambiguousEvents, misses, operatorState,
                    strength.level, strength.reason, d.reason));
        }

        int totalEvents = replay != null && replay.analysis != null
                ? replay.analysis.movementEvents : previous == null ? 0 : previous.totalEvents;
        int normalEvents = replay != null && replay.analysis != null
                ? replay.analysis.normalCorrections : previous == null ? 0 : previous.normalEvents;
        int releaseEvents = replay != null && replay.analysis != null
                ? replay.analysis.decelReleases : previous == null ? 0 : previous.releaseEvents;
        int ambiguousEvents = replay != null && replay.analysis != null
                ? replay.analysis.ambiguousEvents : previous == null ? 0 : previous.ambiguousEvents;

        ResultWords words = words(decision, compatibilityActionable, proposalState);
        int supported = decision.keepCount + decision.proposeCount;
        return new DecelHistoricalResultModel(words.state, sourceName,
                words.headline, words.recommendation, words.explanation,
                decision.compatibility == null ? "" : decision.compatibility.status,
                supported, decision.proposeCount, decision.withholdCount,
                decision.bins.size(), strong, good, minimum,
                totalEvents, normalEvents, releaseEvents,
                ambiguousEvents, releaseMissesTotal, proposalAvailable,
                compatibilityActionable && decision.withholdCount > 0,
                bins, doubles(missing), technical);
    }

    public String missingBinsText() {
        if (missingRpmBins.length == 0) return "none";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < missingRpmBins.length; i++) {
            if (i > 0) out.append(" / ");
            out.append(Math.round(missingRpmBins[i]));
        }
        return out.toString();
    }

    public String evidenceStrengthSummary() {
        if (validatedBins <= 0) return "none";
        return strongBins + " strong / " + goodBins + " good / "
                + minimumBins + " minimum";
    }

    public String compactSummary() {
        return headline + "\n"
                + recommendation + "\n"
                + "Evidence-supported " + validatedBins + " / " + totalBins
                + " | Change " + proposedBins
                + " | Need evidence " + missingBins + "\n"
                + "Evidence strength: " + evidenceStrengthSummary() + "\n"
                + (missingBins > 0 ? "Missing RPM: " + missingBinsText() + "\n" : "")
                + "Events: " + normalEvents + " normal / " + releaseEvents
                + " release / " + ambiguousEvents + " ambiguous"
                + (releaseMisses > 0 ? " | release misses " + releaseMisses : "");
    }

    private static ResultWords words(DecelHistoricalThresholdDecision.Result decision,
                                     boolean compatibilityActionable,
                                     DecelHistoricalThresholdProposal.State proposalState) {
        if (!compatibilityActionable
                || proposalState == DecelHistoricalThresholdProposal.State.WITHHELD) {
            String status = decision.compatibility == null
                    ? "Captured/current Decel compatibility or proposal safety could not be proven."
                    : decision.compatibility.status;
            return new ResultWords(OverallState.RESULT_WITHHELD,
                    "NO TUNING RESULT",
                    "Resolve the stated compatibility/evidence safety condition before changing the Decel threshold curve.",
                    status);
        }
        if (decision.proposeCount > 0) {
            return new ResultWords(OverallState.CHANGE_RECOMMENDED,
                    "LOG-DERIVED THRESHOLD VALUES READY",
                    decision.proposeCount + " RPM region(s) have classification-boundary threshold values derived from independent historical Normal/Release events.",
                    "Each CHANGE value sits conservatively inside the observed gap between Normal Corrections and credible Decel Releases. Evidence strength is shown separately and does not broaden write authority. Only the exact result values may enter guarded Apply/readback/Restore.");
        }
        if (decision.keepCount > 0 && decision.withholdCount == 0) {
            return new ResultWords(OverallState.VALIDATED_KEEP,
                    "LOG-DERIVED TARGETS ALREADY MATCH ECU",
                    "No Decel threshold change is required.",
                    "All evidence-supported RPM cells already equal their classification-boundary targets. Evidence strength remains visible as coverage context.");
        }
        if (decision.keepCount > 0) {
            return new ResultWords(OverallState.PARTIAL_VALIDATION,
                    "SUPPORTED TARGETS ALREADY MATCH ECU",
                    "Keep the matching values and capture only the missing RPM regions if full-curve coverage is desired.",
                    decision.keepCount + " / " + decision.bins.size()
                            + " RPM regions match their classification-boundary targets; " + decision.withholdCount
                            + " region(s) remain unresolved. Evidence strength does not turn unresolved bins into tuned cells.");
        }
        return new ResultWords(OverallState.MORE_EVIDENCE_NEEDED,
                "MORE DECEL EVIDENCE NEEDED",
                "Collect targeted Normal Correction and clear Decel Release events only in the unresolved RPM regions.",
                "The log is compatible, but no RPM region currently has enough safely separated independent evidence to produce a classification-boundary ECU target.");
    }

    private static final class ResultWords {
        final OverallState state;
        final String headline;
        final String recommendation;
        final String explanation;
        ResultWords(OverallState state, String headline,
                    String recommendation, String explanation) {
            this.state = state;
            this.headline = headline;
            this.recommendation = recommendation;
            this.explanation = explanation;
        }
    }

    private static double[] doubles(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i).doubleValue();
        return out;
    }

    private static String withReportIdentity(String technical) {
        if (technical != null && technical.startsWith("AE TUNER REPORT IDENTITY\n")) {
            return technical;
        }
        StringBuilder out = new StringBuilder();
        out.append("AE TUNER REPORT IDENTITY\n")
                .append("Plugin build: ").append(BuildIdentity.VERSION).append('\n')
                .append("Decel decision algorithm: ")
                .append(BuildIdentity.DECEL_DECISION_ALGORITHM).append("\n\n");
        if (technical != null) out.append(technical);
        return out.toString();
    }

    private static DecelHistoricalResultModel unavailable(String sourceName,
                                                           String reason,
                                                           String technical) {
        return new DecelHistoricalResultModel(OverallState.RESULT_WITHHELD,
                sourceName, "NO TUNING RESULT",
                "Historical Decel evidence could not be resolved into a tuning result.",
                reason, "", 0, 0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0, 0, false, false,
                Collections.<BinResult>emptyList(), new double[0], withReportIdentity(technical));
    }

    public static String fmt(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "—";
    }
}
