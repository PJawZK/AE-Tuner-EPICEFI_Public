package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;

/** Dedicated AE Foundation 2 Threshold / Sensitivity driver and evidence Focus. */
public final class FoundationThresholdGuidedFocusPanel extends JPanel {
    private static final String CARD_DRIVER = "driver";
    private static final String CARD_DETAILS = "details";

    private final CardLayout cardsLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardsLayout);
    private final JPanel driver = new JPanel();
    private final JPanel details = new JPanel(new BorderLayout(7, 7));

    private final JTextArea driverInstruction = area("READ WORKING TUNE", 2, 30f, Font.BOLD);
    private final JLabel driverState = new JLabel("Threshold / Sensitivity", SwingConstants.CENTER);
    private final JLabel driverLive = new JLabel("Fuel: TPS AE change / AccelThreshold: n/a", SwingConstants.CENTER);
    private final JLabel crossingStatus = new JLabel("Threshold crossing: n/a", SwingConstants.CENTER);
    private final JProgressBar crossing = bar(0, 200);
    private final JLabel quietStatus = new JLabel("Quiet calibration: n/a", SwingConstants.CENTER);
    private final JProgressBar quietProgress = bar(0, 100);
    private final JLabel driverBin = new JLabel("RPM region: n/a", SwingConstants.CENTER);
    private final JLabel eventCounts = new JLabel("Normal Corrections 0/3 · Acceleration Openings 0/3", SwingConstants.CENTER);
    private final JProgressBar normalProgress = bar(0, 3);
    private final JProgressBar accelerationProgress = bar(0, 3);
    private final JLabel separationStatus = new JLabel("Separation: waiting for event evidence", SwingConstants.CENTER);
    private final JTextArea lastAttempt = area("Last attempt: none yet.", 2, 16f, Font.BOLD);

    private final JLabel currentTune = new JLabel("Working tune not read");
    private final JLabel recommendation = new JLabel("WITHHOLD — working tune not read");
    private final JLabel liveValues = new JLabel("Live: n/a");
    private final JLabel evidenceSummary = new JLabel("Evidence: n/a");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Broad RPM region", "Current", "Normal corrections", "Acceleration openings",
                    "Normal upper p95", "Acceleration lower p25", "Effective window", "Gap / required",
                    "Static window", "Decision"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable bins = new JTable(tableModel);
    private final JTextArea review = area("No Threshold / Sensitivity review yet.", 8, 12f, Font.PLAIN);
    private boolean driverView = true;

    public FoundationThresholdGuidedFocusPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildDriver();
        buildDetails();
        cards.add(driver, CARD_DRIVER);
        cards.add(details, CARD_DETAILS);
        add(cards, BorderLayout.CENTER);
        setDriverView(true);
    }

    private void buildDriver() {
        driver.setLayout(new BoxLayout(driver, BoxLayout.Y_AXIS));
        driver.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        driverState.setFont(driverState.getFont().deriveFont(Font.BOLD, 16f));
        driverState.setAlignmentX(CENTER_ALIGNMENT);
        driverInstruction.setOpaque(false);
        driverInstruction.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        driverInstruction.setAlignmentX(CENTER_ALIGNMENT);
        driverLive.setAlignmentX(CENTER_ALIGNMENT);
        crossingStatus.setAlignmentX(CENTER_ALIGNMENT);
        quietStatus.setAlignmentX(CENTER_ALIGNMENT);
        driverBin.setAlignmentX(CENTER_ALIGNMENT);
        eventCounts.setAlignmentX(CENTER_ALIGNMENT);
        separationStatus.setAlignmentX(CENTER_ALIGNMENT);
        eventCounts.setFont(eventCounts.getFont().deriveFont(Font.BOLD, 18f));
        separationStatus.setFont(separationStatus.getFont().deriveFont(Font.BOLD, 18f));
        lastAttempt.setOpaque(false);
        lastAttempt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        lastAttempt.setAlignmentX(CENTER_ALIGNMENT);

        crossing.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        quietProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        normalProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        accelerationProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        crossing.setStringPainted(false);
        quietProgress.setStringPainted(false);
        normalProgress.setStringPainted(false);
        accelerationProgress.setStringPainted(false);

        JPanel eventBars = new JPanel(new GridLayout(1, 2, 12, 0));
        JPanel normalBox = new JPanel(new BorderLayout(0, 3));
        normalBox.setBorder(BorderFactory.createTitledBorder("NORMAL CORRECTIONS — small pedal adjustments, no acceleration request"));
        normalBox.add(normalProgress, BorderLayout.CENTER);
        JPanel accelBox = new JPanel(new BorderLayout(0, 3));
        accelBox.setBorder(BorderFactory.createTitledBorder("ACCELERATION OPENINGS — clear normal quick openings to accelerate"));
        accelBox.add(accelerationProgress, BorderLayout.CENTER);
        eventBars.add(normalBox);
        eventBars.add(accelBox);
        eventBars.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));

        driver.add(driverState);
        driver.add(Box.createVerticalStrut(6));
        driver.add(driverInstruction);
        driver.add(Box.createVerticalStrut(8));
        driver.add(driverLive);
        driver.add(Box.createVerticalStrut(4));
        driver.add(crossingStatus);
        driver.add(crossing);
        driver.add(Box.createVerticalStrut(9));
        driver.add(quietStatus);
        driver.add(quietProgress);
        driver.add(Box.createVerticalStrut(10));
        driver.add(driverBin);
        driver.add(Box.createVerticalStrut(5));
        driver.add(eventCounts);
        driver.add(eventBars);
        driver.add(Box.createVerticalStrut(7));
        driver.add(separationStatus);
        driver.add(Box.createVerticalStrut(5));
        driver.add(lastAttempt);
    }

    private void buildDetails() {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel("AE Foundation 2 — Threshold / Sensitivity");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.NORTH);
        header.add(currentTune, BorderLayout.CENTER);
        recommendation.setFont(recommendation.getFont().deriveFont(Font.BOLD));
        header.add(recommendation, BorderLayout.SOUTH);
        details.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        JPanel live = new JPanel(new GridLayout(2, 1, 3, 3));
        live.setBorder(BorderFactory.createTitledBorder("LIVE THRESHOLD DIAGNOSTICS"));
        live.add(liveValues);
        live.add(evidenceSummary);
        center.add(live, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(bins);
        tableScroll.setBorder(BorderFactory.createTitledBorder("RPM-REGION NORMAL CORRECTION / ACCELERATION OPENING SEPARATION"));
        tableScroll.getVerticalScrollBar().setUnitIncrement(18);
        center.add(tableScroll, BorderLayout.CENTER);
        details.add(center, BorderLayout.CENTER);

        JScrollPane reviewScroll = new JScrollPane(review);
        reviewScroll.setPreferredSize(new Dimension(900, 190));
        reviewScroll.setBorder(BorderFactory.createTitledBorder("RECOMMENDATION / WITHHOLD EVIDENCE"));
        reviewScroll.getVerticalScrollBar().setUnitIncrement(18);
        details.add(reviewScroll, BorderLayout.SOUTH);
    }

    public void setDriverView(boolean driverView) {
        this.driverView = driverView;
        cardsLayout.show(cards, driverView ? CARD_DRIVER : CARD_DETAILS);
    }

    public void updateModel(FoundationThresholdFocusModel model, String fallbackGuidance) {
        if (model == null) {
            driverInstruction.setText("READ WORKING TUNE");
            currentTune.setText(fallbackGuidance == null || fallbackGuidance.length() == 0
                    ? "Working tune not read" : fallbackGuidance);
            recommendation.setText("WITHHOLD — Threshold / Sensitivity evidence model unavailable");
            return;
        }

        driverInstruction.setText(model.driverInstruction());
        driverInstruction.setCaretPosition(0);
        driverState.setText(model.recommendationStatus());
        driverLive.setText("RPM " + f0(model.liveRpm) + " · TPS " + f1(model.liveTps)
                + "% · Fuel: TPS AE change " + f3(model.liveDelta)
                + " · AccelThreshold " + f3(model.liveThreshold));

        int ratio = Double.isFinite(model.liveRatio) ? (int) Math.round(model.liveRatio * 100.0) : 0;
        crossing.setValue(Math.max(0, Math.min(200, ratio)));
        crossingStatus.setText(Double.isFinite(model.liveRatio)
                ? "LIVE THRESHOLD POSITION — " + f0(model.liveRatio * 100.0)
                    + "% of AccelThreshold · 100% = crossing"
                : "LIVE THRESHOLD POSITION — n/a");

        int quietCountPct = model.quietTarget <= 0 ? 0
                : Math.min(100, model.quietSamples * 100 / model.quietTarget);
        int quietTimePct = (int) Math.min(100.0,
                model.quietDurationSeconds * 100.0 / FoundationThresholdFocusModel.QUIET_CALIBRATION_MIN_SECONDS);
        quietProgress.setValue(Math.min(quietCountPct, quietTimePct));
        quietStatus.setText((model.calibrationFrozen ? "QUIET CALIBRATION LOCKED — " : "QUIET CALIBRATION — ")
                + model.quietSamples + "/" + model.quietTarget + " samples · "
                + f2(model.quietDurationSeconds) + "/"
                + f2(FoundationThresholdFocusModel.QUIET_CALIBRATION_MIN_SECONDS)
                + " continuous s · p95 " + f2(model.quietRateP95) + " %/s");

        FoundationThresholdFocusModel.Bin liveBin = model.liveBin();
        if (liveBin == null) {
            driverBin.setText("RPM region: n/a");
            normalProgress.setValue(0);
            accelerationProgress.setValue(0);
            eventCounts.setText("Normal Corrections 0/3 · Acceleration Openings 0/3");
            separationStatus.setText("SEPARATION — waiting for event evidence");
            lastAttempt.setText("Last attempt: none yet.");
        } else {
            driverBin.setText(liveBin.regionLabel + " · current threshold "
                    + f3(liveBin.currentThreshold));
            normalProgress.setValue(Math.min(3, liveBin.normalCorrectionEvents));
            accelerationProgress.setValue(Math.min(3, liveBin.accelerationOpeningEvents));
            eventCounts.setText("NORMAL CORRECTIONS " + liveBin.normalCorrectionEvents + "/3   ·   "
                    + "ACCELERATION OPENINGS " + liveBin.accelerationOpeningEvents + "/3"
                    + (liveBin.rejectedEvents > 0 ? "   ·   REJECTED " + liveBin.rejectedEvents : ""));

            if (liveBin.effectiveValidated) {
                separationStatus.setText("SEPARATION VALID — gap " + signed(liveBin.separationGap)
                        + " · required " + f3(liveBin.requiredSeparation));
            } else if (liveBin.eventCountsComplete()
                    && Double.isFinite(liveBin.separationGap)
                    && Double.isFinite(liveBin.requiredSeparation)) {
                separationStatus.setText("SEPARATION OPEN — gap " + signed(liveBin.separationGap)
                        + " · need at least +" + f3(liveBin.requiredSeparation)
                        + " · NEXT: " + liveBin.requestedClassLabel());
            } else {
                separationStatus.setText("SEPARATION WAITING — event counts are not complete · NEXT: "
                        + liveBin.requestedClassLabel());
            }

            if (liveBin.lastRejectedReason.length() > 0) {
                lastAttempt.setText("LAST REJECTED ATTEMPT — " + liveBin.lastRejectedReason
                        + "\nRate " + f1(liveBin.lastRejectedRate) + " %/s · TPS excursion "
                        + f2(liveBin.lastRejectedExcursion) + "% · detector Δ "
                        + f3(liveBin.lastRejectedDelta) + " · "
                        + f0(liveBin.lastRejectedRatio * 100.0) + "% of threshold · repeat the requested maneuver");
            } else {
                lastAttempt.setText("LAST ATTEMPT — no rejected maneuver in this region.\n"
                        + liveBin.status);
            }
            lastAttempt.setCaretPosition(0);
        }

        currentTune.setText(model.currentTuneText());
        recommendation.setText(model.recommendationStatus());
        liveValues.setText("RPM " + f0(model.liveRpm) + " | TPS " + f1(model.liveTps)
                + "% | Fuel: TPS AE change " + f3(model.liveDelta)
                + " | AccelThreshold " + f3(model.liveThreshold)
                + " | raw TPS rate " + f1(model.liveTpsRate) + " %/s");
        evidenceSummary.setText("Valid " + model.validSamples
                + " | calibration " + (model.calibrationFrozen ? "LOCKED" : "CALIBRATING")
                + " | movement events " + model.movementEvents
                + " | rejected maneuver mismatches " + model.semanticRejectedEvents
                + " | effective regions locked " + model.effectiveValidatedBins
                + " | movement floor " + f2(model.movementRateFloor) + " %/s"
                + " | acceleration-opening minimum rate " + f2(model.intentRateFloor) + " %/s"
                + " | quiet p99 diagnostic " + f2(model.quietRateP99) + " %/s");

        tableModel.setRowCount(0);
        for (FoundationThresholdFocusModel.Bin bin : model.bins) {
            tableModel.addRow(new Object[]{
                    bin.regionLabel, f3(bin.currentThreshold),
                    Integer.toString(bin.normalCorrectionEvents), Integer.toString(bin.accelerationOpeningEvents),
                    f3(bin.normalCorrectionP95), f3(bin.accelerationOpeningP25),
                    f3(bin.effectiveSeparationLow) + " .. " + f3(bin.effectiveSeparationHigh),
                    signed(bin.separationGap) + " / " + f3(bin.requiredSeparation),
                    f3(bin.staticSeparationLow) + " .. " + f3(bin.staticSeparationHigh), bin.status
            });
        }
        review.setText(model.reviewText);
        review.setCaretPosition(0);
    }

    JTable tableForTest() { return bins; }
    String driverInstructionForTest() { return driverInstruction.getText(); }
    String recommendationTextForTest() { return recommendation.getText(); }
    String reviewTextForTest() { return review.getText(); }
    String eventCountsTextForTest() { return eventCounts.getText(); }
    String separationTextForTest() { return separationStatus.getText(); }
    String lastAttemptTextForTest() { return lastAttempt.getText(); }
    boolean eventBarsPaintStringsForTest() {
        return normalProgress.isStringPainted() || accelerationProgress.isStringPainted();
    }
    boolean driverViewForTest() { return driverView; }
    boolean hasRootScrollForTest() { return false; }

    private static JProgressBar bar(int min, int max) { return new JProgressBar(min, max); }
    private static JTextArea area(String text, int rows, float size, int style) {
        JTextArea area = new JTextArea(text, rows, 1);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(area.getFont().deriveFont(style, size));
        area.setFocusable(false);
        return area;
    }
    private static String f0(double value) { return f(value, 0); }
    private static String f1(double value) { return f(value, 1); }
    private static String f2(double value) { return f(value, 2); }
    private static String f3(double value) { return f(value, 3); }
    private static String signed(double value) {
        if (!Double.isFinite(value)) return "n/a";
        return String.format(Locale.ROOT, "%+.3f", value);
    }
    private static String f(double value, int decimals) {
        if (!Double.isFinite(value)) return "n/a";
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
