package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.passive.MapEstimateCollector;
import se.anders.tunerstudio.aetuner.proposal.AeTableSuggestion;
import se.anders.tunerstudio.aetuner.proposal.MapEstimateSuggestion;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import se.anders.tunerstudio.aetuner.guided.mapestimate.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Shared evidence-capture shell for Guided AE methods.
 *
 * Capture itself never mutates the working tune. After Finish/Review, any method
 * may expose an immutable ProposalWritePlan through its module contract. The
 * caller may then pass that plan to the common ProposalApplyCoordinator for
 * stale-check, exact write, readback and Restore. A null plan means the current
 * evidence/tuning logic does not call for a change; it is not a separate
 * read-only product state. Burn remains outside this path entirely.
 */
final class GuidedMethodProbeSession {
    private static final int MAX_SAMPLES = 6000;
    private static final double ACTIVITY_QUIET_SECONDS = 0.20;
    private static final double ENGAGEMENT_CLEAR_SECONDS = 0.15;

    private GuidedAeMethodModule module;
    private GuidedCaptureState state = GuidedCaptureState.IDLE;
    private final List<LiveSample> samples = new ArrayList<LiveSample>();
    private final EnumMap<ChannelRole, Integer> finiteCounts =
            new EnumMap<ChannelRole, Integer>(ChannelRole.class);
    private final MapEstimateCollector mapEstimateCollector = new MapEstimateCollector();
    private final MapEstimateGuidedController mapEstimateGuided =
            new MapEstimateGuidedController(MapEstimateMemoryPaths.store());
    private AeProjectSnapshot mapEstimateConfiguredSnapshot;
    private boolean mapEstimateWorkingTuneReadRequired;
    private GuidedWorkflowEvent.Listener workflowEvents = GuidedWorkflowEvent.NONE;
    private final GuidedTpsAeEventAccumulator tpsAeEvents = new GuidedTpsAeEventAccumulator();
    private AeProjectSnapshot projectSnapshot;
    private int activitySamples;
    private int activityEvents;
    private int completeRequiredSamples;
    private int droppedSamples;
    private int targetActivityEvents = 5;
    private int mapMinimumSamples = 20;
    private double mapCapKpa = 115.0;
    private boolean activityLatched;
    private double lastActivitySeconds = Double.NaN;
    private double firstSeconds = Double.NaN;
    private double lastSeconds = Double.NaN;
    private boolean engagementReadyAnnounced;
    private boolean engagementDetectorLatched;
    private double engagementLastActiveSeconds = Double.NaN;

    GuidedMethodProbeSession() {
        GuidedFocusHub.setMapEstimateConfigurationListener(
                new MapEstimateGuidedFocusPanel.ConfigurationListener() {
                    @Override public void onStrategyRequested(MapEstimateCoverageStrategy strategy) {
                        synchronized (GuidedMethodProbeSession.this) {
                            if (!mapEstimateGuided.configured() || mapEstimateGuided.active()) return;
                            mapEstimateGuided.setPendingStrategy(strategy);
                            publishMapEstimateFocus(null);
                        }
                    }
                    @Override public void onScopeRequested(MapEstimateCellScope scope) {
                        synchronized (GuidedMethodProbeSession.this) {
                            if (!mapEstimateGuided.configured() || mapEstimateGuided.active()) return;
                            mapEstimateGuided.setPendingScope(scope);
                            publishMapEstimateFocus(null);
                        }
                    }
                    @Override public void onEvidenceBasisRequested(MapEstimateEvidenceBasis basis) {
                        synchronized (GuidedMethodProbeSession.this) {
                            if (!mapEstimateGuided.configured() || mapEstimateGuided.active()) return;
                            mapEstimateGuided.setPendingEvidenceBasis(basis);
                            publishMapEstimateFocus(null);
                        }
                    }
                    @Override public void onProposalLimitPolicyRequested(MapEstimateProposalLimitPolicy policy) {
                        synchronized (GuidedMethodProbeSession.this) {
                            if (!mapEstimateGuided.configured() || mapEstimateGuided.active()) return;
                            mapEstimateGuided.setPendingProposalLimitPolicy(policy);
                            publishMapEstimateFocus(null);
                        }
                    }
                });
    }

    synchronized void setWorkflowEventListener(GuidedWorkflowEvent.Listener listener) {
        workflowEvents = listener == null ? GuidedWorkflowEvent.NONE : listener;
    }

    synchronized void configureMapEstimateFocus(AeProjectSnapshot snapshot,
                                                int minimumSamples,
                                                double capKpa) {
        if (snapshot == null || !snapshot.hasMapEstimateTable() || mapEstimateGuided.active()) return;
        if (!mapEstimateGuided.configured() || mapEstimateConfiguredSnapshot != snapshot) {
            mapEstimateGuided.configure(snapshot.getConfigurationName(),
                    snapshot.getMapEstimateTpsBins(), snapshot.getMapEstimateRpmBins(),
                    snapshot.getMapEstimateTable(), minimumSamples, capKpa);
            mapEstimateConfiguredSnapshot = snapshot;
        } else {
            mapEstimateGuided.updateReviewSettings(minimumSamples, capKpa);
        }
        publishMapEstimateFocus(null);
    }

    synchronized void updateMapEstimateReviewSettings(int minimumSamples, double capKpa) {
        if (!mapEstimateGuided.configured() || mapEstimateGuided.active()) return;
        mapEstimateGuided.updateReviewSettings(minimumSamples, capKpa);
        publishMapEstimateFocus(null);
    }

    synchronized boolean mapEstimateWorkingTuneReadRequired() {
        return mapEstimateWorkingTuneReadRequired;
    }

    synchronized void noteWorkingTuneRead(AeProjectSnapshot snapshot,
                                          int minimumSamples,
                                          double capKpa) {
        if (snapshot != null && snapshot.hasMapEstimateTable()) {
            mapEstimateWorkingTuneReadRequired = false;
            configureMapEstimateFocus(snapshot, minimumSamples, capKpa);
        }
    }

    synchronized void markMapEstimateWorkingTuneChanged() {
        mapEstimateWorkingTuneReadRequired = true;
    }

    synchronized void start(GuidedAeMethodModule selected) {
        start(selected, null, 5, 20, 115.0);
    }

    synchronized void start(GuidedAeMethodModule selected,
                            AeProjectSnapshot snapshot,
                            int targetEvents,
                            int minimumMapSamples,
                            double mapCap) {
        if (selected == null || selected.captureMode()
                != GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
            throw new IllegalArgumentException("Probe capture requires a READ_ONLY_PROBE method module");
        }
        boolean append = module == selected && state == GuidedCaptureState.COMPLETE
                && !samples.isEmpty();
        if (!append) reset();
        module = selected;
        projectSnapshot = snapshot;
        targetActivityEvents = Math.max(1, targetEvents);
        mapMinimumSamples = Math.max(3, minimumMapSamples);
        mapCapKpa = Math.max(90.0, Math.min(180.0, mapCap));
        if (selected.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            if (mapEstimateWorkingTuneReadRequired) {
                state = GuidedCaptureState.IDLE;
                publishFocusState(null);
                return;
            }
            configureMapEstimateFocus(snapshot, mapMinimumSamples, mapCapKpa);
            mapEstimateCollector.clear();
            mapEstimateCollector.configure(snapshot);
            mapEstimateGuided.start();
        }
        if (selected.recipe() == GuidedTuningRecipe.TPS_AE) {
            if (append) tpsAeEvents.resume();
            else tpsAeEvents.reset();
        }
        activityLatched = false;
        lastActivitySeconds = Double.NaN;
        engagementReadyAnnounced = false;
        engagementDetectorLatched = false;
        engagementLastActiveSeconds = Double.NaN;
        state = GuidedCaptureState.CAPTURING;
        workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.SESSION_STARTED,
                selected.recipe().displayName + " capture started", System.nanoTime());
        publishFocusState(null);
    }

    synchronized void accept(LiveSample sample) {
        if (state != GuidedCaptureState.CAPTURING || sample == null || module == null) return;
        if (!Double.isFinite(firstSeconds)) firstSeconds = sample.getSeconds();
        lastSeconds = sample.getSeconds();
        if (samples.size() >= MAX_SAMPLES) {
            samples.remove(0);
            droppedSamples++;
        }
        samples.add(sample);

        boolean requiredComplete = allFinite(sample, module.requiredRoles());
        if (requiredComplete) completeRequiredSamples++;

        boolean active = module.activityObserved(sample);
        if (active) {
            activitySamples++;
            if (!activityLatched && (!Double.isFinite(lastActivitySeconds)
                    || sample.getSeconds() - lastActivitySeconds >= ACTIVITY_QUIET_SECONDS)) {
                activityEvents++;
            }
            activityLatched = true;
            lastActivitySeconds = sample.getSeconds();
        } else if (activityLatched && Double.isFinite(lastActivitySeconds)
                && sample.getSeconds() - lastActivitySeconds >= ACTIVITY_QUIET_SECONDS) {
            activityLatched = false;
        }

        for (ChannelRole role : module.probeRoles()) {
            double value = sample.get(role);
            if (Double.isFinite(value)) {
                Integer count = finiteCounts.get(role);
                finiteCounts.put(role, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
        }

        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            if (requiredComplete) {
                boolean stableAccepted = mapEstimateCollector.addSample(sample);
                if (stableAccepted) {
                    mapEstimateGuided.acceptStable(
                            sample.get(ChannelRole.TPS), sample.get(ChannelRole.RPM),
                            sample.get(ChannelRole.MAP), sample.get(ChannelRole.COOLANT),
                            sample.get(ChannelRole.IAT));
                }
            } else {
                mapEstimateCollector.pauseForIncompleteRequiredData(sample);
            }
        } else if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            tpsAeEvents.accept(sample);
        } else if (module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            updateEngagementCueState(sample, requiredComplete);
        }
        publishFocusState(sample);
    }

    synchronized void togglePause() {
        boolean pausing = state == GuidedCaptureState.CAPTURING;
        if (module != null && module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE
                && mapEstimateGuided.configured()) {
            mapEstimateGuided.togglePause();
        }
        if (pausing) {
            state = GuidedCaptureState.PAUSED;
            workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.PAUSED,
                    module == null ? "Guided method capture paused" : module.recipe().displayName + " paused",
                    System.nanoTime());
        } else if (state == GuidedCaptureState.PAUSED) {
            state = GuidedCaptureState.CAPTURING;
        }
        publishFocusState(null);
    }

    synchronized void finish() {
        if (state == GuidedCaptureState.CAPTURING || state == GuidedCaptureState.PAUSED) {
            if (module != null && module.recipe() == GuidedTuningRecipe.TPS_AE) {
                tpsAeEvents.finish();
            } else if (module != null && module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE
                    && mapEstimateGuided.configured()) {
                mapEstimateGuided.finish();
            }
            state = GuidedCaptureState.COMPLETE;
            activityLatched = false;
            engagementDetectorLatched = false;
            workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.SERIES_COMPLETE,
                    module == null ? "Guided method capture complete" : module.recipe().displayName + " review ready",
                    System.nanoTime());
            publishFocusState(null);
        }
    }

    synchronized void reset() {
        module = null;
        projectSnapshot = null;
        state = GuidedCaptureState.IDLE;
        samples.clear();
        finiteCounts.clear();
        mapEstimateCollector.clear();
        if (mapEstimateGuided.configured()) mapEstimateGuided.resetCurrentCapture();
        tpsAeEvents.reset();
        activitySamples = 0;
        activityEvents = 0;
        completeRequiredSamples = 0;
        droppedSamples = 0;
        targetActivityEvents = 5;
        mapMinimumSamples = 20;
        mapCapKpa = 115.0;
        activityLatched = false;
        lastActivitySeconds = Double.NaN;
        firstSeconds = Double.NaN;
        lastSeconds = Double.NaN;
        engagementReadyAnnounced = false;
        engagementDetectorLatched = false;
        engagementLastActiveSeconds = Double.NaN;
        GuidedFocusHub.clear();
    }

    synchronized int sampleCount() { return samples.size(); }
    synchronized int observedSampleCount() { return samples.size() + droppedSamples; }
    synchronized int activitySampleCount() { return activitySamples; }
    synchronized int activityEventCount() { return activityEvents; }
    synchronized int completeRequiredSampleCount() { return completeRequiredSamples; }
    synchronized int tpsAeTableEventCount() { return tpsAeEvents.eventCount(); }
    synchronized int tpsAeFuelProvedEventCount() { return tpsAeEvents.fuelProvedEventCount(); }
    synchronized boolean hasEvidence() { return !samples.isEmpty(); }
    synchronized GuidedCaptureState state() { return state; }
    synchronized GuidedAeMethodModule module() { return module; }
    synchronized MapEstimateEvidenceBasis mapEstimatePendingEvidenceBasisForTest() {
        return mapEstimateGuided.pendingEvidenceBasis();
    }
    synchronized MapEstimateProposalLimitPolicy mapEstimatePendingProposalLimitForTest() {
        return mapEstimateGuided.pendingProposalLimitPolicy();
    }
    synchronized long mapEstimateCollectorAcceptedForTest() {
        return mapEstimateCollector.getAcceptedSamples();
    }

    synchronized MapEstimateFocusSnapshot mapEstimateFocusSnapshot(LiveSample latest) {
        if (projectSnapshot == null) {
            return MapEstimateFocusSnapshot.empty(mapMinimumSamples);
        }
        if (module == null || module.recipe() != GuidedTuningRecipe.MAP_ESTIMATE) {
            return MapEstimateFocusSnapshot.setup(projectSnapshot, mapMinimumSamples, latest);
        }
        return MapEstimateFocusSnapshot.fromCollector(
                projectSnapshot, mapEstimateCollector, mapMinimumSamples, latest);
    }

    private void publishFocusState(LiveSample latest) {
        if (module == null) return;
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            publishMapEstimateFocus(latest);
        } else if (module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            GuidedFocusHub.publishEngagement(state,
                    EngagementFocusModel.build(projectSnapshot, latest, state,
                            activityEvents, targetActivityEvents,
                            observedSampleCount(), completeRequiredSamples),
                    module.captureGoal() + "\n\n" + module.operatorInputs(projectSnapshot));
        } else {
            GuidedFocusHub.publish(module.recipe(), state, (MapEstimateFocusModel) null,
                    module.setupGuidance() + "\n\n" + module.operatorInputs(projectSnapshot));
        }
    }

    private void publishMapEstimateFocus(LiveSample latest) {
        if (!mapEstimateGuided.configured()) return;
        double liveTps = latest == null ? mapEstimateCollector.getLastLiveTps()
                : latest.get(ChannelRole.TPS);
        double liveRpm = latest == null ? mapEstimateCollector.getLastLiveRpm()
                : latest.get(ChannelRole.RPM);
        String eligibility = mapEstimateCollector.getLastEligibility().getDisplayText();
        GuidedFocusHub.publish(GuidedTuningRecipe.MAP_ESTIMATE, state,
                mapEstimateGuided.focus(liveTps, liveRpm, eligibility),
                "MAP Estimate Table learns stable measured MAP across drives. "
                + "Interpolated Coverage is the default; Direct Fine Tune restricts capture/proposal authority to selected cells. "
                + "Evidence basis and proposal limit are independent experiment controls and lock when capture starts.");
    }

    private void updateEngagementCueState(LiveSample sample, boolean requiredComplete) {
        if (!requiredComplete || projectSnapshot == null || sample == null) return;
        double selected = EngagementFocusModel.selectedDetectorOutput(projectSnapshot, sample);
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        if (!Double.isFinite(selected) || !Double.isFinite(threshold)) return;

        if (!engagementReadyAnnounced && selected <= threshold) {
            engagementReadyAnnounced = true;
            workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.READY_ENTERED,
                    "Detector diagnostics ready; selected detector is below AccelThreshold",
                    System.nanoTime());
        }

        if (selected > threshold) {
            if (!engagementDetectorLatched) {
                workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.TARGET_ACQUIRED,
                        "Selected detector crossed AccelThreshold",
                        System.nanoTime());
            }
            engagementDetectorLatched = true;
            engagementLastActiveSeconds = sample.getSeconds();
            return;
        }

        if (engagementDetectorLatched
                && Double.isFinite(engagementLastActiveSeconds)
                && sample.getSeconds() - engagementLastActiveSeconds >= ENGAGEMENT_CLEAR_SECONDS) {
            engagementDetectorLatched = false;
            workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.RETURN_TO_BASELINE,
                    "Selected detector cleared below AccelThreshold after the event",
                    System.nanoTime());
        }
    }

    synchronized GuidedSessionSnapshot snapshot() {
        if (module == null) {
            return new GuidedSessionSnapshot(GuidedCaptureState.IDLE,
                    "SETUP", "Choose an AE method from the selector.",
                    "No method capture is active.",
                    "No method evidence collected.", 0, "");
        }
        String headline;
        String instruction;
        if (state == GuidedCaptureState.COMPLETE) {
            headline = "REVIEW — " + module.recipe().displayName;
            instruction = "Capture complete. Review required-channel readiness, accumulated method evidence and any generated proposal/draft before exporting, applying, or continuing capture.";
        } else if (state == GuidedCaptureState.PAUSED) {
            headline = "PAUSED — " + module.recipe().displayName;
            instruction = "Capture is paused; resume when ready.";
        } else {
            headline = "CAPTURE — " + module.recipe().displayName;
            instruction = module.captureGoal();
        }
        return new GuidedSessionSnapshot(state, headline, instruction,
                coverageText(), resultText(), activityEvents, "");
    }

    synchronized String coverageText() {
        if (module == null) return "No method selected.";
        StringBuilder out = new StringBuilder();
        int observedSamples = observedSampleCount();
        out.append("METHOD EVIDENCE ACCUMULATION\n")
                .append("Method: ").append(module.recipe().displayName).append('\n')
                .append("Samples observed: ").append(observedSamples).append('\n')
                .append("Samples retained in export window: ").append(samples.size()).append('\n')
                .append("Samples with every REQUIRED channel present (observed): ")
                .append(completeRequiredSamples).append('/').append(observedSamples).append('\n');
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            out.append("Stable-cell accumulation does not use transient activity-event counting.\n");
        } else {
            out.append("Distinct method-activity events: ").append(activityEvents)
                    .append('/').append(targetActivityEvents)
                    .append(" | activity samples observed: ").append(activitySamples).append('\n');
        }
        out.append("Dropped by probe retention cap: ").append(droppedSamples).append('\n');
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            out.append(mapEstimateCollector.statusText(mapMinimumSamples)).append('\n');
        } else if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            out.append("Completed TPS AE table-analysis windows: ")
                    .append(tpsAeEvents.eventCount())
                    .append(" | fuel-proved: ").append(tpsAeEvents.fuelProvedEventCount()).append('\n');
        }
        out.append("\nREQUIRED CHANNELS\n");
        appendCoverage(out, module.requiredRoles(), true);
        out.append("\nCONTEXT / ATTRIBUTION CHANNELS\n");
        appendCoverage(out, module.contextRoles(), false);
        out.append("\nCapture never writes ECU parameters. After Finish/Review, an explicit reviewed proposal may use guarded Apply/readback/Restore. No automatic Apply and no burn.");
        return out.toString();
    }

    synchronized String resultText() {
        if (module == null) return "No method capture active.";
        double duration = Double.isFinite(firstSeconds) && Double.isFinite(lastSeconds)
                ? Math.max(0.0, lastSeconds - firstSeconds) : 0.0;
        StringBuilder out = new StringBuilder();
        int observedSamples = observedSampleCount();
        out.append(module.recipe().displayName).append(" evidence\n")
                .append("Observed coherent samples: ").append(observedSamples).append('\n')
                .append("Retained coherent samples: ").append(samples.size()).append('\n')
                .append("Required-complete observed samples: ").append(completeRequiredSamples)
                .append('/').append(observedSamples).append('\n');
        if (module.recipe() != GuidedTuningRecipe.MAP_ESTIMATE) {
            out.append("Method activity: ").append(activityEvents).append(" event(s), ")
                    .append(activitySamples).append(" active observed sample(s)\n");
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            out.append("TPS AE table-analysis windows: ").append(tpsAeEvents.eventCount())
                    .append(" completed / ").append(tpsAeEvents.fuelProvedEventCount())
                    .append(" fuel-proved\n");
        }
        out.append("Observed duration: ").append(f3(duration)).append(" s\n")
                .append("Status: ").append(state.name()).append("\n\n")
                .append(methodMetricsText());
        return out.toString();
    }

    synchronized String reviewText() {
        if (module == null) return "No method capture active.";
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            return mapEstimateGuided.configured()
                    ? mapEstimateGuided.reviewText()
                    : "MAP Estimate Table is not configured; Read Working Tune first.";
        }

        ProposalWritePlan plan = reviewedWritePlan();
        String proposalState = plan == null
                ? "No supported setting/value change is currently proposed. No automatic Apply and no burn."
                : plan.reviewText()
                    + "\n\nGuarded working-tune Apply/readback/Restore is available for this reviewed plan. No burn.";

        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            AeTableSuggestion suggestion = AeTableSuggestion.build(
                    projectSnapshot, tpsAeEvents.eventsSnapshot());
            return "TPS AE TABLE REVIEW\n"
                    + suggestion.getDisplayText()
                    + "\n\nCURRENT CAPTURE METRICS\n" + methodMetricsText()
                    + (suggestion.isAvailable()
                    ? "\n\nPaste-ready TPS AE draft is available through Copy Reviewed Draft and the Guided export."
                    : "\n\nNo paste-ready TPS AE draft yet; continue repeated fuel-proved events in the same TPS-to rows.")
                    + "\n\n" + proposalState;
        }
        return "METHOD REVIEW OUTPUTS\n" + module.reviewOutputs()
                + "\n\nCURRENT CAPTURE METRICS\n" + methodMetricsText()
                + "\n\n" + proposalState;
    }

    synchronized String copyPasteBlock() {
        if (module == null) return "";
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            return mapEstimateGuided.configured()
                    ? mapEstimateGuided.reviewedCopyPasteBlock() : "";
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            AeTableSuggestion suggestion = AeTableSuggestion.build(
                    projectSnapshot, tpsAeEvents.eventsSnapshot());
            return suggestion.isAvailable() ? suggestion.getCopyPasteBlock() : "";
        }
        return "";
    }

    synchronized ProposalWritePlan reviewedWritePlan() {
        if (module == null || state != GuidedCaptureState.COMPLETE) return null;
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            return mapEstimateGuided.configured()
                    ? mapEstimateGuided.reviewedWritePlan() : null;
        }
        return module.reviewedWritePlan(projectSnapshot,
                Collections.unmodifiableList(new ArrayList<LiveSample>(samples)));
    }

    synchronized String reportText(String pluginVersion) {
        StringBuilder out = new StringBuilder();
        out.append("AE Tuner Guided method report\n")
                .append("Plugin version: ").append(pluginVersion).append('\n')
                .append("Method: ").append(module == null ? "none" : module.recipe().displayName).append('\n')
                .append("Capture boundary: capture itself never writes. After Finish/Review, any explicit ProposalWritePlan may use the common guarded Apply/readback/Restore gateway. No automatic Apply and no burn.\n\n");
        if (module != null) {
            out.append("OPERATOR INPUTS\n").append(module.operatorInputs(projectSnapshot)).append("\n\n")
                    .append("ACCUMULATION PLAN\n").append(module.accumulationPlan()).append("\n\n")
                    .append("CURRENT TUNE CONTEXT\n").append(module.currentTuneContext(projectSnapshot)).append("\n\n");
        }
        out.append(coverageText()).append("\n\n")
                .append(resultText()).append("\n\n")
                .append(reviewText()).append('\n');
        String draft = copyPasteBlock();
        if (draft.length() > 0) {
            out.append("\nPASTE-READY DRAFT\n=================\n").append(draft).append('\n');
        }
        return out.toString();
    }

    synchronized String csvText() {
        StringBuilder out = new StringBuilder();
        out.append("sample_index,dt_s");
        ChannelRole[] roles = module == null ? new ChannelRole[0] : module.probeRoles();
        for (ChannelRole role : roles) out.append(',').append(csv(role.getLabel()));
        out.append('\n');
        if (samples.isEmpty()) return out.toString();
        double base = samples.get(0).getSeconds();
        for (int i = 0; i < samples.size(); i++) {
            LiveSample sample = samples.get(i);
            out.append(i).append(',').append(f6(sample.getSeconds() - base));
            for (ChannelRole role : roles) {
                double value = sample.get(role);
                out.append(',');
                if (Double.isFinite(value)) out.append(f6(value));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private void appendCoverage(StringBuilder out, ChannelRole[] roles, boolean required) {
        if (roles == null || roles.length == 0) {
            out.append("  - none\n");
            return;
        }
        for (ChannelRole role : roles) {
            Integer count = finiteCounts.get(role);
            int finite = count == null ? 0 : count.intValue();
            int observedSamples = observedSampleCount();
            out.append("  - ").append(role.getLabel()).append(": ")
                    .append(finite).append('/').append(observedSamples);
            if (finite == 0) out.append(required ? " — MISSING REQUIRED" : " — unavailable context");
            else if (finite < observedSamples) out.append(required ? " — PARTIAL REQUIRED" : " — partial context");
            else out.append(required ? " — ready" : " — present");
            out.append('\n');
        }
    }

    private String methodMetricsText() {
        if (module == null) return "No metrics.";
        GuidedTuningRecipe recipe = module.recipe();
        StringBuilder out = new StringBuilder();
        if (recipe != GuidedTuningRecipe.MAP_ESTIMATE) {
            out.append("Retained-window metrics (up to 6000 coherent samples):\n");
        }
        if (recipe == GuidedTuningRecipe.MAP_PREDICT) {
            out.append("Prediction-active samples retained: ").append(boolCount(ChannelRole.MAP_PRED_ACTIVE)).append('\n')
                    .append("Peak smoothedDeltaTps / AccelThreshold: ").append(metric(peakRatio())).append('\n')
                    .append("Maximum fallbackMap - MAP lead while prediction active: ")
                    .append(metric(maxDifferenceWhilePredictionActive(ChannelRole.FALLBACK_MAP, ChannelRole.MAP))).append(" kPa\n")
                    .append("Maximum |Effective MAP - fallbackMap| while prediction active: ")
                    .append(metric(maxAbsDifferenceWhilePredictionActive(ChannelRole.EFFECTIVE_MAP, ChannelRole.FALLBACK_MAP))).append(" kPa\n")
                    .append("predTimerResetCnt retained-window span: ").append(metric(span(ChannelRole.MAP_PRED_RESET_CNT))).append('\n')
                    .append("mapPredEventOver retained-window span: ").append(metric(span(ChannelRole.MAP_PRED_EVENT_OVER))).append('\n');
        } else if (recipe == GuidedTuningRecipe.MAP_ESTIMATE) {
            MapEstimateEvidenceBasis basis = mapEstimateGuided.configured()
                    ? ((state == GuidedCaptureState.COMPLETE || mapEstimateGuided.active())
                        ? mapEstimateGuided.activeEvidenceBasis()
                        : mapEstimateGuided.pendingEvidenceBasis())
                    : MapEstimateEvidenceBasis.LEARNED_MEMORY;
            MapEstimateProposalLimitPolicy limit = mapEstimateGuided.configured()
                    ? ((state == GuidedCaptureState.COMPLETE || mapEstimateGuided.active())
                        ? mapEstimateGuided.activeProposalLimitPolicy()
                        : mapEstimateGuided.pendingProposalLimitPolicy())
                    : MapEstimateProposalLimitPolicy.HIGH_TPS_CAP;
            out.append(mapEstimateCollector.statusText(mapMinimumSamples)).append('\n')
                    .append("Persistent learned samples: ")
                    .append(mapEstimateGuided.configured() ? mapEstimateGuided.storedSamples() : 0L)
                    .append(" | current capture samples: ")
                    .append(mapEstimateGuided.configured() ? mapEstimateGuided.currentRunSamples() : 0L).append('\n')
                    .append("Evidence basis: ").append(basis)
                    .append(" | proposal limit: ").append(limit);
            if (limit == MapEstimateProposalLimitPolicy.HIGH_TPS_CAP) {
                out.append(" (").append(f1(mapCapKpa)).append(" kPa from ")
                        .append(f1(MapEstimateProposal.HIGH_TPS_CAP_START)).append("% TPS)");
            }
            out.append('\n')
                    .append("Minimum samples/direct anchor: ").append(mapMinimumSamples).append('\n')
                    .append("Direct evidence and bounded interpolation retain separate provenance; no extrapolation is permitted.\n");
        } else if (recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            out.append("Working detector: ")
                    .append(projectSnapshot == null ? "unknown" : projectSnapshot.getEngagementModel())
                    .append('\n')
                    .append("Peak detector / AccelThreshold ratios:\n")
                    .append("  legacy max step: ").append(metric(detectorPeakRatio(ChannelRole.AE_DELTA_MAX_STEP))).append('\n')
                    .append("  timed max step: ").append(metric(detectorPeakRatio(ChannelRole.AE_DELTA_TIMED))).append('\n')
                    .append("  window span: ").append(metric(detectorPeakRatio(ChannelRole.AE_DELTA_SPAN))).append('\n')
                    .append("  rise from floor: ").append(metric(detectorPeakRatio(ChannelRole.AE_DELTA_FLOOR))).append('\n')
                    .append("  dual stride/newest: ").append(metric(detectorPeakRatio(ChannelRole.AE_DELTA_NEWEST_PAIR))).append('\n')
                    .append("Selected-detector above-threshold samples: ").append(selectedDetectorAboveThresholdCount()).append('\n')
                    .append("Maximum |Fuel: TPS AE change - selected detector output|: ")
                    .append(metric(maxProductionSelectedDifference())).append('\n')
                    .append("These are comparison measurements only; no detector setting recommendation is generated automatically.\n");
        } else if (recipe == GuidedTuningRecipe.WALL_WETTING) {
            out.append("Maximum |Fuel: wall correction|: ").append(metric(maxAbs(ChannelRole.WALL_CORRECTION))).append('\n')
                    .append("Maximum |fuel wallwetting injection time|: ").append(metric(maxAbs(ChannelRole.WALL_WETTING_PW))).append(" ms\n")
                    .append("Lambda - target range: ").append(lambdaErrorRange()).append('\n')
                    .append("TPS AE overlap samples: ").append(fuelOverlapCount()).append('\n')
                    .append("Instant Fuel overlap samples: ").append(positiveCount(ChannelRole.INSTANT_PULSE_PW)).append('\n')
                    .append("MAP Predict overlap samples: ").append(boolCount(ChannelRole.MAP_PRED_ACTIVE)).append('\n');
        } else if (recipe == GuidedTuningRecipe.TPS_AE) {
            out.append("Completed TPS AE table-analysis windows: ").append(tpsAeEvents.eventCount())
                    .append(" | fuel-proved: ").append(tpsAeEvents.fuelProvedEventCount()).append('\n')
                    .append("Peak smoothedDeltaTps / AccelThreshold: ").append(metric(peakRatio())).append('\n')
                    .append("TPS-to observed range: ").append(range(ChannelRole.TPS_TO)).append('\n')
                    .append("Maximum Fuel: TPS AE add fuel ms: ").append(metric(maxAbs(ChannelRole.AE_ADD_MS))).append(" ms\n")
                    .append("Maximum |Fuel: TPS extraFuel|: ").append(metric(maxAbs(ChannelRole.EXTRA_FUEL))).append('\n')
                    .append("Maximum tpsAeCycleMult: ").append(metric(max(ChannelRole.TPS_AE_CYCLE_MULT))).append('\n')
                    .append("Maximum Engine cycles AE duration: ").append(metric(max(ChannelRole.TPS_AE_CYCLE_CNT))).append('\n')
                    .append("Lambda - target range: ").append(lambdaErrorRange()).append('\n');
        } else if (recipe == GuidedTuningRecipe.INSTANT_FUEL) {
            out.append("Peak smoothedDeltaTps / AccelThreshold: ").append(metric(peakRatio())).append('\n')
                    .append("Maximum aeInstantPulsePw: ").append(metric(maxAbs(ChannelRole.INSTANT_PULSE_PW))).append(" ms\n")
                    .append("aeInstantPulseCnt observed span: ").append(metric(span(ChannelRole.INSTANT_PULSE_CNT))).append('\n')
                    .append("Lambda - target range: ").append(lambdaErrorRange()).append('\n')
                    .append("TPS AE overlap samples: ").append(fuelOverlapCount()).append('\n')
                    .append("Wall Wetting overlap samples: ").append(positiveCount(ChannelRole.WALL_WETTING_PW)).append('\n')
                    .append("MAP Predict overlap samples: ").append(boolCount(ChannelRole.MAP_PRED_ACTIVE)).append('\n');
        } else {
            out.append("No method-specific metrics.");
        }
        return out.toString();
    }

    private boolean allFinite(LiveSample sample, ChannelRole[] roles) {
        if (roles == null) return true;
        for (ChannelRole role : roles) {
            if (!Double.isFinite(sample.get(role))) return false;
        }
        return true;
    }

    private int boolCount(ChannelRole role) {
        int count = 0;
        for (LiveSample sample : samples) if (sample.bool(role)) count++;
        return count;
    }

    private int positiveCount(ChannelRole role) {
        int count = 0;
        for (LiveSample sample : samples) {
            double value = sample.get(role);
            if (Double.isFinite(value) && Math.abs(value) > 0.000001) count++;
        }
        return count;
    }

    private int fuelOverlapCount() {
        int count = 0;
        for (LiveSample sample : samples) {
            double add = sample.get(ChannelRole.AE_ADD_MS);
            double extra = sample.get(ChannelRole.EXTRA_FUEL);
            if ((Double.isFinite(add) && Math.abs(add) > 0.000001)
                    || (Double.isFinite(extra) && Math.abs(extra) > 0.000001)) count++;
        }
        return count;
    }

    private double peakRatio() {
        double best = Double.NaN;
        for (LiveSample sample : samples) {
            double delta = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
            double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
            if (!Double.isFinite(delta) || !Double.isFinite(threshold) || threshold <= 0.000001) continue;
            double ratio = delta / threshold;
            if (!Double.isFinite(best) || ratio > best) best = ratio;
        }
        return best;
    }

    private double detectorPeakRatio(ChannelRole detectorRole) {
        double best = Double.NaN;
        for (LiveSample sample : samples) {
            double detector = sample.get(detectorRole);
            double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
            if (!Double.isFinite(detector) || !Double.isFinite(threshold) || threshold <= 0.000001) continue;
            double ratio = detector / threshold;
            if (!Double.isFinite(best) || ratio > best) best = ratio;
        }
        return best;
    }

    private int selectedDetectorAboveThresholdCount() {
        int count = 0;
        for (LiveSample sample : samples) {
            double detector = EngagementFocusModel.selectedDetectorOutput(projectSnapshot, sample);
            double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
            if (Double.isFinite(detector) && Double.isFinite(threshold) && detector > threshold) count++;
        }
        return count;
    }

    private double maxProductionSelectedDifference() {
        double best = Double.NaN;
        for (LiveSample sample : samples) {
            double production = sample.get(ChannelRole.DELTA_TPS);
            double detector = EngagementFocusModel.selectedDetectorOutput(projectSnapshot, sample);
            if (!Double.isFinite(production) || !Double.isFinite(detector)) continue;
            double difference = Math.abs(production - detector);
            if (!Double.isFinite(best) || difference > best) best = difference;
        }
        return best;
    }

    private double maxDifferenceWhilePredictionActive(ChannelRole a, ChannelRole b) {
        double best = Double.NaN;
        for (LiveSample sample : samples) {
            if (!sample.bool(ChannelRole.MAP_PRED_ACTIVE)) continue;
            double first = sample.get(a);
            double second = sample.get(b);
            if (!Double.isFinite(first) || !Double.isFinite(second)) continue;
            double value = first - second;
            if (!Double.isFinite(best) || value > best) best = value;
        }
        return best;
    }

    private double maxAbsDifferenceWhilePredictionActive(ChannelRole a, ChannelRole b) {
        double best = Double.NaN;
        for (LiveSample sample : samples) {
            if (!sample.bool(ChannelRole.MAP_PRED_ACTIVE)) continue;
            double first = sample.get(a);
            double second = sample.get(b);
            if (!Double.isFinite(first) || !Double.isFinite(second)) continue;
            double value = Math.abs(first - second);
            if (!Double.isFinite(best) || value > best) best = value;
        }
        return best;
    }

    private double maxAbs(ChannelRole role) {
        double best = Double.NaN;
        for (LiveSample sample : samples) {
            double value = sample.get(role);
            if (!Double.isFinite(value)) continue;
            value = Math.abs(value);
            if (!Double.isFinite(best) || value > best) best = value;
        }
        return best;
    }

    private double max(ChannelRole role) {
        double best = Double.NaN;
        for (LiveSample sample : samples) {
            double value = sample.get(role);
            if (!Double.isFinite(value)) continue;
            if (!Double.isFinite(best) || value > best) best = value;
        }
        return best;
    }

    private double span(ChannelRole role) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (LiveSample sample : samples) {
            double value = sample.get(role);
            if (!Double.isFinite(value)) continue;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return min == Double.POSITIVE_INFINITY ? Double.NaN : max - min;
    }

    private String range(ChannelRole role) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (LiveSample sample : samples) {
            double value = sample.get(role);
            if (!Double.isFinite(value)) continue;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return min == Double.POSITIVE_INFINITY ? "n/a" : f2(min) + " to " + f2(max);
    }

    private String lambdaErrorRange() {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (LiveSample sample : samples) {
            double lambda = sample.get(ChannelRole.LAMBDA);
            double target = sample.get(ChannelRole.TARGET_LAMBDA);
            if (!Double.isFinite(lambda) || !Double.isFinite(target)) continue;
            double error = lambda - target;
            min = Math.min(min, error);
            max = Math.max(max, error);
        }
        return min == Double.POSITIVE_INFINITY ? "n/a" : f3(min) + " to " + f3(max);
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String metric(double value) {
        return Double.isFinite(value) ? f3(value) : "n/a";
    }

    private static String f1(double value) { return String.format(Locale.US, "%.1f", value); }
    private static String f2(double value) { return String.format(Locale.US, "%.2f", value); }
    private static String f3(double value) { return String.format(Locale.US, "%.3f", value); }
    private static String f6(double value) { return String.format(Locale.US, "%.6f", value); }
}
