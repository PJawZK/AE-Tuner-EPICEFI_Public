package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTaskSettingsDraft;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.guided.GuidedWorkingTuneSurfaceCache;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.List;

/**
 * Active read-only evidence module for the complete baseline subtasks that used
 * to be architecture-only scaffolds. Each instance has its own recipe/channel
 * contract and review wording while sharing the bounded baseline math layer.
 */
final class BaselineGuidedTaskModule extends AbstractProbeMethodModule {
    private final GuidedTuningRecipe recipe;
    private final String title;
    private final String guidance;
    private final String goal;
    private final String inputs;
    private final String accumulation;
    private final String outputs;
    private final ChannelRole[] required;
    private final ChannelRole[] context;
    private volatile String lastReview;

    private BaselineGuidedTaskModule(GuidedTuningRecipe recipe,
                                     String title, String guidance,
                                     String goal, String inputs,
                                     String accumulation, String outputs,
                                     ChannelRole[] required,
                                     ChannelRole[] context) {
        this.recipe = recipe;
        this.title = title;
        this.guidance = guidance;
        this.goal = goal;
        this.inputs = inputs;
        this.accumulation = accumulation;
        this.outputs = outputs;
        this.required = required == null ? new ChannelRole[0] : required.clone();
        this.context = context == null ? new ChannelRole[0] : context.clone();
        this.lastReview = outputs;
    }

    static BaselineGuidedTaskModule tpsCompensation() {
        return new BaselineGuidedTaskModule(
                GuidedTuningRecipe.TPS_AE_COMPENSATION,
                "TPS AE condition compensation",
                "Run the same clean opening across RPM, coolant temperature and ending TPS after the base cycle table is credible. The complete RPM curve, TPS-vs-CLT table and AE-vs-CLT curve are frozen from Working Tune and remain review/apply-capable.",
                "Accumulate comparable fuel-proved TPS AE events over useful RPM/CLT/TPS bins while keeping the base TPS-to/cycle table unchanged.",
                "Use one unchanged Working Tune and repeat a standardized pedal opening. Cover conditions deliberately rather than mixing maneuver size with temperature/load changes.",
                "Group clean lambda-target residual by RPM, CLT and TPS/CLT cell. The baseline distributes correction authority across the multiplicative surfaces so the same residual is not applied in full three times.",
                "Show covered bins/cells, residual direction, proposed multiplier movement, uncovered settings retained, and overlap from Wall/Instant/MAP Predict.",
                roles(ChannelRole.RPM, ChannelRole.TPS, ChannelRole.COOLANT,
                        ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA,
                        ChannelRole.AE_ADD_MS, ChannelRole.TPS_AE_CYCLE_CNT),
                roles(ChannelRole.MAP, ChannelRole.DELTA_TPS,
                        ChannelRole.WALL_CORRECTION, ChannelRole.INSTANT_PULSE_PW,
                        ChannelRole.MAP_PRED_ACTIVE, ChannelRole.DFCO,
                        ChannelRole.FUEL_CUT, ChannelRole.STFT1));
    }

    static BaselineGuidedTaskModule tpsCompletion() {
        return new BaselineGuidedTaskModule(
                GuidedTuningRecipe.TPS_AE_COMPLETION,
                "TPS AE completion / closed-loop handoff",
                "Record the whole TPS AE tail through lambda recovery and closed-loop return. Engine-cycle axis, Burn Skip, EGO reset and post-accel trim inhibit are all present in the Working Tune surface.",
                "Capture clean complete AE events plus stacked/re-applied openings so fuel tail and closed-loop handoff are measured instead of inferred from one AFR peak.",
                "Use the tuned fuel/compensation setup unchanged. Include isolated openings plus several controlled re-applies. Do not change EGO/trim settings during the capture.",
                "Measure end-of-AE to lambda recovery, STFT behavior and re-apply response. Baseline automatic movement targets only settings the trace attributes; all other completion controls remain directly reviewable.",
                "Show AE tail duration, lambda recovery distribution, STFT/closed-loop context, re-apply evidence and any bounded completion-setting proposal.",
                roles(ChannelRole.TIME, ChannelRole.RPM, ChannelRole.TPS,
                        ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA,
                        ChannelRole.AE_ADD_MS, ChannelRole.TPS_AE_CYCLE_CNT,
                        ChannelRole.STFT1),
                roles(ChannelRole.AE_ABOVE_THRESHOLD, ChannelRole.AE_EVENT_JUST_OCCURRED,
                        ChannelRole.WALL_CORRECTION, ChannelRole.INSTANT_PULSE_PW,
                        ChannelRole.MAP_PRED_ACTIVE, ChannelRole.DFCO, ChannelRole.FUEL_CUT));
    }

    static BaselineGuidedTaskModule tpsValidation() {
        return validation(GuidedTuningRecipe.TPS_AE_VALIDATION,
                "TPS AE final validation",
                "Validate early amount, mid-event shape, late tail and rapid re-apply after the TPS AE subtasks are tuned.",
                roles(ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                        ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA,
                        ChannelRole.AE_ADD_MS, ChannelRole.TPS_AE_CYCLE_CNT),
                roles(ChannelRole.WALL_CORRECTION, ChannelRole.INSTANT_PULSE_PW,
                        ChannelRole.MAP_PRED_ACTIVE, ChannelRole.STFT1,
                        ChannelRole.DFCO, ChannelRole.FUEL_CUT));
    }

    static BaselineGuidedTaskModule wallAdvanced() {
        return new BaselineGuidedTaskModule(
                GuidedTuningRecipe.WALL_WETTING_ADVANCED,
                "Advanced Wall Wetting mapping",
                "After Base Tau/Beta is credible, map residual film behavior over CLT and RPM/MAP. Tau/Beta CLT curves and both RPM/MAP tables are complete Working Tune surfaces, not hidden scaffolds.",
                "Collect paired tip-in/tip-out events over deliberate CLT and RPM/MAP coverage while keeping the base model unchanged.",
                "Use comparable bidirectional transients. Cover one region at a time and retain TPS AE/Instant/MAP Predict overlap channels for attribution.",
                "Bin amplitude residual by CLT and RPM/MAP for Beta surfaces; retain Tau surfaces and use persistence timing evidence before automatic Tau movement.",
                "Show CLT/RPM/MAP coverage, Beta cell/curve proposals, Tau persistence evidence, overlap counts and every advanced setting still available for direct reviewed Apply.",
                roles(ChannelRole.RPM, ChannelRole.MAP, ChannelRole.COOLANT,
                        ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA,
                        ChannelRole.WALL_CORRECTION, ChannelRole.WALL_WETTING_PW),
                roles(ChannelRole.TPS, ChannelRole.DELTA_TPS,
                        ChannelRole.AE_ADD_MS, ChannelRole.INSTANT_PULSE_PW,
                        ChannelRole.MAP_PRED_ACTIVE, ChannelRole.DFCO, ChannelRole.FUEL_CUT));
    }

    static BaselineGuidedTaskModule wallValidation() {
        return validation(GuidedTuningRecipe.WALL_WETTING_VALIDATION,
                "Wall Wetting film validation",
                "Validate the finished wall model in both directions, including decay and return to neutral correction across conditions.",
                roles(ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                        ChannelRole.COOLANT, ChannelRole.LAMBDA,
                        ChannelRole.TARGET_LAMBDA, ChannelRole.WALL_CORRECTION),
                roles(ChannelRole.WALL_WETTING_PW, ChannelRole.AE_ADD_MS,
                        ChannelRole.INSTANT_PULSE_PW, ChannelRole.MAP_PRED_ACTIVE,
                        ChannelRole.DFCO, ChannelRole.FUEL_CUT));
    }

    static BaselineGuidedTaskModule instantSetup() {
        return new BaselineGuidedTaskModule(
                GuidedTuningRecipe.INSTANT_FUEL_SETUP,
                "Instant Fuel global pulse / inhibit",
                "With Instant Fuel enabled in the Working Tune, tune the complete three-setting setup: enable state, global multiplier and inhibit cycles. If Instant Fuel is OFF, this task is unavailable and collects no evidence until the method is enabled and Working Tune is read again.",
                "Collect repeatable sharp openings and controlled rapid re-applies after upstream TPS/MAP/Wall behavior is credible.",
                "Use one unchanged enabled Working Tune. Include enough events to distinguish a first-moment lean hole from a sustained upstream error, plus several rapid re-applies for inhibit-cycle evidence.",
                "Compare first-moment lambda residual, pulse counter/PW and blocked re-apply behavior. Sustained errors remain upstream faults rather than a reason to inflate Instant Fuel.",
                "Show first-moment repeatability, enable-state context, multiplier decision, inhibit-cycle evidence, pulse count and overlap from TPS AE/Wall/MAP Predict.",
                roles(ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                        ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA,
                        ChannelRole.SMOOTHED_DELTA_TPS, ChannelRole.ACCEL_THRESHOLD,
                        ChannelRole.AE_EVENT_JUST_OCCURRED, ChannelRole.INSTANT_PULSE_CNT),
                roles(ChannelRole.INSTANT_PULSE_PW, ChannelRole.COOLANT,
                        ChannelRole.AE_ADD_MS, ChannelRole.WALL_CORRECTION,
                        ChannelRole.MAP_PRED_ACTIVE, ChannelRole.DFCO, ChannelRole.FUEL_CUT));
    }

    static BaselineGuidedTaskModule instantEventStrength() {
        return new BaselineGuidedTaskModule(
                GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH,
                "Instant Fuel event strength",
                "Shape pulse strength versus the latched TPS change after global pulse/inhibit is credible. The complete Delta-TPS axis and multiplier curve are frozen and editable.",
                "Accumulate clean Instant Fuel events spanning small, medium and sharp accepted TPS changes.",
                "Keep RPM/load/temperature as repeatable as practical while deliberately varying opening magnitude.",
                "Bin first-moment lambda residual by Fuel: TPS AE change and adjust only covered multiplier bins; the axis remains the Working Tune baseline unless explicitly edited in Task Settings.",
                "Show Delta-TPS coverage, clean event count, residual per bin and bounded multiplier-curve proposals.",
                roles(ChannelRole.DELTA_TPS, ChannelRole.LAMBDA,
                        ChannelRole.TARGET_LAMBDA, ChannelRole.INSTANT_PULSE_PW,
                        ChannelRole.INSTANT_PULSE_CNT),
                roles(ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                        ChannelRole.COOLANT, ChannelRole.AE_ADD_MS,
                        ChannelRole.WALL_CORRECTION, ChannelRole.DFCO, ChannelRole.FUEL_CUT));
    }

    static BaselineGuidedTaskModule instantConditions() {
        return new BaselineGuidedTaskModule(
                GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS,
                "Instant Fuel operating conditions",
                "Tune the four complete RPM, ending-TPS, MAP and CLT multiplier curves from clean pulse events after global and Delta-TPS behavior is credible.",
                "Collect repeatable Instant Fuel events over deliberate RPM/TPS/MAP/CLT coverage.",
                "Change operating condition coverage deliberately while keeping pedal-event severity comparable. The baseline distributes residual authority across four multiplicative curves to avoid quadruple correction.",
                "Bin first-moment residual independently by RPM, ending TPS, MAP and CLT. Only covered bins move; axes and uncovered multipliers stay at their frozen Working Tune values.",
                "Show coverage and proposed bins for all four condition curves plus overlap/rejection diagnostics.",
                roles(ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                        ChannelRole.COOLANT, ChannelRole.LAMBDA,
                        ChannelRole.TARGET_LAMBDA, ChannelRole.INSTANT_PULSE_PW,
                        ChannelRole.INSTANT_PULSE_CNT),
                roles(ChannelRole.DELTA_TPS, ChannelRole.AE_ADD_MS,
                        ChannelRole.WALL_CORRECTION, ChannelRole.MAP_PRED_ACTIVE,
                        ChannelRole.DFCO, ChannelRole.FUEL_CUT));
    }

    private static BaselineGuidedTaskModule validation(GuidedTuningRecipe recipe,
                                                        String title, String guidance,
                                                        ChannelRole[] required,
                                                        ChannelRole[] context) {
        return new BaselineGuidedTaskModule(recipe, title, guidance,
                "Accumulate representative completed-method events and classify residual error without changing another setting.",
                "Keep the completed method stack unchanged. Include isolated events, re-applies and the operating regions that were tuned.",
                "Classify early lean/rich, mid-event error, late tail, recovery/reapply and overlap. Route failures back to the owning tuning subtask.",
                "Validation verdict and residual classification only. No direct controller write target belongs to this task.",
                required, context);
    }

    @Override public GuidedTuningRecipe recipe() { return recipe; }
    @Override public String setupTitle() { return title; }
    @Override public String setupGuidance() { return guidance; }
    @Override public String captureGoal() { return goal; }
    @Override public ChannelRole[] requiredRoles() { return required.clone(); }
    @Override public ChannelRole[] contextRoles() { return context.clone(); }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) { return inputs; }
    @Override public String accumulationPlan() { return accumulation; }
    @Override public String reviewOutputs() { return lastReview; }

    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        GuidedTaskSettingsDraft baseline = GuidedWorkingTuneSurfaceCache.ensureBaseline(recipe, snapshot);
        String surface = baseline == null
                ? GuidedWorkingTuneSurfaceCache.statusFor(recipe)
                : GuidedWorkingTuneSurfaceCache.statusFor(recipe);
        if (snapshot == null) return "Working tune not read yet. " + surface;
        return surface
                + " | TPS AE " + enabled(snapshot.isTpsAeEnabled())
                + " | Wall " + enabled(snapshot.isWallWettingEnabled())
                + " | Instant " + enabled(snapshot.isExtraShotEnabled())
                + " | MAP Predict " + enabled(snapshot.isMapEstimateEnabled());
    }

    @Override public boolean activityObserved(LiveSample sample) {
        if (sample == null) return false;
        if (recipe == GuidedTuningRecipe.WALL_WETTING_ADVANCED
                || recipe == GuidedTuningRecipe.WALL_WETTING_VALIDATION) {
            return nonZero(sample, ChannelRole.WALL_CORRECTION)
                    || nonZero(sample, ChannelRole.WALL_WETTING_PW)
                    || Math.abs(sample.getTpsDot()) > 1.0;
        }
        if (recipe == GuidedTuningRecipe.INSTANT_FUEL_SETUP) {
            double smooth = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
            double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
            return sample.bool(ChannelRole.AE_EVENT_JUST_OCCURRED)
                    || positive(sample, ChannelRole.INSTANT_PULSE_PW)
                    || (Double.isFinite(smooth) && Double.isFinite(threshold)
                        && smooth > threshold);
        }
        if (recipe == GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH
                || recipe == GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS) {
            return positive(sample, ChannelRole.INSTANT_PULSE_PW)
                    || sample.bool(ChannelRole.AE_EVENT_JUST_OCCURRED);
        }
        return sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)
                || positive(sample, ChannelRole.AE_ADD_MS)
                || Math.abs(sample.getTpsDot()) > 1.0;
    }

    @Override public ProposalWritePlan reviewedWritePlan(
            AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        BaselineRecommendationCacheGuard.prepare(recipe, snapshot, evidence);
        BaselineSurfaceRecommendation.Result result =
                BaselineSurfaceRecommendation.evaluate(recipe, snapshot, evidence);
        lastReview = outputs + "\n\n" + result.reviewText;
        return result.plan;
    }

    private static ChannelRole[] roles(ChannelRole... roles) { return roles; }
}
