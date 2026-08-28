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

/** Non-destructive evidence ledger for the controlled Guided capture session. */
final class GuidedEvidenceRecorder {
    private static final String RECIPE_ID = "predictive-map-blend-duration";
    private static final int DIAGNOSTIC_COLUMNS = 24;

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
                outcome.durationSeconds, outcome.decisionText(), outcome.isValid(),
                outcome.validCount, outcome.groupId, outcome.groupCount,
                outcome.details, outcome.trace));
        if (snapshot != null) finalSummary = snapshot.result;
    }

    synchronized void discardLastAccepted() {
        for (int i = records.size() - 1; i >= 0; i--) {
            Record record = records.get(i);
            if (record.included && isIncludedDecision(record.decision)) {
                record.included = false;
                record.disposition = "DISCARDED_FROM_CONTROLLED_MEASUREMENT_GROUPS";
                return;
            }
        }
    }

    synchronized void finish(GuidedSessionSnapshot snapshot) {
        finishedAt = nowIso();
        finalSummary = snapshot == null ? "" : snapshot.result;
    }

    synchronized int recordCount() { return records.size(); }
    synchronized String sessionId() { return sessionId; }

    synchronized String reportText(String pluginVersion,
                                   GuidedSessionSnapshot current) {
        return reportText(pluginVersion, current, "", "");
    }

    synchronized String reportText(String pluginVersion,
                                   GuidedSessionSnapshot current,
                                   String tuningResult,
                                   String copyPasteBlock) {
        StringBuilder out = new StringBuilder();
        out.append("AE Tuner Guided Session report\n")
                .append("Plugin version: ").append(pluginVersion).append('\n')
                .append("Session ID: ").append(sessionId).append('\n')
                .append("Recipe: Predictive MAP Blend Duration\n")
                .append("Recipe ID: ").append(RECIPE_ID).append('\n')
                .append("Started: ").append(startedAt).append('\n')
                .append("Finished: ").append(finishedAt.length() == 0
                        ? "not finished" : finishedAt).append('\n')
                .append("Capture boundary: Guided measurement/capture is read-only. Explicit reviewed Apply/Restore may write only declared TunerStudio working-tune RAM values when a validated recipe exposes a write plan; automatic application and ECU burn remain prohibited.\n\n")
                .append("SESSION SUMMARY\n")
                .append("===============\n")
                .append("Selected actual table RPM bin: ").append(format(startRpm, 0)).append('\n')
                .append("Configured desired TPS step: ")
                .append(format(heldTps, 1)).append(" points\n")
                .append("Comparable-event target: ").append(targetCount).append('\n')
                .append("Gear mode: ").append(gearMode).append('\n')
                .append("Recorded guided outcomes: ").append(records.size()).append('\n')
                .append("\nLast Guided outcome\n")
                .append("-------------------\n");
        String summary = finalSummary.length() > 0
                ? finalSummary
                : current == null ? "No session summary available." : current.result;
        out.append(summary).append('\n');

        if ((tuningResult != null && tuningResult.trim().length() > 0)
                || (copyPasteBlock != null && copyPasteBlock.trim().length() > 0)) {
            out.append("\nTUNING RESULT / COPY-PASTE DATA\n")
                    .append("===============================\n");
            if (tuningResult != null && tuningResult.trim().length() > 0) {
                out.append(tuningResult.trim()).append('\n');
            }
            out.append("\nTunerStudio copy/paste block:\n");
            if (copyPasteBlock != null && copyPasteBlock.trim().length() > 0) {
                out.append(copyPasteBlock.trim()).append('\n');
            } else {
                out.append("Unavailable — numerical proposal is currently withheld.\n");
            }
        }

        out.append("\nCOMPLETED GUIDED OUTCOMES\n")
                .append("=========================\n");
        if (records.isEmpty()) out.append("No completed guided outcomes recorded.\n");
        for (Record record : records) {
            out.append('\n').append("Event ").append(record.sequence)
                    .append(" | ").append(record.decision)
                    .append(" | controlled-series inclusion: ")
                    .append(record.included ? "YES" : "NO")
                    .append(" | recorded in audit ledger: YES")
                    .append(" | valid count after event: ")
                    .append(record.acceptedCount);
            if (Double.isFinite(record.durationSeconds)) {
                out.append(" | final-target duration: ")
                        .append(format(record.durationSeconds, 3)).append(" s");
            }
            if (record.groupId.length() > 0) {
                out.append(" | measurement group: ").append(record.groupId)
                        .append(" (").append(record.groupCount).append(" event")
                        .append(record.groupCount == 1 ? "" : "s").append(")");
            }
            out.append(" | sample time: ")
                    .append(format(record.sampleSeconds, 3)).append(" s\n");
            out.append("Disposition: ").append(record.disposition).append('\n');
            out.append(indent(record.details)).append('\n');
        }
        return out.toString();
    }

    synchronized String diagnosticText() {
        StringBuilder out = new StringBuilder();
        out.append("GUIDED DIAGNOSTIC TRACES\n")
                .append("========================\n")
                .append("Raw/compact attempt traces are retained here so the session summary and model review remain readable first.\n");
        boolean any = false;
        for (Record record : records) {
            if (record.trace.length() == 0) continue;
            any = true;
            out.append("\nEvent ").append(record.sequence)
                    .append(" | ").append(record.decision).append('\n')
                    .append("-------------------------\n")
                    .append(record.trace);
            if (!record.trace.endsWith("\n")) out.append('\n');
        }
        if (!any) out.append("No compact trace evidence recorded.\n");
        return out.toString();
    }

    synchronized String csvText(String pluginVersion) {
        StringBuilder out = new StringBuilder();
        out.append("session_id,plugin_version,recipe_id,sequence,sample_time_s,duration_s,decision,")
                .append("included_in_controlled_series,recorded_in_audit_ledger,disposition,")
                .append("accepted_count_after_event,configured_start_rpm,configured_desired_tps_step,")
                .append("group_id,group_count,target_count,gear_mode,detail\n");
        for (Record record : records) {
            out.append(csv(sessionId)).append(',')
                    .append(csv(pluginVersion)).append(',')
                    .append(csv(RECIPE_ID)).append(',')
                    .append(record.sequence).append(',')
                    .append(format(record.sampleSeconds, 3)).append(',')
                    .append(format(record.durationSeconds, 3)).append(',')
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
                    .append(csv(singleLine(record.details))).append('\n');
        }
        return out.toString();
    }

    synchronized String diagnosticsCsvText(String audioCsv) {
        StringBuilder out = new StringBuilder();
        out.append("record_type,event_sequence,trace_index,dt_s,rpm,tps,map,fallback_map,effective_map,gap,tpsdot,")
                .append("detector,prediction,pred_reset_cnt,pred_expired,gear,vss,key,value,")
                .append("audio_sequence,audio_timestamp,audio_stage,audio_cue,detail\n");
        for (Record record : records) appendTraceDiagnostics(out, record);
        appendAudioDiagnostics(out, audioCsv);
        return out.toString();
    }

    private static void appendTraceDiagnostics(StringBuilder out, Record record) {
        if (record.trace.length() == 0) return;
        String[] lines = record.trace.replace("\r", "").split("\n");
        boolean samples = false;
        int traceIndex = 0;
        for (String line : lines) {
            if (line.length() == 0 || line.startsWith("Compact attempt trace")) continue;
            if (line.startsWith("dt_s,")) { samples = true; continue; }
            if (samples) {
                String[] fields = line.split(",", -1);
                if (fields.length >= 9 && isNumber(fields[0])) {
                    String[] row = diagnosticRow("TRACE_SAMPLE", record.sequence);
                    row[2] = Integer.toString(traceIndex++);
                    row[3] = field(fields, 0);
                    row[4] = field(fields, 1);
                    row[5] = field(fields, 2);
                    row[6] = field(fields, 3);
                    row[7] = field(fields, 4);
                    if (fields.length >= 14) {
                        row[8] = field(fields, 5);
                        row[9] = field(fields, 6);
                        row[10] = field(fields, 7);
                        row[11] = field(fields, 8);
                        row[12] = field(fields, 9);
                        row[13] = field(fields, 10);
                        row[14] = field(fields, 11);
                        row[15] = field(fields, 12);
                        row[16] = field(fields, 13);
                    } else {
                        // Compatibility with older 9-column compact traces.
                        row[9] = field(fields, 5);
                        row[10] = field(fields, 6);
                        row[11] = field(fields, 7);
                        row[12] = field(fields, 8);
                    }
                    appendDiagnosticRow(out, row);
                    continue;
                }
            }
            if (line.indexOf('=') >= 0) {
                String[] row = diagnosticRow("TRACE_MARKER", record.sequence);
                row[17] = markerKey(line);
                row[18] = markerValue(line);
                row[23] = singleLine(line);
                appendDiagnosticRow(out, row);
                continue;
            }
            int comma = line.indexOf(',');
            if (!samples && comma > 0) {
                String[] row = diagnosticRow("TRACE_META", record.sequence);
                row[17] = line.substring(0, comma);
                row[18] = singleLine(line.substring(comma + 1));
                appendDiagnosticRow(out, row);
            } else {
                String[] row = diagnosticRow("TRACE_NOTE", record.sequence);
                row[23] = singleLine(line);
                appendDiagnosticRow(out, row);
            }
        }
    }

    private static void appendAudioDiagnostics(StringBuilder out, String audioCsv) {
        if (audioCsv == null || audioCsv.length() == 0) return;
        String[] lines = audioCsv.replace("\r", "").split("\n");
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().length() == 0) continue;
            List<String> fields = parseCsvLine(lines[i]);
            if (fields.size() < 5) continue;
            String[] row = diagnosticRow("AUDIO", -1);
            row[19] = fields.get(0);
            row[20] = fields.get(1);
            row[21] = fields.get(2);
            row[22] = fields.get(3);
            row[23] = singleLine(fields.get(4));
            appendDiagnosticRow(out, row);
        }
    }

    private static String[] diagnosticRow(String type, int eventSequence) {
        String[] row = new String[DIAGNOSTIC_COLUMNS];
        for (int i = 0; i < row.length; i++) row[i] = "";
        row[0] = type == null ? "" : type;
        if (eventSequence >= 0) row[1] = Integer.toString(eventSequence);
        return row;
    }

    private static void appendDiagnosticRow(StringBuilder out, String[] row) {
        if (row == null || row.length != DIAGNOSTIC_COLUMNS) {
            throw new IllegalArgumentException("Guided diagnostic row must have exactly "
                    + DIAGNOSTIC_COLUMNS + " fields");
        }
        for (int i = 0; i < row.length; i++) {
            if (i > 0) out.append(',');
            String value = row[i] == null ? "" : row[i];
            if (value.length() == 0) continue;
            if (diagnosticNumericColumn(i) && isNumber(value)) out.append(value);
            else out.append(csv(value));
        }
        out.append('\n');
    }

    private static boolean diagnosticNumericColumn(int index) {
        return (index >= 1 && index <= 16) || index == 19;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"'); i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                fields.add(current.toString()); current.setLength(0);
            } else current.append(c);
        }
        fields.add(current.toString());
        return fields;
    }

    private static String field(String[] fields, int index) {
        return index >= 0 && index < fields.length ? fields[index] : "";
    }

    private static boolean isNumber(String value) {
        if (value == null || value.length() == 0) return false;
        try { Double.parseDouble(value); return true; }
        catch (NumberFormatException ex) { return false; }
    }

    private static String markerKey(String line) {
        int equals = line.indexOf('=');
        return equals <= 0 ? "marker" : line.substring(0, equals);
    }

    private static String markerValue(String line) {
        int equals = line.indexOf('=');
        if (equals < 0 || equals + 1 >= line.length()) return "";
        int comma = line.indexOf(',', equals + 1);
        return comma < 0 ? line.substring(equals + 1)
                : line.substring(equals + 1, comma);
    }

    private static boolean isIncludedDecision(String decision) {
        return "VALID".equals(decision) || "VALID_WITH_WARNING".equals(decision);
    }

    private static String initialDisposition(String decision, boolean included) {
        if (included && isIncludedDecision(decision)) {
            return "RETAINED_IN_CONTROLLED_MEASUREMENT_GROUPS";
        }
        if ("EXCLUDED".equals(decision)) return "EXCLUDED_FROM_GUIDED_MEASUREMENT";
        if ("RETURN_TO_BASELINE".equals(decision)) return "RETURNED_TO_BASELINE_BEFORE_VALID_CAPTURE";
        return "NOT_INCLUDED_IN_GUIDED_MEASUREMENT";
    }

    private static String indent(String value) {
        if (value == null || value.length() == 0) return "  No event detail available.";
        return "  " + value.replace("\n", "\n  ");
    }

    private static String singleLine(String value) {
        if (value == null) return "";
        return value.replace("\r", "")
                .replace("\n", " | ")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String format(double value, int decimals) {
        if (!Double.isFinite(value)) return "";
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    private static String nowIso() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }

    private static final class Record {
        final int sequence;
        final double sampleSeconds;
        final double durationSeconds;
        final String decision;
        final int acceptedCount;
        final String groupId;
        final int groupCount;
        final String details;
        final String trace;
        boolean included;
        String disposition;

        Record(int sequence, double sampleSeconds, double durationSeconds,
               String decision, boolean included, int acceptedCount,
               String groupId, int groupCount, String details, String trace) {
            this.sequence = sequence;
            this.sampleSeconds = sampleSeconds;
            this.durationSeconds = durationSeconds;
            this.decision = decision;
            this.included = included;
            this.acceptedCount = acceptedCount;
            this.groupId = groupId == null ? "" : groupId;
            this.groupCount = groupCount;
            this.details = details == null ? "" : details;
            this.trace = trace == null ? "" : trace;
            this.disposition = initialDisposition(decision, included);
        }
    }
}
