package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class PhaseA3LegacyInvariantMigrationTest {
    public static void main(String[] args) {
        pauseRequiresBaselineReacquisition();
        pendingOpeningConfirmsAndSmallAbortReturnsReady();
        unconfirmedOpeningIsAuditedAndCued();
        acquisitionTimerStartsAtConfirmation();
        vssDropoutRemainsAdvisory();
        discardPreservesPassiveEvidenceStatement();
        System.out.println("PhaseA3LegacyInvariantMigrationTest passed");
    }

    private static void pauseRequiresBaselineReacquisition() {
        BlendDurationGuidedSession session = session();
        double t = settle(session, 0.0);
        require(session.snapshot().state == GuidedCaptureState.READY,
                "adaptive pause test did not reach READY");
        session.togglePause();
        session.accept(sample(t + 0.05, 2000, 60, 14, 90,
                true, true, 2, 40));
        require(session.snapshot().state == GuidedCaptureState.PAUSED,
                "paused adaptive session processed an opening");
        require(session.validCount() == 0,
                "paused adaptive session changed valid evidence");
        session.togglePause();
        require(session.snapshot().state == GuidedCaptureState.SETTLING,
                "resume did not require baseline reacquisition");
        session.reset();
    }

    private static void pendingOpeningConfirmsAndSmallAbortReturnsReady() {
        BlendDurationGuidedSession session = session();
        double t = settle(session, 0.0);
        session.accept(sample(t + 0.05, 2000, 50, 9.2, 50,
                false, false, 2, 40));
        require(session.snapshot().state == GuidedCaptureState.OPENING_PENDING,
                "small road opening did not enter OPENING_PENDING");
        double frozen = session.baselineTpsForDisplay();
        session.accept(sample(t + 0.10, 2000, 50.5, 9.5, 50.5,
                false, false, 2, 40));
        require(session.snapshot().state == GuidedCaptureState.OPENING_PENDING,
                "sub-threshold movement confirmed too early");
        require(Math.abs(session.baselineTpsForDisplay() - frozen) < 0.0001,
                "pending baseline moved after freeze");
        session.accept(sample(t + 0.15, 2010, 60, 14.0, 90,
                true, true, 2, 40));
        require(session.snapshot().state == GuidedCaptureState.CAPTURING,
                "delayed ECU detector did not confirm pending opening");
        session.reset();

        session = session();
        t = settle(session, 10.0);
        session.accept(sample(t + 0.05, 2000, 50, 9.2, 50,
                false, false, 2, 40));
        session.accept(sample(t + 0.15, 2000, 50, 8.3, 50,
                false, false, 2, 40));
        require(session.snapshot().state == GuidedCaptureState.READY,
                "small aborted movement did not silently restore READY");
        require(session.snapshot().result.contains("attempts: 0"),
                "small aborted movement entered the attempt ledger");
        session.reset();
    }

    private static void unconfirmedOpeningIsAuditedAndCued() {
        BlendDurationGuidedSession session = session();
        final List<GuidedWorkflowEvent> events = new ArrayList<GuidedWorkflowEvent>();
        session.setWorkflowEventListener(new GuidedWorkflowEvent.Listener() {
            @Override
            public void onGuidedWorkflowEvent(GuidedWorkflowEvent event,
                                               String detail, long nanoTime) {
                events.add(event);
            }
        });
        double t = settle(session, 20.0);
        session.accept(sample(t + 0.05, 2000, 50, 9.2, 50,
                false, false, 2, 40));
        session.accept(sample(t + 0.70, 2000, 50.5, 9.4, 50.5,
                false, false, 2, 40));
        require(session.snapshot().state == GuidedCaptureState.RETURNING,
                "unconfirmed partial opening was not made explicit");
        require(events.contains(GuidedWorkflowEvent.OPENING_PENDING),
                "OPENING_PENDING event was not emitted");
        require(events.contains(GuidedWorkflowEvent.RETURN_TO_BASELINE),
                "RETURN_TO_BASELINE event was not emitted");
        GuidedOutcome outcome = session.drainOutcome();
        require(outcome != null
                        && outcome.decision == GuidedOutcome.Decision.RETURN_TO_BASELINE,
                "unconfirmed opening did not produce typed return outcome");
        require(outcome.trace.contains("RETURN_TO_BASELINE"),
                "partial-opening compact trace was not retained");
        session.reset();
    }

    private static void acquisitionTimerStartsAtConfirmation() {
        BlendDurationGuidedSession session = session();
        double t = settle(session, 30.0);
        session.accept(sample(t + 0.05, 2000, 50, 9.2, 50,
                false, false, 2, 40));
        session.accept(sample(t + 0.45, 2000, 51, 9.5, 51,
                false, false, 2, 40));
        session.accept(sample(t + 0.50, 2010, 60, 14.0, 90,
                true, true, 2, 40));
        session.accept(sample(t + 0.70, 2020, 70, 22.0, 90,
                false, false, 2, 40));
        session.accept(sample(t + 0.90, 2040, 80, 30.0, 90,
                false, false, 2, 40));
        session.accept(sample(t + 1.00, 2050, 83, 30.5, 90,
                false, false, 2, 40));
        session.accept(sample(t + 1.10, 2060, 85, 30.2, 90,
                false, false, 2, 40));
        session.accept(sample(t + 1.20, 2070, 88, 30.2, 90,
                false, false, 2, 40));
        session.accept(sample(t + 1.25, 2080, 89, 30.1, 90,
                false, false, 2, 40));
        GuidedOutcome outcome = session.drainOutcome();
        require(outcome != null && outcome.isValid(),
                "pending time incorrectly consumed target-acquisition allowance");
        require(outcome.durationSeconds > 0.65 && outcome.durationSeconds < 0.75,
                "duration did not remain anchored to confirmed current-event prediction");
        require(outcome.trace.contains("measurement_anchor_dt_s="),
                "valid adaptive event lost measurement-anchor trace evidence");
        require(outcome.trace.length() < 12000,
                "compact attempt trace exceeded historical bounded-export limit");
        session.reset();
    }

    private static void vssDropoutRemainsAdvisory() {
        BlendDurationGuidedSession session = session();
        double t = settle(session, 40.0);
        GuidedOutcome outcome = completeOpening(session, t, 0.0);
        require(outcome != null && outcome.isValid(),
                "VSS dropout rejected otherwise valid MAP evidence");
        require(outcome.decision == GuidedOutcome.Decision.VALID_WITH_WARNING,
                "VSS dropout was not retained as advisory warning");
        require(outcome.details.contains("VSS/gear evidence is unreliable"),
                "VSS advisory warning was not visible");
        session.reset();
    }

    private static void discardPreservesPassiveEvidenceStatement() {
        BlendDurationGuidedSession session = session();
        double t = settle(session, 50.0);
        GuidedOutcome outcome = completeOpening(session, t, 40.0);
        require(outcome != null && outcome.isValid() && session.validCount() == 1,
                "discard reference event was not captured");
        session.discardLast();
        require(session.validCount() == 0,
                "discard did not remove the latest guided evidence");
        require(session.snapshot().result.contains("passive/raw event remains available"),
                "discard no longer states that passive/raw evidence is preserved");
        session.reset();
    }

    private static BlendDurationGuidedSession session() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        BlendDurationGuidedSession session = new BlendDurationGuidedSession();
        session.start(new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false));
        return session;
    }

    private static double settle(BlendDurationGuidedSession session, double start) {
        double t = start;
        for (int i = 0; i < 24; i++) {
            session.accept(sample(t, 2000, 50, 8.0, 50,
                    false, false, 2, 40));
            t += 0.05;
        }
        require(session.snapshot().state == GuidedCaptureState.READY,
                "adaptive invariant fixture did not reach READY");
        return t;
    }

    private static GuidedOutcome completeOpening(BlendDurationGuidedSession session,
                                                  double t, double vss) {
        session.accept(sample(t + 0.05, 2000, 60, 14.0, 90,
                true, true, 2, vss));
        session.accept(sample(t + 0.20, 2020, 70, 25.0, 90,
                false, false, 2, vss));
        session.accept(sample(t + 0.30, 2040, 80, 30.0, 90,
                false, false, 2, vss));
        session.accept(sample(t + 0.40, 2050, 84, 30.2, 90,
                false, false, 2, vss));
        session.accept(sample(t + 0.50, 2060, 86, 30.1, 90,
                false, false, 2, vss));
        session.accept(sample(t + 0.60, 2070, 88, 30.2, 90,
                false, false, 2, vss));
        session.accept(sample(t + 0.65, 2080, 89, 30.1, 90,
                false, false, 2, vss));
        return session.drainOutcome();
    }

    private static LiveSample sample(double seconds, double rpm, double map,
                                     double tps, double fallback,
                                     boolean detector, boolean prediction,
                                     double gear, double vss) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, detector ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        values.put(ChannelRole.GEAR, gear);
        values.put(ChannelRole.VSS, vss);
        return new LiveSample(Math.round(seconds * 1000000000.0),
                seconds, values, detector ? 60.0 : 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
