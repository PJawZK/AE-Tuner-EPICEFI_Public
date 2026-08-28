package se.anders.tunerstudio.aetuner.guided.mapestimate;

/**
 * Driveable operating window around one MAP Estimate table coordinate.
 * The bounds deliberately mirror the tight direct-evidence acceptance zone,
 * not the much wider nearest-table-cell Voronoi region that Archive25 showed
 * can mix real surface slope into apparent MAP noise.
 */
public final class MapEstimateTargetZone {
    public final int row;
    public final int col;
    public final double targetTps;
    public final double targetRpm;
    public final double minTps;
    public final double maxTps;
    public final double minRpm;
    public final double maxRpm;

    private MapEstimateTargetZone(int row, int col,
                                  double targetTps, double targetRpm,
                                  double minTps, double maxTps,
                                  double minRpm, double maxRpm) {
        this.row = row;
        this.col = col;
        this.targetTps = targetTps;
        this.targetRpm = targetRpm;
        this.minTps = minTps;
        this.maxTps = maxTps;
        this.minRpm = minRpm;
        this.maxRpm = maxRpm;
    }

    public static MapEstimateTargetZone forCell(double[] tpsAxis, double[] rpmAxis,
                                                 int row, int col) {
        if (tpsAxis == null || rpmAxis == null
                || row < 0 || row >= tpsAxis.length
                || col < 0 || col >= rpmAxis.length) {
            return unavailable();
        }
        double tps = tpsAxis[row];
        double rpm = rpmAxis[col];
        double tpsTolerance = directTolerance(tpsAxis, row, 2.25);
        double rpmTolerance = directTolerance(rpmAxis, col, 150.0);
        return new MapEstimateTargetZone(row, col, tps, rpm,
                Math.max(0.0, tps - tpsTolerance),
                Math.min(100.0, tps + tpsTolerance),
                Math.max(0.0, rpm - rpmTolerance),
                rpm + rpmTolerance);
    }

    public boolean available() { return row >= 0 && col >= 0; }
    public boolean containsTps(double tps) {
        return available() && Double.isFinite(tps) && tps >= minTps && tps <= maxTps;
    }
    public boolean containsRpm(double rpm) {
        return available() && Double.isFinite(rpm) && rpm >= minRpm && rpm <= maxRpm;
    }
    public boolean contains(double tps, double rpm) {
        return containsTps(tps) && containsRpm(rpm);
    }

    private static MapEstimateTargetZone unavailable() {
        return new MapEstimateTargetZone(-1, -1,
                Double.NaN, Double.NaN,
                Double.NaN, Double.NaN,
                Double.NaN, Double.NaN);
    }

    static double directTolerance(double[] axis, int index, double cap) {
        double step = Double.POSITIVE_INFINITY;
        if (index > 0) step = Math.min(step, Math.abs(axis[index] - axis[index - 1]));
        if (index + 1 < axis.length) step = Math.min(step, Math.abs(axis[index + 1] - axis[index]));
        if (!Double.isFinite(step)) step = cap * 2.0;
        return Math.min(cap, Math.max(cap * 0.45, step * 0.35));
    }
}
