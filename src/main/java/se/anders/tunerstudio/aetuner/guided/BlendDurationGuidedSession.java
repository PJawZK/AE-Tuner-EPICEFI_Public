package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
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
 * The driver performs one controlled throttle opening. The engine maintains a
 * rolling pre-opening baseline, freezes it when the opening begins, requires a
 * stable plateau inside broad natural-pedal usability bounds, and times the
 * central 20->80% portion of the event's own physical MAP rise. Predictive MAP
 * target/replay data is retained as diagnostics only and cannot define whether
 * a physical timing measurement exists. The configured TPS step is coaching
 * only; valid events are assigned to comparable groups after capture.
 */
final class BlendDurationGuidedSession {
    private static final double OUTCOME_DISPLAY_SECONDS = 0.80;
    private static final double RECOVERY_SECONDS = 1.50;
    private static final double TARGET_CUE_MIN_SECONDS = 0.12;
    private static final double MAJOR_PEDAL_MOVE = 8.0;
    static final double ENTRY_RPM_TOLERANCE = 300.0;

    private final RoadBaselineTracker roadBaseline = new RoadBaselineTracker();
    /** Prediction/fallback/Effective-MAP diagnostics only. */
    private final MapCatchupMeasurement mapCatchup = new MapCatchupMeasurement();
    /** Primary physical Blend timing authority. */
    private final PhysicalMapResponseMeasurement physicalResponse =
            new PhysicalMapResponseMeasurement();
    private final PedalOpeningDetector openingDetector = new PedalOpeningDetector();
    private final GuidedAttemptEvidence attemptEvidence = new GuidedAttemptEvidence();
    private final List<BlendDurationAttempt> validAttempts =
            new ArrayList<BlendDurationAttempt>();
    private final BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
    private final BlendDurationRpmBinLatch rpmBinLatch = new BlendDurationRpmBinLatch();

    private BlendDurationCaptureConfig settings = new BlendDurationCaptureConfig(2000.0, 20.0, 5, 0, false);
    private BlendDurationCaptureConfig eventSettings;
    private GuidedVehicleTestLimits.Snapshot limits =
            GuidedVehicleTestLimits.defaults(false);
    private GuidedCaptureState state = GuidedCaptureState.IDLE;
    private String instruction = "Start a guided Blend Duration session.";
    private String latestResult = "No guided event completed yet.";
    private String checkText = "Start a guided session to evaluate road baseline checks.";
    private String lastAttemptTrace = "";
    private String lastSessionGearEvidence = "";
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

    // Presentation-only bounded traces. These never participate in capture
    // validity, timing, proposal generation or controller writes.
    private BlendDurationFocusTrace lastOutcomeFocusTrace = BlendDurationFocusTrace.empty();
    private BlendDurationFocusTrace lastAcceptedFocusTrace = BlendDurationFocusTrace.empty();
    private BlendDurationFocusTrace previousAcceptedFocusTrace = BlendDurationFocusTrace.empty();
    private BlendDurationFocusTrace cachedLiveFocusTrace = BlendDurationFocusTrace.empty();
    private long cachedLiveFocusTraceNano;

    private GuidedWorkflowEvent.Listener workflowEvents = GuidedWorkflowEvent.NONE;

    synchronized void setWorkflowEventListener(GuidedWorkflowEvent.Listener listener) {
        workflowEvents = listener == null ? GuidedWorkflowEvent.NONE : listener;
    }

    synchronized void start(BlendDurationCaptureConfig next) {
        settings = next == null ? settings : next;
        mapCatchup.configure(settings);
        rpmBinLatch.configure(settings.armedRpmBins());
        eventSettings = null;
        limits = GuidedVehicleTestLimits.beginSession();
        roadBaseline.clear();
        rpmBinLatch.release();
        eventSettings = null;
        validAttempts.clear();
        groups.rebuild(validAttempts);
        attempts = 0;
        excluded = 0;
        returnedToBaseline = 0;
        lastOutcome = 0L;
        pendingOutcome = null;
        lastAttemptTrace = "";
        lastSessionGearEvidence = "";
        clearFocusTraceHistory();
        clearReadyAndCapture();
        latestResult = "Session started. Armed actual Blend Duration RPM bins: "
                + settings.armedRpmText() + " RPM."
                + "\nAutomatic RPM-bin latch: enter one armed bin ±"
                + f0(ENTRY_RPM_TOLERANCE) + " RPM and remain there for ~"
                + f2(BlendDurationRpmBinLatch.LATCH_DWELL_SECONDS)
                + " s while the road baseline is smooth. The bin then stays immutable through that event."
                + "\nTPS opening: +" + f1(settings.desiredTpsStep)
                + " points is a coaching suggestion only. Any stable +"
                + f1(settings.usableStepLow()) + " to +" + f1(settings.usableStepHigh())
                + " point opening can form a physically valid event; repeatability is decided by comparable-event grouping."
                + "\nRPM only qualifies the entry: the automatically latched armed bin is the event's table point. Once the opening is confirmed there is no RPM ceiling and the latch cannot migrate."
                + "\nPrimary Blend timing is prediction-independent: after the pedal settles, hold steady while real MAP develops its own late response level. The central 20->80% of that observed physical MAP rise is timed."
                + "\nPredictive/fallback MAP target, timer resets and current-tune Effective MAP replay remain diagnostic context only; they cannot accept, reject, time, or group a physical event."
                + "\nCurrent-tune Effective MAP replay diagnostic: "
                + (settings.hasBlendCurve()
                    ? "available as context only."
                    : "curve unavailable; physical measurement authority is unchanged.")
                + "\nVehicle-test timing limits: " + limits.summary();
        instruction = baselineInstruction();
        state = GuidedCaptureState.SETTLING;
        emit(GuidedWorkflowEvent.SESSION_STARTED, latestResult, System.nanoTime());
        publishFocus(null);
    }

    synchronized void reset() {
        if (state != GuidedCaptureState.IDLE) {
            emit(GuidedWorkflowEvent.SESSION_ENDED,
                    "Controlled Guided session reset", System.nanoTime());
        }
        roadBaseline.clear();
        rpmBinLatch.release();
        eventSettings = null;
        validAttempts.clear();
        groups.rebuild(validAttempts);
        attempts = 0;
        excluded = 0;
        returnedToBaseline = 0;
        lastOutcome = 0L;
        pendingOutcome = null;
        lastAttemptTrace = "";
        lastSessionGearEvidence = "";
        clearFocusTraceHistory();
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
            rpmBinLatch.release();
            eventSettings = null;
            state = GuidedCaptureState.SETTLING;
            instruction = baselineInstruction();
        } else {
            clearReadyAndCapture();
            rpmBinLatch.release();
            eventSettings = null;
            state = GuidedCaptureState.PAUSED;
            instruction = "Guided capture paused. Passive logging remains active.";
            emit(GuidedWorkflowEvent.PAUSED, instruction, System.nanoTime());
        }
        publishFocus(null);
    }

    synchronized void finish() {
        if (state == GuidedCaptureState.IDLE
                || state == GuidedCaptureState.COMPLETE) return;
        clearReadyAndCapture();
        rpmBinLatch.release();
        eventSettings = null;
        state = GuidedCaptureState.COMPLETE;
        instruction = "Review the comparable physical-response groups. No ECU value was written.";
        GuidedVehicleTestLimits.endSession();
        emit(GuidedWorkflowEvent.SESSION_ENDED, instruction, System.nanoTime());
        publishFocus(null);
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
                + "\nComparability groups were rebuilt from the retained valid events.";
        if (state == GuidedCaptureState.COMPLETE) {
            state = GuidedCaptureState.SETTLING;
            GuidedVehicleTestLimits.beginSession();
        }
        publishFocus(null);
    }

    synchronized void accept(LiveSample sample) {
        if (sample == null) return;
        roadBaseline.add(sample);
        try {
            if (state == GuidedCaptureState.IDLE
                    || state == GuidedCaptureState.PAUSED
                    || state == GuidedCaptureState.COMPLETE) return;

            if (isOutcomeState(state)) {
                if (seconds(lastOutcome, sample.getNanoTime())
                        < OUTCOME_DISPLAY_SECONDS) return;
                clearReadyAndCapture();
                // The event is already immutable in retained evidence. Release
                // only after its outcome display has completed, before recovery
                // begins, so the next event may acquire a different armed bin.
                rpmBinLatch.release();
                eventSettings = null;
                state = GuidedCaptureState.RECOVERING;
                instruction = "Return to normal light throttle; the next armed RPM bin will be acquired automatically after recovery.";
            }

            if (state == GuidedCaptureState.RECOVERING) {
                double candidateRpm = rpmBinLatch.observe(
                        sample.get(ChannelRole.RPM), sample.getNanoTime());
                if (!Double.isFinite(candidateRpm)) {
                    checkText = rpmBinLatch.statusText(
                            sample.get(ChannelRole.RPM), sample.getNanoTime());
                    return;
                }
                RoadBaselineTracker.AcquireCheck check = roadBaseline.acquireCheck(
                        sample, candidateRpm, lastOutcome, RECOVERY_SECONDS,
                        ENTRY_RPM_TOLERANCE);
                checkText = rpmBinLatch.statusText(sample.get(ChannelRole.RPM),
                        sample.getNanoTime()) + "\n" + check.text;
                if (!check.recovered) return;
                state = GuidedCaptureState.SETTLING;
                instruction = baselineInstruction();
            }

            if (state == GuidedCaptureState.SETTLING) {
                double candidateRpm = rpmBinLatch.observe(
                        sample.get(ChannelRole.RPM), sample.getNanoTime());
                if (!Double.isFinite(candidateRpm)) {
                    checkText = rpmBinLatch.statusText(
                            sample.get(ChannelRole.RPM), sample.getNanoTime());
                    instruction = baselineInstruction();
                    return;
                }
                RoadBaselineTracker.AcquireCheck check = roadBaseline.acquireCheck(
                        sample, candidateRpm, lastOutcome, RECOVERY_SECONDS,
                        ENTRY_RPM_TOLERANCE);
                checkText = rpmBinLatch.statusText(sample.get(ChannelRole.RPM),
                        sample.getNanoTime()) + "\n" + check.text;
                if (check.ready && rpmBinLatch.candidateStable(sample.getNanoTime())) {
                    double latched = rpmBinLatch.latch(sample.getNanoTime());
                    if (!Double.isFinite(latched)) return;
                    eventSettings = settings.withStartRpm(latched);
                    mapCatchup.configure(eventSettings);
                    rollingBaseline = roadBaseline.baseline(false);
                    state = GuidedCaptureState.READY;
                    instruction = readyInstruction();
                    emit(GuidedWorkflowEvent.READY_ENTERED,
                            instruction, sample.getNanoTime());
                }
                return;
            }

            if (state == GuidedCaptureState.READY) {
                double eventRpm = rpmBinLatch.latchedRpm();
                if (!Double.isFinite(eventRpm)) {
                    rollingBaseline = null;
                    eventSettings = null;
                    state = GuidedCaptureState.SETTLING;
                    instruction = baselineInstruction();
                    return;
                }
                double readyBaselineTps = rollingBaseline == null
                        ? Double.NaN : rollingBaseline.tps;
                if (openingDetector.localTipInStarted(
                        sample, readyBaselineTps, limits.localTpsOnsetRise)) {
                    beginConfirmed(sample);
                    return;
                }
                if (triggered(sample) || sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                        || openingDetector.movementStarted(sample, readyBaselineTps)) {
                    beginOpeningPending(sample);
                    return;
                }
                RoadBaselineTracker.ReadyCheck ready =
                        roadBaseline.readyCheck(sample, eventRpm,
                                ENTRY_RPM_TOLERANCE);
                checkText = rpmBinLatch.statusText(sample.get(ChannelRole.RPM),
                        sample.getNanoTime()) + "\n" + ready.text;
                if (!ready.ready) {
                    rollingBaseline = null;
                    rpmBinLatch.release();
                    eventSettings = null;
                    state = GuidedCaptureState.SETTLING;
                    instruction = ready.instruction
                            + " Automatic RPM-bin latch released; reacquire any armed bin.";
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
        } finally {
            publishFocus(sample);
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

    synchronized String gearStatusForDisplay() {
        if (state == GuidedCaptureState.IDLE) {
            return "Gear: no active Guided session";
        }
        if (settings.manualGear > 0) {
            return "Gear: manual " + settings.manualGear + " — operator authoritative";
        }
        if (!settings.automaticGear) {
            return "Gear: ignored for this Guided session";
        }
        RoadBaselineTracker.Baseline source = baseline != null ? baseline : rollingBaseline;
        String evidence = source == null ? lastSessionGearEvidence : source.sessionGearEvidence();
        if (evidence == null || evidence.length() == 0) {
            return "Gear: automatic | ACQUIRING — waiting for trusted gear/VSS evidence";
        }
        if (evidence.startsWith("session gear not latched")) {
            return "Gear: automatic | ACQUIRING | " + evidence;
        }
        if (evidence.startsWith("session gear ") && evidence.contains(" latched")) {
            return "Gear: automatic | SESSION LATCHED | " + evidence;
        }
        return "Gear: automatic | ACQUIRING | " + evidence;
    }

    private String baselineInstruction() {
        double target = rpmBinLatch.displayTargetRpm();
        if (Double.isFinite(target)) {
            return "Hold smooth RPM near candidate Blend Duration bin " + f0(target)
                    + " RPM (entry ±" + f0(ENTRY_RPM_TOLERANCE)
                    + " RPM) until the automatic latch completes. Armed bins: "
                    + settings.armedRpmText() + " RPM.";
        }
        return "Drive smoothly near any armed Blend Duration bin: "
                + settings.armedRpmText() + " RPM (entry ±"
                + f0(ENTRY_RPM_TOLERANCE)
                + " RPM). Hold there briefly; the event bin latches automatically.";
    }

    private String readyInstruction() {
        double groupStep = groups.bestGroupMeanStep();
        String stepGuidance = Double.isFinite(groupStep)
                ? " Repeat close to the leading group's +" + f1(groupStep)
                    + " TPS step so the next event can join that comparable set."
                : " The suggested +" + f1(settings.desiredTpsStep)
                    + " TPS step is guidance only; the first valid event establishes the repeatable set.";
        double eventRpm = rpmBinLatch.latchedRpm();
        return "LATCHED " + f0(eventRpm)
                + " RPM table bin — make one smooth clear throttle opening when safe."
                + stepGuidance
                + " Broad usable opening: +" + f1(settings.usableStepLow())
                + " to +" + f1(settings.usableStepHigh()) + " TPS points."
                + " Stop increasing the pedal, then hold that position while the real MAP response is observed."
                + " Once the opening starts, RPM may rise freely; there is no post-start RPM ceiling.";
    }

    private void beginConfirmed(LiveSample sample) {
        List<LiveSample> early = openingDetector.consumePendingSamples();
        attempts++;
        baseline = rollingBaseline != null
                ? rollingBaseline
                : roadBaseline.baseline(true);
        rollingBaseline = null;
        if (settings.automaticGear && baseline != null) {
            baseline.sessionDetectedGear();
            lastSessionGearEvidence = baseline.sessionGearEvidence();
        }
        clearAttemptOnly();
        lastOutcomeFocusTrace = BlendDurationFocusTrace.empty();
        cachedLiveFocusTrace = BlendDurationFocusTrace.empty();
        cachedLiveFocusTraceNano = 0L;
        lastAttemptTrace = "";
        detectorSeen = triggered(sample) || sample.bool(ChannelRole.MAP_PRED_ACTIVE);
        openingStarted = sample.getNanoTime();
        peakTps = sample.get(ChannelRole.TPS);
        for (LiveSample earlySample : early) {
            if (triggered(earlySample) || earlySample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
                detectorSeen = true;
            }
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
                ? "Opening detected. Settle into one stable pedal plateau and hold."
                : "Opening detected locally; waiting briefly for ECU detector/prediction evidence while you settle into one stable pedal plateau.";
    }

    private void beginOpeningPending(LiveSample sample) {
        openingDetector.beginPending(sample);
        state = GuidedCaptureState.OPENING_PENDING;
        instruction = "Opening pending: continue the same smooth pedal movement. The immediately preceding rolling baseline is frozen now.";
        emit(GuidedWorkflowEvent.OPENING_PENDING,
                instruction, sample.getNanoTime());
    }

    private void monitorOpeningPending(LiveSample sample) {
        double eventRpm = rpmBinLatch.latchedRpm();
        PedalOpeningDetector.Decision decision = openingDetector.observePending(
                sample, rollingBaseline, eventRpm, limits,
                ENTRY_RPM_TOLERANCE);
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
            physicalResponse.begin(baseline.map, plateauAcquiredNano);
            physicalResponse.observe(attemptEvidence.samples(), sample,
                    limits.mapCatchupSeconds);
            // Preserve prediction/fallback catch-up only as diagnostic context.
            mapCatchup.beginCatchup(attemptEvidence.samples(), limits.mapCatchupSeconds);
            instruction = "Pedal stopped around " + f1(plateau.medianTps)
                    + "% (step +" + f1(plateau.step)
                    + "). Keep that pedal position steady while the real MAP response develops."
                    + " The event's own late MAP level defines the physical step; 20->80% of that step is timed."
                    + " Predictive/fallback MAP is diagnostic only. RPM may continue rising; it is measurement metadata, not an exclusion gate.";
            emit(GuidedWorkflowEvent.TARGET_ACQUIRED,
                    instruction, sample.getNanoTime());
            return;
        }

        if (elapsed > limits.targetAcquisitionSeconds) {
            String reason;
            if (plateau.step < PedalPlateauDetector.MIN_USABLE_STEP) {
                reason = "The opening settled at only +" + f1(plateau.step)
                        + " TPS points; at least +" + f1(PedalPlateauDetector.MIN_USABLE_STEP)
                        + " is needed for this recipe.";
            } else if (plateau.step > PedalPlateauDetector.MAX_USABLE_STEP) {
                reason = "The opening settled at +" + f1(plateau.step)
                        + " TPS points; this exceeds the +" + f1(PedalPlateauDetector.MAX_USABLE_STEP)
                        + " road-capture ceiling.";
            } else {
                reason = "The pedal did not form a usable controlled plateau within "
                        + f2(limits.targetAcquisitionSeconds)
                        + " seconds (recent range " + f1(plateau.range)
                        + " points).";
            }
            exclude(sample, reason,
                    "Repeat one smooth clear opening and settle into one stable pedal plateau; comparable valid events are grouped automatically.");
            return;
        }

        double actualStep = tps - baseline.tps;
        instruction = "Opening detected. Current step +" + f1(actualStep)
                + " TPS; settle into a stable plateau and hold. Broad usable range +"
                + f1(settings.usableStepLow()) + " to +" + f1(settings.usableStepHigh())
                + "; matching to the five-event set happens after capture.";
    }

    private void monitorCatchup(LiveSample sample) {
        // Prediction/fallback evidence remains useful diagnostics, but physical
        // event timing no longer waits for or targets fallbackMap.
        mapCatchup.observePredictionGap(sample);
        double tps = sample.get(ChannelRole.TPS);
        double held = holdAnchor == null
                ? Double.NaN : holdAnchor.get(ChannelRole.TPS);
        if (Double.isFinite(tps) && Double.isFinite(held)) {
            if (tps < held - MAJOR_PEDAL_MOVE) {
                exclude(sample,
                        "Throttle backed off " + f1(held - tps)
                                + " points before the physical MAP response measurement completed.",
                        "Small pedal motion is allowed; avoid a distinct release until the completion cue.");
                return;
            }
            if (tps > held + MAJOR_PEDAL_MOVE) {
                exclude(sample,
                        "A second throttle opening of " + f1(tps - held)
                                + " points occurred before the physical MAP response measurement completed.",
                        "Use one opening per Guided event.");
                return;
            }
        }

        // Retain old predicted-target catch-up as diagnostic evidence only.
        mapCatchup.observeCatchup(sample);
        physicalResponse.observe(attemptEvidence.samples(), sample,
                limits.mapCatchupSeconds);
        if (physicalResponse.isFailed()) {
            exclude(sample,
                    physicalResponse.failureReason(),
                    "Repeat one clear opening and steady hold. The physical MAP step must be large enough to define a useful 20->80% response; do not chase fallbackMap.");
            return;
        }
        if (physicalResponse.isComplete()
                && seconds(plateauAcquiredNano, sample.getNanoTime())
                >= TARGET_CUE_MIN_SECONDS) {
            complete(physicalResponse.completionSample(),
                    physicalResponse.durationSeconds());
        }
    }

    private void complete(LiveSample completedAt, double duration) {
        BlendDurationCaptureConfig retainedEventSettings = eventSettings();
        BlendDurationAttempt candidate = attemptEvidence.buildPhysicalAttempt(
                attempts, baseline, physicalResponse.lowCrossSample(),
                holdAnchor, completedAt, duration, retainedEventSettings,
                physicalResponse.mapStep(), physicalResponse.lateMap(),
                physicalResponse.lowThreshold(), physicalResponse.highThreshold(),
                mapCatchup.bestGap(), physicalResponse.usedBoundedLateWindow());

        BlendDurationComparabilityGroups.Assignment assignment = groups.assign(candidate);
        validAttempts.add(candidate);
        boolean replayConsistent = mapCatchup.effectiveMapModelConsistent();
        boolean warning = candidate.gearReliabilityWarning()
                || assignment.nearBoundary
                || physicalResponse.usedBoundedLateWindow();
        lastAttemptTrace = compactTrace(
                warning ? "VALID_WITH_WARNING" : "VALID", completedAt);
        BlendDurationFocusTrace acceptedFocusTrace = buildFocusTrace(
                completedAt, true, warning ? "VALID WITH WARNING" : "VALID");
        previousAcceptedFocusTrace = lastAcceptedFocusTrace;
        lastAcceptedFocusTrace = acceptedFocusTrace;
        lastOutcomeFocusTrace = acceptedFocusTrace;
        cachedLiveFocusTrace = acceptedFocusTrace;
        cachedLiveFocusTraceNano = completedAt.getNanoTime();
        lastOutcome = completedAt.getNanoTime();

        String predictionContext = Double.isFinite(mapCatchup.finalPredictionTarget())
                ? "latest timer-reset prediction target " + f2(mapCatchup.finalPredictionTarget())
                    + " kPa | prediction target-anchor gap " + f2(mapCatchup.bestGap()) + " kPa"
                : "prediction target unavailable for this event";

        latestResult = (warning ? "VALID ROAD EVENT WITH WARNING" : "VALID ROAD EVENT")
                + "\nPhysical MAP 20->80 response duration: " + f3(duration) + " s"
                + "\nPhysical MAP response: " + f2(candidate.baseMap) + " -> "
                + f2(candidate.physicalLateMap) + " kPa"
                + " | step " + f2(candidate.physicalMapStep) + " kPa"
                + " | 20% " + f2(candidate.responseLowMap)
                + " -> 80% " + f2(candidate.responseHighMap) + " kPa"
                + "\nBaseline: " + f0(candidate.baseRpm) + " RPM / "
                + f2(candidate.baseMap) + " kPa / "
                + f1(candidate.baseTps) + "% TPS"
                + "\nControlled held TPS: " + f1(candidate.heldTps) + "%"
                + " | TPS step: +" + f1(candidate.tpsStep) + " points"
                + " | RPM trend: " + candidate.trend
                + "\nPrediction diagnostic only: " + predictionContext
                + "\nGear: " + candidate.gearText()
                + "\n" + physicalResponse.evidenceText()
                + "\n" + mapCatchup.modelEvidenceText()
                + "\n" + mapCatchup.counterEvidenceText()
                + "\nGroup: " + assignment.groupId + " | "
                + assignment.description
                + "\nAll valid groups are retained; only comparable physical-response events in one group are combined for repeatability review."
                + "\nNumerical Blend Duration Apply is intentionally withheld while the physical-response-to-curve conversion is being validated.";
        if (physicalResponse.usedBoundedLateWindow()) {
            latestResult += "\nAdvisory: MAP was still moving enough that the bounded observation horizon supplied the late physical level. The event is retained as lower-confidence evidence; repeatability across the group decides usefulness.";
        }
        if (candidate.gearReliabilityWarning()) {
            latestResult += "\nAdvisory: automatic gear/VSS evidence did not establish a reliable latch. Manual gear mode treats operator-selected gear as authoritative metadata.";
        }
        if (!replayConsistent) {
            latestResult += "\nDiagnostic only: current-tune Effective MAP replay is missing or inconsistent."
                    + " This does not change physical event validity, warning state, measured duration, or repeatability authority.";
        }
        state = warning ? GuidedCaptureState.WARNING
                : GuidedCaptureState.ACCEPTED;
        instruction = "Valid physical MAP-response event captured. Return to normal light throttle; another road baseline will acquire automatically.";
        pendingOutcome = new GuidedOutcome(
                warning ? GuidedOutcome.Decision.VALID_WITH_WARNING
                        : GuidedOutcome.Decision.VALID,
                completedAt.getSeconds(), duration, validAttempts.size(),
                assignment.groupId, assignment.groupCount,
                latestResult, lastAttemptTrace);
        emit(GuidedWorkflowEvent.EVENT_ACCEPTED,
                instruction, completedAt.getNanoTime());

        double completedBin = retainedEventSettings.startRpm;
        int binProgress = groups.bestGroupCountForBin(completedBin);
        if (groups.targetsReached(settings.armedRpmBins(), settings.targetCount)) {
            state = GuidedCaptureState.COMPLETE;
            instruction = "SERIES COMPLETE — every armed RPM bin reached "
                    + settings.targetCount + " comparable physical-response events. "
                    + groups.binProgress(settings.armedRpmBins(), settings.targetCount)
                    + ". Review each bin independently; numerical Apply remains withheld in this validation stage.";
            rpmBinLatch.release();
            eventSettings = null;
            GuidedVehicleTestLimits.endSession();
            emit(GuidedWorkflowEvent.SERIES_COMPLETE,
                    instruction, completedAt.getNanoTime());
        } else {
            instruction = "Valid event stored at latched " + f0(completedBin)
                    + " RPM bin (" + binProgress + "/" + settings.targetCount
                    + "). Return to normal light throttle; after recovery any armed bin may auto-latch. "
                    + groups.binProgress(settings.armedRpmBins(), settings.targetCount) + ".";
        }
    }

    private void exclude(LiveSample sample, String reason, String correction) {
        if (lastAttemptTrace.length() == 0) {
            lastAttemptTrace = compactTrace("EXCLUDED", sample);
        }
        lastOutcomeFocusTrace = buildFocusTrace(sample, true, "EXCLUDED");
        cachedLiveFocusTrace = lastOutcomeFocusTrace;
        cachedLiveFocusTraceNano = sample == null ? System.nanoTime() : sample.getNanoTime();
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
            lastOutcomeFocusTrace = buildFocusTrace(sample, true, "RETURN / RE-ARM");
            cachedLiveFocusTrace = lastOutcomeFocusTrace;
            cachedLiveFocusTraceNano = sample == null ? System.nanoTime() : sample.getNanoTime();
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
        String base = GuidedAttemptTrace.build(disposition, attemptEvidence.samples(), eventSettings(), limits,
                RoadBaselineTracker.BASELINE_SECONDS, PedalPlateauDetector.WINDOW_SECONDS, PedalPlateauDetector.RANGE_LIMIT,
                MAJOR_PEDAL_MOVE, physicalResponse.lowCrossSample(),
                physicalResponse.mapStep(), holdAnchor, outcome);
        return base
                + "physical_map_baseline_kpa=" + f2(physicalResponse.baselineMap()) + "\n"
                + "physical_map_late_kpa=" + f2(physicalResponse.lateMap()) + "\n"
                + "physical_map_step_kpa=" + f2(physicalResponse.mapStep()) + "\n"
                + "physical_response_20_kpa=" + f2(physicalResponse.lowThreshold()) + "\n"
                + "physical_response_80_kpa=" + f2(physicalResponse.highThreshold()) + "\n"
                + "physical_response_20_80_s=" + f3(physicalResponse.durationSeconds()) + "\n"
                + "physical_late_window_bounded=" + physicalResponse.usedBoundedLateWindow() + "\n"
                + "prediction_target_diagnostic_kpa=" + f2(mapCatchup.finalPredictionTarget()) + "\n"
                + "prediction_target_gap_diagnostic_kpa=" + f2(mapCatchup.bestGap()) + "\n"
                + "prediction_target_catchup_diagnostic_s=" + f3(mapCatchup.catchupDurationSeconds()) + "\n"
                + "effective_map_model_check=" + singleLine(mapCatchup.modelEvidenceText()) + "\n"
                + "prediction_counter_check=" + singleLine(mapCatchup.counterEvidenceText()) + "\n";
    }

    private BlendDurationFocusTrace buildFocusTrace(LiveSample latest,
                                                     boolean frozen,
                                                     String outcome) {
        return BlendDurationFocusTrace.build(
                attemptEvidence.samples(),
                holdAnchor,
                physicalResponse.lowCrossSample(),
                physicalResponse.highCrossSample(),
                physicalResponse.lateMap(),
                physicalResponse.lowThreshold(),
                physicalResponse.highThreshold(),
                mapCatchup.finalPredictionTarget(),
                frozen,
                outcome);
    }

    private BlendDurationFocusTrace currentFocusTrace(LiveSample latest) {
        boolean activeAttempt = attemptEvidence.sampleCount() > 0
                && (state == GuidedCaptureState.CAPTURING
                    || state == GuidedCaptureState.OPENING_PENDING);
        if (activeAttempt) {
            long now = latest == null ? System.nanoTime() : latest.getNanoTime();
            if (!cachedLiveFocusTrace.hasData()
                    || cachedLiveFocusTraceNano == 0L
                    || now - cachedLiveFocusTraceNano >= 50000000L) {
                cachedLiveFocusTrace = buildFocusTrace(latest, false, "CURRENT ATTEMPT");
                cachedLiveFocusTraceNano = now;
            }
            return cachedLiveFocusTrace;
        }
        if (lastOutcomeFocusTrace.hasData()) return lastOutcomeFocusTrace;
        return BlendDurationFocusTrace.empty();
    }

    private BlendDurationFocusTrace ghostFocusTrace(BlendDurationFocusTrace current) {
        if (current == lastAcceptedFocusTrace) {
            return previousAcceptedFocusTrace;
        }
        return lastAcceptedFocusTrace;
    }

    private void clearFocusTraceHistory() {
        lastOutcomeFocusTrace = BlendDurationFocusTrace.empty();
        lastAcceptedFocusTrace = BlendDurationFocusTrace.empty();
        previousAcceptedFocusTrace = BlendDurationFocusTrace.empty();
        cachedLiveFocusTrace = BlendDurationFocusTrace.empty();
        cachedLiveFocusTraceNano = 0L;
    }

    private void clearAttemptOnly() {
        mapCatchup.reset();
        physicalResponse.reset();
        attemptEvidence.reset();
        openingDetector.reset();
        holdAnchor = null;
        detectorSeen = false;
        plateauAcquired = false;
        peakTps = Double.NaN;
        openingStarted = 0L;
        plateauAcquiredNano = 0L;
        cachedLiveFocusTrace = BlendDurationFocusTrace.empty();
        cachedLiveFocusTraceNano = 0L;
    }

    private void clearAttemptStateAfterOutcome() {
        mapCatchup.reset();
        physicalResponse.reset();
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

    private BlendDurationCaptureConfig eventSettings() {
        if (eventSettings != null) return eventSettings;
        double latched = rpmBinLatch.latchedRpm();
        if (Double.isFinite(latched)) return settings.withStartRpm(latched);
        return settings;
    }

    private BlendDurationCaptureConfig presentationSettings() {
        double display = rpmBinLatch.displayTargetRpm();
        return settings.withStartRpm(display);
    }

    synchronized double latchedRpmForTest() { return rpmBinLatch.latchedRpm(); }
    synchronized double candidateRpmForTest() { return rpmBinLatch.candidateRpm(); }
    synchronized double[] armedRpmBinsForTest() { return settings.armedRpmBins(); }

    synchronized GuidedSessionSnapshot snapshot() {
        BlendDurationCaptureConfig presentation = presentationSettings();
        return BlendDurationGuidedSummary.snapshot(
                state, plateauAcquired, instruction,
                gearStatusForDisplay() + "\n" + rpmBinLatch.statusText(
                        Double.NaN, System.nanoTime()) + "\n" + checkText, latestResult,
                presentation, validAttempts.size(), excluded, returnedToBaseline,
                attempts, groups, lastAttemptTrace);
    }

    private void publishFocus(LiveSample latest) {
        BlendDurationFocusTrace currentTrace = currentFocusTrace(latest);
        BlendDurationFocusTrace ghostTrace = ghostFocusTrace(currentTrace);
        BlendDurationCaptureConfig presentation = presentationSettings();
        GuidedFocusHub.publishBlendDuration(state,
                BlendDurationFocusModel.build(state, presentation, latest,
                        rollingBaseline, baseline, holdAnchor, plateauAcquired,
                        physicalResponse, mapCatchup, groups, validAttempts,
                        excluded, returnedToBaseline,
                        instruction, checkText, latestResult,
                        currentTrace, ghostTrace),
                "Blend Duration primary timing uses the event's own physical MAP rise: stable baseline, one opening, steady pedal, then normalized 20->80% response timing. Predictive/fallback MAP target, timer-reset evidence and current-tune Effective MAP replay are diagnostic context only and cannot define physical event validity or duration. Numerical Apply remains intentionally withheld.");
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
                && finite(sample, ChannelRole.MAP);
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

    private static String singleLine(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
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
