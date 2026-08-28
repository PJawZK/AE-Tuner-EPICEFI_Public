package se.anders.tunerstudio.aetuner.guided.mapestimate;

/** Immutable presentation state for the MAP Estimate Table Guided Focus. */
public final class MapEstimateFocusModel {
    public static final class Cell {
        public final MapEstimateSurface.State state;
        public final MapEstimateSurface.Maturity maturity;
        public final double valueKpa;
        public final long evidenceSamples;
        public final int sessionCount;
        public final double betweenSessionRangeKpa;
        public final double confidence;
        public final String reason;
        public final boolean selected;
        public final boolean currentRun;
        public final boolean proposalChange;

        Cell(MapEstimateSurface.Cell cell, boolean selected, boolean currentRun, boolean proposalChange) {
            this.state = cell.state;
            this.maturity = cell.maturity;
            this.valueKpa = cell.valueKpa;
            this.evidenceSamples = cell.evidenceSamples;
            this.sessionCount = cell.sessionCount;
            this.betweenSessionRangeKpa = cell.betweenSessionRangeKpa;
            this.confidence = cell.confidence;
            this.reason = cell.reason;
            this.selected = selected;
            this.currentRun = currentRun;
            this.proposalChange = proposalChange;
        }
    }

    public final double[] tpsAxis;
    public final double[] rpmAxis;
    public final Cell[][] cells;
    public final MapEstimateCoverageStrategy strategy;
    public final MapEstimateCellScope scope;
    public final MapEstimateEvidenceBasis evidenceBasis;
    public final MapEstimateProposalLimitPolicy proposalLimitPolicy;
    public final int liveRow;
    public final int liveCol;
    public final int targetRow;
    public final int targetCol;
    public final String targetReason;
    public final MapEstimateTargetZone targetZone;
    public final boolean liveTpsInTargetZone;
    public final boolean liveRpmInTargetZone;
    public final boolean liveInTargetZone;
    public final String liveEligibility;
    public final boolean captureActive;
    public final long storedSamples;
    public final long currentRunSamples;
    public final long evidenceSamplesUsed;
    public final int directCount;
    public final int interpolatedStrongCount;
    public final int weakCount;
    public final int conflictCount;
    public final int confirmedCount;
    public final int provisionalCount;
    public final int recheckCount;
    public final int proposalChangeCount;

    private MapEstimateFocusModel(double[] tpsAxis, double[] rpmAxis, Cell[][] cells,
            MapEstimateCoverageStrategy strategy, MapEstimateCellScope scope,
            MapEstimateEvidenceBasis evidenceBasis, MapEstimateProposalLimitPolicy proposalLimitPolicy,
            int liveRow, int liveCol, int targetRow, int targetCol,
            String targetReason, MapEstimateTargetZone targetZone,
            boolean liveTpsInTargetZone, boolean liveRpmInTargetZone,
            String liveEligibility, boolean captureActive,
            long storedSamples, long currentRunSamples, long evidenceSamplesUsed, int directCount,
            int interpolatedStrongCount, int weakCount, int conflictCount,
            int confirmedCount, int provisionalCount, int recheckCount,
            int proposalChangeCount) {
        this.tpsAxis = tpsAxis.clone();
        this.rpmAxis = rpmAxis.clone();
        this.cells = cells;
        this.strategy = strategy;
        this.scope = scope;
        this.evidenceBasis = evidenceBasis;
        this.proposalLimitPolicy = proposalLimitPolicy;
        this.liveRow = liveRow;
        this.liveCol = liveCol;
        this.targetRow = targetRow;
        this.targetCol = targetCol;
        this.targetReason = targetReason == null ? "" : targetReason;
        this.targetZone = targetZone == null
                ? MapEstimateTargetZone.forCell(new double[0], new double[0], -1, -1)
                : targetZone;
        this.liveTpsInTargetZone = liveTpsInTargetZone;
        this.liveRpmInTargetZone = liveRpmInTargetZone;
        this.liveInTargetZone = liveTpsInTargetZone && liveRpmInTargetZone;
        this.liveEligibility = liveEligibility == null ? "" : liveEligibility;
        this.captureActive = captureActive;
        this.storedSamples = storedSamples;
        this.currentRunSamples = currentRunSamples;
        this.evidenceSamplesUsed = evidenceSamplesUsed;
        this.directCount = directCount;
        this.interpolatedStrongCount = interpolatedStrongCount;
        this.weakCount = weakCount;
        this.conflictCount = conflictCount;
        this.confirmedCount = confirmedCount;
        this.provisionalCount = provisionalCount;
        this.recheckCount = recheckCount;
        this.proposalChangeCount = proposalChangeCount;
    }

    public static MapEstimateFocusModel build(MapEstimateEvidenceSession session,
            double[][] currentTable, int minimumSamples, double capKpa,
            double liveTps, double liveRpm, String liveEligibility) {
        return build(session, currentTable, minimumSamples, capKpa, liveTps, liveRpm,
                liveEligibility, session == null ? null : session.strategy(),
                session == null ? null : session.scope(),
                MapEstimateEvidenceBasis.LEARNED_MEMORY,
                MapEstimateProposalLimitPolicy.HIGH_TPS_CAP);
    }

    public static MapEstimateFocusModel build(MapEstimateEvidenceSession session,
            double[][] currentTable, int minimumSamples, double capKpa,
            double liveTps, double liveRpm, String liveEligibility,
            MapEstimateCoverageStrategy strategyOverride,
            MapEstimateCellScope scopeOverride) {
        return build(session,currentTable,minimumSamples,capKpa,liveTps,liveRpm,liveEligibility,
                strategyOverride,scopeOverride,MapEstimateEvidenceBasis.LEARNED_MEMORY,
                MapEstimateProposalLimitPolicy.HIGH_TPS_CAP);
    }

    public static MapEstimateFocusModel build(MapEstimateEvidenceSession session,
            double[][] currentTable, int minimumSamples, double capKpa,
            double liveTps, double liveRpm, String liveEligibility,
            MapEstimateCoverageStrategy strategyOverride,
            MapEstimateCellScope scopeOverride,
            MapEstimateEvidenceBasis evidenceBasisOverride,
            MapEstimateProposalLimitPolicy proposalLimitPolicyOverride) {
        if (session == null) throw new IllegalArgumentException("MAP Estimate session required");
        MapEstimateMemory learned = session.combined();
        MapEstimateMemory currentRun = session.currentRunMemory();
        MapEstimateEvidenceBasis basis = evidenceBasisOverride == null
                ? MapEstimateEvidenceBasis.LEARNED_MEMORY : evidenceBasisOverride;
        MapEstimateProposalLimitPolicy limitPolicy = proposalLimitPolicyOverride == null
                ? MapEstimateProposalLimitPolicy.HIGH_TPS_CAP : proposalLimitPolicyOverride;
        // Current-capture-only is an analysis/proposal isolation mode. Stored
        // memory may still be archived for later use, but it cannot influence
        // this surface, target coach, confidence classification or write plan.
        MapEstimateMemory evidence = basis == MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY
                ? currentRun : learned;
        MapEstimateSurface surface = new MapEstimateSurface(evidence, minimumSamples);
        double[] tps = evidence.tpsAxis();
        double[] rpm = evidence.rpmAxis();
        MapEstimateCellScope scope = scopeOverride == null ? session.scope() : scopeOverride;
        MapEstimateCoverageStrategy strategy = strategyOverride == null ? session.strategy() : strategyOverride;
        MapEstimateProposal proposal = currentTable == null ? null
                : MapEstimateProposal.build(evidence.configuration(), tps, rpm,
                    currentTable, surface, strategy, scope, capKpa, limitPolicy);
        boolean[][] run = currentRunMask(currentRun, tps, rpm);
        Cell[][] cells = new Cell[tps.length][rpm.length];
        for (int r = 0; r < tps.length; r++) {
            for (int c = 0; c < rpm.length; c++) {
                cells[r][c] = new Cell(surface.cell(r,c), scope.contains(r,c), run[r][c],
                        proposal != null && proposal.changed(r,c));
            }
        }
        MapEstimateTargetSelector.Target target = new MapEstimateTargetSelector().choose(
                surface, evidence, currentRun, strategy, scope, liveTps, liveRpm);
        MapEstimateTargetZone zone = MapEstimateTargetZone.forCell(tps, rpm, target.row, target.col);
        boolean active = session.state() == MapEstimateEvidenceSession.State.CAPTURING
                || session.state() == MapEstimateEvidenceSession.State.PAUSED;
        return new MapEstimateFocusModel(tps, rpm, cells, strategy, scope, basis, limitPolicy,
                nearest(tps, liveTps), nearest(rpm, liveRpm),
                target.row, target.col, target.reason, zone,
                zone.containsTps(liveTps), zone.containsRpm(liveRpm),
                liveEligibility, active,
                session.stored().sampleCount(), session.currentRunSamples(), evidence.sampleCount(),
                surface.count(MapEstimateSurface.State.DIRECT),
                surface.count(MapEstimateSurface.State.INTERPOLATED_STRONG),
                surface.count(MapEstimateSurface.State.INTERPOLATED_WEAK),
                surface.count(MapEstimateSurface.State.CONFLICT),
                surface.count(MapEstimateSurface.Maturity.CONFIRMED),
                surface.count(MapEstimateSurface.Maturity.PROVISIONAL),
                surface.count(MapEstimateSurface.Maturity.RECHECK),
                proposal == null ? 0 : proposal.changeCount());
    }

    public boolean hasTable() { return tpsAxis.length > 0 && rpmAxis.length > 0; }
    public int rows() { return cells.length; }
    public int cols() { return cells.length == 0 ? 0 : cells[0].length; }
    public Cell cell(int row, int col) { return cells[row][col]; }
    public boolean isLive(int row, int col) { return row == liveRow && col == liveCol; }
    public boolean isTarget(int row, int col) { return row == targetRow && col == targetCol; }

    private static boolean[][] currentRunMask(MapEstimateMemory memory, double[] tps, double[] rpm) {
        boolean[][] mask = new boolean[tps.length][rpm.length];
        for (MapEstimateEvidenceBucket bucket : memory.buckets()) {
            if (bucket.count() <= 0) continue;
            int row = nearest(tps, bucket.meanTps());
            int col = nearest(rpm, bucket.meanRpm());
            if (row >= 0 && col >= 0) mask[row][col] = true;
        }
        return mask;
    }

    private static int nearest(double[] axis, double value) {
        if (!Double.isFinite(value) || axis.length == 0) return -1;
        int best = 0;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < axis.length; i++) {
            double next = Math.abs(axis[i] - value);
            if (next < distance) { distance = next; best = i; }
        }
        return best;
    }
}
