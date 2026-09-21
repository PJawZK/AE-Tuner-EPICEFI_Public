package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCollector;
import se.anders.tunerstudio.aetuner.proposal.AeTableSuggestion;
import se.anders.tunerstudio.aetuner.proposal.MapEstimateSuggestion;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import se.anders.tunerstudio.aetuner.guided.mapestimate.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** Shared evidence-capture shell for Guided AE methods. */
final class GuidedMethodProbeSession {
    private static final int DEFAULT_MAX_SAMPLES = 6000;
    private static final int FOUNDATION_THRESHOLD_MAX_SAMPLES = 24000;
    private static final double ACTIVITY_QUIET_SECONDS = 0.20;

    private GuidedAeMethodModule module;
    private GuidedCaptureState state = GuidedCaptureState.IDLE;
    private final List<LiveSample> samples =
            new BoundedLiveSampleList(FOUNDATION_THRESHOLD_MAX_SAMPLES);
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
    private long evidenceRevision;
    private long cachedEvidenceRevision = Long.MIN_VALUE;
    private List<LiveSample> cachedEvidenceSnapshot = Collections.emptyList();

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

    synchronized boolean mapEstimateWorkingTuneReadRequired() { return mapEstimateWorkingTuneReadRequired; }

    synchronized void noteWorkingTuneRead(AeProjectSnapshot snapshot,
                                          int minimumSamples,
                                          double capKpa) {
        if (snapshot != null && snapshot.hasMapEstimateTable()) {
            mapEstimateWorkingTuneReadRequired = false;
            configureMapEstimateFocus(snapshot, minimumSamples, capKpa);
        }
    }

    synchronized void markMapEstimateWorkingTuneChanged() { mapEstimateWorkingTuneReadRequired = true; }

    synchronized void start(GuidedAeMethodModule selected) { start(selected, null, 5, 20, 115.0); }

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
        if (!append && !reset()) return;
        module = selected;
        projectSnapshot = snapshot;
        targetActivityEvents = Math.max(1, targetEvents);
        mapMinimumSamples = Math.max(3, minimumMapSamples);
        mapCapKpa = Math.max(90.0, Math.min(180.0, mapCap));
        if (selected.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            if (!append) EngagementPassiveCapture.reset();
            EngagementPassiveCapture.configureTarget(targetActivityEvents);
        }
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
        state = GuidedCaptureState.CAPTURING;
        workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.SESSION_STARTED,
                selected.recipe().displayName + " capture started", System.nanoTime());
        publishFocusState(null);
    }

    synchronized void accept(LiveSample sample) {
        if (state != GuidedCaptureState.CAPTURING || sample == null || module == null) return;
        if (!Double.isFinite(firstSeconds)) firstSeconds = sample.getSeconds();
        lastSeconds = sample.getSeconds();
        int retentionLimit = retentionLimit();
        if (samples.size() >= retentionLimit) {
            samples.remove(0);
            droppedSamples++;
        }
        samples.add(sample);
        evidenceRevision++;
        cachedEvidenceRevision = Long.MIN_VALUE;

        boolean requiredComplete = allFinite(sample, module.requiredRoles());
        if (requiredComplete) completeRequiredSamples++;

        EngagementPassiveCapture.Snapshot passiveBefore =
                module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION
                ? EngagementPassiveCapture.snapshot() : null;
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

        if (module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
            activityEvents = passive.comparableEvents;
            if (passiveBefore != null && passiveBefore.settling
                    && passive.readyForMovement()) {
                workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.READY_ENTERED,
                        "Foundation 1 re-anchored — ready for the next pedal movement",
                        System.nanoTime());
            }
            if (active) {
                workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.EVENT_ACCEPTED,
                        "Comparable passive TPS movement stored — "
                                + passive.comparableEvents + "/" + passive.targetComparable,
                        System.nanoTime());
            }
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
        }
        publishFocusState(sample);
    }

    synchronized boolean togglePause() {
        boolean pausing = state == GuidedCaptureState.CAPTURING;
        if (module != null && module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE
                && mapEstimateGuided.configured()) mapEstimateGuided.togglePause();
        if (pausing) {
            state = GuidedCaptureState.PAUSED;
            workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.PAUSED,
                    module == null ? "Guided method capture paused" : module.recipe().displayName + " paused",
                    System.nanoTime());
        } else if (state == GuidedCaptureState.PAUSED) {
            state = GuidedCaptureState.CAPTURING;
        }
        publishFocusState(null);
        return true;
    }

    synchronized boolean finish() {
        if (state == GuidedCaptureState.CAPTURING || state == GuidedCaptureState.PAUSED) {
            if (module != null && module.recipe() == GuidedTuningRecipe.TPS_AE) {
                tpsAeEvents.finish();
            } else if (module != null && module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE
                    && mapEstimateGuided.configured()) {
                mapEstimateGuided.finish();
            }
            state = GuidedCaptureState.COMPLETE;
            activityLatched = false;
            boolean ready = reviewReady();
            workflowEvents.onGuidedWorkflowEvent(GuidedWorkflowEvent.SERIES_COMPLETE,
                    module == null ? "Guided method capture ended"
                            : module.recipe().displayName + (ready
                            ? " evidence ready for Review"
                            : " capture ended — more evidence is required before Review"),
                    System.nanoTime());
            publishFocusState(null);
        }
        return true;
    }

    synchronized boolean reset() {
        EngagementPassiveCapture.reset();
        module = null;
        projectSnapshot = null;
        state = GuidedCaptureState.IDLE;
        samples.clear();
        evidenceRevision++;
        cachedEvidenceRevision = Long.MIN_VALUE;
        cachedEvidenceSnapshot = Collections.emptyList();
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
        GuidedFocusHub.clear();
        return true;
    }

    synchronized boolean closeForLifecycle() {
        if (state == GuidedCaptureState.CAPTURING || state == GuidedCaptureState.PAUSED) {
            if (module != null && module.recipe() == GuidedTuningRecipe.TPS_AE) tpsAeEvents.finish();
            else if (module != null && module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE
                    && mapEstimateGuided.configured()) mapEstimateGuided.finish();
            state = GuidedCaptureState.COMPLETE;
            activityLatched = false;
        }
        return true;
    }

    private int retentionLimit() {
        return module != null && module.recipe() == GuidedTuningRecipe.FOUNDATION_THRESHOLD
                ? FOUNDATION_THRESHOLD_MAX_SAMPLES : DEFAULT_MAX_SAMPLES;
    }

    synchronized int sampleCount() { return samples.size(); }
    synchronized int observedSampleCount() { return samples.size() + droppedSamples; }
    synchronized int activitySampleCount() { return activitySamples; }
    synchronized int activityEventCount() {
        return module != null && module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION
                ? EngagementPassiveCapture.snapshot().comparableEvents : activityEvents;
    }
    synchronized int completeRequiredSampleCount() { return completeRequiredSamples; }
    synchronized int tpsAeTableEventCount() { return tpsAeEvents.eventCount(); }
    synchronized int tpsAeFuelProvedEventCount() { return tpsAeEvents.fuelProvedEventCount(); }
    synchronized boolean hasEvidence() { return !samples.isEmpty(); }

    /**
     * Lifecycle completion only means capture has stopped. This method owns the
     * separate evidence-quality gate used by Review and evidence-derived Apply.
     */
    synchronized boolean reviewReady() {
        if (module == null || state != GuidedCaptureState.COMPLETE
                || samples.isEmpty() || completeRequiredSamples <= 0) return false;
        GuidedTuningRecipe recipe = module.recipe();
        if (recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            return EngagementPassiveCapture.snapshot().complete();
        }
        if (recipe == GuidedTuningRecipe.MAP_ESTIMATE) {
            return mapEstimateCollector.getAcceptedSamples() >= mapMinimumSamples;
        }
        if (recipe == GuidedTuningRecipe.TPS_AE) {
            return tpsAeEvents.fuelProvedEventCount() >= targetActivityEvents;
        }
        return activityEventCount() >= targetActivityEvents;
    }

    synchronized List<LiveSample> evidenceSnapshot() {
        if (cachedEvidenceRevision == evidenceRevision) return cachedEvidenceSnapshot;
        cachedEvidenceSnapshot = Collections.unmodifiableList(new ArrayList<LiveSample>(samples));
        cachedEvidenceRevision = evidenceRevision;
        return cachedEvidenceSnapshot;
    }
    synchronized GuidedCaptureState state() { return state; }
    synchronized GuidedAeMethodModule module() { return module; }
    synchronized MapEstimateEvidenceBasis mapEstimatePendingEvidenceBasisForTest() { return mapEstimateGuided.pendingEvidenceBasis(); }
    synchronized MapEstimateProposalLimitPolicy mapEstimatePendingProposalLimitForTest() { return mapEstimateGuided.pendingProposalLimitPolicy(); }
    synchronized long mapEstimateCollectorAcceptedForTest() { return mapEstimateCollector.getAcceptedSamples(); }
    synchronized long evidenceRevisionForTest() { return evidenceRevision; }

    private void publishFocusState(LiveSample latest) {
        if (module == null) return;
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            publishMapEstimateFocus(latest);
        } else if (module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
            GuidedFocusHub.publishEngagement(state,
                    EngagementFocusModel.build(projectSnapshot, latest, state,
                            passive.comparableEvents, passive.targetComparable,
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
            if (reviewReady()) {
                headline = "REVIEW — " + module.recipe().displayName;
                instruction = "Capture ended with sufficient evidence. Review required-channel readiness, accumulated method evidence and any generated proposal/draft before exporting or applying.";
            } else {
                headline = "CAPTURE ENDED — MORE EVIDENCE NEEDED";
                instruction = "Capture stopped, but the evidence target is not satisfied. Start Capture again to append evidence; Review and evidence-derived Apply remain blocked.";
            }
        } else if (state == GuidedCaptureState.PAUSED) {
            headline = "PAUSED — " + module.recipe().displayName;
            instruction = "Capture is paused; resume when ready.";
        } else {
            headline = "CAPTURE — " + module.recipe().displayName;
            instruction = module.captureGoal();
        }
        String snapshotResult = state == GuidedCaptureState.COMPLETE
                ? resultText() : liveResultText();
        return new GuidedSessionSnapshot(state, headline, instruction,
                coverageText(), snapshotResult, activityEventCount(), "");
    }

    private String liveResultText() {
        if (module == null) return "No method capture active.";
        double duration = Double.isFinite(firstSeconds) && Double.isFinite(lastSeconds)
                ? Math.max(0.0, lastSeconds - firstSeconds) : 0.0;
        StringBuilder out = new StringBuilder();
        int observedSamples = observedSampleCount();
        out.append(module.recipe().displayName).append(" live evidence\n")
                .append("Observed coherent samples: ").append(observedSamples).append('\n')
                .append("Retained coherent samples: ").append(samples.size()).append('\n')
                .append("Required-complete observed samples: ").append(completeRequiredSamples)
                .append('/').append(observedSamples).append('\n');
        if (module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
            out.append("Comparable pedal movements: ").append(passive.comparableEvents)
                    .append('/').append(passive.targetComparable).append('\n')
                    .append("Stored natural movements: ").append(passive.storedEvents).append('\n')
                    .append("Last movement: ").append(passive.lastEvent).append('\n');
        } else if (module.recipe() != GuidedTuningRecipe.MAP_ESTIMATE) {
            out.append("Method activity: ").append(activityEvents).append(" event(s), ")
                    .append(activitySamples).append(" active observed sample(s)\n");
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            out.append("TPS AE table-analysis windows: ").append(tpsAeEvents.eventCount())
                    .append(" completed / ").append(tpsAeEvents.fuelProvedEventCount())
                    .append(" fuel-proved\n");
        }
        out.append("Observed duration: ").append(f3(duration)).append(" s\n")
                .append("Status: ").append(state.name()).append('\n')
                .append("Detailed retained-window metrics are calculated for Review/export, not on the live sample path.");
        return out.toString();
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
        } else if (module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
            out.append("Comparable passive movements: ").append(passive.comparableEvents)
                    .append('/').append(passive.targetComparable)
                    .append(" | stored movements: ").append(passive.storedEvents).append('\n');
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
                    .append(tpsAeEvents.eventCount()).append(" | fuel-proved: ")
                    .append(tpsAeEvents.fuelProvedEventCount()).append('\n');
        }
        out.append("Review readiness: ").append(reviewReady() ? "READY" : "INCOMPLETE").append('\n');
        out.append("\nREQUIRED CHANNELS\n");
        appendCoverage(out, module.requiredRoles(), true);
        out.append("\nCONTEXT / ATTRIBUTION CHANNELS\n");
        appendCoverage(out, module.contextRoles(), false);
        out.append("\nGuided capture observes only. The selected method owns its own evidence/recommendation boundary. Capture never writes; final Apply remains explicit and there is no burn.");
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
        if (module.recipe() == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
            out.append("Comparable pedal movements: ").append(passive.comparableEvents)
                    .append('/').append(passive.targetComparable).append('\n');
        } else if (module.recipe() != GuidedTuningRecipe.MAP_ESTIMATE) {
            out.append("Method activity: ").append(activityEvents).append(" event(s), ")
                    .append(activitySamples).append(" active observed sample(s)\n");
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            out.append("TPS AE table-analysis windows: ").append(tpsAeEvents.eventCount())
                    .append(" completed / ").append(tpsAeEvents.fuelProvedEventCount())
                    .append(" fuel-proved\n");
        }
        out.append("Observed duration: ").append(f3(duration)).append(" s\n")
                .append("Capture state: ").append(state.name()).append('\n')
                .append("Evidence readiness: ").append(reviewReady() ? "READY" : "INCOMPLETE")
                .append("\n\n").append(methodMetricsText());
        return out.toString();
    }

    synchronized String reviewText() {
        if (module == null) return "No method capture active.";
        if (!reviewReady()) {
            return "EVIDENCE INCOMPLETE — REVIEW AUTHORITY WITHHELD\n"
                    + "Capture may be stopped, but the task evidence target and required-channel gate are not both satisfied. Start Capture again to append evidence. No evidence-derived Apply is available.\n\n"
                    + coverageText() + "\n\nCURRENT CAPTURE METRICS\n" + methodMetricsText();
        }
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            return mapEstimateGuided.configured() ? mapEstimateGuided.reviewText()
                    : "MAP Estimate Table is not configured; Read Working Tune first.";
        }
        ProposalWritePlan plan = reviewedWritePlan();
        String proposalState = plan == null
                ? "No supported setting/value change is currently proposed. No automatic Apply and no burn."
                : plan.reviewText() + "\n\nGuarded working-tune Apply/readback/Restore is available for this reviewed plan. No burn.";
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            AeTableSuggestion suggestion = AeTableSuggestion.build(projectSnapshot, tpsAeEvents.eventsSnapshot());
            return "TPS AE TABLE REVIEW\n" + suggestion.getDisplayText()
                    + "\n\nCURRENT CAPTURE METRICS\n" + methodMetricsText()
                    + (suggestion.isAvailable()
                    ? "\n\nPaste-ready TPS AE draft is available through Copy Reviewed Draft and the Guided export; changed cells are also eligible for one explicit guarded multi-cell Apply/Restore plan."
                    : "\n\nNo paste-ready TPS AE draft yet; continue repeated fuel-proved events in the same TPS-to rows.")
                    + "\n\n" + proposalState;
        }
        return "METHOD REVIEW OUTPUTS\n" + module.reviewOutputs()
                + "\n\nCURRENT CAPTURE METRICS\n" + methodMetricsText()
                + "\n\n" + proposalState;
    }

    synchronized String copyPasteBlock() {
        if (module == null || !reviewReady()) return "";
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            return mapEstimateGuided.configured() ? mapEstimateGuided.reviewedCopyPasteBlock() : "";
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            AeTableSuggestion suggestion = AeTableSuggestion.build(projectSnapshot, tpsAeEvents.eventsSnapshot());
            return suggestion.isAvailable() ? suggestion.getCopyPasteBlock() : "";
        }
        return "";
    }

    synchronized ProposalWritePlan reviewedWritePlan() {
        if (module == null || !reviewReady()) return null;
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            return mapEstimateGuided.configured() ? mapEstimateGuided.reviewedWritePlan() : null;
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            return AeTableSuggestion.build(projectSnapshot,
                    tpsAeEvents.eventsSnapshot()).getWritePlan();
        }
        return module.reviewedWritePlan(projectSnapshot, evidenceSnapshot());
    }

    synchronized String reportText(String pluginVersion) {
        StringBuilder out = new StringBuilder();
        out.append("AE Tuner Guided method report\n")
                .append("Plugin version: ").append(pluginVersion).append('\n')
                .append("Method: ").append(module == null ? "none" : module.recipe().displayName).append('\n')
                .append("Capture boundary: Guided capture observes only. The selected method owns its own evidence/recommendation boundary. Capture never writes; final Apply remains explicit and there is no burn.\n\n");
        if (module != null) {
            out.append("OPERATOR INPUTS\n").append(module.operatorInputs(projectSnapshot)).append("\n\n")
                    .append("ACCUMULATION PLAN\n").append(module.accumulationPlan()).append("\n\n")
                    .append("CURRENT TUNE CONTEXT\n").append(module.currentTuneContext(projectSnapshot)).append("\n\n");
        }
        out.append(coverageText()).append("\n\n");
        if (state != GuidedCaptureState.COMPLETE) {
            out.append(liveResultText()).append("\n\n")
                    .append("REVIEW STATUS\nFull retained-window metrics and reviewed proposal output are generated after Finish/Review.\n");
            return out.toString();
        }
        out.append(resultText()).append("\n\n").append(reviewText()).append('\n');
        String draft = copyPasteBlock();
        if (draft.length() > 0) out.append("\nPASTE-READY DRAFT\n=================\n").append(draft).append('\n');
        return out.toString();
    }

    String csvText() {
        final ChannelRole[] roles;
        final List<LiveSample> retained;
        synchronized (this) {
            roles = module == null ? new ChannelRole[0] : module.probeRoles();
            retained = new ArrayList<LiveSample>(samples);
        }
        StringBuilder out = new StringBuilder();
        out.append("sample_index,dt_s");
        for (ChannelRole role : roles) out.append(',').append(csv(role.getLabel()));
        out.append('\n');
        if (retained.isEmpty()) return out.toString();
        double base = retained.get(0).getSeconds();
        for (int i = 0; i < retained.size(); i++) {
            LiveSample sample = retained.get(i);
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
        if (roles == null || roles.length == 0) { out.append("  - none\n"); return; }
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
            out.append("Retained-window metrics (up to ")
                    .append(retentionLimit()).append(" coherent samples):\n");
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
                    ? ((state == GuidedCaptureState.COMPLETE || mapEstimateGuided.active()) ? mapEstimateGuided.activeEvidenceBasis() : mapEstimateGuided.pendingEvidenceBasis())
                    : MapEstimateEvidenceBasis.LEARNED_MEMORY;
            MapEstimateProposalLimitPolicy limit = mapEstimateGuided.configured()
                    ? ((state == GuidedCaptureState.COMPLETE || mapEstimateGuided.active()) ? mapEstimateGuided.activeProposalLimitPolicy() : mapEstimateGuided.pendingProposalLimitPolicy())
                    : MapEstimateProposalLimitPolicy.HIGH_TPS_CAP;
            out.append(mapEstimateCollector.statusText(mapMinimumSamples)).append('\n')
                    .append("Persistent learned samples: ").append(mapEstimateGuided.configured() ? mapEstimateGuided.storedSamples() : 0L)
                    .append(" | current capture samples: ").append(mapEstimateGuided.configured() ? mapEstimateGuided.currentRunSamples() : 0L).append('\n')
                    .append("Evidence basis: ").append(basis).append(" | proposal limit: ").append(limit);
            if (limit == MapEstimateProposalLimitPolicy.HIGH_TPS_CAP) out.append(" (").append(f1(mapCapKpa)).append(" kPa from ").append(f1(MapEstimateProposal.HIGH_TPS_CAP_START)).append("% TPS)");
            out.append('\n').append("Minimum samples/direct anchor: ").append(mapMinimumSamples).append('\n')
                    .append("Direct evidence and bounded interpolation retain separate provenance; no extrapolation is permitted.\n");
        } else if (recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            out.append(EngagementPassiveCapture.reviewText(projectSnapshot)).append('\n');
        } else if (recipe == GuidedTuningRecipe.WALL_WETTING) {
            out.append("Maximum |Fuel: wall correction|: ").append(metric(maxAbs(ChannelRole.WALL_CORRECTION))).append('\n')
                    .append("Maximum |fuel wallwetting injection time|: ").append(metric(maxAbs(ChannelRole.WALL_WETTING_PW))).append(" ms\n")
                    .append("Lambda - target range: ").append(lambdaErrorRange()).append('\n')
                    .append("TPS AE overlap samples: ").append(fuelOverlapCount()).append('\n')
                    .append("Instant Fuel overlap samples: ").append(positiveCount(ChannelRole.INSTANT_PULSE_PW)).append('\n')
                    .append("MAP Predict overlap samples: ").append(boolCount(ChannelRole.MAP_PRED_ACTIVE)).append('\n');
        } else if (recipe == GuidedTuningRecipe.TPS_AE) {
            out.append("Completed TPS AE table-analysis windows: ").append(tpsAeEvents.eventCount()).append(" | fuel-proved: ").append(tpsAeEvents.fuelProvedEventCount()).append('\n')
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
        } else out.append("No method-specific metrics.");
        return out.toString();
    }

    private boolean allFinite(LiveSample sample, ChannelRole[] roles) {
        if (roles == null) return true;
        for (ChannelRole role : roles) if (!Double.isFinite(sample.get(role))) return false;
        return true;
    }

    private int boolCount(ChannelRole role) { int count=0; for (LiveSample sample: samples) if (sample.bool(role)) count++; return count; }
    private int positiveCount(ChannelRole role) { int count=0; for (LiveSample sample: samples) { double v=sample.get(role); if (Double.isFinite(v)&&Math.abs(v)>0.000001) count++; } return count; }
    private int fuelOverlapCount() { int count=0; for (LiveSample sample: samples) { double add=sample.get(ChannelRole.AE_ADD_MS), extra=sample.get(ChannelRole.EXTRA_FUEL); if ((Double.isFinite(add)&&Math.abs(add)>0.000001)||(Double.isFinite(extra)&&Math.abs(extra)>0.000001)) count++; } return count; }
    private double peakRatio() { double best=Double.NaN; for (LiveSample sample: samples) { double d=sample.get(ChannelRole.SMOOTHED_DELTA_TPS), t=sample.get(ChannelRole.ACCEL_THRESHOLD); if (!Double.isFinite(d)||!Double.isFinite(t)||t<=0.000001) continue; double r=d/t; if (!Double.isFinite(best)||r>best) best=r; } return best; }
    private double maxDifferenceWhilePredictionActive(ChannelRole a, ChannelRole b) { double best=Double.NaN; for (LiveSample s:samples) { if(!s.bool(ChannelRole.MAP_PRED_ACTIVE)) continue; double x=s.get(a),y=s.get(b); if(!Double.isFinite(x)||!Double.isFinite(y)) continue; double v=x-y; if(!Double.isFinite(best)||v>best) best=v;} return best; }
    private double maxAbsDifferenceWhilePredictionActive(ChannelRole a, ChannelRole b) { double best=Double.NaN; for (LiveSample s:samples) { if(!s.bool(ChannelRole.MAP_PRED_ACTIVE)) continue; double x=s.get(a),y=s.get(b); if(!Double.isFinite(x)||!Double.isFinite(y)) continue; double v=Math.abs(x-y); if(!Double.isFinite(best)||v>best) best=v;} return best; }
    private double maxAbs(ChannelRole role) { double best=Double.NaN; for (LiveSample s:samples) { double v=s.get(role); if(!Double.isFinite(v))continue; v=Math.abs(v); if(!Double.isFinite(best)||v>best)best=v;} return best; }
    private double max(ChannelRole role) { double best=Double.NaN; for (LiveSample s:samples) { double v=s.get(role); if(!Double.isFinite(v))continue; if(!Double.isFinite(best)||v>best)best=v;} return best; }
    private double span(ChannelRole role) { double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY; for(LiveSample s:samples){double v=s.get(role); if(!Double.isFinite(v))continue; min=Math.min(min,v);max=Math.max(max,v);} return min==Double.POSITIVE_INFINITY?Double.NaN:max-min; }
    private String range(ChannelRole role) { double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY; for(LiveSample s:samples){double v=s.get(role);if(!Double.isFinite(v))continue;min=Math.min(min,v);max=Math.max(max,v);} return min==Double.POSITIVE_INFINITY?"n/a":f2(min)+" to "+f2(max); }
    private String lambdaErrorRange() { double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY; for(LiveSample s:samples){double l=s.get(ChannelRole.LAMBDA),t=s.get(ChannelRole.TARGET_LAMBDA);if(!Double.isFinite(l)||!Double.isFinite(t))continue;double e=l-t;min=Math.min(min,e);max=Math.max(max,e);} return min==Double.POSITIVE_INFINITY?"n/a":f3(min)+" to "+f3(max); }
    private static String csv(String value) { String safe=value==null?"":value; return '"'+safe.replace("\"","\"\"")+'"'; }
    private static String metric(double value) { return Double.isFinite(value)?f3(value):"n/a"; }
    private static String f1(double value) { return String.format(Locale.US,"%.1f",value); }
    private static String f2(double value) { return String.format(Locale.US,"%.2f",value); }
    private static String f3(double value) { return String.format(Locale.US,"%.3f",value); }
    private static String f6(double value) { return String.format(Locale.US,"%.6f",value); }
}
