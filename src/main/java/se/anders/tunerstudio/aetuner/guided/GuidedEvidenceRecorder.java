package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Non-destructive evidence ledger for the adaptive guided capture session.
 *
 * It records completed typed outcomes without replacing passive events or
 * changing proposal eligibility. Discarding an included guided event marks it
 * as removed from proposal use while retaining the original evidence in exports.
 */
final class GuidedEvidenceRecorder {
    private static final String RECIPE_ID = "predictive-map-blend-duration";

    private final List<Record> records = new ArrayList<Record>();

    private String sessionId = "not-started";
    private String startedAt = "not-started";
    private String finishedAt = "";
    private double startRpm = Double.NaN;
    private double heldTps = Double.NaN;
    private int targetCount;
    private String gearMode = "unknown";
    private String finalSummary = "";
    private int sequence;

    synchronized void startAdaptive(double configuredStartRpm,
                                    double configuredDesiredStep,
                                    int configuredTargetCount,
                                    String configuredGearMode,
                                    long identityNano) {
        records.clear();
        sequence = 0;
        sessionId = "guided-" + Long.toHexString(identityNano);
        startedAt = nowIso();
        finishedAt = "";
        startRpm = configuredStartRpm;
        heldTps = configuredDesiredStep;
        targetCount = configuredTargetCount;
        gearMode = configuredGearMode == null ? "unknown" : configuredGearMode;
        finalSummary = "";
    }

    synchronized void reset() {
        records.clear();
        sequence = 0;
        sessionId = "not-started";
        startedAt = "not-started";
        finishedAt = "";
        startRpm = Double.NaN;
        heldTps = Double.NaN;
        targetCount = 0;
        gearMode = "unknown";
        finalSummary = "";
    }

    synchronized void record(GuidedOutcome outcome,
                             GuidedSessionSnapshot snapshot) {
        if (outcome == null) return;
        sequence++;
        records.add(new Record(sequence, outcome.sampleSeconds,
                outcome.decisionText(), outcome.isValid(), outcome.validCount,
                outcome.groupId, outcome.groupCount,
                outcome.details, outcome.trace));
        if (snapshot != null) {
            finalSummary = snapshot.result;
        }
    }

    synchronized void discardLastAccepted() {
        for (int i = records.size() - 1; i >= 0; i--) {
            Record record = records.get(i);
            if (record.included && isIncludedDecision(record.decision)) {
                record.included = false;
                record.disposition = "DISCARDED_FROM_ADAPTIVE_PROPOSAL_GROUPS";
                return;
            }
        }
    }

    synchronized void finish(GuidedSessionSnapshot snapshot) {
        finishedAt = nowIso();
        finalSummary = snapshot == null ? "" : snapshot.result;
    }

    synchronized int recordCount() {
        return records.size();
    }

    synchronized String sessionId() {
        return sessionId;
    }

    synchronized String reportText(String pluginVersion,
                                   GuidedSessionSnapshot current) {
        StringBuilder out = new StringBuilder();
        out.append("AE Tuner Guided Capture evidence\n")
                .append("Plugin version: ").append(pluginVersion).append('\n')
                .append("Session ID: ").append(sessionId).append('\n')
                .append("Recipe: Adaptive Predictive MAP Blend Duration\n")
                .append("Recipe ID: ").append(RECIPE_ID).append('\n')
                .append("Started: ").append(startedAt).append('\n')
                .append("Finished: ").append(finishedAt.length() == 0
                        ? "not finished" : finishedAt).append('\n')
                .append("Configured start RPM: ").append(format(startRpm, 0)).append('\n')
                .append("Configured desired TPS step: ")
                .append(format(heldTps, 1)).append(" points\n")
                .append("Comparable-event target: ")
                .append(targetCount).append('\n')
                .append("Gear mode: ").append(gearMode).append('\n')
                .append("Read-only: no ECU RAM write, burn, or automatic paste occurred.\n\n")
                .append("Completed guided outcomes\n")
                .append("=========================\n");

        if (records.isEmpty()) {
            out.append("No completed guided outcomes recorded.\n");
        }
        for (Record record : records) {
            out.append('\n').append("Event ").append(record.sequence)
                    .append(" | ").append(record.decision)
                    .append(" | proposal inclusion: ")
                    .append(record.included ? "YES" : "NO")
                    .append(" | recorded in audit ledger: YES")
                    .append(" | valid count after event: ")
                    .append(record.acceptedCount);
            if (record.groupId.length() > 0) {
                out.append(" | adaptive group: ").append(record.groupId)
                        .append(" (").append(record.groupCount).append(" event")
                        .append(record.groupCount == 1 ? "" : "s").append(")");
            }
            out.append(" | sample time: ")
                    .append(format(record.sampleSeconds, 3)).append(" s\n");
            if (record.disposition.length() > 0) {
                out.append("Disposition: ").append(record.disposition).append('\n');
            }
            out.append(indent(record.details)).append('\n');
            if (record.trace.length() > 0) {
                out.append("  Per-attempt compact trace\n")
                        .append("  -------------------------\n")
                        .append(indent(record.trace)).append('\n');
            }
        }

        out.append("\nFinal/current guided summary\n")
                .append("============================\n");
        String summary = finalSummary.length() > 0
                ? finalSummary
                : current == null ? "No session summary available." : current.result;
        out.append(summary).append('\n');
        return out.toString();
    }

    synchronized String csvText(String pluginVersion) {
        StringBuilder out = new StringBuilder();
        out.append("session_id,plugin_version,recipe_id,sequence,sample_time_s,decision,")
                .append("included_in_controlled_series,recorded_in_audit_ledger,disposition,")
                .append("accepted_count_after_event,configured_start_rpm,configured_held_tps,")
                .append("group_id,group_count,target_count,gear_mode,details,compact_trace\n");
        for (Record record : records) {
            out.append(csv(sessionId)).append(',')
                    .append(csv(pluginVersion)).append(',')
                    .append(csv(RECIPE_ID)).append(',')
                    .append(record.sequence).append(',')
                    .append(format(record.sampleSeconds, 3)).append(',')
                    .append(csv(record.decision)).append(',')
                    .append(record.included ? "true" : "false").append(',')
                    .append("true,")
                    .append(csv(record.disposition)).append(',')
                    .append(record.acceptedCount).append(',')
                    .append(format(startRpm, 0)).append(',')
                    .append(format(heldTps, 1)).append(',')
                    .append(csv(record.groupId)).append(',')
                    .append(record.groupCount).append(',')
                    .append(targetCount).append(',')
                    .append(csv(gearMode)).append(',')
                    .append(csv(record.details)).append(',')
                    .append(csv(record.trace)).append('\n');
        }
        return out.toString();
    }

    private static boolean isIncludedDecision(String decision) {
        return "VALID".equals(decision)
                || "VALID_WITH_WARNING".equals(decision);
    }

    private static String indent(String value) {
        if (value == null || value.length() == 0) {
            return "  No event detail available.";
        }
        return "  " + value.replace("\n", "\n  ");
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String format(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    private static String nowIso() {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }

    private static final class Record {
        final int sequence;
        final double sampleSeconds;
        final String decision;
        final int acceptedCount;
        final String groupId;
        final int groupCount;
        final String details;
        final String trace;
        boolean included;
        String disposition = "";

        Record(int sequence, double sampleSeconds, String decision,
               boolean included, int acceptedCount, String details,
               String trace) {
            this(sequence, sampleSeconds, decision, included, acceptedCount,
                    "", 0, details, trace);
        }

        Record(int sequence, double sampleSeconds, String decision,
               boolean included, int acceptedCount, String groupId,
               int groupCount, String details, String trace) {
            this.sequence = sequence;
            this.sampleSeconds = sampleSeconds;
            this.decision = decision;
            this.included = included;
            this.acceptedCount = acceptedCount;
            this.groupId = groupId == null ? "" : groupId;
            this.groupCount = groupCount;
            this.details = details == null ? "" : details;
            this.trace = trace == null ? "" : trace;
        }
    }
}
