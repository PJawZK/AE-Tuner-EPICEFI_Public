package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.passive.MapEstimateCollector;

/**
 * Immutable, presentation-only MAP Estimate Guided Focus state.
 *
 * The existing MapEstimateCollector remains authoritative for evidence
 * acceptance and the existing draft generator remains authoritative for
 * proposal eligibility. This snapshot only turns those facts into a driver
 * guidance model; it does not change tuning math or ECU write authority.
 */
public final class MapEstimateFocusSnapshot {
    private static final double MAX_STDDEV_KPA = 5.0;
    private static final double MAX_RANGE_KPA = 20.0;
    private static final double ACCEPT_RATE_HZ = 25.0;

    public final double[] tpsBins;
    public final double[] rpmBins;
    public final long[][] counts;
    public final double[][] acceptedSeconds;
    public final double[][] standardDeviations;
    public final double[][] ranges;
    public final int minimumSamples;
    public final int liveRow;
    public final int liveCol;
    public final int targetRow;
    public final int targetCol;
    public final double liveTps;
    public final double liveRpm;
    public final MapEstimateCollector.LiveEligibility eligibility;

    private MapEstimateFocusSnapshot(double[] tpsBins,
                                     double[] rpmBins,
                                     long[][] counts,
                                     double[][] acceptedSeconds,
                                     double[][] standardDeviations,
                                     double[][] ranges,
                                     int minimumSamples,
                                     int liveRow,
                                     int liveCol,
                                     int targetRow,
                                     int targetCol,
                                     double liveTps,
                                     double liveRpm,
                                     MapEstimateCollector.LiveEligibility eligibility) {
        this.tpsBins = cloneArray(tpsBins);
        this.rpmBins = cloneArray(rpmBins);
        this.counts = cloneLongTable(counts);
        this.acceptedSeconds = cloneDoubleTable(acceptedSeconds);
        this.standardDeviations = cloneDoubleTable(standardDeviations);
        this.ranges = cloneDoubleTable(ranges);
        this.minimumSamples = Math.max(3, minimumSamples);
        this.liveRow = liveRow;
        this.liveCol = liveCol;
        this.targetRow = targetRow;
        this.targetCol = targetCol;
        this.liveTps = liveTps;
        this.liveRpm = liveRpm;
        this.eligibility = eligibility == null
                ? MapEstimateCollector.LiveEligibility.NO_TABLE : eligibility;
    }

    public static MapEstimateFocusSnapshot setup(AeProjectSnapshot snapshot,
                                                  int minimumSamples,
                                                  LiveSample live) {
        if (snapshot == null || !snapshot.hasMapEstimateTable()) {
            return empty(minimumSamples);
        }
        double[] tps = snapshot.getMapEstimateTpsBins();
        double[] rpm = snapshot.getMapEstimateRpmBins();
        long[][] counts = new long[tps.length][rpm.length];
        double[][] seconds = emptyDoubleTable(tps.length, rpm.length, 0.0);
        double[][] stddev = emptyDoubleTable(tps.length, rpm.length, Double.NaN);
        double[][] ranges = emptyDoubleTable(tps.length, rpm.length, Double.NaN);
        double liveTps = live == null ? Double.NaN : live.get(ChannelRole.TPS);
        double liveRpm = live == null ? Double.NaN : live.get(ChannelRole.RPM);
        int liveRow = nearestOrMinusOne(tps, liveTps);
        int liveCol = nearestOrMinusOne(rpm, liveRpm);
        int[] target = chooseTarget(tps, rpm, counts, stddev, ranges,
                Math.max(3, minimumSamples), liveRow, liveCol, liveTps, liveRpm);
        return new MapEstimateFocusSnapshot(tps, rpm, counts, seconds, stddev, ranges,
                minimumSamples, liveRow, liveCol, target[0], target[1], liveTps, liveRpm,
                MapEstimateCollector.LiveEligibility.WAITING_FOR_CAPTURE);
    }

    public static MapEstimateFocusSnapshot fromCollector(AeProjectSnapshot snapshot,
                                                          MapEstimateCollector collector,
                                                          int minimumSamples,
                                                          LiveSample live) {
        if (snapshot == null || !snapshot.hasMapEstimateTable() || collector == null) {
            return setup(snapshot, minimumSamples, live);
        }
        double[] tps = collector.copyTpsBins();
        double[] rpm = collector.copyRpmBins();
        long[][] counts = collector.copyCounts();
        double[][] seconds = collector.copyAcceptedSeconds();
        double[][] stddev = collector.copyStandardDeviations();
        double[][] ranges = collector.copyRanges();
        double liveTps = collector.getLastLiveTps();
        double liveRpm = collector.getLastLiveRpm();
        int liveRow = collector.getLastMappedRow();
        int liveCol = collector.getLastMappedCol();
        if ((liveRow < 0 || liveCol < 0) && live != null) {
            liveTps = live.get(ChannelRole.TPS);
            liveRpm = live.get(ChannelRole.RPM);
            liveRow = nearestOrMinusOne(tps, liveTps);
            liveCol = nearestOrMinusOne(rpm, liveRpm);
        }
        int[] target = chooseTarget(tps, rpm, counts, stddev, ranges,
                Math.max(3, minimumSamples), liveRow, liveCol, liveTps, liveRpm);
        return new MapEstimateFocusSnapshot(tps, rpm, counts, seconds, stddev, ranges,
                minimumSamples, liveRow, liveCol, target[0], target[1], liveTps, liveRpm,
                collector.getLastEligibility());
    }

    public static MapEstimateFocusSnapshot empty(int minimumSamples) {
        return new MapEstimateFocusSnapshot(new double[0], new double[0],
                new long[0][0], new double[0][0], new double[0][0], new double[0][0],
                minimumSamples, -1, -1, -1, -1, Double.NaN, Double.NaN,
                MapEstimateCollector.LiveEligibility.NO_TABLE);
    }

    public boolean hasTable() {
        return tpsBins.length > 0 && rpmBins.length > 0;
    }

    public int rowCount() { return tpsBins.length; }
    public int columnCount() { return rpmBins.length; }

    public long countAt(int row, int col) {
        return validCell(row, col) ? counts[row][col] : 0L;
    }

    public double acceptedSecondsAt(int row, int col) {
        return validCell(row, col) ? acceptedSeconds[row][col] : 0.0;
    }

    public double standardDeviationAt(int row, int col) {
        return validCell(row, col) ? standardDeviations[row][col] : Double.NaN;
    }

    public double rangeAt(int row, int col) {
        return validCell(row, col) ? ranges[row][col] : Double.NaN;
    }

    public boolean isMinimumReached(int row, int col) {
        return countAt(row, col) >= minimumSamples;
    }

    public boolean isRangeRejected(int row, int col) {
        if (!isMinimumReached(row, col)) return false;
        double range = rangeAt(row, col);
        return Double.isFinite(range) && range > MAX_RANGE_KPA;
    }

    public boolean isNoisy(int row, int col) {
        if (!isMinimumReached(row, col) || isRangeRejected(row, col)) return false;
        double sd = standardDeviationAt(row, col);
        return Double.isFinite(sd) && sd > MAX_STDDEV_KPA;
    }

    public boolean isComplete(int row, int col) {
        return isMinimumReached(row, col) && !isNoisy(row, col) && !isRangeRejected(row, col);
    }

    public boolean isLiveCell(int row, int col) {
        return row == liveRow && col == liveCol;
    }

    public boolean isTargetCell(int row, int col) {
        return row == targetRow && col == targetCol;
    }

    /**
     * Minimum additional stable time implied by the unchanged 25 Hz collector
     * sample-count gate. This is a display estimate only; the draft still uses
     * actual accepted sample count plus spread/range rules.
     */
    public double minimumSecondsRemaining(int row, int col) {
        long remaining = Math.max(0L, minimumSamples - countAt(row, col));
        return remaining / ACCEPT_RATE_HZ;
    }

    public int completeCellCount() {
        int count = 0;
        for (int row = 0; row < rowCount(); row++) {
            for (int col = 0; col < columnCount(); col++) {
                if (isComplete(row, col)) count++;
            }
        }
        return count;
    }

    public int noisyCellCount() {
        int count = 0;
        for (int row = 0; row < rowCount(); row++) {
            for (int col = 0; col < columnCount(); col++) {
                if (isNoisy(row, col) || isRangeRejected(row, col)) count++;
            }
        }
        return count;
    }

    public int partialCellCount() {
        int count = 0;
        for (int row = 0; row < rowCount(); row++) {
            for (int col = 0; col < columnCount(); col++) {
                long samples = countAt(row, col);
                if (samples > 0 && samples < minimumSamples) count++;
            }
        }
        return count;
    }

    public String targetText() {
        if (!hasTable()) return "No MAP Estimate table loaded.";
        if (targetRow < 0 || targetCol < 0) {
            return "No unfinished clean target remains in the loaded table.";
        }
        return format(tpsBins[targetRow], 1) + "% TPS × "
                + Math.round(rpmBins[targetCol]) + " RPM";
    }

    public String liveCellText() {
        if (liveRow < 0 || liveCol < 0) return "Live cell: n/a";
        return "Live cell: " + format(tpsBins[liveRow], 1) + "% TPS × "
                + Math.round(rpmBins[liveCol]) + " RPM";
    }

    public String instructionText(GuidedCaptureState state) {
        if (!hasTable()) {
            return "Read Working Tune first so the MAP Estimate TPS/RPM table can be displayed.";
        }
        if (state == GuidedCaptureState.IDLE) {
            return "Start Capture. The live operating cell will be highlighted and only accepted stable evidence advances its timer.";
        }
        if (state == GuidedCaptureState.PAUSED) {
            return "Capture paused — resume when ready. Heat-map evidence remains retained.";
        }
        if (state == GuidedCaptureState.COMPLETE) {
            return "Capture complete — review green, partial and noisy cells before export or Continue Capture.";
        }
        if (liveRow < 0 || liveCol < 0) {
            return eligibility.getDisplayText();
        }
        if (isRangeRejected(liveRow, liveCol)) {
            return "This cell exceeded the 20 kPa session range gate. More samples cannot repair that range in this session; move to another highlighted cell or reset later to recollect it.";
        }
        if (isNoisy(liveRow, liveCol)) {
            return "This cell has enough samples but SD is still above 5 kPa. Keep the operating point steady to see whether additional evidence stabilizes it.";
        }
        if (isComplete(liveRow, liveCol)) {
            if (targetRow >= 0 && targetCol >= 0) {
                return "Current cell complete. Move smoothly toward the suggested nearby target: " + targetText() + ".";
            }
            return "Current cell complete. No unfinished clean target remains.";
        }
        if (eligibility.isCollecting()) {
            return "Hold steady in " + liveCellText().substring("Live cell: ".length())
                    + " — approximately " + format(minimumSecondsRemaining(liveRow, liveCol), 1)
                    + " s minimum stable evidence remaining.";
        }
        return eligibility.getDisplayText();
    }

    private boolean validCell(int row, int col) {
        return row >= 0 && row < counts.length
                && col >= 0 && col < (counts[row] == null ? 0 : counts[row].length);
    }

    private static int[] chooseTarget(double[] tps,
                                      double[] rpm,
                                      long[][] counts,
                                      double[][] stddev,
                                      double[][] ranges,
                                      int minimumSamples,
                                      int liveRow,
                                      int liveCol,
                                      double liveTps,
                                      double liveRpm) {
        if (tps.length == 0 || rpm.length == 0) return new int[]{-1, -1};

        if (liveRow >= 0 && liveRow < tps.length && liveCol >= 0 && liveCol < rpm.length
                && targetEligible(counts, stddev, ranges, minimumSamples, liveRow, liveCol)) {
            return new int[]{liveRow, liveCol};
        }

        int[] partial = nearestCandidate(tps, rpm, counts, stddev, ranges,
                minimumSamples, liveRow, liveCol, liveTps, liveRpm, true);
        if (partial[0] >= 0) return partial;
        return nearestCandidate(tps, rpm, counts, stddev, ranges,
                minimumSamples, liveRow, liveCol, liveTps, liveRpm, false);
    }

    private static int[] nearestCandidate(double[] tps,
                                          double[] rpm,
                                          long[][] counts,
                                          double[][] stddev,
                                          double[][] ranges,
                                          int minimumSamples,
                                          int liveRow,
                                          int liveCol,
                                          double liveTps,
                                          double liveRpm,
                                          boolean partialOnly) {
        int bestRow = -1;
        int bestCol = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int row = 0; row < tps.length; row++) {
            for (int col = 0; col < rpm.length; col++) {
                if (!targetEligible(counts, stddev, ranges, minimumSamples, row, col)) continue;
                long count = safeCount(counts, row, col);
                if (partialOnly && count <= 0) continue;
                double score;
                if (liveRow >= 0 && liveCol >= 0) {
                    score = Math.abs(row - liveRow) + Math.abs(col - liveCol);
                } else if (Double.isFinite(liveTps) && Double.isFinite(liveRpm)) {
                    double tpsScale = axisStep(tps);
                    double rpmScale = axisStep(rpm);
                    score = Math.abs(tps[row] - liveTps) / tpsScale
                            + Math.abs(rpm[col] - liveRpm) / rpmScale;
                } else {
                    score = count > 0 ? 0.0 : row + col;
                }
                if (count > 0) score -= 0.25;
                if (liveRow >= 0 && row == liveRow) score -= 0.10;
                if (liveCol >= 0 && col == liveCol) score -= 0.03;
                if (score < bestScore) {
                    bestScore = score;
                    bestRow = row;
                    bestCol = col;
                }
            }
        }
        return new int[]{bestRow, bestCol};
    }

    private static boolean targetEligible(long[][] counts,
                                          double[][] stddev,
                                          double[][] ranges,
                                          int minimumSamples,
                                          int row,
                                          int col) {
        long count = safeCount(counts, row, col);
        if (count < minimumSamples) return true;
        double sd = safeDouble(stddev, row, col);
        double range = safeDouble(ranges, row, col);
        if (Double.isFinite(range) && range > MAX_RANGE_KPA) return false;
        return Double.isFinite(sd) && sd > MAX_STDDEV_KPA;
    }

    private static long safeCount(long[][] values, int row, int col) {
        if (values == null || row < 0 || row >= values.length || values[row] == null
                || col < 0 || col >= values[row].length) return 0L;
        return values[row][col];
    }

    private static double safeDouble(double[][] values, int row, int col) {
        if (values == null || row < 0 || row >= values.length || values[row] == null
                || col < 0 || col >= values[row].length) return Double.NaN;
        return values[row][col];
    }

    private static int nearestOrMinusOne(double[] values, double value) {
        if (values == null || values.length == 0 || !Double.isFinite(value)) return -1;
        int best = 0;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            double next = Math.abs(values[i] - value);
            if (next < distance) {
                distance = next;
                best = i;
            }
        }
        return best;
    }

    private static double axisStep(double[] values) {
        if (values == null || values.length < 2) return 1.0;
        double total = 0.0;
        int count = 0;
        for (int i = 1; i < values.length; i++) {
            double step = Math.abs(values[i] - values[i - 1]);
            if (step > 1.0e-9) {
                total += step;
                count++;
            }
        }
        return count == 0 ? 1.0 : total / count;
    }

    private static double[] cloneArray(double[] values) {
        return values == null ? new double[0] : values.clone();
    }

    private static long[][] cloneLongTable(long[][] values) {
        if (values == null) return new long[0][0];
        long[][] copy = new long[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i] == null ? new long[0] : values[i].clone();
        }
        return copy;
    }

    private static double[][] cloneDoubleTable(double[][] values) {
        if (values == null) return new double[0][0];
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i] == null ? new double[0] : values[i].clone();
        }
        return copy;
    }

    private static double[][] emptyDoubleTable(int rows, int cols, double value) {
        double[][] table = new double[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) table[row][col] = value;
        }
        return table;
    }

    private static String format(double value, int decimals) {
        if (!Double.isFinite(value)) return "n/a";
        return String.format(java.util.Locale.US, decimals == 0 ? "%.0f" : "%.1f", value);
    }
}
