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
        System.out.println("GuidedEvidenceRecorderRegressionTest passed");
    }

    private static void typedOutcomesAndDiscardRemainAuditable() {
        GuidedEvidenceRecorder recorder = new GuidedEvidenceRecorder();
        recorder.startAdaptive(2000.0, 22.0, 5, "Manual 2nd", 12345L);

        recorder.record(outcome(GuidedOutcome.Decision.VALID,
                10.0, 0.50, 1, "A", 1, "VALID ROAD EVENT"),
                snapshot(GuidedCaptureState.ACCEPTED, 1, "VALID ROAD EVENT"));
        recorder.record(outcome(GuidedOutcome.Decision.VALID_WITH_WARNING,
                11.0, 0.52, 2, "A", 2,
                "VALID ROAD EVENT WITH WARNING\nVSS unreliable"),
                snapshot(GuidedCaptureState.WARNING, 2,
                        "VALID ROAD EVENT WITH WARNING\nVSS unreliable"));
        require(recorder.recordCount() == 2,
                "typed valid outcomes were not recorded");

        recorder.discardLastAccepted();
        String csv = recorder.csvText("0.4.0-dev.1");
        require(csv.contains("\"VALID_WITH_WARNING\",false,true,\"DISCARDED_FROM_ADAPTIVE_PROPOSAL_GROUPS\""),
                "discard did not retain adaptive audit disposition");

        recorder.record(outcome(GuidedOutcome.Decision.EXCLUDED,
                12.0, Double.NaN, 1, "", 0,
                "EVENT EXCLUDED\nKeep the pedal steady longer."),
                snapshot(GuidedCaptureState.EXCLUDED, 1,
                        "EVENT EXCLUDED\nKeep the pedal steady longer."));
        require(recorder.recordCount() == 3,
                "typed excluded outcome was not retained");
        String excludedCsv = recorder.csvText("0.4.0-dev.1");
        require(excludedCsv.contains("\"EXCLUDED\",false,true"),
                "excluded outcome must remain audit-only");
        require(excludedCsv.contains("Compact attempt trace (EXCLUDED)"),
                "typed exclusion trace was not retained");

        recorder.finish(snapshot(GuidedCaptureState.COMPLETE, 1,
                "Series quality: INCOMPLETE"));
        String report = recorder.reportText("0.4.0-dev.1",
                snapshot(GuidedCaptureState.COMPLETE, 1, "current"));
        require(report.contains("Recipe: Adaptive Predictive MAP Blend Duration"),
                "report omitted authoritative adaptive recipe identity");
        require(report.contains("proposal inclusion: NO"),
                "report omitted non-included adaptive outcome status");
        require(report.contains("recorded in audit ledger: YES"),
                "report omitted audit-ledger status");
        require(report.contains("Per-attempt compact trace"),
                "report omitted compact trace evidence");
        require(report.contains("no ECU RAM write, burn, or automatic paste occurred"),
                "report omitted read-only statement");
    }

    private static GuidedOutcome outcome(GuidedOutcome.Decision decision,
                                         double seconds, double duration,
                                         int validCount, String group,
                                         int groupCount, String details) {
        String trace = "Compact attempt trace (" + decision.name() + ")\n"
                + "dt_s,rpm,tps,map,fallbackMap,gap,detector,prediction\n"
                + "0.000,2000,8.0,50.0,50.0,0.0,0,0\n";
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
