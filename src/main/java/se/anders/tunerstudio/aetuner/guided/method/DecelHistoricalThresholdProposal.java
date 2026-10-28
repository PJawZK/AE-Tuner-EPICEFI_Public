package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Explicit promotion boundary from read-only historical Decel evidence into the
 * existing guarded production proposal path.
 *
 * Historical replay and eligibility remain read-only and do not construct a
 * ProposalWritePlan. Only after explicit operator Review may retained evidence
 * be converted into exact classification-boundary threshold targets. Every
 * staged plan still terminates exclusively in ProposalApplyCoordinator.
 */
public final class DecelHistoricalThresholdProposal {
    private static final double DIRECT_TWIN_TOLERANCE = 0.0005;
    private static final double RPM_TOLERANCE = 0.001;
    private static final double VALUE_TOLERANCE = 0.000001;
    private static final int MIN_NORMAL_EVENTS = 3;
    private static final int MIN_RELEASE_EVENTS = 3;

    public enum State {
        PLAN_READY,
        CONVERGED,
        WITHHELD
    }

    public static final class Evidence {
        public final String sourceName;
        public final String fingerprint;
        public final String historicalDetectorMode;
        public final ChannelRole modeTwin;
        public final int qualifyingTwinSamples;
        public final int directComparedSamples;
        public final int directRawMismatchedSamples;
        public final int directMaterialMismatchedSamples;
        public final double directMaxDifference;
        public final double[] rpmBins;
        public final DecelHistoricalEvidenceAnalyzer.Result analysis;
        public final MlgEmbeddedTuneMetadata embeddedTune;

        private Evidence(String sourceName, String fingerprint,
                         String historicalDetectorMode, ChannelRole modeTwin,
                         int qualifyingTwinSamples, int directComparedSamples,
                         int directRawMismatchedSamples, int directMaterialMismatchedSamples,
                         double directMaxDifference, double[] rpmBins,
                         DecelHistoricalEvidenceAnalyzer.Result analysis,
                         MlgEmbeddedTuneMetadata embeddedTune) {
            this.sourceName = sourceName == null ? "historical log" : sourceName;
            this.fingerprint = fingerprint == null ? "" : fingerprint;
            this.historicalDetectorMode = historicalDetectorMode == null ? "" : historicalDetectorMode;
            this.modeTwin = modeTwin;
            this.qualifyingTwinSamples = qualifyingTwinSamples;
            this.directComparedSamples = directComparedSamples;
            this.directRawMismatchedSamples = directRawMismatchedSamples;
            this.directMaterialMismatchedSamples = directMaterialMismatchedSamples;
            this.directMaxDifference = directMaxDifference;
            this.rpmBins = rpmBins == null ? new double[0] : rpmBins.clone();
            this.analysis = analysis;
            this.embeddedTune = embeddedTune;
        }
    }

    public static final class Eligibility {
        public final boolean promotable;
        public final State state;
        public final Evidence evidence;
        public final DecelHistoricalThresholdDecision.Result decision;
        public final String reviewText;

        private Eligibility(boolean promotable, State state, Evidence evidence,
                            DecelHistoricalThresholdDecision.Result decision,
                            String reviewText) {
            this.promotable = promotable;
            this.state = state;
            this.evidence = evidence;
            this.decision = decision;
            this.reviewText = reviewText == null ? "" : reviewText;
        }
    }

    public static final class Result {
        public final State state;
        public final ProposalWritePlan plan;
        public final DecelHistoricalThresholdDecision.Result decision;
        public final String reviewText;

        private Result(State state, ProposalWritePlan plan,
                       DecelHistoricalThresholdDecision.Result decision,
                       String reviewText) {
            this.state = state;
            this.plan = plan;
            this.decision = decision;
            this.reviewText = reviewText == null ? "" : reviewText;
        }
    }

    private static final class MaterialAgreement {
        final int materialMismatches;
        final double maxMaterialDifference;

        MaterialAgreement(int materialMismatches, double maxMaterialDifference) {
            this.materialMismatches = materialMismatches;
            this.maxMaterialDifference = maxMaterialDifference;
        }
    }

    private DecelHistoricalThresholdProposal() { }

    public static Eligibility reviewEligibility(File source,
                                                AeProjectSnapshot snapshot,
                                                DecelHistoricalReplayService.Result replay) {
        if (snapshot == null || !snapshot.hasDecelThresholdCurve()) {
            return withheldEligibility("Working Tune Decel threshold curve is unavailable.");
        }
        if (replay == null || replay.importResult == null
                || !replay.importResult.usable() || replay.analysis == null
                || replay.thresholdDecision == null) {
            return withheldEligibility("Historical replay did not produce complete threshold evidence.");
        }
        if (!replay.thresholdDecision.compatibility.actionable) {
            return withheldEligibility(replay.thresholdDecision.compatibility.status);
        }
        if (!replay.analysis.quietCalibrationLocked) {
            return withheldEligibility("Quiet calibration is not locked.");
        }
        if (replay.embeddedTune == null || !replay.embeddedTune.available
                || empty(replay.embeddedTune.detectorModeRaw)) {
            return withheldEligibility("Captured detector mode is unavailable.");
        }

        String historicalMode = replay.embeddedTune.detectorModeRaw;
        ChannelRole twin = DecelSignalSamples.modeTwin(historicalMode);
        if (twin == null) {
            return withheldEligibility("Captured detector mode cannot be resolved to a published falling twin.");
        }

        int qualifying = 0;
        int missingTwin = 0;
        for (LiveSample sample : replay.importResult.samples) {
            if (!qualifiesForPhysicalMagnitudeEvidence(sample)) continue;
            qualifying++;
            if (!Double.isFinite(sample.get(twin))) missingTwin++;
        }
        if (qualifying == 0) {
            return withheldEligibility("No running-engine physical Decel samples qualify for proposal promotion.");
        }
        if (missingTwin > 0) {
            return withheldEligibility("Mode-matched falling twin " + twin.getLabel()
                    + " is missing from " + missingTwin + " / " + qualifying
                    + " qualifying physical sample(s); direct fallback may remain diagnostic but cannot gain write authority.");
        }

        double[] rpmBins = snapshot.getDecelThresholdRpmBins();
        if (!sameAxis(rpmBins, replay.thresholdDecision.bins)) {
            return withheldEligibility("Working Tune Decel RPM axis does not match the axis used by the historical decision preview.");
        }

        DecelSignalSamples.Agreement agreement = DecelSignalSamples.agreement(
                replay.importResult.samples, historicalMode, DIRECT_TWIN_TOLERANCE);
        MaterialAgreement materialAgreement = materialAgreement(
                replay.importResult.samples, historicalMode, replay.thresholdDecision);
        if (materialAgreement.materialMismatches > 0) {
            return withheldEligibility("Direct Fuel: TPS decel change materially disagrees with the captured mode twin on a current/proposed Decel threshold crossing in "
                    + materialAgreement.materialMismatches + " proposal-relevant sample(s); proposal authority is withheld.");
        }

        String sourceName = source == null ? "historical log" : source.getName();
        String fingerprint = replay.embeddedTune.fingerprint;
        Evidence evidence = new Evidence(sourceName, fingerprint, historicalMode, twin,
                qualifying, agreement.comparedSamples, agreement.mismatchedSamples,
                materialAgreement.materialMismatches, agreement.maxDifference,
                rpmBins, replay.analysis, replay.embeddedTune);

        DecelHistoricalThresholdDecision.Result decision =
                DecelHistoricalThresholdDecision.evaluate(snapshot,
                        evidence.analysis, evidence.embeddedTune);
        if (!decision.compatibility.actionable) {
            return withheldEligibility(decision.compatibility.status);
        }

        State state;
        if (decision.proposeCount > 0) state = State.PLAN_READY;
        else if (decision.keepCount > 0) state = State.CONVERGED;
        else state = State.WITHHELD;

        StringBuilder text = new StringBuilder();
        text.append("HISTORICAL THRESHOLD PROPOSAL ELIGIBILITY — READ ONLY\n")
                .append("Source: ").append(sourceName).append('\n')
                .append("Captured detector twin: ").append(twin.getLabel()).append('\n')
                .append("Qualifying physical samples with exact twin: ")
                .append(qualifying).append(" / ").append(qualifying).append('\n')
                .append("Direct/twin cross-check: ");
        if (agreement.comparedSamples > 0) {
            text.append(agreement.comparedSamples).append(" compared, ")
                    .append(agreement.mismatchedSamples).append(" raw mismatch(es), ")
                    .append(materialAgreement.materialMismatches)
                    .append(" proposal-relevant material mismatch(es), max |difference| ")
                    .append(fmt(agreement.maxDifference));
            if (agreement.mismatchedSamples > 0 && materialAgreement.materialMismatches == 0) {
                text.append(" — raw mismatch(es) are diagnostic only because direct and twin remain on the same side of both current and log-derived thresholds");
            }
        } else {
            text.append("direct channel absent/not comparable; explicit mode twin remains authoritative");
        }
        text.append('\n')
                .append("Calibration fingerprint: ")
                .append(empty(fingerprint) ? "n/a" : fingerprint).append("\n\n")
                .append(decision.reportText);
        if (state == State.PLAN_READY) {
            text.append("\n\nCandidate result: ").append(decision.proposeCount)
                    .append(" classification-boundary threshold value(s) are available for explicit Review.");
        } else if (state == State.CONVERGED) {
            text.append("\n\nCandidate result: all evidence-supported cells already equal their classification-boundary targets.");
        }
        text.append("\n\nBoundary: candidate values and eligibility are read-only. No ProposalWritePlan has been constructed or staged. "
                + "Only the explicit Review Proposed Threshold Changes action may construct the guarded plan. No automatic Apply and no Burn.");

        return new Eligibility(state == State.PLAN_READY, state, evidence,
                decision, text.toString());
    }

    public static Result evaluate(AeProjectSnapshot snapshot, Evidence evidence) {
        if (evidence == null) {
            return withheld("No historical Decel proposal evidence is staged.", null);
        }
        if (snapshot == null || !snapshot.hasDecelThresholdCurve()) {
            return withheld("Working Tune Decel threshold curve is unavailable.", null);
        }
        if (!sameAxis(snapshot.getDecelThresholdRpmBins(), evidence.rpmBins)) {
            return withheld("WITHHELD — Working Tune Decel RPM axis changed after historical review. Re-analyze/review before any further proposal.", null);
        }

        DecelHistoricalThresholdDecision.Result decision =
                DecelHistoricalThresholdDecision.evaluate(snapshot,
                        evidence.analysis, evidence.embeddedTune);
        if (!decision.compatibility.actionable) {
            return withheld(decision.compatibility.status, decision);
        }

        if (decision.proposeCount == 0) {
            if (decision.keepCount > 0) {
                String text = "HISTORICAL DECEL RESULT — CONVERGED FOR AVAILABLE EVIDENCE\n"
                        + "Every evidence-supported bin already equals its classification-boundary target. "
                        + "KEEP " + decision.keepCount + " | PROPOSE 0 | WITHHOLD "
                        + decision.withholdCount + ".\n"
                        + "WITHHOLD bins remain unresolved rather than being treated as tuned. "
                        + "No ProposalWritePlan is created, no Working Tune value is changed, and no Burn occurs.\n\n"
                        + decision.reportText;
                return new Result(State.CONVERGED, null, decision, text);
            }
            return withheld("Historical evidence has no eligible classification-boundary threshold target.", decision);
        }

        double[] currentValues = snapshot.getDecelThresholdValues();
        List<ProposalWritePlan.Change> changes = new ArrayList<ProposalWritePlan.Change>();
        for (int i = 0; i < decision.bins.size(); i++) {
            DecelHistoricalThresholdDecision.BinDecision bin = decision.bins.get(i);
            if (bin.outcome != DecelHistoricalThresholdDecision.Outcome.PROPOSE) continue;
            if (i >= currentValues.length || !near(currentValues[i], bin.current, VALUE_TOLERANCE)) {
                return withheld("WITHHELD — Working Tune threshold values changed while the historical proposal was being resolved.", decision);
            }
            if (bin.normalEvents < MIN_NORMAL_EVENTS || bin.releaseEvents < MIN_RELEASE_EVENTS) {
                return withheld("WITHHELD — a proposed bin no longer has the required 3+3 physical event backing.", decision);
            }
            if (!Double.isFinite(bin.preview) || bin.current <= 0.0
                    || Math.abs(bin.preview - bin.current) <= VALUE_TOLERANCE) {
                return withheld("WITHHELD — a proposed classification-boundary target is not a distinct safe nonzero controller value.", decision);
            }
            changes.add(ProposalWritePlan.Change.arrayCell(
                    AeParameterNames.TPS_DECEL_THRESHOLD_VALUES,
                    i, bin.current, bin.preview,
                    "TPS decel threshold @ " + Math.round(bin.rpm) + " RPM", "#"));
        }

        if (changes.isEmpty()) {
            return withheld("Historical decision reported PROPOSE but no exact writable classification-boundary cells survived promotion checks.", decision);
        }

        String context = "Explicitly reviewed historical Decel evidence from "
                + evidence.sourceName + "; mode twin " + evidence.modeTwin.getLabel()
                + "; fingerprint " + (empty(evidence.fingerprint) ? "n/a" : evidence.fingerprint)
                + ". Direct/twin raw disagreement is diagnostic unless it changes a current/proposed threshold crossing; proposal-relevant material mismatches remain zero. Only log-derived classification-boundary threshold targets from independent Normal/Release physical events are declared. Decel Hold and fuel multiplier remain unchanged. Apply still requires exact stale-check/readback and a fresh Working Tune read. No automatic Apply and no Burn.";
        ProposalWritePlan plan = new ProposalWritePlan(
                "decel-historical-threshold-curve",
                "Decel Detection — historical classification-boundary threshold targets",
                snapshot.getConfigurationName(), context, changes);

        StringBuilder text = new StringBuilder();
        text.append("HISTORICAL DECEL RESULT — CLASSIFICATION-BOUNDARY TARGETS READY\n")
                .append("KEEP ").append(decision.keepCount)
                .append(" | PROPOSE ").append(decision.proposeCount)
                .append(" | WITHHOLD ").append(decision.withholdCount).append(".\n")
                .append("Each proposed value is derived from the observed gap between independent Normal Correction and credible Decel Release populations; no pair multiplication is used.\n\n")
                .append(plan.reviewText("HISTORICAL CLASSIFICATION-BOUNDARY THRESHOLD PROPOSAL — NOT YET APPLIED"))
                .append("\n\nAfter a verified Apply: Read Working Tune. The same retained historical evidence should then resolve applied cells to KEEP/CONVERGED; unresolved WITHHOLD bins remain unchanged.\n\n")
                .append(decision.reportText);
        return new Result(State.PLAN_READY, plan, decision, text.toString());
    }

    public static String reviewChangesText(AeProjectSnapshot snapshot, Evidence evidence) {
        return evaluate(snapshot, evidence).reviewText;
    }

    private static MaterialAgreement materialAgreement(
            List<LiveSample> samples,
            String historicalMode,
            DecelHistoricalThresholdDecision.Result decision) {
        if (samples == null || decision == null || decision.bins == null
                || decision.bins.isEmpty()) {
            return new MaterialAgreement(0, Double.NaN);
        }
        ChannelRole twin = DecelSignalSamples.modeTwin(historicalMode);
        if (twin == null) return new MaterialAgreement(0, Double.NaN);

        double[] rpm = new double[decision.bins.size()];
        double[] current = new double[decision.bins.size()];
        double[] proposed = new double[decision.bins.size()];
        for (int i = 0; i < decision.bins.size(); i++) {
            DecelHistoricalThresholdDecision.BinDecision bin = decision.bins.get(i);
            rpm[i] = bin.rpm;
            current[i] = bin.current;
            proposed[i] = Double.isFinite(bin.ecuResultValue())
                    ? bin.ecuResultValue() : bin.current;
        }

        int material = 0;
        double maxDifference = 0.0;
        for (LiveSample sample : samples) {
            if (!qualifiesForPhysicalMagnitudeEvidence(sample)) continue;
            double direct = sample.get(ChannelRole.DECEL_DELTA_TPS);
            double twinValue = sample.get(twin);
            double sampleRpm = sample.get(ChannelRole.RPM);
            if (!Double.isFinite(direct) || !Double.isFinite(twinValue)
                    || !Double.isFinite(sampleRpm)) continue;
            double difference = Math.abs(direct - twinValue);
            if (difference <= DIRECT_TWIN_TOLERANCE) continue;

            double currentThreshold = interpolate(rpm, current, sampleRpm);
            double proposedThreshold = interpolate(rpm, proposed, sampleRpm);
            boolean currentDisagreement = edge(direct, currentThreshold) != edge(twinValue, currentThreshold);
            boolean proposedDisagreement = edge(direct, proposedThreshold) != edge(twinValue, proposedThreshold);
            if (currentDisagreement || proposedDisagreement) {
                material++;
                if (difference > maxDifference) maxDifference = difference;
            }
        }
        return new MaterialAgreement(material,
                material == 0 ? Double.NaN : maxDifference);
    }

    private static boolean edge(double signal, double threshold) {
        return Double.isFinite(signal) && Double.isFinite(threshold)
                && threshold > 0.0 && signal < -threshold;
    }

    private static double interpolate(double[] rpm, double[] values, double sampleRpm) {
        if (rpm == null || values == null || rpm.length == 0 || rpm.length != values.length
                || !Double.isFinite(sampleRpm)) return Double.NaN;
        if (sampleRpm <= rpm[0]) return values[0];
        int last = rpm.length - 1;
        if (sampleRpm >= rpm[last]) return values[last];
        for (int i = 0; i < last; i++) {
            if (sampleRpm > rpm[i + 1]) continue;
            double span = rpm[i + 1] - rpm[i];
            if (!Double.isFinite(span) || span <= 0.0) return Double.NaN;
            double fraction = (sampleRpm - rpm[i]) / span;
            return values[i] + ((values[i + 1] - values[i]) * fraction);
        }
        return values[last];
    }

    private static Eligibility withheldEligibility(String reason) {
        String text = "HISTORICAL THRESHOLD PROPOSAL ELIGIBILITY — WITHHELD\n"
                + safe(reason) + "\nNo ProposalWritePlan was created and no Working Tune value changed.";
        return new Eligibility(false, State.WITHHELD, null, null, text);
    }

    private static Result withheld(String reason,
                                   DecelHistoricalThresholdDecision.Result decision) {
        StringBuilder text = new StringBuilder("HISTORICAL DECEL RESULT — WITHHELD\n")
                .append(safe(reason))
                .append("\nNo ProposalWritePlan is available, no Working Tune value changed, and no Burn occurs.");
        if (decision != null) text.append("\n\n").append(decision.reportText);
        return new Result(State.WITHHELD, null, decision, text.toString());
    }

    private static boolean qualifiesForPhysicalMagnitudeEvidence(LiveSample sample) {
        if (sample == null || !DecelSignalSamples.physicalEvidenceAllowed(sample)) return false;
        return Double.isFinite(sample.getSeconds())
                && Double.isFinite(sample.get(ChannelRole.RPM))
                && Double.isFinite(sample.get(ChannelRole.TPS))
                && Double.isFinite(sample.getTpsDot());
    }

    private static boolean sameAxis(double[] actual,
                                    List<DecelHistoricalThresholdDecision.BinDecision> bins) {
        if (actual == null || bins == null || actual.length != bins.size()) return false;
        for (int i = 0; i < actual.length; i++) {
            if (!near(actual[i], bins.get(i).rpm, RPM_TOLERANCE)) return false;
        }
        return true;
    }

    private static boolean sameAxis(double[] actual, double[] expected) {
        if (actual == null || expected == null || actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if (!near(actual[i], expected[i], RPM_TOLERANCE)) return false;
        }
        return true;
    }

    private static boolean near(double a, double b, double tolerance) {
        return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= tolerance;
    }

    private static boolean empty(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static String safe(String value) {
        return empty(value) ? "Historical proposal authority is unavailable." : value.trim();
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
