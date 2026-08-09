package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a conservative, copy/paste-only TPS AE table draft from repeated
 * event guidance. It deliberately does not write anything to the ECU.
 */
public final class AeTableSuggestion {
    private static final DecimalFormat F2 = new DecimalFormat("0.00");

    private final boolean available;
    private final String displayText;
    private final String copyPasteBlock;
    private final int changedCells;

    private AeTableSuggestion(boolean available, String displayText, String copyPasteBlock, int changedCells) {
        this.available = available;
        this.displayText = displayText;
        this.copyPasteBlock = copyPasteBlock;
        this.changedCells = changedCells;
    }

    public static AeTableSuggestion build(AeProjectSnapshot snapshot, List<TransientEvent> events) {
        if (snapshot == null) {
            return unavailable("Read AE project data first, then collect TPS AE fuel-proved events.");
        }
        double[] cycleBins = snapshot.getCycleBins();
        double[] tpsToBins = snapshot.getTpsToBins();
        double[][] current = snapshot.getCycleValues();
        if (cycleBins.length == 0 || tpsToBins.length == 0 || current.length == 0) {
            return unavailable("TPS AE table or axes were not available from TunerStudio project data.");
        }
        int rows = Math.min(tpsToBins.length, current.length);
        int cols = cycleBins.length;
        if (rows == 0 || cols == 0) {
            return unavailable("TPS AE table dimensions were empty.");
        }
        for (int row = 0; row < rows; row++) {
            if (current[row] == null || current[row].length < cols) {
                return unavailable("TPS AE table dimensions did not match the cycle/TPS-to bins. Re-read project data and try again.");
            }
        }

        double[][] factorSum = new double[rows][cols];
        double[][] weightSum = new double[rows][cols];
        int[] directCoverage = new int[rows];
        double[] effectiveCoverage = new double[rows];
        int[] combinedCoverage = new int[rows];
        int[] shapeCoverage = new int[rows];
        int[] rateCoverage = new int[rows];
        int[] amountCoverage = new int[rows];
        int[] leanCoverage = new int[rows];
        int provedEvents = 0;
        int shapeEvents = 0;
        int rateEvents = 0;
        int amountEvents = 0;
        int overallLeanEvents = 0;
        int skippedEvents = 0;
        int excludedWallDominantEvents = 0;
        int missingWallChannelEvents = 0;
        double usableEvidenceWeight = 0.0;

        if (snapshot.isWallWettingEnabled()) {
            boolean wallChannelAvailable = false;
            for (TransientEvent event : events) {
                wallChannelAvailable = wallChannelAvailable || event.isWallWettingPwAvailable();
            }
            if (!wallChannelAvailable) {
                return unavailable("Wall Wetting AE is enabled in the project, but fuel wallwetting injection time was not available in captured events. Multiplier-table attribution would be unsafe.");
            }
        }

        for (TransientEvent event : events) {
            if (!event.isTpsAeFuelProved()) {
                continue;
            }
            double evidenceWeight = event.multiplierSuggestionWeight(snapshot);
            if (evidenceWeight <= 0.0) {
                if (snapshot.isWallWettingEnabled() && !event.isWallWettingPwAvailable()) {
                    missingWallChannelEvents++;
                } else if (snapshot.isWallWettingEnabled()) {
                    excludedWallDominantEvents++;
                }
                skippedEvents++;
                continue;
            }
            String guidance = lower(event.getAeFuelTableGuidance());
            int kind = classifyGuidance(guidance);
            if (kind == 0) {
                skippedEvents++;
                continue;
            }
            int row = nearestRow(tpsToBins, eventTpsTo(event));
            if (row < 0 || row >= rows) {
                skippedEvents++;
                continue;
            }
            provedEvents++;
            directCoverage[row]++;
            effectiveCoverage[row] += evidenceWeight;
            usableEvidenceWeight += evidenceWeight;
            if (evidenceWeight < 0.99) {
                combinedCoverage[row]++;
            }
            if (kind == 1) {
                shapeEvents++;
                shapeCoverage[row]++;
            } else if (kind == 2) {
                rateEvents++;
                rateCoverage[row]++;
            } else if (kind == 3) {
                amountEvents++;
                amountCoverage[row]++;
            } else if (kind == 4) {
                overallLeanEvents++;
                leanCoverage[row]++;
            }

            addEventRecommendation(factorSum, weightSum, cycleBins, row, evidenceWeight, kind);
            if (row > 0) {
                addEventRecommendation(factorSum, weightSum, cycleBins, row - 1, 0.35 * evidenceWeight, kind);
            }
            if (row + 1 < rows) {
                addEventRecommendation(factorSum, weightSum, cycleBins, row + 1, 0.35 * evidenceWeight, kind);
            }
        }

        if (provedEvents < 3 || usableEvidenceWeight < 3.0) {
            return unavailable("Need at least 3.0 effective TPS AE-dominant events with repeatable guidance before building a table draft. Current usable events: "
                    + provedEvents + ", effective evidence " + F2.format(usableEvidenceWeight)
                    + ". Wall Wetting-dominant/mixed events are excluded or down-weighted.");
        }

        double[][] suggested = cloneTable(current, rows, cols);
        int changed = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (tpsToBins[row] <= 0.001) {
                    suggested[row][col] = 0.0;
                    continue;
                }
                // Require direct evidence in the row being changed. Neighbour
                // spreading can shape a covered row, but cannot create coverage.
                if (effectiveCoverage[row] < 3.0 || weightSum[row][col] <= 0.0) {
                    continue;
                }
                double factor = factorSum[row][col] / weightSum[row][col];
                // Keep the tool conservative: one generated draft should not
                // add more than 10% or remove more than 70% from any touched cell.
                factor = Math.max(0.30, Math.min(1.10, factor));
                double oldValue = current[row][col];
                double newValue = round2(Math.max(0.0, oldValue * factor));
                suggested[row][col] = newValue;
                if (Math.abs(newValue - oldValue) >= 0.005) {
                    changed++;
                }
            }
        }

        String copyPaste = buildTunerStudioPasteBlock(tpsToBins, suggested, rows, cols);
        StringBuilder text = new StringBuilder();
        text.append("Suggested TPS AE table draft copied to clipboard. READ-ONLY: plugin did not write to ECU.\n");
        text.append("Project/session expectation: ").append(snapshot.expectedSessionModeText()).append(".\n");
        text.append("Basis: ").append(provedEvents).append(" usable TPS AE fuel-proved event(s), effective evidence ")
                .append(F2.format(usableEvidenceWeight))
                .append(" | early-lean/late-rich ").append(shapeEvents)
                .append(" | late-rich/rate ").append(rateEvents)
                .append(" | early amount ").append(amountEvents)
                .append(" | overall lean ").append(overallLeanEvents)
                .append(" | skipped/mixed ").append(skippedEvents)
                .append(" | Wall Wetting-dominant excluded ").append(excludedWallDominantEvents)
                .append(" | missing Wall Wetting channel ").append(missingWallChannelEvents).append(".\n");
        if (snapshot.isWallWettingEnabled()) {
            text.append("Combined-mode safeguard: TPS AE-isolated/dominant events count fully; events with Wall Wetting at 10-40% of TPS AE count as 0.5; stronger Wall Wetting influence is excluded.\n");
        }
        text.append("Changed cells: ").append(changed)
                .append(". Rows with less than 3.0 effective directly mapped events were left unchanged.\n");
        text.append("Coverage by TPS-to row:\n");
        for (int row = 0; row < rows; row++) {
            text.append("  ").append(F2.format(tpsToBins[row])).append(": ")
                    .append(directCoverage[row]).append(" direct event(s), effective ")
                    .append(F2.format(effectiveCoverage[row])).append(", ")
                    .append(confidenceLabel(effectiveCoverage[row]))
                    .append(" | combined/down-weighted ").append(combinedCoverage[row])
                    .append(" | shape ").append(shapeCoverage[row])
                    .append(" | rate ").append(rateCoverage[row])
                    .append(" | amount ").append(amountCoverage[row])
                    .append(" | overall lean ").append(leanCoverage[row]).append("\n");
        }
        text.append("Changed-cell diff:\n");
        appendDiff(text, tpsToBins, cycleBins, current, suggested, rows, cols);
        text.append("Paste block order: ").append(pasteOrderDescription(tpsToBins)).append(". Columns remain Engine Cycle order.\n\n");
        text.append(copyPaste);
        return new AeTableSuggestion(true, text.toString(), copyPaste, changed);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getCopyPasteBlock() {
        return copyPasteBlock;
    }

    int getChangedCells() {
        return changedCells;
    }

    private static String confidenceLabel(double count) {
        if (count >= 7.0) return "high confidence";
        if (count >= 4.0) return "medium confidence";
        if (count >= 3.0) return "low confidence";
        return "insufficient coverage";
    }

    private static void appendDiff(StringBuilder text,
                                   double[] tpsToBins,
                                   double[] cycleBins,
                                   double[][] current,
                                   double[][] suggested,
                                   int rows,
                                   int cols) {
        int listed = 0;
        for (int row = 0; row < rows; row++) {
            StringBuilder rowDiff = new StringBuilder();
            for (int col = 0; col < cols; col++) {
                double oldValue = current[row][col];
                double newValue = suggested[row][col];
                if (Math.abs(newValue - oldValue) < 0.005) {
                    continue;
                }
                if (rowDiff.length() > 0) rowDiff.append(", ");
                rowDiff.append("cycle ").append(F2.format(cycleBins[col]))
                        .append(" ").append(F2.format(oldValue))
                        .append("->").append(F2.format(newValue));
                listed++;
            }
            if (rowDiff.length() > 0) {
                text.append("  TPS-to ").append(F2.format(tpsToBins[row]))
                        .append(": ").append(rowDiff).append("\n");
            }
        }
        if (listed == 0) {
            text.append("  No cells changed: either coverage was insufficient or the current table already matched the bounded draft.\n");
        }
    }

    private static AeTableSuggestion unavailable(String reason) {
        return new AeTableSuggestion(false, "Suggested TPS AE table draft unavailable: " + reason, "", 0);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int classifyGuidance(String guidance) {
        if (guidance.indexOf("early lean with later rich") >= 0) {
            return 1; // amount/rate split: keep early, shorten late
        }
        if (guidance.indexOf("hangs rich late") >= 0) {
            return 2; // rate/decay only
        }
        if (guidance.indexOf("add a little early ae fuel") >= 0) {
            return 3; // amount only, early cycles
        }
        if (guidance.indexOf("event remains lean overall") >= 0) {
            return 4; // modest amount increase through early/mid
        }
        return 0;
    }

    private static double eventTpsTo(TransientEvent event) {
        double value = event.getMaxTpsAeTo();
        if (Double.isFinite(value) && value > 0.0) {
            return value;
        }
        value = event.getMaxTps();
        if (Double.isFinite(value) && value > 0.0) {
            return value;
        }
        return event.getTpsRise();
    }

    private static int nearestRow(double[] bins, double value) {
        if (!Double.isFinite(value) || bins.length == 0) {
            return -1;
        }
        int best = 0;
        double bestDistance = Math.abs(bins[0] - value);
        for (int i = 1; i < bins.length; i++) {
            double distance = Math.abs(bins[i] - value);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void addEventRecommendation(double[][] factorSum,
                                               double[][] weightSum,
                                               double[] cycleBins,
                                               int row,
                                               double weight,
                                               int kind) {
        for (int col = 0; col < cycleBins.length; col++) {
            double factor = factorForCycle(cycleBins[col], kind);
            factorSum[row][col] += factor * weight;
            weightSum[row][col] += weight;
        }
    }

    private static double factorForCycle(double cycle, int kind) {
        if (kind == 1) { // early lean with later rich
            if (cycle <= 4.0) return 1.00;
            if (cycle <= 6.0) return 0.85;
            if (cycle <= 10.0) return 0.55;
            if (cycle <= 12.0) return 0.20;
            return 0.00;
        }
        if (kind == 2) { // late rich/rate only
            if (cycle <= 4.0) return 1.00;
            if (cycle <= 6.0) return 0.90;
            if (cycle <= 10.0) return 0.60;
            if (cycle <= 12.0) return 0.25;
            return 0.00;
        }
        if (kind == 3) { // early amount
            if (cycle <= 4.0) return 1.08;
            if (cycle <= 6.0) return 1.04;
            return 1.00;
        }
        if (kind == 4) { // overall lean, cautiously add earlier fuel
            if (cycle <= 4.0) return 1.06;
            if (cycle <= 6.0) return 1.03;
            return 1.00;
        }
        return 1.00;
    }

    private static double[][] cloneTable(double[][] values, int rows, int cols) {
        double[][] copy = new double[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                copy[row][col] = values[row][col];
            }
        }
        return copy;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String buildTunerStudioPasteBlock(double[] tpsToBins, double[][] table, int rows, int cols) {
        List<Integer> rowOrder = displayRowOrder(tpsToBins, rows);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rowOrder.size(); i++) {
            int row = rowOrder.get(i);
            for (int col = 0; col < cols; col++) {
                if (col > 0) {
                    out.append('\t');
                }
                out.append(F2.format(table[row][col]));
            }
            if (i + 1 < rowOrder.size()) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String pasteOrderDescription(double[] tpsToBins) {
        if (tpsToBins.length >= 2 && tpsToBins[0] < tpsToBins[tpsToBins.length - 1]) {
            return "high TPS-to row first, matching the usual TunerStudio reversed display";
        }
        return "project row order, because TPS-to bins were not ascending";
    }

    private static List<Integer> displayRowOrder(double[] tpsToBins, int rows) {
        List<Integer> order = new ArrayList<Integer>();
        if (tpsToBins.length >= 2 && tpsToBins[0] < tpsToBins[tpsToBins.length - 1]) {
            for (int row = rows - 1; row >= 0; row--) {
                order.add(Integer.valueOf(row));
            }
        } else {
            for (int row = 0; row < rows; row++) {
                order.add(Integer.valueOf(row));
            }
        }
        return order;
    }
}
