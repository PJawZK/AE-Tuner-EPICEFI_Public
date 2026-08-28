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
    private static final long MAX_CONTIGUOUS_EVIDENCE_GAP_NS = 250000000L;
    private static final double MAX_STABLE_TPS_DOT = 2.0;
    private static final double MAX_STABLE_MAP_DOT = 12.0;

    /** Driver-facing live reason. This never changes draft eligibility math. */
    public enum LiveEligibility {
        NO_TABLE("MAP Estimate table is not loaded.", false),
        WAITING_FOR_CAPTURE("Ready — start capture to accumulate stable-cell evidence.", false),
        MISSING_REQUIRED("A required MAP Estimate channel is incomplete — accepted evidence is paused.", false),
        MISSING_CORE("Waiting for finite RPM, TPS and MAP.", false),
        RPM_BELOW_MINIMUM("RPM below the 500 RPM collection floor.", false),
        TPS_MOVING("TPS is moving too quickly — hold the pedal steady.", false),
        MAP_MOVING("MAP is changing too quickly — wait for load to settle.", false),
        DFCO_OR_FUEL_CUT("DFCO / fuel cut active — stable MAP evidence is paused.", false),
        MAP_PREDICT_ACTIVE("MAP Predict active — wait for measured MAP operation.", false),
        TPS_AE_ACTIVE("TPS AE / extra-shot activity present — wait for the transient to finish.", false),
        INSTANT_FUEL_ACTIVE("Instant Fuel pulse active — wait for the transient to finish.", false),
        ELIGIBLE_RATE_LIMITED("Stable and eligible — collector is rate-limited to 25 Hz.", true),
        ACCEPTED("Stable evidence accepted.", true);

        private final String displayText;
        private final boolean collecting;

        LiveEligibility(String displayText, boolean collecting) {
            this.displayText = displayText;
            this.collecting = collecting;
        }

        public String getDisplayText() { return displayText; }
        public boolean isCollecting() { return collecting; }
    }

    private double[] rpmBins = new double[0];
    private double[] tpsBins = new double[0];
    private long[][] counts = new long[0][0];
    private double[][] sums = new double[0][0];
    private double[][] sumSquares = new double[0][0];
    private double[][] minimums = new double[0][0];
    private double[][] maximums = new double[0][0];
    private double[][] acceptedSeconds = new double[0][0];
    private long lastAcceptedNano;
    private long acceptedSamples;

    private long lastEvidenceNano;
    private int lastEvidenceRow = -1;
    private int lastEvidenceCol = -1;
    private int lastMappedRow = -1;
    private int lastMappedCol = -1;
    private double lastLiveTps = Double.NaN;
    private double lastLiveRpm = Double.NaN;
    private LiveEligibility lastEligibility = LiveEligibility.NO_TABLE;

    public synchronized void configure(AeProjectSnapshot snapshot) {
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
            acceptedSeconds = new double[tpsBins.length][rpmBins.length];
            initializeExtremes();
            acceptedSamples = 0L;
            lastAcceptedNano = 0L;
            resetLiveState(rpmBins.length == 0 || tpsBins.length == 0
                    ? LiveEligibility.NO_TABLE : LiveEligibility.WAITING_FOR_CAPTURE);
        }
    }

    public synchronized void clear() {
        rpmBins = new double[0];
        tpsBins = new double[0];
        counts = new long[0][0];
        sums = new double[0][0];
        sumSquares = new double[0][0];
        minimums = new double[0][0];
        maximums = new double[0][0];
        acceptedSeconds = new double[0][0];
        acceptedSamples = 0L;
        lastAcceptedNano = 0L;
        resetLiveState(LiveEligibility.NO_TABLE);
    }

    /** Presentation-only pause when the enclosing Guided route lacks a required channel. */
    public synchronized void pauseForIncompleteRequiredData(LiveSample sample) {
        if (sample != null) {
            lastLiveRpm = sample.get(ChannelRole.RPM);
            lastLiveTps = sample.get(ChannelRole.TPS);
            if (Double.isFinite(lastLiveRpm) && Double.isFinite(lastLiveTps)
                    && rpmBins.length > 0 && tpsBins.length > 0) {
                lastMappedRow = nearest(tpsBins, lastLiveTps);
                lastMappedCol = nearest(rpmBins, lastLiveRpm);
            } else {
                lastMappedRow = -1;
                lastMappedCol = -1;
            }
        }
        lastEligibility = LiveEligibility.MISSING_REQUIRED;
        breakEvidenceContinuity();
    }

    public synchronized boolean addSample(LiveSample sample) {
        if (sample == null || rpmBins.length == 0 || tpsBins.length == 0) {
            lastEligibility = LiveEligibility.NO_TABLE;
            breakEvidenceContinuity();
            return false;
        }

        double rpm = sample.get(ChannelRole.RPM);
        double tps = sample.get(ChannelRole.TPS);
        double map = sample.get(ChannelRole.MAP);
        lastLiveRpm = rpm;
        lastLiveTps = tps;
        if (Double.isFinite(rpm) && Double.isFinite(tps)) {
            lastMappedRow = nearest(tpsBins, tps);
            lastMappedCol = nearest(rpmBins, rpm);
        } else {
            lastMappedRow = -1;
            lastMappedCol = -1;
        }

        if (!Double.isFinite(rpm) || !Double.isFinite(tps) || !Double.isFinite(map)) {
            return reject(LiveEligibility.MISSING_CORE);
        }
        if (rpm < 500.0) {
            return reject(LiveEligibility.RPM_BELOW_MINIMUM);
        }
        if (Math.abs(sample.getTpsDot()) > MAX_STABLE_TPS_DOT) {
            return reject(LiveEligibility.TPS_MOVING);
        }
        if (Math.abs(sample.getMapDot()) > MAX_STABLE_MAP_DOT) {
            return reject(LiveEligibility.MAP_MOVING);
        }
        if (sample.bool(ChannelRole.DFCO) || sample.bool(ChannelRole.FUEL_CUT)) {
            return reject(LiveEligibility.DFCO_OR_FUEL_CUT);
        }
        if (sample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
            return reject(LiveEligibility.MAP_PREDICT_ACTIVE);
        }
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD) || sample.bool(ChannelRole.AE_EXTRA_SHOT)) {
            return reject(LiveEligibility.TPS_AE_ACTIVE);
        }
        if (Math.abs(zeroIfNaN(sample.get(ChannelRole.INSTANT_PULSE_PW))) > 0.0001) {
            return reject(LiveEligibility.INSTANT_FUEL_ACTIVE);
        }

        if (sample.getNanoTime() - lastAcceptedNano < MIN_ACCEPT_GAP_NS) {
            lastEligibility = LiveEligibility.ELIGIBLE_RATE_LIMITED;
            return false;
        }

        int row = nearest(tpsBins, tps);
        int col = nearest(rpmBins, rpm);
        counts[row][col]++;
        sums[row][col] += map;
        sumSquares[row][col] += map * map;
        minimums[row][col] = Math.min(minimums[row][col], map);
        maximums[row][col] = Math.max(maximums[row][col], map);
        acceptedSeconds[row][col] += evidenceSeconds(sample.getNanoTime(), row, col);
        acceptedSamples++;
        lastAcceptedNano = sample.getNanoTime();
        lastEvidenceNano = sample.getNanoTime();
        lastEvidenceRow = row;
        lastEvidenceCol = col;
        lastEligibility = LiveEligibility.ACCEPTED;
        return true;
    }

    public synchronized long getAcceptedSamples() { return acceptedSamples; }

    public synchronized int getCoveredCells(int minimumSamples) {
        int covered = 0;
        for (long[] row : counts) {
            for (long count : row) {
                if (count >= minimumSamples) covered++;
            }
        }
        return covered;
    }

    public synchronized long[][] copyCounts() {
        long[][] copy = new long[counts.length][];
        for (int row = 0; row < counts.length; row++) copy[row] = counts[row].clone();
        return copy;
    }

    public synchronized double[][] copyAcceptedSeconds() { return cloneTable(acceptedSeconds); }
    public synchronized double[] copyRpmBins() { return rpmBins.clone(); }
    public synchronized double[] copyTpsBins() { return tpsBins.clone(); }
    public synchronized int getLastMappedRow() { return lastMappedRow; }
    public synchronized int getLastMappedCol() { return lastMappedCol; }
    public synchronized double getLastLiveTps() { return lastLiveTps; }
    public synchronized double getLastLiveRpm() { return lastLiveRpm; }
    public synchronized LiveEligibility getLastEligibility() { return lastEligibility; }

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

    public synchronized String statusText(int minimumSamples) {
        int total = rpmBins.length * tpsBins.length;
        return "MAP Estimate collection: " + acceptedSamples + " stable sample(s), "
                + getCoveredCells(minimumSamples) + "/" + total + " cell(s) with at least "
                + minimumSamples + " samples.";
    }

    private boolean reject(LiveEligibility reason) {
        lastEligibility = reason;
        breakEvidenceContinuity();
        return false;
    }

    private void breakEvidenceContinuity() {
        lastEvidenceNano = 0L;
        lastEvidenceRow = -1;
        lastEvidenceCol = -1;
    }

    private double evidenceSeconds(long now, int row, int col) {
        double nominal = MIN_ACCEPT_GAP_NS / 1000000000.0;
        if (now <= 0L || lastEvidenceNano <= 0L || row != lastEvidenceRow || col != lastEvidenceCol) {
            return nominal;
        }
        long gap = now - lastEvidenceNano;
        if (gap < MIN_ACCEPT_GAP_NS || gap > MAX_CONTIGUOUS_EVIDENCE_GAP_NS) return nominal;
        return gap / 1000000000.0;
    }

    private void resetLiveState(LiveEligibility initial) {
        lastMappedRow = -1;
        lastMappedCol = -1;
        lastLiveTps = Double.NaN;
        lastLiveRpm = Double.NaN;
        lastEligibility = initial;
        breakEvidenceContinuity();
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
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1.0e-6) return false;
        }
        return true;
    }

    private static double zeroIfNaN(double value) { return Double.isFinite(value) ? value : 0.0; }

    private static double[][] cloneTable(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int row = 0; row < values.length; row++) copy[row] = values[row].clone();
        return copy;
    }
}
