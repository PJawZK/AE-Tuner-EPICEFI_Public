package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTaskFocusCatalog;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.ChannelRole;

import java.util.HashSet;
import java.util.Set;

public final class GuidedAeMethodModulesRegressionTest {
    private GuidedAeMethodModulesRegressionTest() { }

    public static void main(String[] args) {
        everySelectorEntryHasOneModule();
        implementedAeMethodsUseSeparatedImplementationClasses();
        plannedTasksUseNonFunctionalScaffolds();
        engagementDetectionDeclaresPassiveTimingEvidence();
        foundationThresholdDeclaresSensitivityEvidence();
        FoundationThresholdRecommendationRegressionTest.main(new String[0]);
        mapPredictCapturesTriggerAndCoherenceEvidence();
        mapEstimateDeclaresSteadyCellEvidence();
        wallWettingDeclaresLambdaAndOverlapEvidence();
        tpsAeDeclaresTableAndCycleEvidence();
        instantFuelDeclaresEarlyPulseEvidence();
        activeEvidenceMethodsPreserveTheirCaptureRoutes();
        ignitionRemainsOutsideTuningTaskAuthority();
        System.out.println("GuidedAeMethodModulesRegressionTest passed");
    }

    private static void everySelectorEntryHasOneModule() {
        for (GuidedTuningRecipe recipe : GuidedTuningRecipe.values()) {
            GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(recipe);
            require(module != null && module.recipe() == recipe,
                    "selector entry is not routed to exactly one method module: " + recipe);
        }
    }

    private static void implementedAeMethodsUseSeparatedImplementationClasses() {
        Set<String> classes = new HashSet<String>();
        for (GuidedTuningRecipe recipe : GuidedTuningRecipe.values()) {
            if (!recipe.implemented) continue;
            GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(recipe);
            require(classes.add(module.getClass().getName()),
                    "two implemented AE methods share the same implementation class: " + recipe);
        }
    }

    private static void plannedTasksUseNonFunctionalScaffolds() {
        for (GuidedTuningRecipe recipe : GuidedTuningRecipe.values()) {
            if (recipe.implemented || recipe == GuidedTuningRecipe.OPTIMIZATION) continue;
            GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(recipe);
            require(module instanceof PlannedGuidedTaskModule,
                    "planned selector entry did not route through explicit scaffold module: " + recipe);
            require(module.captureMode() == GuidedAeMethodModule.CaptureMode.ARCHITECTURE_ONLY,
                    "planned task unexpectedly gained a live capture path: " + recipe);
            require(module.requiredRoles().length == 0 && module.contextRoles().length == 0,
                    "planned task pretends to have validated evidence requirements: " + recipe);
            require(module.reviewedWritePlan(null, java.util.Collections.emptyList()) == null
                            && module.explicitSettingWritePlan(null) == null,
                    "planned task unexpectedly created write authority: " + recipe);
            require(GuidedTaskFocusCatalog.controlsText(recipe).length() > 20,
                    "planned task omitted the known current EpicEFI control family: " + recipe);
        }
    }

    private static void engagementDetectionDeclaresPassiveTimingEvidence() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(
                GuidedTuningRecipe.ENGAGEMENT_DETECTION);
        requireAll(module.requiredRoles(), ChannelRole.RPM, ChannelRole.TPS,
                ChannelRole.ACCEL_THRESHOLD, ChannelRole.AE_ABOVE_THRESHOLD);
        requireAll(module.contextRoles(), ChannelRole.DELTA_TPS,
                ChannelRole.AE_DELTA_NEWEST_PAIR, ChannelRole.AE_WINDOW_MS,
                ChannelRole.AE_WINDOW_SAMPLES, ChannelRole.AE_DELTA_STRIDE,
                ChannelRole.TPS_DECEL_ACTIVE, ChannelRole.SMOOTHED_DELTA_TPS,
                ChannelRole.TPS_AE_CYCLE_CNT, ChannelRole.MAP_PRED_ACTIVE);
        String setup = module.setupGuidance();
        String accumulation = module.accumulationPlan();
        String review = module.reviewOutputs();
        require(setup.contains("There is no exact TPS target"),
                "Foundation 1 setup revived an exact TPS target");
        require(setup.contains("presentation-only visual reference marker"),
                "Foundation 1 setup lost the visual-reference-not-acceptance boundary");
        require(setup.contains("provisional evidence")
                        && setup.contains("guarded Apply is withheld")
                        && setup.contains("no fixed 2000 RPM target"),
                "Foundation 1 setup lost provisional idle versus representative-operating-range Apply semantics");
        require(setup.contains("No temporary timing write")
                        && setup.contains("Sample Length remains a history-capacity constraint"),
                "Foundation 1 setup lost the read-only timing/history-capacity contract");
        require(accumulation.contains("median and MAD")
                        && accumulation.contains("bounded Delta Window candidate set"),
                "Foundation 1 accumulation lost robust clustering or internal candidate scoring");
        require(accumulation.contains("tied/near-tied")
                        && accumulation.contains("Final confirmation requires several comparable openings")
                        && accumulation.contains("pre-event operating RPM spread")
                        && accumulation.contains("not a fixed RPM target or a working VSS channel")
                        && accumulation.contains("No controller writes occur during capture"),
                "Foundation 1 accumulation lost ambiguity withholding, operating-range confirmation or no-write safety");
        require(review.length() > 0
                        && module.currentTuneContext(null).contains("passive capture"),
                "Foundation 1 omitted passive review/current-tune context");
        require(module.explicitSettingWritePlan(null) == null,
                "passive Foundation 1 exposed a pre-capture/manual write plan");
    }

    private static void foundationThresholdDeclaresSensitivityEvidence() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(
                GuidedTuningRecipe.FOUNDATION_THRESHOLD);
        require(module instanceof FoundationThresholdMethodModule,
                "Threshold / Sensitivity did not route to its dedicated evidence module");
        requireAll(module.requiredRoles(), ChannelRole.RPM, ChannelRole.TPS,
                ChannelRole.DELTA_TPS, ChannelRole.ACCEL_THRESHOLD);
        requireAll(module.contextRoles(), ChannelRole.SMOOTHED_DELTA_TPS,
                ChannelRole.AE_ABOVE_THRESHOLD, ChannelRole.AE_DELTA_NEWEST_PAIR,
                ChannelRole.AE_WINDOW_MS, ChannelRole.AE_WINDOW_SAMPLES,
                ChannelRole.AE_DELTA_STRIDE);
        String operator = module.operatorInputs(null);
        String accumulation = module.accumulationPlan();
        String setup = module.setupGuidance();
        String review = module.reviewOutputs();
        require(operator.contains("steady pedal until Guided Focus says the quiet calibration is LOCKED")
                        && operator.contains("NORMAL CORRECTION =")
                        && operator.contains("ACCELERATION OPENING =")
                        && operator.contains("80 qualifying quiet samples")
                        && operator.contains("1.5 continuous seconds")
                        && accumulation.contains("80 qualifying quiet samples spanning 1.5 continuous seconds freeze")
                        && accumulation.contains("Whole physical opening events")
                        && accumulation.contains("locks after conservative 3+3 effective-space separation")
                        && review.contains("frozen quiet calibration")
                        && review.contains("locked effective-space separation")
                        && review.contains("no ProposalWritePlan"),
                "Threshold / Sensitivity contract omitted physical maneuver language, time-qualified calibration, locked separation or guarded recommendation boundary");
        require(!operator.contains("ORDINARY") && !operator.contains("DELIBERATE")
                        && !setup.contains("ordinary") && !setup.contains("deliberate"),
                "Foundation 2 user-facing method contract leaked legacy Ordinary/Deliberate terminology");
        require(module.explicitSettingWritePlan(null) == null
                        && module.reviewedWritePlan(null, java.util.Collections.emptyList()) == null,
                "Threshold / Sensitivity created a plan without a working-tune baseline and evidence");
    }

    private static void mapPredictCapturesTriggerAndCoherenceEvidence() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT);
        requireAll(module.requiredRoles(),
                ChannelRole.MAP, ChannelRole.FALLBACK_MAP, ChannelRole.EFFECTIVE_MAP,
                ChannelRole.MAP_PRED_ACTIVE, ChannelRole.SMOOTHED_DELTA_TPS,
                ChannelRole.ACCEL_THRESHOLD, ChannelRole.MAP_PRED_RESET_CNT,
                ChannelRole.MAP_PRED_EVENT_OVER);
        requireAll(module.contextRoles(), ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA,
                ChannelRole.WALL_WETTING_PW, ChannelRole.INSTANT_PULSE_PW,
                ChannelRole.GEAR, ChannelRole.VSS);
        require(module.accumulationPlan().contains("smoothedDeltaTps")
                        && module.reviewOutputs().contains("Effective MAP"),
                "MAP Predict user contract does not explain trigger/coherence accumulation");
    }

    private static void mapEstimateDeclaresSteadyCellEvidence() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE);
        requireAll(module.requiredRoles(), ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                ChannelRole.MAP_PRED_ACTIVE, ChannelRole.AE_ABOVE_THRESHOLD,
                ChannelRole.INSTANT_PULSE_PW, ChannelRole.DFCO, ChannelRole.FUEL_CUT);
        require(module.accumulationPlan().contains("25 Hz")
                        && module.accumulationPlan().contains("5 kPa standard deviation")
                        && module.reviewOutputs().contains("paste-ready MAP Estimate table"),
                "MAP Estimate user contract does not describe stable-cell accumulation/draft output");
    }

    private static void wallWettingDeclaresLambdaAndOverlapEvidence() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.WALL_WETTING);
        requireAll(module.requiredRoles(), ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA,
                ChannelRole.PW, ChannelRole.WALL_CORRECTION, ChannelRole.WALL_WETTING_PW);
        requireAll(module.contextRoles(), ChannelRole.AE_ADD_MS,
                ChannelRole.INSTANT_PULSE_PW, ChannelRole.MAP_PRED_ACTIVE);
        require(module.operatorInputs(null).contains("tip-ins and tip-outs"),
                "Wall Wetting operator plan omitted balanced transient direction");
    }

    private static void tpsAeDeclaresTableAndCycleEvidence() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.TPS_AE);
        requireAll(module.requiredRoles(), ChannelRole.TPS_FROM, ChannelRole.TPS_TO,
                ChannelRole.DELTA_TPS, ChannelRole.AE_ADD_MS, ChannelRole.EXTRA_FUEL,
                ChannelRole.TPS_AE_CYCLE_MULT, ChannelRole.TPS_AE_CYCLE_CNT,
                ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA);
        require(module.operatorInputs(null).contains("ending TPS")
                        && module.reviewOutputs().contains("cycle multiplier/count"),
                "TPS AE user contract omitted table-row/cycle evidence");
    }

    private static void instantFuelDeclaresEarlyPulseEvidence() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.INSTANT_FUEL);
        requireAll(module.requiredRoles(), ChannelRole.AE_EVENT_JUST_OCCURRED,
                ChannelRole.INSTANT_PULSE_PW, ChannelRole.INSTANT_PULSE_CNT,
                ChannelRole.LAMBDA, ChannelRole.TARGET_LAMBDA,
                ChannelRole.SMOOTHED_DELTA_TPS, ChannelRole.ACCEL_THRESHOLD);
        require(module.setupGuidance().contains("residual early lean hole")
                        && module.reviewOutputs().contains("overlap counts"),
                "Instant Fuel residual-validation contract does not preserve residual-lean/overlap boundary");
    }

    private static void activeEvidenceMethodsPreserveTheirCaptureRoutes() {
        GuidedTuningRecipe[] probes = new GuidedTuningRecipe[]{
                GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                GuidedTuningRecipe.MAP_PREDICT,
                GuidedTuningRecipe.MAP_ESTIMATE,
                GuidedTuningRecipe.WALL_WETTING,
                GuidedTuningRecipe.TPS_AE,
                GuidedTuningRecipe.INSTANT_FUEL
        };
        for (GuidedTuningRecipe recipe : probes) {
            require(recipe.implemented, "active AE method selector entry is not active: " + recipe);
            GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(recipe);
            require(module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE,
                    "AE evidence route changed capture type unexpectedly: " + recipe);
            require(module.requiredRoles().length > 0,
                    "active AE method does not declare required evidence: " + recipe);
            require(module.operatorInputs(null).length() > 0
                            && module.accumulationPlan().length() > 0
                            && module.reviewOutputs().length() > 0,
                    "active AE method does not expose a complete user-facing data contract: " + recipe);
        }
        require(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.BLEND_DURATION).captureMode()
                        == GuidedAeMethodModule.CaptureMode.BLEND_DURATION,
                "existing Blend Duration controlled capture path was not preserved");
    }

    private static void ignitionRemainsOutsideTuningTaskAuthority() {
        for (GuidedTuningRecipe recipe : GuidedTuningRecipe.values()) {
            require(!recipe.displayName.toLowerCase().contains("ignition retard"),
                    "transient ignition retard leaked into AE Tuner tuning authority");
        }
        String review = GuidedTaskFocusCatalog.focusText(
                GuidedTuningRecipe.FINAL_SIMPLIFICATION, "test context");
        require(review.contains("ignition") && review.contains("observation/quality context"),
                "Guided Focus did not preserve ignition as read-only/confounder context");
    }

    private static void requireAll(ChannelRole[] actual, ChannelRole... expected) {
        Set<ChannelRole> roles = new HashSet<ChannelRole>();
        for (ChannelRole role : actual) roles.add(role);
        for (ChannelRole role : expected) {
            require(roles.contains(role), "method evidence contract omitted " + role);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
