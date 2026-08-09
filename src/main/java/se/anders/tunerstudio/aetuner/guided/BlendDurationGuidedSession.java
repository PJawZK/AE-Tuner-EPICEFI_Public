package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Road-friendly Guided Capture engine.
 *
 * The driver performs one moderate throttle opening. The engine maintains a
 * rolling pre-opening baseline, detects the pedal's natural plateau, retains
 * every physically valid measurement, and assigns valid events to comparable
 * groups after capture. Proposal strictness therefore lives in grouping and
 * statistics rather than in an exact absolute pedal target.
 */
final class BlendDurationGuidedSession {
    private static final double OUTCOME_DISPLAY_SECONDS = 0.80;
    private static final double RECOVERY_SECONDS = 1.50;
    private static final double TARGET_CUE_MIN_SECONDS = 0.12;
    private static final double MIN_GAP = 4.0;
    private static final double MAJOR_PEDAL_MOVE = 8.0;


    private final RoadBaselineTracker roadBaseline = new RoadBaselineTracker();
    private final MapCatchupMeasurement mapCatchup = new MapCatchupMeasurement();
    private final PedalOpeningDetector openingDetector = new PedalOpeningDetector();
    private final GuidedAttemptEvidence attemptEvidence = new GuidedAttemptEvidence();
    private final List<BlendDurationAttempt> validAttempts =
            new ArrayList<BlendDurationAttempt>();
    private final BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();

    private BlendDurationCaptureConfig settings = new BlendDurationCaptureConfig(2000.0, 20.0, 5, 0, false);
    private GuidedVehicleTestLimits.Snapshot limits =
            GuidedVehicleTestLimits.defaults(false);
    private GuidedCaptureState state = GuidedCaptureState.IDLE;
    private String instruction = "Start a guided Blend Duration session.";
    private String latestResult = "No guided event completed yet.";
    private String checkText = "Start a guided session to evaluate road baseline checks.";
    private String lastAttemptTrace = "";
    private RoadBaselineTracker.Baseline rollingBaseline;
    private RoadBaselineTracker.Baseline baseline;
    private LiveSample holdAnchor;
    private boolean detectorSeen;
    private boolean plateauAcquired;
    private double peakTps;
    private long openingStarted;
    private long plateauAcquiredNano;
    private long lastOutcome;
    private int attempts;
    private int excluded;
    private int returnedToBaseline;
    private GuidedOutcome pendingOutcome;
    private GuidedWorkflowEvent.Listener workflowEvents = GuidedWorkflowEvent.NONE;

    synchronized void setWorkflowEventListener(GuidedWorkflowEvent.Listener listener) {
        workflowEvents = listener == null ? GuidedWorkflowEvent.NONE : listener;
    }

    synchronized void start(BlendDurationCaptureConfig next) {
        settings = next == null ? settings : next;
        limits = GuidedVehicleTestLimits.beginSession();
        roadBaseline.clear();
        validAttempts.clear();
        groups.rebuild(validAttempts);
        attempts = 0;
        excluded = 0;
        returnedToBaseline = 0;
        lastOutcome = 0L;
        pendingOutcome = null;
        lastAttemptTrace = "";
        clearReadyAndCapture();
        latestResult = "Session started. Drive smoothly near the selected RPM region."
                + "\nAdaptive capture: absolute TPS target is advisory only; "
                + "the actual pedal step and plateau are measured."
                + "\nVehicle-test timing limits: " + limits.summary();
        instruction = baselineInstruction();
        state = GuidedCaptureState.SETTLING;
        emit(GuidedWorkflowEvent.SESSION_STARTED, latestResult, System.nanoTime());
    }

    synchronized void reset() {
        if (state != GuidedCaptureState.IDLE) {
            emit(GuidedWorkflowEvent.SESSION_ENDED,
                    "Adaptive Guided session reset", System.nanoTime());
        }
        roadBaseline.clear();
        validAttempts.clear();
        groups.rebuild(validAttempts);
        attempts = 0;
        excluded = 0;
        returnedToBaseline = 0;
        lastOutcome = 0L;
        pendingOutcome = null;
        lastAttemptTrace = "";
        clearReadyAndCapture();
        GuidedVehicleTestLimits.endSession();
        limits = GuidedVehicleTestLimits.current();
        latestResult = "Guided session reset. Passive evidence was not changed.";
        instruction = "Start a guided Blend Duration session.";
        checkText = "Start a guided session to evaluate road baseline checks.";
        state = GuidedCaptureState.IDLE;
    }

    synchronized void togglePause() {
        if (state == GuidedCaptureState.IDLE
                || state == GuidedCaptureState.COMPLETE) return;
        if (state == GuidedCaptureState.PAUSED) {
            clearReadyAndCapture();
            state = GuidedCaptureState.SETTLING;
            instruction = baselineInstruction();
        } else {
            clearReadyAndCapture();
            state = GuidedCaptureState.PAUSED;
            instruction = "Guided capture paused. Passive logging remains active.";
            emit(GuidedWorkflowEvent.PAUSED, instruction, System.nanoTime());
        }
    }

    synchronized void finish() {
        if (state == GuidedCaptureState.IDLE
                || state == GuidedCaptureState.COMPLETE) return;
        clearReadyAndCapture();
        state = GuidedCaptureState.COMPLETE;
        instruction = "Review the adaptive groups. No ECU value was written.";
        GuidedVehicleTestLimits.endSession();
        emit(GuidedWorkflowEvent.SESSION_ENDED, instruction, System.nanoTime());
    }

    synchronized void terminateForClose() {
        if (state != GuidedCaptureState.IDLE
                && state != GuidedCaptureState.COMPLETE) {
            finish();
        }
    }

    synchronized void discardLast() {
        if (validAttempts.isEmpty()) return;
        BlendDurationAttempt removed =
                validAttempts.remove(validAttempts.size() - 1);
        groups.rebuild(validAttempts);
        latestResult = "Removed valid guided attempt " + removed.number
                + ". The passive/raw event remains available."
                + "\nAdaptive groups were rebuilt from the retained valid events.";
        if (state == GuidedCaptureState.COMPLETE) {
            state = GuidedCaptureState.SETTLING;
            GuidedVehicleTestLimits.beginSession();
        }
    }

    synchronized void accept(LiveSample sample) {
        if (sample == null) return;
        roadBaseline.add(sample);
        if (state == GuidedCaptureState.IDLE
                || state == GuidedCaptureState.PAUSED
                || state == GuidedCaptureState.COMPLETE) return;

        if (isOutcomeState(state)) {
            if (seconds(lastOutcome, sample.getNanoTime())
                    < OUTCOME_DISPLAY_SECONDS) return;
            clearReadyAndCapture();
            state = GuidedCaptureState.RECOVERING;
            instruction = "Return to normal light throttle; the next rolling baseline will establish automatically.";
        }

        if (state == GuidedCaptureState.RECOVERING) {
            RoadBaselineTracker.AcquireCheck check = roadBaseline.acquireCheck(
                    sample, settings.startRpm, lastOutcome, RECOVERY_SECONDS);
            checkText = check.text;
            if (!check.recovered) return;
            state = GuidedCaptureState.SETTLING;
            instruction = baselineInstruction();
        }

        if (state == GuidedCaptureState.SETTLING) {
            RoadBaselineTracker.AcquireCheck check = roadBaseline.acquireCheck(
                    sample, settings.startRpm, lastOutcome, RECOVERY_SECONDS);
            checkText = check.text;
            if (check.ready) {
                rollingBaseline = roadBaseline.baseline(false);
                state = GuidedCaptureState.READY;
                instruction = readyInstruction();
                emit(GuidedWorkflowEvent.READY_ENTERED,
                        instruction, sample.getNanoTime());
            }
            return;
        }

        if (state == GuidedCaptureState.READY) {
            if (triggered(sample) || openingDetector.localTipInStarted(
                    sample, rollingBaseline == null ? Double.NaN : rollingBaseline.tps,
                    limits.localTpsOnsetRise)) {
                beginConfirmed(sample);
                return;
            }
            if (openingDetector.movementStarted(
                    sample, rollingBaseline == null ? Double.NaN : rollingBaseline.tps)) {
                beginOpeningPending(sample);
                return;
            }
            RoadBaselineTracker.ReadyCheck ready =
                    roadBaseline.readyCheck(sample, settings.startRpm);
            checkText = ready.text;
            if (!ready.ready) {
                rollingBaseline = null;
                state = GuidedCaptureState.SETTLING;
                instruction = ready.instruction;
            } else {
                RoadBaselineTracker.Baseline next = roadBaseline.baseline(true);
                if (next.valid()) rollingBaseline = next;
            }
            return;
        }

        if (state == GuidedCaptureState.OPENING_PENDING) {
            monitorOpeningPending(sample);
            return;
        }

        if (state == GuidedCaptureState.CAPTURING) {
            capture(sample);
        }
    }

    synchronized GuidedOutcome drainOutcome() {
        GuidedOutcome outcome = pendingOutcome;
        pendingOutcome = null;
        return outcome;
    }

    synchronized double baselineTpsForDisplay() {
        if (state == GuidedCaptureState.OPENING_PENDING) {
            return rollingBaseline == null ? Double.NaN : rollingBaseline.tps;
        }
        if (state == GuidedCaptureState.CAPTURING) {
            return baseline == null ? Double.NaN : baseline.tps;
        }
        return Double.NaN;
    }

    synchronized int bestGroupCount() {
        return groups.bestGroupCount();
    }

    synchronized String bestGroupId() {
        return groups.bestGroupId();
    }

    synchronized int validCount() {
        return validAttempts.size();
    }

    private String baselineInstruction() {
        return "Drive smoothly near " + f0(settings.startRpm)
                + " RPM. Small hill/road corrections are allowed; READY uses a rolling trend-aware baseline.";
    }

    private String readyInstruction() {
        return "Make one moderate throttle opening when safe, roughly +"
                + f1(settings.desiredTpsStep)
                + " TPS points. Let your foot settle naturally and hold approximately steady until the cue.";
    }

    private void beginConfirmed(LiveSample sample) {
        List<LiveSample> early = openingDetector.consumePendingSamples();
        attempts++;
        baseline = rollingBaseline != null
                ? rollingBaseline
                : roadBaseline.baseline(true);
        rollingBaseline = null;
        clearAttemptOnly();
        lastAttemptTrace = "";
        detectorSeen = triggered(sample) || sample.bool(ChannelRole.MAP_PRED_ACTIVE);
        openingStarted = sample.getNanoTime();
        peakTps = sample.get(ChannelRole.TPS);
        for (LiveSample earlySample : early) {
            attemptEvidence.add(earlySample);
            mapCatchup.observePredictionGap(earlySample);
        }
        if (early.isEmpty()
                || early.get(early.size() - 1).getNanoTime()
                != sample.getNanoTime()) {
            attemptEvidence.add(sample);
            mapCatchup.observePredictionGap(sample);
        }
        state = GuidedCaptureState.CAPTURING;
        instruction = detectorSeen
                ? "Opening detected. Let the pedal settle naturally; exact TPS is not required."
                : "Opening detected locally; waiting briefly for ECU detector/prediction evidence while the pedal settles.";
    }

    private void beginOpeningPending(LiveSample sample) {
        openingDetector.beginPending(sample);
        state = GuidedCaptureState.OPENING_PENDING;
        instruction = "Opening pending: continue the same smooth pedal movement. The immediately preceding rolling baseline is frozen now.";
        emit(GuidedWorkflowEvent.OPENING_PENDING,
                instruction, sample.getNanoTime());
    }

    private void monitorOpeningPending(LiveSample sample) {
        PedalOpeningDetector.Decision decision = openingDetector.observePending(
                sample, rollingBaseline, settings.startRpm, limits);
        switch (decision.type) {
            case CONFIRM:
                beginConfirmed(sample);
                return;
            case ABORT_TO_READY:
                openingDetector.clearPending();
                state = GuidedCaptureState.READY;
                instruction = readyInstruction();
                return;
            case RETURN_TO_BASELINE:
                returnToBaseline(sample, decision.reason, decision.correction);
                return;
            default:
                return;
        }
    }

    private void capture(LiveSample sample) {
        attemptEvidence.add(sample);
        mapCatchup.observePredictionGap(sample);
        if (!plateauAcquired) {
            acquirePlateau(sample);
        } else {
            monitorCatchup(sample);
        }
    }

    private void acquirePlateau(LiveSample sample) {
        double elapsed = seconds(openingStarted, sample.getNanoTime());
        if (triggered(sample) || sample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
            detectorSeen = true;
        }
        if (!detectorSeen && elapsed > limits.detectorConfirmSeconds) {
            exclude(sample,
                    "The opening was visible in TPS but ECU detector/prediction evidence did not arrive within "
                            + f2(limits.detectorConfirmSeconds) + " seconds.",
                    "Repeat one clear acceleration opening after READY.");
            return;
        }

        double tps = sample.get(ChannelRole.TPS);
        if (!Double.isFinite(tps) || baseline == null || !baseline.valid()) {
            exclude(sample, "TPS or the frozen road baseline became unavailable.",
                    "Wait for a valid rolling baseline before repeating.");
            return;
        }
        if (!Double.isFinite(peakTps) || tps > peakTps) peakTps = tps;
        if (peakTps - tps > MAJOR_PEDAL_MOVE) {
            exclude(sample,
                    "Throttle backed off " + f1(peakTps - tps)
                            + " points before a usable pedal plateau was established.",
                    "A small road-induced movement is allowed; avoid a distinct backoff until the capture cue.");
            return;
        }

        PedalPlateauDetector.Result plateau = PedalPlateauDetector.evaluate(
                attemptEvidence.samples(), baseline.tps, sample.getNanoTime());
        if (plateau.usable) {
            holdAnchor = plateau.anchor;
            plateauAcquired = true;
            plateauAcquiredNano = sample.getNanoTime();
            mapCatchup.beginCatchup(attemptEvidence.samples(), limits.mapCatchupSeconds);
            if (mapCatchup.measurementAnchor() == null
                    || !Double.isFinite(mapCatchup.bestGap())) {
                exclude(sample, "No valid measured-MAP to fallbackMap gap was captured.",
                        "Repeat one moderate opening after READY.");
                return;
            }
            if (mapCatchup.bestGap() < MIN_GAP) {
                exclude(sample, "Initial MAP gap was only "
                                + f2(mapCatchup.bestGap()) + " kPa.",
                        "Use a clearer acceleration opening after READY.");
                return;
            }
            instruction = "Pedal hold acquired around " + f1(plateau.medianTps)
                    + "% (step +" + f1(plateau.step)
                    + "). Small movement is allowed; avoid a clear backoff or second opening.";
            emit(GuidedWorkflowEvent.TARGET_ACQUIRED,
                    instruction, sample.getNanoTime());
            return;
        }

        if (elapsed > limits.targetAcquisitionSeconds) {
            String reason = plateau.step < PedalPlateauDetector.MIN_USABLE_STEP
                    ? "The opening settled at only +" + f1(plateau.step)
                            + " TPS points; at least +" + f1(PedalPlateauDetector.MIN_USABLE_STEP)
                            + " is needed for this recipe."
                    : plateau.step > PedalPlateauDetector.MAX_USABLE_STEP
                    ? "The opening settled at +" + f1(plateau.step)
                            + " TPS points; this exceeds the +" + f1(PedalPlateauDetector.MAX_USABLE_STEP)
                            + " road-capture ceiling."
                    : "The pedal did not form a usable natural plateau within "
                            + f2(limits.targetAcquisitionSeconds)
                            + " seconds (recent range " + f1(plateau.range)
                            + " points).";
            exclude(sample, reason,
                    "Use one moderate opening and let the pedal settle naturally; exact absolute TPS is not required.");
            return;
        }
        instruction = "Opening detected. Let the pedal settle naturally; current step +"
                + f1(tps - baseline.tps) + " TPS points.";
    }

    private void monitorCatchup(LiveSample sample) {
        double tps = sample.get(ChannelRole.TPS);
        double held = holdAnchor == null
                ? Double.NaN : holdAnchor.get(ChannelRole.TPS);
        if (Double.isFinite(tps) && Double.isFinite(held)) {
            if (tps < held - MAJOR_PEDAL_MOVE) {
                exclude(sample,
                        "Throttle backed off " + f1(held - tps)
                                + " points before MAP catch-up completed.",
                        "Small pedal motion is allowed; avoid a distinct release until the completion cue.");
                return;
            }
            if (tps > held + MAJOR_PEDAL_MOVE) {
                exclude(sample,
                        "A second throttle opening of " + f1(tps - held)
                                + " points occurred before MAP catch-up completed.",
                        "Use one opening per Guided event.");
                return;
            }
        }

        mapCatchup.observeCatchup(sample);
        if (mapCatchup.timedOut(sample, limits.mapCatchupSeconds)) {
            exclude(sample,
                    "MAP catch-up exceeded " + f2(limits.mapCatchupSeconds)
                            + " seconds.",
                    "Return to normal throttle and repeat another moderate opening later.");
            return;
        }
        LiveSample caught = mapCatchup.catchSample();
        if (caught != null
                && seconds(plateauAcquiredNano, sample.getNanoTime())
                >= TARGET_CUE_MIN_SECONDS) {
            complete(caught, mapCatchup.catchupDurationSeconds());
        }
    }

    private void complete(LiveSample completedAt, double duration) {
        BlendDurationAttempt candidate = attemptEvidence.buildAttempt(
                attempts, baseline, mapCatchup.measurementAnchor(),
                holdAnchor, completedAt, duration, settings);

        BlendDurationComparabilityGroups.Assignment assignment = groups.assign(candidate);
        validAttempts.add(candidate);
        boolean warning = candidate.gearOscillation || candidate.vssBad
                || assignment.nearBoundary;
        lastAttemptTrace = compactTrace(
                warning ? "VALID_WITH_WARNING" : "VALID", completedAt);
        lastOutcome = completedAt.getNanoTime();
        latestResult = (warning ? "VALID ROAD EVENT WITH WARNING" : "VALID ROAD EVENT")
                + "\nDuration: " + f3(duration) + " s"
                + "\nBaseline: " + f0(candidate.baseRpm) + " RPM / "
                + f2(candidate.baseMap) + " kPa / "
                + f1(candidate.baseTps) + "% TPS"
                + "\nNatural held TPS: " + f1(candidate.heldTps) + "%"
                + " | TPS step: +" + f1(candidate.tpsStep) + " points"
                + " | peak initial gap: " + f2(candidate.gap) + " kPa"
                + " | RPM trend: " + candidate.trend
                + "\nGroup: " + assignment.groupId + " | "
                + assignment.description
                + "\nAll valid groups are retained; only one comparable group is combined for a proposal.";
        if (candidate.gearOscillation || candidate.vssBad) {
            latestResult += "\nAdvisory: VSS/gear evidence is unreliable.";
        }
        state = warning ? GuidedCaptureState.WARNING
                : GuidedCaptureState.ACCEPTED;
        instruction = "Valid event captured. Return to normal light throttle; another road baseline will acquire automatically.";
        pendingOutcome = new GuidedOutcome(
                warning ? GuidedOutcome.Decision.VALID_WITH_WARNING
                        : GuidedOutcome.Decision.VALID,
                completedAt.getSeconds(), duration, validAttempts.size(),
                assignment.groupId, assignment.groupCount,
                latestResult, lastAttemptTrace);
        emit(GuidedWorkflowEvent.EVENT_ACCEPTED,
                instruction, completedAt.getNanoTime());

        if (groups.bestGroupCount() >= settings.targetCount) {
            state = GuidedCaptureState.COMPLETE;
            instruction = "SERIES COMPLETE — adaptive group "
                    + groups.bestGroupId() + " reached "
                    + groups.bestGroupCount() + " comparable events. Review statistical consistency before using any proposal.";
            GuidedVehicleTestLimits.endSession();
            emit(GuidedWorkflowEvent.SERIES_COMPLETE,
                    instruction, completedAt.getNanoTime());
        }
    }

    private void exclude(LiveSample sample, String reason, String correction) {
        if (lastAttemptTrace.length() == 0) {
            lastAttemptTrace = compactTrace("EXCLUDED", sample);
        }
        excluded++;
        clearAttemptStateAfterOutcome();
        state = GuidedCaptureState.EXCLUDED;
        lastOutcome = sample == null ? System.nanoTime() : sample.getNanoTime();
        latestResult = "EVENT EXCLUDED\n" + reason
                + "\nRequired correction: " + correction;
        instruction = "Return to normal light throttle. The rolling baseline will reacquire automatically.";
        pendingOutcome = new GuidedOutcome(GuidedOutcome.Decision.EXCLUDED,
                sample == null ? Double.NaN : sample.getSeconds(),
                Double.NaN, validAttempts.size(), "", 0,
                latestResult, lastAttemptTrace);
        emit(GuidedWorkflowEvent.EVENT_EXCLUDED, reason, lastOutcome);
    }

    private void returnToBaseline(LiveSample sample,
                                  String reason, String correction) {
        lastAttemptTrace = "";
        attemptEvidence.replaceSamplesForTrace(
                openingDetector.pendingSamples(), sample);
        if (attemptEvidence.sampleCount() > 0) {
            lastAttemptTrace = compactTrace("RETURN_TO_BASELINE", sample);
        }
        returnedToBaseline++;
        clearReadyAndCapture();
        state = GuidedCaptureState.RETURNING;
        lastOutcome = sample == null ? System.nanoTime() : sample.getNanoTime();
        latestResult = "RETURN TO NORMAL THROTTLE\n" + reason
                + "\nRequired correction: " + correction;
        instruction = "Resume smooth driving; READY will return when the rolling baseline is suitable.";
        pendingOutcome = new GuidedOutcome(
                GuidedOutcome.Decision.RETURN_TO_BASELINE,
                sample == null ? Double.NaN : sample.getSeconds(),
                Double.NaN, validAttempts.size(), "", 0,
                latestResult, lastAttemptTrace);
        emit(GuidedWorkflowEvent.RETURN_TO_BASELINE, reason, lastOutcome);
    }

    private String compactTrace(String disposition, LiveSample outcome) {
        return GuidedAttemptTrace.build(disposition, attemptEvidence.samples(), settings, limits,
                RoadBaselineTracker.BASELINE_SECONDS, PedalPlateauDetector.WINDOW_SECONDS, PedalPlateauDetector.RANGE_LIMIT,
                MAJOR_PEDAL_MOVE, mapCatchup.measurementAnchor(),
                mapCatchup.bestGap(), holdAnchor, outcome);
    }

    private void clearAttemptOnly() {
        mapCatchup.reset();
        attemptEvidence.reset();
        openingDetector.reset();
        holdAnchor = null;
        detectorSeen = false;
        plateauAcquired = false;
        peakTps = Double.NaN;
        openingStarted = 0L;
        plateauAcquiredNano = 0L;
    }

    private void clearAttemptStateAfterOutcome() {
        mapCatchup.reset();
        openingDetector.reset();
        holdAnchor = null;
        detectorSeen = false;
        plateauAcquired = false;
    }

    private void clearReadyAndCapture() {
        rollingBaseline = null;
        baseline = null;
        clearAttemptOnly();
    }

    synchronized GuidedSessionSnapshot snapshot() {
        return BlendDurationGuidedSummary.snapshot(
                state, plateauAcquired, instruction, checkText, latestResult,
                settings, validAttempts.size(), excluded, returnedToBaseline,
                attempts, groups, lastAttemptTrace);
    }

    private static boolean isOutcomeState(GuidedCaptureState state) {
        return state == GuidedCaptureState.ACCEPTED
                || state == GuidedCaptureState.WARNING
                || state == GuidedCaptureState.EXCLUDED
                || state == GuidedCaptureState.RETURNING;
    }

    private void emit(GuidedWorkflowEvent event, String detail, long nanoTime) {
        try {
            workflowEvents.onGuidedWorkflowEvent(event, detail, nanoTime);
        } catch (RuntimeException ignored) {
            // Advisory listeners must never affect deterministic capture.
        }
    }

    private static boolean triggered(LiveSample sample) {
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) return true;
        double change = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double limit = sample.get(ChannelRole.ACCEL_THRESHOLD);
        return Double.isFinite(change) && Double.isFinite(limit)
                && limit > 0.0 && change > limit;
    }

    private static boolean requiredFinite(LiveSample sample) {
        return finite(sample, ChannelRole.RPM)
                && finite(sample, ChannelRole.TPS)
                && finite(sample, ChannelRole.MAP)
                && finite(sample, ChannelRole.FALLBACK_MAP);
    }

    private static boolean safe(LiveSample sample) {
        boolean running = Double.isFinite(sample.get(ChannelRole.ENGINE_RUNNING))
                ? sample.bool(ChannelRole.ENGINE_RUNNING)
                : sample.get(ChannelRole.RPM) >= 400.0;
        return running
                && !sample.bool(ChannelRole.ENGINE_CRANKING)
                && !sample.bool(ChannelRole.FUEL_CUT)
                && !sample.bool(ChannelRole.TOTAL_SPARK_CUT)
                && !sample.bool(ChannelRole.TRIGGER_ERROR);
    }

    private static boolean finite(LiveSample sample, ChannelRole role) {
        return Double.isFinite(sample.get(role));
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }

    private static String f0(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.0f", value) : "n/a";
    }

    private static String f1(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.1f", value) : "n/a";
    }

    private static String f2(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.2f", value) : "n/a";
    }

    private static String f3(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.3f", value) : "n/a";
    }
}
