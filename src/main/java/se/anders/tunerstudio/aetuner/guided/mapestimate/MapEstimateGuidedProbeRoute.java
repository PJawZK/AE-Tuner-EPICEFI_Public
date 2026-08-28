package se.anders.tunerstudio.aetuner.guided.mapestimate;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.passive.MapEstimateCollector;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

/**
 * Thin integration route used by GuidedMethodProbeSession.
 *
 * The physically validated dev15 MapEstimateCollector remains authoritative
 * for required-channel/stability/transient rejection and 25 Hz acceptance.
 * Only samples for which collector.addSample(sample) returns true are forwarded
 * into learned memory. This keeps the persistent/surface model from quietly
 * redefining the vehicle-tested evidence gate.
 */
public final class MapEstimateGuidedProbeRoute implements MapEstimateGuidedFocusPanel.ConfigurationListener {
    private final MapEstimateCollector collector;
    private final MapEstimateGuidedController guided;
    private AeProjectSnapshot snapshot;
    private int minimumSamples = 20;
    private double capKpa = 115.0;

    public MapEstimateGuidedProbeRoute() {
        this(new MapEstimateCollector(),
                new MapEstimateGuidedController(MapEstimateMemoryPaths.store()));
    }

    MapEstimateGuidedProbeRoute(MapEstimateCollector collector,
                                MapEstimateGuidedController guided) {
        if (collector == null || guided == null) {
            throw new IllegalArgumentException("MAP Estimate collector/controller required");
        }
        this.collector = collector;
        this.guided = guided;
    }

    /** Prepare idle Focus/memory state after Read Working Tune. */
    public void prepare(AeProjectSnapshot snapshot, int minimumSamples, double capKpa) {
        if (snapshot == null || !snapshot.hasMapEstimateTable()) {
            throw new IllegalArgumentException("working MAP Estimate table required");
        }
        this.snapshot = snapshot;
        this.minimumSamples = Math.max(3, minimumSamples);
        this.capKpa = Math.max(90.0, Math.min(180.0, capKpa));
        guided.configure(snapshot.getConfigurationName(),
                snapshot.getMapEstimateTpsBins(),
                snapshot.getMapEstimateRpmBins(),
                snapshot.getMapEstimateTable(),
                this.minimumSamples, this.capKpa);
        collector.configure(snapshot);
    }

    public void start() {
        requirePrepared();
        // The collector is current-run diagnostics only. A Continue capture
        // starts a fresh stability/progress window without deleting learned
        // memory; CURRENT_CAPTURE_ONLY can independently exclude that stored
        // memory from this run's surface/proposal authority.
        collector.clear();
        collector.configure(snapshot);
        guided.start();
    }

    public boolean accept(LiveSample sample, boolean requiredComplete) {
        requirePrepared();
        if (sample == null) return false;
        if (!requiredComplete) {
            collector.pauseForIncompleteRequiredData(sample);
            return false;
        }
        boolean accepted = collector.addSample(sample);
        if (!accepted) return false;
        return guided.acceptStable(
                sample.get(ChannelRole.TPS),
                sample.get(ChannelRole.RPM),
                sample.get(ChannelRole.MAP),
                sample.get(ChannelRole.COOLANT),
                sample.get(ChannelRole.IAT));
    }

    public void togglePause() {
        if (guided.active()) guided.togglePause();
    }

    public void finish() {
        if (guided.active()) guided.finish();
    }

    /** Reset only the current run; stored learned memory remains intact. */
    public void resetCurrentCapture() {
        guided.resetCurrentCapture();
        collector.clear();
        if (snapshot != null) collector.configure(snapshot);
    }

    public void updateReviewSettings(int minimumSamples, double capKpa) {
        this.minimumSamples = Math.max(3, minimumSamples);
        this.capKpa = Math.max(90.0, Math.min(180.0, capKpa));
        guided.updateReviewSettings(this.minimumSamples, this.capKpa);
    }

    public void setPendingStrategy(MapEstimateCoverageStrategy strategy) {
        guided.setPendingStrategy(strategy);
    }

    public void setPendingScope(MapEstimateCellScope scope) {
        guided.setPendingScope(scope);
    }

    public void setPendingEvidenceBasis(MapEstimateEvidenceBasis basis) {
        guided.setPendingEvidenceBasis(basis);
    }

    public void setPendingProposalLimitPolicy(MapEstimateProposalLimitPolicy policy) {
        guided.setPendingProposalLimitPolicy(policy);
    }

    @Override public void onStrategyRequested(MapEstimateCoverageStrategy strategy) {
        if (!guided.active()) setPendingStrategy(strategy);
    }

    @Override public void onScopeRequested(MapEstimateCellScope scope) {
        if (!guided.active()) setPendingScope(scope);
    }

    @Override public void onEvidenceBasisRequested(MapEstimateEvidenceBasis basis) {
        if (!guided.active()) setPendingEvidenceBasis(basis);
    }

    @Override public void onProposalLimitPolicyRequested(MapEstimateProposalLimitPolicy policy) {
        if (!guided.active()) setPendingProposalLimitPolicy(policy);
    }

    public MapEstimateCoverageStrategy pendingStrategy() { return guided.pendingStrategy(); }
    public MapEstimateCellScope pendingScope() { return guided.pendingScope(); }
    public MapEstimateEvidenceBasis pendingEvidenceBasis() { return guided.pendingEvidenceBasis(); }
    public MapEstimateProposalLimitPolicy pendingProposalLimitPolicy() { return guided.pendingProposalLimitPolicy(); }

    public MapEstimateFocusModel focus(LiveSample latest) {
        requirePrepared();
        double tps = latest == null ? collector.getLastLiveTps() : latest.get(ChannelRole.TPS);
        double rpm = latest == null ? collector.getLastLiveRpm() : latest.get(ChannelRole.RPM);
        return guided.focus(tps, rpm, collector.getLastEligibility().getDisplayText());
    }

    public String reviewText() { return guided.reviewText(); }
    public String copyPasteBlock() { return guided.reviewedCopyPasteBlock(); }
    public ProposalWritePlan writePlan() { return guided.reviewedWritePlan(); }
    public String memoryStatus() { return guided.status(); }
    public String collectorStatus() { return collector.statusText(minimumSamples); }
    public long currentRunStableSamples() { return collector.getAcceptedSamples(); }
    public long storedStableSamples() { return guided.storedSamples(); }
    public boolean configured() { return guided.configured(); }
    public boolean active() { return guided.active(); }
    public boolean complete() { return guided.complete(); }

    private void requirePrepared() {
        if (snapshot == null || !guided.configured()) {
            throw new IllegalStateException("MAP Estimate Table route not prepared from Working Tune");
        }
    }
}
