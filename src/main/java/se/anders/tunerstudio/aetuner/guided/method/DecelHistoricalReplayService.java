package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.BuildIdentity;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read-only composition boundary for one historical Decel log and one Working
 * Tune snapshot. Cross-log accumulation is intentionally not performed here;
 * later accumulation must first prove calibration compatibility.
 */
public final class DecelHistoricalReplayService {
    private static final int MAX_AUDIT_EVENTS = 400;

    public static final class Result {
        public final DecelHistoricalLogImporter.Result importResult;
        public final MlgEmbeddedTuneMetadata embeddedTune;
        public final DecelHistoricalEvidenceAnalyzer.Result analysis;
        public final DecelHistoricalStateReplay.Result stateReplay;
        public final DecelHistoricalThresholdDecision.Result thresholdDecision;
        public final String reportText;

        Result(DecelHistoricalLogImporter.Result importResult,
               MlgEmbeddedTuneMetadata embeddedTune,
               DecelHistoricalEvidenceAnalyzer.Result analysis,
               DecelHistoricalStateReplay.Result stateReplay,
               DecelHistoricalThresholdDecision.Result thresholdDecision,
               String reportText) {
            this.importResult = importResult;
            this.embeddedTune = embeddedTune;
            this.analysis = analysis;
            this.stateReplay = stateReplay;
            this.thresholdDecision = thresholdDecision;
            this.reportText = reportText == null ? "" : reportText;
        }

        public boolean analyzed() {
            return importResult != null && importResult.usable() && analysis != null;
        }
    }

    private DecelHistoricalReplayService() { }

    public static Result analyze(File file, AeProjectSnapshot snapshot) throws IOException {
        return analyze(file, snapshot, DecelHistoricalProgress.NONE);
    }

    public static Result analyze(File file, AeProjectSnapshot snapshot,
                                 DecelHistoricalProgress progress) throws IOException {
        DecelHistoricalLogImporter.Result imported = DecelHistoricalLogImporter.load(file, progress);
        DecelHistoricalProgress.report(progress, file, "Reading calibration metadata", imported.inputRows);
        MlgEmbeddedTuneMetadata embedded = MlgEmbeddedTuneMetadata.read(file);
        DecelHistoricalProgress.report(progress, file, "Checking detector signals", imported.inputRows);
        String historicalDetectorMode = historicalDetectorMode(embedded, snapshot);
        boolean detectorModeFromLog = embedded != null && !empty(embedded.detectorModeRaw);

        StringBuilder report = new StringBuilder();
        report.append("AE TUNER REPORT IDENTITY\n")
                .append("Plugin build: ").append(BuildIdentity.VERSION).append('\n')
                .append("Decel decision algorithm: ")
                .append(BuildIdentity.DECEL_DECISION_ALGORITHM).append("\n\n")
                .append("DECEL HISTORICAL LOG ANALYSIS — READ ONLY\n")
                .append("Source: ").append(file == null ? "n/a" : file.getName()).append('\n')
                .append("Working Tune: ")
                .append(snapshot == null ? "not loaded" : snapshot.getConfigurationName()).append('\n')
                .append("Input: ").append(imported.sourceType).append(" | rows ")
                .append(imported.inputRows).append(" | accepted ")
                .append(imported.acceptedRows).append(" | skipped ")
                .append(imported.skippedRows).append('\n')
                .append("Importer: ").append(imported.status).append('\n')
                .append("\nSIGNAL AUTHORITY\n")
                .append("Historical detector mode authority: ")
                .append(empty(historicalDetectorMode) ? "unresolved" : historicalDetectorMode)
                .append(detectorModeFromLog ? " (embedded captured tune)" : " (Working Tune fallback)")
                .append('\n')
                .append("Decel analysis source: ")
                .append(DecelSignalSamples.sourceLabel(imported.samples, historicalDetectorMode)).append('\n')
                .append("Fuel: TPS AE change: ")
                .append(hasAny(imported.samples, ChannelRole.DELTA_TPS)
                        ? "present as positive-side context only" : "not present").append('\n')
                .append("Fuel: TPS Decel Active: ")
                .append(imported.hasDecelActive ? "present" : "not present").append('\n')
                .append("Fuel: TPS AE Active: ")
                .append(imported.hasAccelActive ? "present" : "not present").append('\n');

        DecelSignalSamples.Agreement agreement =
                DecelSignalSamples.agreement(imported.samples, historicalDetectorMode, 0.0005);
        if (agreement.twinRole != null) {
            report.append("Configured-mode falling twin: ")
                    .append(agreement.twinRole.getLabel()).append('\n')
                    .append("Direct decelTps vs mode-twin comparison: ")
                    .append(agreement.comparedSamples).append(" sample(s), mismatches ")
                    .append(agreement.mismatchedSamples).append(", max |difference| ")
                    .append(fmt(agreement.maxDifference)).append(" | ");
            if (agreement.comparedSamples == 0) {
                report.append("NOT COMPARABLE\n");
            } else if (agreement.verified()) {
                report.append("VERIFIED\n");
            } else {
                report.append("RAW MISMATCH PRESENT — DIRECT REMAINS CROSS-CHECK ONLY; MODE TWIN REMAINS ANALYSIS AUTHORITY. Proposal eligibility separately checks whether any mismatch changes a current/log-derived threshold crossing.\n");
            }
        } else {
            report.append("Configured-mode falling twin: unresolved. Direct decelTps may be shown as fallback evidence, but detector identity is not independently verified.\n");
        }

        report.append("\nAS-RECORDED CALIBRATION / FINGERPRINT\n")
                .append(embedded.status).append('\n');
        if (embedded.available) {
            report.append("Firmware signature: ")
                    .append(empty(embedded.firmwareSignature) ? "n/a" : embedded.firmwareSignature).append('\n')
                    .append("Tune CRC variable: ")
                    .append(empty(embedded.tuneCrcPcVariable) ? "n/a" : embedded.tuneCrcPcVariable).append('\n')
                    .append("Historical detector mode (raw MSQ): ")
                    .append(empty(embedded.detectorModeRaw) ? "n/a" : embedded.detectorModeRaw).append('\n')
                    .append("Historical Delta Window: ").append(fmt(embedded.deltaWindowMs)).append(" ms\n")
                    .append("Historical TPS lookback: ").append(fmt(embedded.sampleLengthSeconds)).append(" s\n")
                    .append("Historical Fast Callback: ")
                    .append(embedded.fastCallback == null ? "n/a" : embedded.fastCallback.toString()).append('\n')
                    .append("Historical Decel Hold: ").append(fmt(embedded.decelHoldCycles)).append(" engine cycles\n")
                    .append("Calibration fingerprint SHA-256: ")
                    .append(empty(embedded.fingerprint) ? "n/a" : embedded.fingerprint).append('\n');
            if (embedded.hasDecelThresholdCurve()) {
                report.append("Historical Decel threshold curve: ")
                        .append(curve(embedded.decelThresholdRpmBins,
                                embedded.decelThresholdValues)).append('\n');
            }
        }
        if (snapshot != null) {
            report.append("Current Working Tune detector mode: ").append(snapshot.getEngagementModel()).append('\n')
                    .append("Current Working Tune Decel Hold: ").append(fmt(snapshot.getDecelHoldCycles()))
                    .append(" engine cycles\n");
        }
        report.append("Cross-log pooling remains WITHHELD until fingerprints/calibration compatibility are explicitly checked.\n\n");

        if (!imported.usable()) {
            report.append("No analysis was performed. Existing log evidence was not modified.\n")
                    .append("This path creates no ProposalWritePlan and cannot write to the controller.");
            return new Result(imported, embedded, null, null, null, report.toString());
        }

        List<LiveSample> decelSamples =
                DecelSignalSamples.asPrimaryDelta(imported.samples, historicalDetectorMode);
        if (DecelSignalSamples.usableCount(imported.samples, historicalDetectorMode) == 0) {
            report.append("No firmware-correct Decel detector samples are available after signal resolution. Analysis withheld.\n")
                    .append("Fuel: TPS AE change alone is not accepted as Decel evidence.\n")
                    .append("No ProposalWritePlan was created and no Working Tune value changed.");
            return new Result(imported, embedded, null, null, null, report.toString());
        }

        DecelHistoricalProgress.report(progress, file, "Classifying physical events", imported.inputRows);
        DecelHistoricalEvidenceAnalyzer.Result analysis =
                DecelHistoricalEvidenceAnalyzer.analyze(snapshot, decelSamples);
        DecelHistoricalProgress.report(progress, file, "Replaying detector state", imported.inputRows);
        DecelHistoricalStateReplay.Result state =
                DecelHistoricalStateReplay.replay(imported.samples, snapshot, embedded);
        DecelHistoricalProgress.report(progress, file, "Evaluating threshold evidence", imported.inputRows);
        DecelHistoricalThresholdDecision.Result thresholdDecision =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis, embedded);

        report.append("PHYSICAL EVENT CLASSIFICATION\n")
                .append("The classifier below receives the captured detector mode's explicit falling twin when available. Direct Fuel: TPS decel change is a cross-check/fallback, never an override of a contradictory mode twin.\n")
                .append(analysis.reportText).append("\n\n")
                .append(state.reportText).append("\n\n")
                .append(thresholdDecision.reportText).append("\n\n")
                .append(evidenceStrengthReport(thresholdDecision)).append("\n\n")
                .append(eventAudit(analysis, imported.samples, snapshot, embedded,
                        historicalDetectorMode))
                .append("\nBOUNDARY\n")
                .append("Historical replay and historical threshold decisions remain evidence-only. They create no ProposalWritePlan, change no Working Tune value, do not tune Hold or the Decel fuel table, and never Burn the ECU. Production threshold changes, if separately qualified, still use the existing guarded Review → ProposalWritePlan → Apply/readback → Restore path.");
        return new Result(imported, embedded, analysis, state, thresholdDecision,
                report.toString());
    }

    private static String evidenceStrengthReport(
            DecelHistoricalThresholdDecision.Result decision) {
        StringBuilder out = new StringBuilder("EVIDENCE STRENGTH — INDEPENDENT PHYSICAL EVENTS\n");
        if (decision == null || decision.bins == null || decision.bins.isEmpty()) {
            return out.append("No threshold-result bins available.\n").toString();
        }
        int strong = 0;
        int good = 0;
        int minimum = 0;
        int none = 0;
        for (DecelHistoricalThresholdDecision.BinDecision bin : decision.bins) {
            DecelHistoricalEvidenceStrength.Assessment assessment =
                    DecelHistoricalEvidenceStrength.assess(bin);
            if (assessment.level == DecelHistoricalEvidenceStrength.Level.STRONG) strong++;
            else if (assessment.level == DecelHistoricalEvidenceStrength.Level.GOOD) good++;
            else if (assessment.level == DecelHistoricalEvidenceStrength.Level.MINIMUM) minimum++;
            else none++;
            out.append("  ").append(Math.round(bin.rpm)).append(" RPM | ")
                    .append(DecelHistoricalEvidenceStrength.display(assessment.level))
                    .append(" | ").append(assessment.reason).append('\n');
        }
        out.append("Summary: ").append(strong).append(" strong / ")
                .append(good).append(" good / ")
                .append(minimum).append(" minimum / ")
                .append(none).append(" none.\n")
                .append("Strength is coverage context only; it does not create proposal/write authority or pool evidence across logs.");
        return out.toString();
    }

    private static String eventAudit(DecelHistoricalEvidenceAnalyzer.Result analysis,
                                     List<LiveSample> samples, AeProjectSnapshot snapshot,
                                     MlgEmbeddedTuneMetadata embedded,
                                     String historicalDetectorMode) {
        StringBuilder out = new StringBuilder("EVENT AUDIT\n");
        out.append("time_s | class | rpm | excursion | decel_peak | current_threshold | current_cross | as_recorded_edge | accel_cancel | decel_active | max_hold_counter | min_fuel_mult\n");
        if (analysis == null || analysis.bins == null) return out.append("No events.\n").toString();
        List<DecelHistoricalEvidenceAnalyzer.Event> events =
                new ArrayList<DecelHistoricalEvidenceAnalyzer.Event>();
        for (DecelHistoricalEvidenceAnalyzer.BinSummary bin : analysis.bins) {
            if (bin != null && bin.events != null) events.addAll(bin.events);
        }
        Collections.sort(events, new Comparator<DecelHistoricalEvidenceAnalyzer.Event>() {
            @Override public int compare(DecelHistoricalEvidenceAnalyzer.Event a,
                                         DecelHistoricalEvidenceAnalyzer.Event b) {
                return Double.compare(a.startSeconds, b.startSeconds);
            }
        });
        int shown = Math.min(events.size(), MAX_AUDIT_EVENTS);
        for (int i = 0; i < shown; i++) {
            DecelHistoricalEvidenceAnalyzer.Event event = events.get(i);
            AuditContext context = auditContext(event, samples, embedded,
                    historicalDetectorMode);
            out.append(fmt(event.startSeconds)).append("..").append(fmt(event.endSeconds)).append(" | ")
                    .append(event.eventClass).append(" | ")
                    .append(Math.round(event.startRpm)).append(" | ")
                    .append(fmt(event.excursion)).append(" | ")
                    .append(fmt(event.peakMagnitude)).append(" | ")
                    .append(fmt(event.currentThreshold)).append(" | ")
                    .append(event.currentThresholdCrossed ? "YES" : "no").append(" | ")
                    .append(context.asRecordedEdge ? "YES" : "no").append(" | ")
                    .append(context.accelCancel ? "YES" : "no").append(" | ")
                    .append(event.decelActiveObserved ? "YES" : "no").append(" | ")
                    .append(fmt(context.maxCycleCounter)).append(" | ")
                    .append(fmt(context.minFuelMult)).append('\n');
        }
        if (events.size() > shown) {
            out.append("Audit truncated after ").append(shown).append(" / ")
                    .append(events.size()).append(" events; classification totals above remain complete.\n");
        } else {
            out.append("Audited events: ").append(events.size()).append(".\n");
        }
        return out.toString();
    }

    private static final class AuditContext {
        boolean asRecordedEdge;
        boolean accelCancel;
        double maxCycleCounter = Double.NaN;
        double minFuelMult = Double.NaN;
    }

    private static AuditContext auditContext(DecelHistoricalEvidenceAnalyzer.Event event,
                                             List<LiveSample> samples,
                                             MlgEmbeddedTuneMetadata embedded,
                                             String historicalDetectorMode) {
        AuditContext out = new AuditContext();
        if (event == null || samples == null) return out;
        double end = event.endSeconds + 0.18;
        for (LiveSample sample : samples) {
            if (sample == null || sample.getSeconds() < event.startSeconds
                    || sample.getSeconds() > end) continue;
            double decel = DecelSignalSamples.value(sample, historicalDetectorMode);
            double rpm = sample.get(ChannelRole.RPM);
            double threshold = sample.get(ChannelRole.DECEL_THRESHOLD);
            if (!Double.isFinite(threshold) && embedded != null && embedded.hasDecelThresholdCurve()) {
                threshold = embedded.decelThresholdForRpm(rpm);
            }
            boolean edge = Double.isFinite(decel) && Double.isFinite(threshold)
                    && threshold > 0.0 && decel < -threshold;
            if (edge) out.asRecordedEdge = true;
            if (edge && sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) out.accelCancel = true;
            double cycle = sample.get(ChannelRole.TPS_DECEL_CYCLE_CNT);
            if (Double.isFinite(cycle)) {
                out.maxCycleCounter = !Double.isFinite(out.maxCycleCounter)
                        ? cycle : Math.max(out.maxCycleCounter, cycle);
            }
            double mult = sample.get(ChannelRole.DECEL_FUEL_MULT);
            if (Double.isFinite(mult)) {
                out.minFuelMult = !Double.isFinite(out.minFuelMult)
                        ? mult : Math.min(out.minFuelMult, mult);
            }
        }
        return out;
    }

    static String historicalDetectorMode(MlgEmbeddedTuneMetadata embedded,
                                         AeProjectSnapshot snapshot) {
        if (embedded != null && !empty(embedded.detectorModeRaw)) return embedded.detectorModeRaw;
        return snapshot == null ? null : snapshot.getEngagementModel();
    }

    private static boolean hasAny(List<LiveSample> samples, ChannelRole role) {
        if (samples == null) return false;
        for (LiveSample sample : samples) {
            if (sample != null && Double.isFinite(sample.get(role))) return true;
        }
        return false;
    }

    private static String curve(double[] rpm, double[] values) {
        if (rpm == null || values == null || rpm.length == 0 || rpm.length != values.length) return "n/a";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rpm.length; i++) {
            if (i > 0) out.append(", ");
            out.append(Math.round(rpm[i])).append('=').append(fmt(values[i]));
        }
        return out.toString();
    }

    private static boolean empty(String value) { return value == null || value.length() == 0; }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
