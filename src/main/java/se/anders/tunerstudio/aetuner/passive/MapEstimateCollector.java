package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Collects steady RPM/TPS/MAP samples for a conservative MAP Estimate draft. */
public final class MapEstimateCollector {
    private static final long MIN_ACCEPT_GAP_NS = 40000000L; // 25 Hz max
    private static final double MAX_STABLE_TPS_DOT = 2.0;
    private static final double MAX_STABLE_MAP_DOT = 12.0;

    private double[] rpmBins = new double[0];
    private double[] tpsBins = new double[0];
    private long[][] counts = new long[0][0];
    private double[][] sums = new double[0][0];
    private double[][] sumSquares = new double[0][0];
    private double[][] minimums = new double[0][0];
    private double[][] maximums = new double[0][0];
    private long lastAcceptedNano;
    private long acceptedSamples;

    synchronized void configure(AeProjectSnapshot snapshot) {
        if (snapshot == null) {
            clear();
            return;
        }
        double[] newRpm = snapshot.getMapEstimateRpmBins();
        double[] newTps = snapshot.getMapEstimateTpsBins();
        if (!sameAxis(rpmBins, newRpm) || !sameAxis(tpsBins, newTps)) {
            rpmBins = newRpm;
            tpsBins = newTps;
            counts = new long[tpsBins.length][rpmBins.length];
            sums = new double[tpsBins.length][rpmBins.length];
            sumSquares = new double[tpsBins.length][rpmBins.length];
            minimums = new double[tpsBins.length][rpmBins.length];
            maximums = new double[tpsBins.length][rpmBins.length];
            initializeExtremes();
            acceptedSamples = 0L;
            lastAcceptedNano = 0L;
        }
    }

    synchronized void clear() {
        rpmBins = new double[0];
        tpsBins = new double[0];
        counts = new long[0][0];
        sums = new double[0][0];
        sumSquares = new double[0][0];
        minimums = new double[0][0];
        maximums = new double[0][0];
        acceptedSamples = 0L;
        lastAcceptedNano = 0L;
    }

    synchronized boolean addSample(LiveSample sample) {
        if (sample == null || rpmBins.length == 0 || tpsBins.length == 0) {
            return false;
        }
        if (sample.getNanoTime() - lastAcceptedNano < MIN_ACCEPT_GAP_NS) {
            return false;
        }
        double rpm = sample.get(ChannelRole.RPM);
        double tps = sample.get(ChannelRole.TPS);
        double map = sample.get(ChannelRole.MAP);
        if (!Double.isFinite(rpm) || rpm < 500.0 || !Double.isFinite(tps) || !Double.isFinite(map)) {
            return false;
        }
        if (Math.abs(sample.getTpsDot()) > MAX_STABLE_TPS_DOT || Math.abs(sample.getMapDot()) > MAX_STABLE_MAP_DOT) {
            return false;
        }
        if (sample.bool(ChannelRole.DFCO) || sample.bool(ChannelRole.FUEL_CUT)
                || sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                || sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)
                || sample.bool(ChannelRole.AE_EXTRA_SHOT)) {
            return false;
        }
        if (Math.abs(zeroIfNaN(sample.get(ChannelRole.INSTANT_PULSE_PW))) > 0.0001) {
            return false;
        }
        int row = nearest(tpsBins, tps);
        int col = nearest(rpmBins, rpm);
        counts[row][col]++;
        sums[row][col] += map;
        sumSquares[row][col] += map * map;
        minimums[row][col] = Math.min(minimums[row][col], map);
        maximums[row][col] = Math.max(maximums[row][col], map);
        acceptedSamples++;
        lastAcceptedNano = sample.getNanoTime();
        return true;
    }

    synchronized long getAcceptedSamples() {
        return acceptedSamples;
    }

    synchronized int getCoveredCells(int minimumSamples) {
        int covered = 0;
        for (long[] row : counts) {
            for (long count : row) {
                if (count >= minimumSamples) {
                    covered++;
                }
            }
        }
        return covered;
    }

    public synchronized long[][] copyCounts() {
        long[][] copy = new long[counts.length][];
        for (int row = 0; row < counts.length; row++) {
            copy[row] = counts[row].clone();
        }
        return copy;
    }

    public synchronized double[][] copyMeans() {
        double[][] means = new double[sums.length][];
        for (int row = 0; row < sums.length; row++) {
            means[row] = new double[sums[row].length];
            for (int col = 0; col < sums[row].length; col++) {
                means[row][col] = counts[row][col] > 0 ? sums[row][col] / counts[row][col] : Double.NaN;
            }
        }
        return means;
    }

    public synchronized double[][] copyStandardDeviations() {
        double[][] values = new double[sums.length][];
        for (int row = 0; row < sums.length; row++) {
            values[row] = new double[sums[row].length];
            for (int col = 0; col < sums[row].length; col++) {
                long count = counts[row][col];
                if (count < 2) {
                    values[row][col] = Double.NaN;
                    continue;
                }
                double mean = sums[row][col] / count;
                double variance = Math.max(0.0, (sumSquares[row][col] / count) - mean * mean);
                values[row][col] = Math.sqrt(variance);
            }
        }
        return values;
    }

    public synchronized double[][] copyRanges() {
        double[][] values = new double[sums.length][];
        for (int row = 0; row < sums.length; row++) {
            values[row] = new double[sums[row].length];
            for (int col = 0; col < sums[row].length; col++) {
                values[row][col] = counts[row][col] > 0
                        ? maximums[row][col] - minimums[row][col]
                        : Double.NaN;
            }
        }
        return values;
    }

    synchronized String statusText(int minimumSamples) {
        int total = rpmBins.length * tpsBins.length;
        return "MAP Estimate collection: " + acceptedSamples + " stable sample(s), "
                + getCoveredCells(minimumSamples) + "/" + total + " cell(s) with at least "
                + minimumSamples + " samples.";
    }

    private void initializeExtremes() {
        for (int row = 0; row < minimums.length; row++) {
            for (int col = 0; col < minimums[row].length; col++) {
                minimums[row][col] = Double.POSITIVE_INFINITY;
                maximums[row][col] = Double.NEGATIVE_INFINITY;
            }
        }
    }

    private static int nearest(double[] values, double value) {
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            double distance = Math.abs(values[i] - value);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean sameAxis(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1.0e-6) {
                return false;
            }
        }
        return true;
    }

    private static double zeroIfNaN(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
