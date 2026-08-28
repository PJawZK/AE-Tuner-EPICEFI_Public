package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

public final class GuidedEvidenceRecorderRegressionTest {
    private GuidedEvidenceRecorderRegressionTest() { }

    public static void main(String[] args) {
        typedOutcomesAndDiscardRemainAuditable();
        spreadsheetExportsStayRectangular();
        System.out.println("GuidedEvidenceRecorderRegressionTest passed");
    }

    private static void typedOutcomesAndDiscardRemainAuditable() {
        GuidedEvidenceRecorder recorder = new GuidedEvidenceRecorder();
        recorder.startAdaptive(2600.0, 22.0, 5,
                "Manual 2nd | actual table point 2600 RPM", 12345L);

        recorder.record(outcome(GuidedOutcome.Decision.VALID,
                10.0, 0.50, 1, "A", 1, "VALID ROAD EVENT"),
                snapshot(GuidedCaptureState.ACCEPTED, 1, "VALID ROAD EVENT"));
        String retainedCsv = recorder.csvText("0.4.2-dev.7");
        require(retainedCsv.contains("\"VALID\",true,true,\"RETAINED_IN_CONTROLLED_MEASUREMENT_GROUPS\""),
                "retained valid outcome did not receive an explicit controlled-measurement disposition");

        recorder.record(outcome(GuidedOutcome.Decision.VALID_WITH_WARNING,
                11.0, 0.52, 2, "A", 2,
                "VALID ROAD EVENT WITH WARNING\nVSS unreliable"),
                snapshot(GuidedCaptureState.WARNING, 2,
                        "VALID ROAD EVENT WITH WARNING\nVSS unreliable"));
        require(recorder.recordCount() == 2,
                "typed valid outcomes were not recorded");

        recorder.discardLastAccepted();
        String csv = recorder.csvText("0.4.2-dev.7");
        require(csv.contains("\"VALID_WITH_WARNING\",false,true,\"DISCARDED_FROM_CONTROLLED_MEASUREMENT_GROUPS\""),
                "discard did not retain controlled measurement audit disposition");

        recorder.record(outcome(GuidedOutcome.Decision.EXCLUDED,
                12.0, Double.NaN, 1, "", 0,
                "EVENT EXCLUDED\nKeep the pedal steady longer."),
                snapshot(GuidedCaptureState.EXCLUDED, 1,
                        "EVENT EXCLUDED\nKeep the pedal steady longer."));
        require(recorder.recordCount() == 3,
                "typed excluded outcome was not retained");
        String excludedCsv = recorder.csvText("0.4.2-dev.7");
        require(excludedCsv.contains("\"EXCLUDED\",false,true,\"EXCLUDED_FROM_GUIDED_MEASUREMENT\""),
                "excluded outcome did not receive an explicit measurement audit disposition");
        require(!excludedCsv.contains("Compact attempt trace"),
                "event CSV must not embed compact trace tables in a cell");

        recorder.finish(snapshot(GuidedCaptureState.COMPLETE, 1,
                "Series repeatability: INCOMPLETE"));
        String report = recorder.reportText("0.4.2-dev.7",
                snapshot(GuidedCaptureState.COMPLETE, 1, "current"));
        require(report.contains("Recipe: Predictive MAP Blend Duration"),
                "report omitted the user-facing recipe identity");
        require(!report.contains("Recipe: Adaptive Predictive MAP Blend Duration"),
                "retired adaptive recipe wording remained in export");
        require(report.contains("Selected actual table RPM bin: 2600"),
                "report did not identify the actual capture RPM bin");
        require(report.contains("Last Guided outcome"),
                "report omitted last-outcome heading");
        require(report.contains("SESSION SUMMARY"),
                "report omitted summary-first hierarchy");
        require(report.indexOf("SESSION SUMMARY")
                        < report.indexOf("COMPLETED GUIDED OUTCOMES"),
                "session summary must precede detailed outcomes");
        require(report.contains("controlled-series inclusion: NO"),
                "report omitted non-included controlled outcome status");
        require(report.contains("recorded in audit ledger: YES"),
                "report omitted audit-ledger status");
        require(report.contains("Disposition: EXCLUDED_FROM_GUIDED_MEASUREMENT"),
                "readable report omitted explicit excluded disposition");
        require(!report.contains("Compact attempt trace"),
                "raw compact traces must not interrupt the readable report body");
        require(recorder.diagnosticText().contains("Compact attempt trace"),
                "diagnostic section omitted compact trace evidence");
        require(report.contains("Guided measurement/capture is read-only")
                        && report.contains("Explicit reviewed Apply/Restore may write only declared")
                        && report.contains("ECU burn remain prohibited"),
                "report did not distinguish read-only capture from explicit guarded Apply/Restore");

        String tuningReport = recorder.reportText("0.4.2-dev.7",
                snapshot(GuidedCaptureState.COMPLETE, 1, "current"),
                "CONTROLLED GUIDED MODEL REVIEW", "");
        require(tuningReport.indexOf("TUNING RESULT / COPY-PASTE DATA")
                        < tuningReport.indexOf("COMPLETED GUIDED OUTCOMES"),
                "model review must precede detailed Guided outcomes");
        require(tuningReport.contains("Unavailable — numerical proposal is currently withheld."),
                "withheld Blend Duration export did not explicitly suppress copy/paste data");
    }

    private static void spreadsheetExportsStayRectangular() {
        GuidedEvidenceRecorder recorder = new GuidedEvidenceRecorder();
        recorder.startAdaptive(2600.0, 22.0, 5, "Manual 2nd", 67890L);
        recorder.record(outcome(GuidedOutcome.Decision.VALID,
                20.0, 0.47, 1, "A", 1,
                "VALID ROAD EVENT\nBaseline and event detail"),
                snapshot(GuidedCaptureState.ACCEPTED, 1, "VALID ROAD EVENT"));

        String events = recorder.csvText("0.4.2-dev.7");
        String[] eventLines = events.split("\\n");
        require(eventLines.length == 2,
                "one Guided outcome must produce exactly one event CSV data row");
        int eventColumns = csvColumnCount(eventLines[0]);
        require(eventColumns == csvColumnCount(eventLines[1]),
                "Guided event CSV row does not match its header width");
        require(eventLines[0].contains("duration_s"),
                "event CSV omitted typed duration column");
        require(eventLines[1].contains("RETAINED_IN_CONTROLLED_MEASUREMENT_GROUPS"),
                "normal retained measurement disposition was blank in spreadsheet export");
        require(eventLines[1].contains("VALID ROAD EVENT | Baseline and event detail"),
                "multiline event detail was not flattened for spreadsheet use");

        String audio = "audio_sequence,audio_timestamp,audio_stage,audio_cue,audio_detail\n"
                + "1,\"2026-08-09T10:00:00+02:00\",\"WORKFLOW\",\"READY\",\"Ready, steady\"\n";
        String diagnostics = recorder.diagnosticsCsvText(audio);
        require(diagnostics.contains("TRACE_SAMPLE"),
                "diagnostic CSV omitted trace sample rows");
        require(diagnostics.contains("AUDIO"),
                "diagnostic CSV omitted audio/workflow rows");
        require(!diagnostics.contains("# Audio cue audit"),
                "diagnostic CSV must remain one rectangular schema");
        require(diagnostics.startsWith("record_type,event_sequence,trace_index,dt_s,rpm,tps,map,fallback_map,effective_map,gap,tpsdot,detector,prediction,pred_reset_cnt,pred_expired,gear,vss,key,value,audio_sequence,audio_timestamp,audio_stage,audio_cue,detail"),
                "diagnostic CSV does not expose the complete compact-trace channel schema");
        require(diagnostics.contains(",50.00,78.00,77.50,28.00,120.00,1,1,10,4,2,26.50,"),
                "Effective MAP, counters, gear or VSS were lost from the machine-readable trace row");
        String[] diagnosticLines = diagnostics.split("\\n");
        require(csvColumnCount(diagnosticLines[0]) == 24,
                "Guided diagnostic schema width changed unexpectedly");
        for (int i = 1; i < diagnosticLines.length; i++) {
            require(csvColumnCount(diagnosticLines[i]) == 24,
                    "Guided diagnostic row " + i + " is not a rectangular 24-column row");
        }
    }

    private static int csvColumnCount(String line) {
        int columns = 1;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (c == ',' && !quoted) columns++;
        }
        return columns;
    }

    private static GuidedOutcome outcome(GuidedOutcome.Decision decision,
                                         double seconds, double duration,
                                         int validCount, String group,
                                         int groupCount, String details) {
        String trace = "Compact attempt trace (" + decision.name() + ")\n"
                + "adaptive_baseline_s,0.75\n"
                + "dt_s,rpm,tps,map,fallbackMap,effectiveMap,gap,tpsdot,detector,prediction,predResetCnt,predExpired,gear,vss\n"
                + "0.000,2600,8.0,50.00,78.00,77.50,28.00,120.00,1,1,10,4,2,26.50\n"
                + "measurement_anchor_dt_s=0.100,gap_kpa=12.00\n"
                + "final_prediction_target_kpa=78.00\n"
                + "effective_map_model_check=Effective MAP firmware replay: 4 sample(s), 0 >±2.00 kPa, mean |error| 0.10 kPa, max |error| 0.20 kPa — CONSISTENT\n";
        return new GuidedOutcome(decision, seconds, duration, validCount,
                group, groupCount, details, trace);
    }

    private static GuidedSessionSnapshot snapshot(GuidedCaptureState state,
                                                   int accepted,
                                                   String result) {
        return new GuidedSessionSnapshot(state, state.name(), "instruction",
                "checks", result, accepted, "");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
