package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.EngagementPassiveCapture;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.host.AeTuningParameterCatalog;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.List;

/**
 * Foundation 1 passive TPS movement/timing analysis.
 *
 * Capture never changes controller settings. The first usable physical opening
 * becomes a presentation-only TPS reference marker, later peaks get shorter
 * repeat markers, and a real settling gate separates independent movements.
 * Delta Window candidates are still calculated from measured traces, not from
 * whether the driver visually hits the reference marker.
 */
public final class EngagementDetectionMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.ACCEL_THRESHOLD,
            ChannelRole.AE_ABOVE_THRESHOLD
    };

    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.VSS,
            ChannelRole.DELTA_TPS,
            ChannelRole.AE_DELTA_NEWEST_PAIR,
            ChannelRole.AE_WINDOW_MS,
            ChannelRole.AE_WINDOW_SAMPLES,
            ChannelRole.AE_DELTA_STRIDE,
            ChannelRole.TPS_DECEL_ACTIVE,
            ChannelRole.SMOOTHED_DELTA_TPS,
            ChannelRole.TPS_AE_CYCLE_CNT,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.AE_ADD_MS,
            ChannelRole.WALL_WETTING_PW,
            ChannelRole.INSTANT_PULSE_PW
    };

    private AeProjectSnapshot latestSnapshot;

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.ENGAGEMENT_DETECTION; }
    @Override public String setupTitle() { return "TPS movement / timing"; }

    @Override public String setupGuidance() {
        return "Read Working Tune, then Start Capture. Make one comfortable moderate positive pedal opening when safe; its peak becomes a full-height presentation-only visual reference marker. "
                + "After SETTLING completes, repeat approximately that movement. Later completed peaks are shown as shorter lower-half markers so the original reference stays obvious. "
                + "There is no exact TPS target and the marker does not decide acceptance. AE Tuner groups the measured repeatable step-size cluster itself. "
                + "Idle/no-load blips are valid provisional evidence, but guarded Apply is withheld until comparable openings cover a useful spread of pre-event operating RPM. Vehicle speed is useful context but is not a hard prerequisite; no fixed 2000 RPM target is required. "
                + "No temporary timing write occurs during capture. With Dual Stride / Newest, Sample Length remains a history-capacity constraint and is increased only if the selected Delta Window would otherwise clamp. No burn.";
    }

    @Override public String captureGoal() {
        return "Capture a small set of naturally repeatable moderate TPS openings. The first usable opening establishes the visual reference; subsequent movements only need to be approximately similar. "
                + "Wait for SETTLING to finish before the next opening so two physical movements cannot overlap. "
                + "The plugin decides comparability and withholds ambiguous/tied Delta Window changes instead of inventing precision.";
    }

    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }

    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Make one comfortable moderate positive pedal opening, then use its full-height TPS marker as a visual memory aid for the next openings. "
                + "Short lower-half markers show where later completed openings peaked. The markers are presentation-only; do not chase an exact number. "
                + "After every event, let TPS return and stabilize before READY returns. Stationary captures also wait for the obvious RPM flare to finish. "
                + "Garage/idle testing is useful for provisional repeatability and scoring; representative openings across a useful pre-event operating RPM spread are required before Apply; VSS is context, not a prerequisite.";
    }

    @Override public String accumulationPlan() {
        return "The live path stores only bounded TPS/RPM/threshold/VSS points around each positive physical opening. "
                + "Movement onset is derived from a rolling vehicle-specific TPS-rate noise floor and the event closes on the first natural peak/reversal. "
                + "A settling gate then requires TPS to return near the pre-event baseline and remain quiet before re-arm; when stationary, rapidly falling post-blip RPM also prevents re-arm. "
                + "A robust densest-cluster calculation uses TPS-step median and MAD with bounded tolerance, independently of the presentation-only reference marker. "
                + "When the comparable set is complete, AE Tuner replays the captured TPS traces through the firmware-equivalent Dual Stride / Newest comparison shape for a bounded Delta Window candidate set. "
                + "Small candidate-score improvements retain the current value, and tied/near-tied change candidates request another independent set instead of choosing arbitrarily. "
                + "Final confirmation requires several comparable openings across a useful pre-event operating RPM spread, but not a fixed RPM target or a working VSS channel. No controller writes occur during capture.";
    }

    @Override public String reviewOutputs() {
        return EngagementPassiveCapture.reviewText(latestSnapshot);
    }

    @Override public synchronized String currentTuneContext(AeProjectSnapshot snapshot) {
        int parameterCount = AeTuningParameterCatalog.forSubsystem(
                AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION).size();
        if (snapshot == null) {
            return "AE Foundation detector/timing family: " + parameterCount
                    + " catalogued settings. Read Working Tune before passive capture.";
        }
        if (latestSnapshot != snapshot) {
            latestSnapshot = snapshot;
            EngagementPassiveCapture.reset();
        }
        EngagementModelOption model = EngagementModelOption.fromControllerText(snapshot.getEngagementModel());
        String modelState = model == EngagementModelOption.DUAL_STRIDE_NEWEST
                ? "Dual Stride / Newest verified"
                : "WARNING: passive timing estimator is calibrated for Dual Stride / Newest";
        String callbackState = snapshot.hasEngagementFastCallback()
                ? (snapshot.isEngagementFastCallback()
                    ? "Fast Callback ON"
                    : "Fast Callback OFF")
                : "Fast Callback state unavailable";
        return "AE Foundation detector/timing family: " + parameterCount
                + " catalogued settings. " + snapshot.engagementSettingsText()
                + ". " + modelState + ". " + callbackState + ". "
                + "Capture is read-only. Idle/no-load evidence may produce a provisional result; guarded Apply remains withheld until representative pre-event operating RPM coverage confirms it. "
                + "Delta Window is estimated after a comparable natural-movement set is captured, while Sample Length remains history capacity. No burn.";
    }

    @Override public boolean activityObserved(LiveSample sample) {
        return EngagementPassiveCapture.accept(sample);
    }

    @Override public ProposalWritePlan explicitSettingWritePlan(AeProjectSnapshot snapshot) {
        return null;
    }

    @Override public ProposalWritePlan reviewedWritePlan(AeProjectSnapshot snapshot,
                                                          List<LiveSample> evidence) {
        return EngagementPassiveCapture.recommendationPlan(snapshot);
    }
}
