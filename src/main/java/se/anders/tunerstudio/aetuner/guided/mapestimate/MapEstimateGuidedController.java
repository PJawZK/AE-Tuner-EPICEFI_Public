package se.anders.tunerstudio.aetuner.guided.mapestimate;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

/**
 * Integration seam between the physically validated stable-sample gate and
 * persistent MAP Estimate learned-state/surface/proposal logic.
 */
public final class MapEstimateGuidedController {
    private final MapEstimateMemoryStore store;
    private MapEstimateEvidenceSession session;
    private MapEstimateCoverageStrategy pendingStrategy = MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE;
    private MapEstimateCellScope pendingScope;
    private MapEstimateEvidenceBasis pendingEvidenceBasis = MapEstimateEvidenceBasis.LEARNED_MEMORY;
    private MapEstimateProposalLimitPolicy pendingProposalLimitPolicy = MapEstimateProposalLimitPolicy.HIGH_TPS_CAP;
    private MapEstimateEvidenceBasis activeEvidenceBasis = MapEstimateEvidenceBasis.LEARNED_MEMORY;
    private MapEstimateProposalLimitPolicy activeProposalLimitPolicy = MapEstimateProposalLimitPolicy.HIGH_TPS_CAP;
    private String configuration = "";
    private double[] tpsAxis = new double[0];
    private double[] rpmAxis = new double[0];
    private double[][] currentTable = new double[0][0];
    private int minimumSamples = 20;
    private double capKpa = 115.0;
    private String status = "MAP Estimate Table memory not configured yet.";

    public MapEstimateGuidedController(MapEstimateMemoryStore store) {
        this.store = store;
    }

    public void configure(String configuration, double[] tpsAxis, double[] rpmAxis,
            double[][] currentTable, int minimumSamples, double capKpa) {
        if (active()) throw new IllegalStateException("cannot reconfigure MAP Estimate Table during capture");
        if (tpsAxis == null || rpmAxis == null || currentTable == null
                || tpsAxis.length == 0 || rpmAxis.length == 0
                || currentTable.length != tpsAxis.length) {
            throw new IllegalArgumentException("valid MAP Estimate table and axes required");
        }
        for (int row = 0; row < currentTable.length; row++) {
            if (currentTable[row] == null || currentTable[row].length != rpmAxis.length) {
                throw new IllegalArgumentException("MAP Estimate table shape does not match axes");
            }
        }
        boolean sameDimensions = this.tpsAxis.length == tpsAxis.length && this.rpmAxis.length == rpmAxis.length;
        this.configuration = configuration == null ? "" : configuration;
        this.tpsAxis = tpsAxis.clone();
        this.rpmAxis = rpmAxis.clone();
        this.currentTable = cloneTable(currentTable);
        this.minimumSamples = Math.max(3, minimumSamples);
        this.capKpa = Math.max(90.0, Math.min(180.0, capKpa));
        try {
            this.session = new MapEstimateEvidenceSession(store, this.configuration, this.tpsAxis, this.rpmAxis);
            status = session.loadStatus();
        } catch (java.io.IOException ex) {
            try {
                this.session = new MapEstimateEvidenceSession(null, this.configuration, this.tpsAxis, this.rpmAxis);
            } catch (java.io.IOException impossible) {
                throw new IllegalStateException(impossible);
            }
            status = "Persistent MAP Estimate memory could not be loaded; this plugin session is using RAM-only learned state: " + safeMessage(ex);
        }
        if (!sameDimensions || pendingScope == null
                || pendingScope.rows() != tpsAxis.length || pendingScope.cols() != rpmAxis.length) {
            pendingScope = MapEstimateCellScope.all(tpsAxis.length, rpmAxis.length);
        }
        if (status.length() == 0) status = session.loadStatus();
    }

    /** Update setup-time evidence/proposal thresholds without reloading learned memory. */
    public void updateReviewSettings(int minimumSamples, double capKpa) {
        requireEditable();
        this.minimumSamples = Math.max(3, minimumSamples);
        this.capKpa = Math.max(90.0, Math.min(180.0, capKpa));
    }

    public void setPendingStrategy(MapEstimateCoverageStrategy strategy) {
        requireEditable();
        pendingStrategy = strategy == null ? MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE : strategy;
    }

    public void setPendingScope(MapEstimateCellScope scope) {
        requireEditable();
        if (scope == null || scope.rows() != tpsAxis.length || scope.cols() != rpmAxis.length) {
            throw new IllegalArgumentException("scope shape does not match MAP Estimate table");
        }
        pendingScope = scope;
    }

    public void setPendingEvidenceBasis(MapEstimateEvidenceBasis basis) {
        requireEditable();
        pendingEvidenceBasis = basis == null ? MapEstimateEvidenceBasis.LEARNED_MEMORY : basis;
    }

    public void setPendingProposalLimitPolicy(MapEstimateProposalLimitPolicy policy) {
        requireEditable();
        pendingProposalLimitPolicy = policy == null
                ? MapEstimateProposalLimitPolicy.HIGH_TPS_CAP : policy;
    }

    public void start() {
        requireConfigured();
        activeEvidenceBasis = pendingEvidenceBasis;
        activeProposalLimitPolicy = pendingProposalLimitPolicy;
        session.start(pendingStrategy, pendingScope);
        status = "MAP Estimate Table capture active; persistent memory is unchanged until Finish and Review."
                + (activeEvidenceBasis == MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY
                    ? " Current-capture-only analysis is isolating this run from previously stored evidence." : "");
    }

    /** Called only when MapEstimateCollector.addSample(sample) returned true. */
    public boolean acceptStable(double tps, double rpm, double map, double clt, double mat) {
        requireConfigured();
        return session.acceptStable(tps, rpm, map, clt, mat);
    }

    public void togglePause() {
        requireConfigured();
        session.togglePause();
    }

    public void finish() {
        requireConfigured();
        long before = session.stored().sampleCount();
        long delta = session.currentRunSamples();
        java.io.IOException failure = null;
        try { session.finish(); } catch (java.io.IOException ex) { failure = ex; }
        long after = session.stored().sampleCount();
        if (failure != null) {
            status = "Finished MAP Estimate Table capture; retained " + (after - before)
                    + " new stable sample(s) in RAM, but persistent memory save FAILED: " + safeMessage(failure);
        } else {
            status = delta > 0
                    ? "Finished MAP Estimate Table capture; merged " + (after - before)
                        + " stable sample(s) as one completed evidence session."
                    : "Finished MAP Estimate Table capture; no new stable sample was committed.";
        }
        if (activeEvidenceBasis == MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY) {
            status += " Review/proposal authority for this completed run uses only its current-capture evidence; earlier stored memory is excluded.";
        }
    }

    /** Discard only the current uncommitted capture delta; persistent learned state remains. */
    public void resetCurrentCapture() {
        if (session == null) return;
        long discarded = session.currentRunSamples();
        session.reset();
        status = "Reset current MAP Estimate Table capture; discarded " + discarded
                + " uncommitted stable sample(s). Stored memory was preserved.";
    }

    public MapEstimateFocusModel focus(double liveTps, double liveRpm, String eligibility) {
        requireConfigured();
        // Focus setup controls are unlocked after Finish. At that point they
        // configure the NEXT capture, while the completed session remains
        // authoritative for review/writePlan().
        boolean captureActive = active();
        return MapEstimateFocusModel.build(session, currentTable, minimumSamples, capKpa,
                liveTps, liveRpm, eligibility,
                captureActive ? session.strategy() : pendingStrategy,
                captureActive ? session.scope() : pendingScope,
                captureActive ? activeEvidenceBasis : pendingEvidenceBasis,
                captureActive ? activeProposalLimitPolicy : pendingProposalLimitPolicy);
    }

    public MapEstimateProposal proposal() {
        requireConfigured();
        MapEstimateMemory evidence = activeEvidenceBasis == MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY
                ? session.currentRunMemory() : session.combined();
        MapEstimateSurface surface = new MapEstimateSurface(evidence, minimumSamples);
        return MapEstimateProposal.build(configuration, tpsAxis, rpmAxis, currentTable,
                surface, session.strategy(), session.scope(), capKpa, activeProposalLimitPolicy);
    }

    /** Write authority is exposed only after explicit Finish/Review. */
    public ProposalWritePlan reviewedWritePlan() {
        if (session == null || session.state() != MapEstimateEvidenceSession.State.COMPLETE) return null;
        return proposal().writePlan();
    }

    public String reviewedVerificationManifestJson() {
        ProposalWritePlan plan = reviewedWritePlan();
        return plan == null ? "" : plan.verificationManifestJson();
    }

    public String reviewedCopyPasteBlock() {
        return session != null && session.state() == MapEstimateEvidenceSession.State.COMPLETE
                ? proposal().copyPasteBlock() : "";
    }

    public String reviewText() {
        if (session == null) return "MAP Estimate Table is not configured.";
        MapEstimateProposal proposal = proposal();
        // Review is historical authority for the just-completed capture. Do
        // not let an operator's next-capture Focus setup rewrite its counts or
        // write allowlist after Finish.
        MapEstimateFocusModel focus = MapEstimateFocusModel.build(
                session, currentTable, minimumSamples, capKpa,
                Double.NaN, Double.NaN, "review",
                session.strategy(), session.scope(), activeEvidenceBasis, activeProposalLimitPolicy);
        StringBuilder out = new StringBuilder();
        out.append("MAP ESTIMATE TABLE REVIEW\n")
                .append("Strategy: ").append(session.strategy()).append('\n')
                .append("Scope: ").append(session.scope().isWholeTable() ? "whole table" : session.scope().size() + " selected cell(s)").append('\n')
                .append("Evidence basis: ").append(activeEvidenceBasis).append('\n')
                .append("Proposal limit: ").append(activeProposalLimitPolicy)
                .append(activeProposalLimitPolicy == MapEstimateProposalLimitPolicy.HIGH_TPS_CAP
                        ? " (" + capKpa + " kPa from " + MapEstimateProposal.HIGH_TPS_CAP_START + "% TPS)" : "")
                .append('\n')
                .append("Evidence samples used by this surface: ").append(focus.evidenceSamplesUsed).append('\n')
                .append("Persistent stable samples retained: ").append(session.stored().sampleCount()).append('\n')
                .append("Direct cells: ").append(focus.directCount)
                .append(" | strong interpolated: ").append(focus.interpolatedStrongCount)
                .append(" | weak: ").append(focus.weakCount)
                .append(" | conflict: ").append(focus.conflictCount).append('\n')
                .append("Evidence maturity: confirmed ").append(focus.confirmedCount)
                .append(" | provisional ").append(focus.provisionalCount)
                .append(" | recheck ").append(focus.recheckCount).append('\n')
                .append("Proposal changes: ").append(proposal.changeCount()).append('\n')
                .append("Direct authority requires evidence that reaches the exact table coordinate. Coherent local TPS/RPM gradients may explain raw spread; supported residual disagreement still becomes Recheck/Conflict.\n")
                .append(activeEvidenceBasis == MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY
                        ? "Current-capture-only mode excludes all earlier stored evidence from surface, target coach and proposal authority. The completed run may still be archived to persistent memory for later use.\n" : "")
                .append(activeProposalLimitPolicy == MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP
                        ? "Unrestricted eligible MAP removes only the experimental high-TPS cap; scope, evidence quality, Conflict/Recheck exclusion and no-extrapolation safeguards remain active.\n" : "")
                .append("Cells outside selected scope are preserved unchanged. No automatic Apply and no burn.\n")
                .append("Memory: ").append(status);
        ProposalWritePlan plan = reviewedWritePlan();
        if (plan != null) {
            out.append("\n\n").append(plan.reviewText())
                    .append("\n\nMAP ESTIMATE WRITE VERIFICATION MANIFEST\n")
                    .append("Copy this JSON to guided-apply-manifest.json when running scripts/verify-msq-apply.py against the physical before/after MSQ pair.\n")
                    .append(plan.verificationManifestJson());
        }
        return out.toString();
    }

    public MapEstimateCoverageStrategy pendingStrategy() { return pendingStrategy; }
    public MapEstimateCellScope pendingScope() { return pendingScope; }
    public MapEstimateEvidenceBasis pendingEvidenceBasis() { return pendingEvidenceBasis; }
    public MapEstimateProposalLimitPolicy pendingProposalLimitPolicy() { return pendingProposalLimitPolicy; }
    public MapEstimateEvidenceBasis activeEvidenceBasis() { return activeEvidenceBasis; }
    public MapEstimateProposalLimitPolicy activeProposalLimitPolicy() { return activeProposalLimitPolicy; }
    public String status() { return status; }
    public boolean configured() { return session != null; }
    public boolean active() {
        return session != null && (session.state() == MapEstimateEvidenceSession.State.CAPTURING
                || session.state() == MapEstimateEvidenceSession.State.PAUSED);
    }
    public boolean complete() { return session != null && session.state() == MapEstimateEvidenceSession.State.COMPLETE; }
    public long currentRunSamples() { return session == null ? 0 : session.currentRunSamples(); }
    public long storedSamples() { return session == null ? 0 : session.stored().sampleCount(); }

    private void requireEditable() {
        requireConfigured();
        if (active()) throw new IllegalStateException("MAP Estimate setup controls are locked during capture");
    }
    private void requireConfigured() { if (session == null) throw new IllegalStateException("MAP Estimate Table not configured"); }
    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        String value = throwable.getMessage();
        return value == null || value.trim().length() == 0 ? throwable.getClass().getSimpleName()
                : value.replace('\n',' ').replace('\r',' ');
    }
    private static double[][] cloneTable(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int i=0;i<values.length;i++) copy[i]=values[i].clone();
        return copy;
    }
}
