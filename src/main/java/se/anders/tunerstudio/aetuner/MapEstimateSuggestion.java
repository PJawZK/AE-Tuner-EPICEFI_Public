package se.anders.tunerstudio.aetuner;

import java.text.DecimalFormat;

/** Builds a read-only, clipboard-ready MAP Estimate draft. */
final class MapEstimateSuggestion {
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final double HIGH_TPS_CAP_START = 33.5;
    private static final double MAX_CELL_STDDEV_KPA = 5.0;
    private static final double MAX_CELL_RANGE_KPA = 20.0;
    private static final double LARGE_CHANGE_WARNING_KPA = 12.0;
    private static final int MAX_DETAIL_LINES = 40;

    private final boolean available;
    private final String displayText;
    private final String copyPasteBlock;

    private MapEstimateSuggestion(boolean available, String displayText, String copyPasteBlock) {
        this.available = available;
        this.displayText = displayText;
        this.copyPasteBlock = copyPasteBlock;
    }

    static MapEstimateSuggestion build(AeProjectSnapshot snapshot,
                                       MapEstimateCollector collector,
                                       int minimumSamples,
                                       double capKpa) {
        if (snapshot == null || !snapshot.hasMapEstimateTable()) {
            return unavailable("MAP Estimate draft unavailable: project MAP Estimate table or axes were not found.");
        }
        if (collector == null) {
            return unavailable("MAP Estimate draft unavailable: no stable-sample collector.");
        }
        double[][] current = snapshot.getMapEstimateTable();
        double[] tpsBins = snapshot.getMapEstimateTpsBins();
        double[] rpmBins = snapshot.getMapEstimateRpmBins();
        long[][] counts = collector.copyCounts();
        double[][] means = collector.copyMeans();
        double[][] deviations = collector.copyStandardDeviations();
        double[][] ranges = collector.copyRanges();
        if (current.length != tpsBins.length || counts.length != tpsBins.length) {
            return unavailable("MAP Estimate draft unavailable: project table dimensions do not match the displayed axes.");
        }

        double[][] proposed = cloneTable(current);
        int observedCells = 0;
        int unstableCells = 0;
        int cappedObserved = 0;
        int unvisitedAboveCap = 0;
        int changedCells = 0;
        int largeChangeCells = 0;
        int lowRpmObservedCells = 0;
        int lowRpmChangedCells = 0;
        double lowRpmLargestIncrease = 0.0;
        double lowRpmLargestDecrease = 0.0;
        StringBuilder coverage = new StringBuilder();
        StringBuilder detail = new StringBuilder();
        int detailLines = 0;

        for (int row = 0; row < proposed.length; row++) {
            int rowCovered = 0;
            for (int col = 0; col < proposed[row].length && col < rpmBins.length; col++) {
                double before = proposed[row][col];
                boolean enoughSamples = row < counts.length && col < counts[row].length
                        && counts[row][col] >= minimumSamples;
                boolean hasMean = row < means.length && col < means[row].length
                        && Double.isFinite(means[row][col]);

                if (enoughSamples && hasMean) {
                    double stddev = deviations[row][col];
                    double range = ranges[row][col];
                    boolean stableSpread = (!Double.isFinite(stddev) || stddev <= MAX_CELL_STDDEV_KPA)
                            && (!Double.isFinite(range) || range <= MAX_CELL_RANGE_KPA);
                    if (!stableSpread) {
                        unstableCells++;
                        if (detailLines < MAX_DETAIL_LINES) {
                            detail.append("- TPS ").append(F2.format(tpsBins[row])).append("% / ")
                                    .append(F2.format(rpmBins[col])).append(" RPM: left unchanged; spread ")
                                    .append(F2.format(stddev)).append(" kPa stddev, ")
                                    .append(F2.format(range)).append(" kPa range across ")
                                    .append(counts[row][col]).append(" samples.\n");
                            detailLines++;
                        }
                    } else {
                        double value = means[row][col];
                        if (tpsBins[row] >= HIGH_TPS_CAP_START && value > capKpa) {
                            value = capKpa;
                            cappedObserved++;
                        }
                        proposed[row][col] = value;
                        observedCells++;
                        rowCovered++;
                        if (rpmBins[col] < 2200.0) {
                            lowRpmObservedCells++;
                        }
                    }
                } else if (tpsBins[row] >= HIGH_TPS_CAP_START && before > capKpa) {
                    // Do not rewrite unvisited cells. A cap without evidence is
                    // shown as a warning so the user can review it separately.
                    unvisitedAboveCap++;
                }

                double change = proposed[row][col] - before;
                if (Math.abs(change) >= 0.01) {
                    changedCells++;
                    if (Math.abs(change) >= LARGE_CHANGE_WARNING_KPA) {
                        largeChangeCells++;
                    }
                    if (rpmBins[col] < 2200.0) {
                        lowRpmChangedCells++;
                        lowRpmLargestIncrease = Math.max(lowRpmLargestIncrease, change);
                        lowRpmLargestDecrease = Math.min(lowRpmLargestDecrease, change);
                    }
                    if (detailLines < MAX_DETAIL_LINES) {
                        double stddev = deviations[row][col];
                        detail.append("- TPS ").append(F2.format(tpsBins[row])).append("% / ")
                                .append(F2.format(rpmBins[col])).append(" RPM: ")
                                .append(F2.format(before)).append(" -> ")
                                .append(F2.format(proposed[row][col])).append(" kPa (Δ ")
                                .append(change >= 0.0 ? "+" : "").append(F2.format(change))
                                .append(", ").append(counts[row][col]).append(" samples, stddev ")
                                .append(Double.isFinite(stddev) ? F2.format(stddev) : "n/a").append(").\n");
                        detailLines++;
                    }
                }
            }
            if (rowCovered > 0) {
                coverage.append("TPS ").append(F2.format(tpsBins[row])).append("%: ")
                        .append(rowCovered).append(" stable observed cell(s). ");
            }
        }

        if (observedCells == 0) {
            return unavailable("MAP Estimate draft unavailable: no cell had at least " + minimumSamples
                    + " stable samples within the spread limits (<= " + F2.format(MAX_CELL_STDDEV_KPA)
                    + " kPa stddev and <= " + F2.format(MAX_CELL_RANGE_KPA) + " kPa range)."
                    + (unvisitedAboveCap > 0 ? " " + unvisitedAboveCap
                    + " unvisited high-TPS cells exceed the selected cap, but were intentionally left unchanged." : ""));
        }

        StringBuilder copy = new StringBuilder();
        // TunerStudio 3D tables display the highest Y-axis row at the top.
        for (int row = proposed.length - 1; row >= 0; row--) {
            for (int col = 0; col < proposed[row].length; col++) {
                if (col > 0) {
                    copy.append('\t');
                }
                copy.append(F2.format(proposed[row][col]));
            }
            if (row > 0) {
                copy.append('\n');
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("MAP Estimate draft copied to clipboard.\n")
                .append("Rows are in descending TPS order for direct TunerStudio paste; columns follow MAP Estimate RPM bins.\n")
                .append("Stable sample requirement: ").append(minimumSamples).append(" per cell.\n")
                .append("Cell-spread safety: <= ").append(F2.format(MAX_CELL_STDDEV_KPA))
                .append(" kPa standard deviation and <= ").append(F2.format(MAX_CELL_RANGE_KPA)).append(" kPa range.\n")
                .append("Turbo protection: observed cells at TPS >= ").append(F2.format(HIGH_TPS_CAP_START))
                .append("% are capped at ").append(F2.format(capKpa)).append(" kPa. Unvisited cells are never changed only because they exceed the cap.\n")
                .append("Stable observed cells used: ").append(observedCells)
                .append(" | unstable observed cells excluded: ").append(unstableCells)
                .append(" | observed values capped: ").append(cappedObserved)
                .append(" | unvisited cells above cap left unchanged: ").append(unvisitedAboveCap)
                .append(" | changed cells: ").append(changedCells)
                .append(" | changes >= ").append(F2.format(LARGE_CHANGE_WARNING_KPA)).append(" kPa: ")
                .append(largeChangeCells).append(".\n")
                .append("Low-RPM review (<2200 RPM): ").append(lowRpmObservedCells)
                .append(" stable observed cell(s), ").append(lowRpmChangedCells).append(" changed; largest increase ")
                .append(F2.format(lowRpmLargestIncrease)).append(" kPa, largest decrease ")
                .append(F2.format(lowRpmLargestDecrease)).append(" kPa.\n")
                .append("Coverage: ").append(coverage.length() == 0 ? "none" : coverage.toString()).append("\n\n")
                .append("Changed/excluded cell details\n")
                .append(detail.length() == 0 ? "- No value changed by at least 0.01 kPa.\n" : detail.toString());
        if (detailLines >= MAX_DETAIL_LINES) {
            report.append("- Additional cell details omitted from this compact report.\n");
        }
        report.append("\nReview before pasting. Only data-backed, sufficiently stable cells are changed; sparse unvisited high-TPS cells are preserved and separately warned about.");
        return new MapEstimateSuggestion(true, report.toString(), copy.toString());
    }

    private static MapEstimateSuggestion unavailable(String reason) {
        return new MapEstimateSuggestion(false, reason, "");
    }

    boolean isAvailable() { return available; }
    String getDisplayText() { return displayText; }
    String getCopyPasteBlock() { return copyPasteBlock; }

    private static double[][] cloneTable(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int row = 0; row < values.length; row++) {
            copy[row] = values[row].clone();
        }
        return copy;
    }
}
