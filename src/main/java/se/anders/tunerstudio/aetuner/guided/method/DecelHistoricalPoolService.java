package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.BuildIdentity;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only orchestration for explicitly selected compatible historical logs. */
public final class DecelHistoricalPoolService {
    public static final String SOURCE_TYPE_PREFIX = "Pooled Decel historical logs";

    private DecelHistoricalPoolService() { }

    public static DecelHistoricalReplayService.Result analyze(File[] files,
                                                              AeProjectSnapshot snapshot)
            throws IOException {
        return analyze(files, snapshot, DecelHistoricalProgress.NONE);
    }

    public static DecelHistoricalReplayService.Result analyze(File[] files,
            AeProjectSnapshot snapshot, DecelHistoricalProgress progress) throws IOException {
        if (files == null || files.length < 2) {
            throw new IOException("Select at least two historical logs for cross-log pooling.");
        }

        Set<String> unique = new HashSet<String>();
        List<File> sources = new ArrayList<File>();
        for (File file : files) {
            if (file == null || !file.isFile()) throw new IOException("One selected historical log is unavailable.");
            String canonical = file.getCanonicalPath();
            if (!unique.add(canonical)) {
                throw new IOException("The same historical log was selected more than once: " + file.getName()
                        + ". Duplicate evidence is not pooled.");
            }
            sources.add(file);
        }

        List<DecelHistoricalReplayService.Result> replays =
                new ArrayList<DecelHistoricalReplayService.Result>();
        List<MlgEmbeddedTuneMetadata> metadata = new ArrayList<MlgEmbeddedTuneMetadata>();
        List<DecelHistoricalEvidenceAnalyzer.Result> analyses =
                new ArrayList<DecelHistoricalEvidenceAnalyzer.Result>();
        for (File file : sources) {
            DecelHistoricalReplayService.Result replay =
                    DecelHistoricalReplayService.analyze(file, snapshot, progress);
            replays.add(replay);
            metadata.add(replay.embeddedTune);
            if (replay.analysis != null) analyses.add(replay.analysis);
        }

        DecelHistoricalProgress.report(progress, null, "Checking detector compatibility across logs", -1);
        DecelHistoricalPoolCompatibility.Result compatibility =
                DecelHistoricalPoolCompatibility.evaluate(snapshot, metadata);
        DecelHistoricalLogImporter.Result combinedImport = combinedImport(replays);
        MlgEmbeddedTuneMetadata firstAuthority = metadata.isEmpty() ? null : metadata.get(0);
        MlgEmbeddedTuneMetadata authority = compatibility.compatible
                ? MlgEmbeddedTuneMetadata.pooledAuthority(
                        firstAuthority, compatibility.detectorFingerprint)
                : firstAuthority;

        StringBuilder report = new StringBuilder();
        report.append("AE TUNER REPORT IDENTITY\n")
                .append("Plugin build: ").append(BuildIdentity.VERSION).append('\n')
                .append("Decel decision algorithm: ").append(BuildIdentity.DECEL_DECISION_ALGORITHM).append('\n')
                .append("Cross-log pool policy: ").append(DecelHistoricalPoolCompatibility.POLICY_ID)
                .append("\n\nDECEL CROSS-LOG EVIDENCE POOL — READ ONLY\n")
                .append("Selected unique logs: ").append(sources.size()).append('\n')
                .append("Compatibility: ").append(compatibility.status).append('\n');
        if (compatibility.detectorFingerprint.length() > 0) {
            report.append("Detector-compatibility fingerprint SHA-256: ")
                    .append(compatibility.detectorFingerprint).append('\n');
        }
        report.append("\nSOURCE SESSIONS\n");
        for (int i = 0; i < sources.size(); i++) {
            DecelHistoricalReplayService.Result replay = replays.get(i);
            MlgEmbeddedTuneMetadata meta = metadata.get(i);
            report.append("  ").append(i + 1).append(". ").append(sources.get(i).getName())
                    .append(" | full calibration fingerprint ")
                    .append(meta == null || meta.fingerprint.length() == 0 ? "n/a" : meta.fingerprint);
            if (replay.analysis != null) {
                report.append(" | events ").append(replay.analysis.normalCorrections)
                        .append(" normal / ").append(replay.analysis.decelReleases)
                        .append(" release / ").append(replay.analysis.ambiguousEvents)
                        .append(" ambiguous | quiet ")
                        .append(replay.analysis.quietCalibrationLocked ? "LOCKED" : "INSUFFICIENT");
            } else {
                report.append(" | physical analysis unavailable");
            }
            report.append('\n');
        }

        if (!compatibility.compatible) {
            report.append("\nPOOL RESULT WITHHELD\n")
                    .append("No cross-log event population was formed. Each source remains independently inspectable, but incompatible or incomplete detector evidence never gains pooled proposal authority.\n")
                    .append("No ProposalWritePlan was created and no Working Tune value changed.");
            return new DecelHistoricalReplayService.Result(
                    combinedImport, authority, null, null, null, report.toString());
        }

        if (analyses.size() != sources.size()) {
            report.append("\nPOOL RESULT WITHHELD\n")
                    .append("At least one selected log did not produce a complete physical-event analysis. Pooling fails closed.\n")
                    .append("No ProposalWritePlan was created and no Working Tune value changed.");
            return new DecelHistoricalReplayService.Result(
                    combinedImport, authority, null, null, null, report.toString());
        }

        DecelHistoricalProgress.report(progress, null, "Pooling independently classified events", -1);
        DecelHistoricalEvidenceAnalyzer.Result pooled =
                DecelHistoricalEvidencePool.combine(snapshot, analyses);
        if (!pooled.quietCalibrationLocked || pooled.bins.isEmpty()) {
            report.append("\n").append(pooled.reportText).append("\n")
                    .append("No ProposalWritePlan was created and no Working Tune value changed.");
            return new DecelHistoricalReplayService.Result(
                    combinedImport, authority, pooled, null, null, report.toString());
        }

        DecelHistoricalProgress.report(progress, null, "Evaluating pooled threshold evidence", -1);
        DecelHistoricalThresholdDecision.Result decision =
                DecelHistoricalThresholdDecision.evaluate(snapshot, pooled, authority);
        report.append("\nPOOLING SEMANTICS\n")
                .append("The full calibration fingerprints above are retained for audit but are not required to match. Whole-tune CRC, historical Decel threshold values/RPM curve and Hold may differ. Detector-defining settings and firmware must match exactly under the pool policy, and each log must independently match the current Working Tune detector configuration.\n")
                .append("Physical events are classified independently inside each source session before pooling, preserving each session's own quiet/noise calibration. The pooled distribution then recomputes Normal p95, Release p25, separation windows, evidence strength and the existing classification-boundary target from the combined independent events.\n")
                .append("The pooled proposal identity uses the detector-compatibility fingerprint above; per-source full tune fingerprints remain audit-only and do not masquerade as pooled identity.\n\n")
                .append(pooled.reportText).append("\n\n")
                .append(decision.reportText).append("\n\n")
                .append("BOUNDARY\n")
                .append("Cross-log pooling is evidence-only. It constructs no ProposalWritePlan by itself. Explicit Review remains required, followed by the existing stale check, exact controller readback and Restore-capable Apply path. No Burn.");

        return new DecelHistoricalReplayService.Result(
                combinedImport, authority, pooled, null, decision, report.toString());
    }

    public static String sourceLabel(File[] files) {
        int count = files == null ? 0 : files.length;
        return count + "-log compatible Decel pool";
    }

    public static boolean isPooled(DecelHistoricalReplayService.Result replay) {
        return replay != null && replay.importResult != null
                && replay.importResult.sourceType.startsWith(SOURCE_TYPE_PREFIX);
    }

    private static DecelHistoricalLogImporter.Result combinedImport(
            List<DecelHistoricalReplayService.Result> replays) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        int input = 0;
        int accepted = 0;
        int skipped = 0;
        boolean decelActive = false;
        boolean accelActive = false;
        boolean loggedThreshold = false;
        boolean cycleCount = false;
        boolean fuelMultiplier = false;
        boolean exactSignal = false;
        boolean twinCandidates = false;
        for (DecelHistoricalReplayService.Result replay : replays) {
            if (replay == null || replay.importResult == null) continue;
            DecelHistoricalLogImporter.Result imported = replay.importResult;
            samples.addAll(imported.samples);
            input += imported.inputRows;
            accepted += imported.acceptedRows;
            skipped += imported.skippedRows;
            decelActive |= imported.hasDecelActive;
            accelActive |= imported.hasAccelActive;
            loggedThreshold |= imported.hasLoggedDecelThreshold;
            cycleCount |= imported.hasDecelCycleCount;
            fuelMultiplier |= imported.hasDecelFuelMultiplier;
            exactSignal |= imported.hasExactDecelSignal;
            twinCandidates |= imported.hasModeTwinCandidates;
        }
        return new DecelHistoricalLogImporter.Result(
                samples, SOURCE_TYPE_PREFIX + " (" + replays.size() + ")",
                "Read-only union of raw samples retained only for detector-twin completeness/material-crossing audit. Physical-event classification was performed per-session before pooling.",
                input, accepted, skipped, decelActive, accelActive, loggedThreshold,
                cycleCount, fuelMultiplier, exactSignal, twinCandidates);
    }
}
