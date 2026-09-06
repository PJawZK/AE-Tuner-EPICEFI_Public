package se.anders.tunerstudio.aetuner.guided;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Minimal eyes-up Foundation 1 passive capture view. */
public final class EngagementDetectionGuidedFocusPanel extends JPanel {
    private static final String CARD_DRIVER = "driver";
    private static final String CARD_DETAILS = "details";

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JPanel driverCard = new JPanel();
    private final JPanel detailsCard = new JPanel(new BorderLayout(7, 7));

    private final JTextArea driverInstruction = area(
            "MAKE ONE COMFORTABLE PEDAL OPENING WHEN SAFE", 2, 32f, Font.BOLD);
    private final JLabel driverStatus = new JLabel("Passive TPS capture", SwingConstants.CENTER);
    private final JLabel driverRpmText = new JLabel("RPM: n/a", SwingConstants.CENTER);
    private final JLabel driverTpsText = new JLabel("TPS: n/a", SwingConstants.CENTER);
    private final JLabel driverCandidate = new JLabel("First usable movement sets the visual reference", SwingConstants.CENTER);
    private final JProgressBar driverEventProgress = bar(0, 6);
    private final BandGauge rpmGauge = new BandGauge();
    private final BandGauge tpsGauge = new BandGauge();

    private final JLabel current = new JLabel("Working tune not read");
    private final JLabel detectorState = new JLabel("Passive capture idle");
    private final JLabel sweepCandidate = new JLabel("No comparable cluster yet");
    private final JLabel sweepTarget = new JLabel("No exact TPS target");
    private final JProgressBar eventProgress = bar(0, 6);
    private final JProgressBar detectedSignal = bar(0, 100);
    private final JTextArea nextAction = area(
            "Start Capture, then make one comfortable positive pedal opening when safe.",
            3, 20f, Font.BOLD);
    private final JTextArea maneuverPlan = area(
            "The first usable opening sets a full-height visual TPS reference marker. Later completed openings get shorter lower-half markers. Those markers are driver aids only; AE Tuner still decides comparability from the measured event data. After every movement, SETTLING must complete before another event can be accepted.",
            6, 13f, Font.PLAIN);
    private final JTextArea audioPlan = area(
            "Foundation 1 uses one optional accepted-event cue only. SETTLING is a physical event-separation state, not target choreography. There are no exact-target, hold or candidate-transition cues.",
            4, 13f, Font.PLAIN);

    private final EngagementQuietCalibrationPanel quietCalibrationDetails =
            new EngagementQuietCalibrationPanel(false);
    private final JPanel settingsPanel = new JPanel();
    private final JSpinner requestedDeltaWindow = spinner(25, 1, 500, 1);
    private final JSpinner rpmStartingPoint = spinner(1800, 600, 6500, 50);
    private final JSpinner sweepEvents = new JSpinner(new SpinnerNumberModel(6, 3, 12, 1));

    private boolean driverView = true;

    public EngagementDetectionGuidedFocusPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildDriver();
        buildDetails();
        cardHost.add(driverCard, CARD_DRIVER);
        cardHost.add(detailsCard, CARD_DETAILS);
        add(cardHost, BorderLayout.CENTER);
        setDriverView(true);
    }

    private void buildDriver() {
        driverCard.setLayout(new BoxLayout(driverCard, BoxLayout.Y_AXIS));
        driverCard.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        driverStatus.setFont(driverStatus.getFont().deriveFont(Font.BOLD, 16f));
        driverStatus.setAlignmentX(CENTER_ALIGNMENT);
        driverInstruction.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        driverInstruction.setAlignmentX(CENTER_ALIGNMENT);
        driverInstruction.setOpaque(false);
        driverCard.add(driverStatus);
        driverCard.add(Box.createVerticalStrut(10));
        driverCard.add(driverInstruction);
        driverCard.add(Box.createVerticalStrut(14));
        driverCard.add(driverRpmText);
        rpmGauge.setPreferredSize(new Dimension(900, 48));
        rpmGauge.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        rpmGauge.setAlignmentX(CENTER_ALIGNMENT);
        driverCard.add(rpmGauge);
        driverCard.add(Box.createVerticalStrut(12));
        driverCard.add(driverTpsText);
        tpsGauge.setPreferredSize(new Dimension(900, 48));
        tpsGauge.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        tpsGauge.setAlignmentX(CENTER_ALIGNMENT);
        driverCard.add(tpsGauge);
        driverCard.add(Box.createVerticalStrut(16));
        driverEventProgress.setBorder(BorderFactory.createTitledBorder("COMPARABLE PEDAL MOVEMENTS"));
        driverEventProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        driverCard.add(driverEventProgress);
        driverCandidate.setFont(driverCandidate.getFont().deriveFont(Font.BOLD, 16f));
        driverCandidate.setAlignmentX(CENTER_ALIGNMENT);
        driverCard.add(Box.createVerticalStrut(7));
        driverCard.add(driverCandidate);
    }

    private void buildDetails() {
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("AE Foundation — Passive TPS Movement / Timing");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.NORTH);
        header.add(current, BorderLayout.SOUTH);
        detailsCard.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        detectorState.setFont(detectorState.getFont().deriveFont(Font.BOLD, 16f));
        center.add(detectorState);
        center.add(Box.createVerticalStrut(6));
        center.add(sweepCandidate);
        center.add(sweepTarget);
        eventProgress.setBorder(BorderFactory.createTitledBorder("Comparable movements"));
        center.add(eventProgress);
        detectedSignal.setBorder(BorderFactory.createTitledBorder("Set completion"));
        center.add(detectedSignal);
        center.add(titled("CURRENT INSTRUCTION", nextAction));
        center.add(titled("CAPTURE LOGIC", maneuverPlan));
        center.add(titled("AUDIO", audioPlan));
        quietCalibrationDetails.setVisible(false);
        center.add(quietCalibrationDetails);
        detailsCard.add(center, BorderLayout.CENTER);
    }

    public void updateModel(EngagementFocusModel model) {
        EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
        double rpm = model == null ? Double.NaN : model.rpm;
        double tps = model == null ? Double.NaN : model.tps;
        driverRpmText.setText("RPM " + fmt0(rpm));
        if (passive.settling) {
            driverTpsText.setText("TPS " + fmt1(tps) + "%"
                    + (Double.isFinite(passive.settleUpperTps)
                        ? " · NEW BASELINE ~" + fmt1(passive.settleUpperTps) + "%"
                        : " · REPOSITION / HOLD STEADY"));
        } else {
            driverTpsText.setText("TPS " + fmt1(tps) + "%");
        }
        rpmGauge.setValues(700, 4500, rpm);
        tpsGauge.setValues(0, 50, tps, passive.referencePeakTps,
                passive.repeatPeakTps,
                passive.settling ? passive.settleUpperTps : Double.NaN);

        int target = Math.max(1, passive.targetComparable);
        int captured = Math.min(passive.comparableEvents, target);
        driverEventProgress.setMaximum(target);
        driverEventProgress.setValue(captured);
        driverEventProgress.setString(passive.comparableEvents + " / " + target + " comparable");
        eventProgress.setMaximum(target);
        eventProgress.setValue(captured);
        eventProgress.setString(passive.comparableEvents + " / " + target + " comparable");
        int percent = (int) Math.round(100.0 * captured / target);
        detectedSignal.setValue(Math.max(0, Math.min(100, percent)));
        detectedSignal.setString(percent + "% of evidence set");

        if (model == null) {
            driverStatus.setText("Passive TPS capture idle");
            setTextIfChanged(driverInstruction, "READ WORKING TUNE, THEN START CAPTURE");
            current.setText("Working tune not read");
            return;
        }

        current.setText("Working tune: " + (model.workingModel == null ? "unknown detector" : model.workingModel)
                + " | Sample Length " + fmt0(model.sampleLengthSeconds * 1000.0)
                + " ms | capture is read-only");

        if (passive.complete()) {
            EngagementPassiveCapture.TimingStatus timing = model.timingStatus;
            if (!timing.deltaResolved) {
                driverStatus.setText("1/2 Delta Window — ambiguous; 2/2 capacity assessed");
                String sampleLine = timing.sampleResolved
                        ? "\n2/2 SAMPLE LENGTH — " + fmt0(timing.sampleLengthMs) + " ms — "
                            + (timing.sampleChangeNeeded ? "INCREASE" : "RETAIN")
                        : "\n2/2 SAMPLE LENGTH — WAITING";
                setTextIfChanged(driverInstruction,
                        "1/2 DELTA WINDOW — AMBIGUOUS" + sampleLine);
                driverCandidate.setText("Pedal " + timing.pedalQuality
                        + " · best " + fmt0(timing.bestDeltaMs) + " ms / runner "
                        + fmt0(timing.secondDeltaMs) + " ms · score gap "
                        + fmt3(timing.scoreGap) + " / " + fmt3(timing.requiredScoreGap)
                        + " · evidence " + timing.evidenceSets + " set(s) / "
                        + timing.evidenceEvents + " openings");
                detectorState.setText(timing.temporalResolutionLimited
                        ? "DELTA AMBIGUOUS — HOST TRACE IS COARSE FOR 5 ms CANDIDATE SPACING"
                        : "DELTA AMBIGUOUS — PRIOR SET EVIDENCE IS RETAINED AND COMBINED");
                setTextIfChanged(nextAction, timing.coaching);
            } else {
                driverStatus.setText(timing.applyReady
                        ? "TIMING PAIR COMPLETE — operating range confirmed"
                        : "TIMING PAIR COMPLETE — provisional operating coverage");
                setTextIfChanged(driverInstruction,
                        "1/2 DELTA WINDOW — " + fmt0(timing.deltaWindowMs) + " ms — "
                                + (timing.deltaChangeNeeded ? "PROPOSE" : "RETAIN")
                                + "\n2/2 SAMPLE LENGTH — " + fmt0(timing.sampleLengthMs) + " ms — "
                                + (timing.sampleChangeNeeded ? "PROPOSE" : "RETAIN"));
                driverCandidate.setText("TIMING PAIR COMPLETE · VSS-road context "
                        + passive.roadComparableEvents + " · pre-event RPM span "
                        + fmt0(passive.roadRpmSpan));
                detectorState.setText("TIMING PAIR COMPLETE — 1/2 DELTA WINDOW -> 2/2 SAMPLE LENGTH");
                setTextIfChanged(nextAction,
                        timing.applyReady
                                ? "Finish/Review exposes one guarded timing-pair proposal containing only settings that actually need to change."
                                : "Delta Window and Sample Length are both resolved, but Apply remains withheld until comparable pre-event operating points span about 400 RPM. VSS is useful context, not a hard prerequisite.");
            }
        } else if (passive.moving) {
            driverStatus.setText("Capturing pedal movement");
            setTextIfChanged(driverInstruction, "MOVEMENT DETECTED — LET IT PEAK NATURALLY");
            driverCandidate.setText(markerSummary(passive));
            detectorState.setText("CAPTURING PHYSICAL TPS MOVEMENT");
            setTextIfChanged(nextAction, "Let this opening peak naturally; no exact target or hold is required.");
        } else if (passive.settling) {
            driverStatus.setText("Re-arming — establish the next operating point");
            if (!passive.settleTpsReturned) {
                setTextIfChanged(driverInstruction, "EASE OFF THE PREVIOUS OPENING");
                detectorState.setText("RE-ARM — WAITING FOR THE PREVIOUS OPENING TO END");
            } else if (!passive.settleTpsQuiet) {
                setTextIfChanged(driverInstruction, "REPOSITION AS NEEDED — THEN HOLD TPS STEADY");
                detectorState.setText("RE-ARM — YOU MAY MOVE TO A NEW ROAD TPS / RPM POINT");
            } else if (!passive.settleRpmReady) {
                setTextIfChanged(driverInstruction, "TPS STEADY — HOLD THE OPERATING POINT");
                detectorState.setText("RE-ARM — WAITING FOR RPM TO BECOME STEADY");
            } else {
                setTextIfChanged(driverInstruction, Double.isFinite(passive.settleUpperTps)
                        ? "NEW BASELINE ~" + fmt1(passive.settleUpperTps) + "% TPS — HOLD FOR READY"
                        : "OPERATING POINT STEADY — HOLD FOR READY");
                detectorState.setText("RE-ARM — LEARNING THE NEXT PRE-EVENT BASELINE");
            }
            driverCandidate.setText(Double.isFinite(passive.settleUpperTps)
                    ? "New steady baseline ~" + fmt1(passive.settleUpperTps)
                        + "% TPS · previous baseline was " + fmt1(passive.settleBaselineTps) + "%"
                    : "Previous baseline " + fmt1(passive.settleBaselineTps)
                        + "% TPS · it does NOT need to be revisited");
            setTextIfChanged(nextAction,
                    "After the previous opening ends, you may reposition to any safe steady road TPS/RPM operating point. Hold that point briefly and AE Tuner will adopt it automatically as the next baseline. You do not need to return to the previous TPS value.");
        } else if (!Double.isFinite(passive.referencePeakTps)) {
            driverStatus.setText("Passive capture — establish visual reference");
            setTextIfChanged(driverInstruction, "MAKE ONE COMFORTABLE PEDAL OPENING WHEN SAFE");
            driverCandidate.setText("First usable movement sets the full-height TPS reference marker");
            detectorState.setText("READY — WAITING FOR FIRST USABLE POSITIVE TPS MOVEMENT");
            setTextIfChanged(nextAction,
                    "Make one comfortable moderate positive pedal opening. Its peak becomes the visual reference only; it is not a hard numerical target.");
        } else {
            driverStatus.setText("Passive capture — " + passive.comparableEvents + "/" + target + " comparable");
            setTextIfChanged(driverInstruction, "REPEAT APPROXIMATELY THE REFERENCE MOVEMENT");
            driverCandidate.setText(markerSummary(passive));
            detectorState.setText("READY — WAITING FOR NEXT NATURAL POSITIVE TPS MOVEMENT");
            setTextIfChanged(nextAction,
                    "Repeat approximately the full-height reference marker. Short lower-half markers show prior completed peaks; AE Tuner, not the marker, decides comparability.");
        }

        if (Double.isFinite(passive.medianStep)) {
            sweepCandidate.setText("Natural cluster: median +" + fmt1(passive.medianStep)
                    + " TPS | MAD " + fmt1(passive.madStep)
                    + " | tolerance +/-" + fmt1(passive.tolerance));
        } else {
            sweepCandidate.setText("Natural cluster: collecting first movements");
        }
        sweepTarget.setText("Visual reference " + fmt1(passive.referencePeakTps)
                + "% TPS | not a hard target | onset floor " + fmt1(passive.onsetRateFloor)
                + " %TPS/s | prior sets " + passive.completedSets);
    }

    private static String markerSummary(EngagementPassiveCapture.Snapshot passive) {
        if (!Double.isFinite(passive.referencePeakTps)) return passive.lastEvent;
        return "Reference " + fmt1(passive.referencePeakTps) + "% TPS · repeats "
                + passive.repeatPeakTps.length + " · " + passive.lastEvent;
    }

    public void setDriverView(boolean driver) {
        driverView = driver;
        cards.show(cardHost, driver ? CARD_DRIVER : CARD_DETAILS);
        revalidate();
        repaint();
    }

    public void refreshFromSelection() { }

    private static JPanel titled(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(component);
        return panel;
    }

    private static JTextArea area(String text, int rows, float size, int style) {
        JTextArea area = new JTextArea(text, rows, 1);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusable(false);
        area.setFont(area.getFont().deriveFont(style, size));
        return area;
    }

    private static JProgressBar bar(int min, int max) {
        JProgressBar bar = new JProgressBar(min, max);
        bar.setStringPainted(true);
        return bar;
    }

    private static JSpinner spinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(Double.valueOf(value), Double.valueOf(min),
                Double.valueOf(max), Double.valueOf(step)));
    }

    private static void setTextIfChanged(JTextArea area, String text) {
        String safe = text == null ? "" : text;
        if (!safe.equals(area.getText())) area.setText(safe);
    }

    private static String fmt0(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.0f", value) : "n/a";
    }
    private static String fmt1(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.1f", value) : "n/a";
    }
    private static String fmt3(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.3f", value) : "n/a";
    }

    boolean settingsToggleVisibleForTest() { return false; }
    boolean settingsPanelVisibleForTest() { return false; }
    int selectedSignalPercentForTest() { return detectedSignal.getValue(); }
    String detectorStateForTest() { return detectorState.getText(); }
    String currentTextForTest() { return current.getText(); }
    String actionTextForTest() { return driverView ? driverInstruction.getText() : nextAction.getText(); }
    String maneuverTextForTest() { return maneuverPlan.getText(); }
    boolean hasRootScrollForTest() { return false; }
    String guidanceTextForTest() { return nextAction.getText() + "\n" + maneuverPlan.getText() + "\n" + audioPlan.getText(); }
    boolean deltaWindowEnabledForTest() { return false; }
    void setDeltaWindowForTest(double value) { requestedDeltaWindow.setValue(value); }
    boolean requestedDeltaWindowEnabledForTest() { return false; }
    double requestedDeltaWindowForTest() { return ((Number) requestedDeltaWindow.getValue()).doubleValue(); }
    void setRequestedDeltaWindowForTest(double value) { setDeltaWindowForTest(value); }
    void setSweepRpmForTest(double value) { rpmStartingPoint.setValue(value); }
    double sweepRpmForTest() { return ((Number) rpmStartingPoint.getValue()).doubleValue(); }
    int sweepEventsForTest() { return ((Number) sweepEvents.getValue()).intValue(); }
    String sweepCandidateTextForTest() { return sweepCandidate.getText(); }
    String sweepTargetTextForTest() { return sweepTarget.getText(); }
    String driverInstructionForTest() { return driverInstruction.getText(); }
    String driverRpmTextForTest() { return driverRpmText.getText(); }
    String driverTpsTextForTest() { return driverTpsText.getText(); }
    boolean driverCardVisibleForTest() { return driverCard.isVisible(); }
    double driverReferenceMarkerForTest() { return tpsGauge.referenceForTest(); }
    int driverRepeatMarkerCountForTest() { return tpsGauge.repeatCountForTest(); }
    double driverReturnMarkerForTest() { return tpsGauge.returnReferenceForTest(); }
    EngagementQuietCalibrationPanel quietCalibrationDetailsForTest() { return quietCalibrationDetails; }

    private static final class BandGauge extends JComponent {
        private double min = Double.NaN;
        private double max = Double.NaN;
        private double value = Double.NaN;
        private double reference = Double.NaN;
        private double[] repeats = new double[0];
        private double returnReference = Double.NaN;

        void setValues(double min, double max, double value) {
            setValues(min, max, value, Double.NaN, new double[0], Double.NaN);
        }

        void setValues(double min, double max, double value,
                       double reference, double[] repeats) {
            setValues(min, max, value, reference, repeats, Double.NaN);
        }

        void setValues(double min, double max, double value,
                       double reference, double[] repeats,
                       double returnReference) {
            this.min = min;
            this.max = max;
            this.value = value;
            this.reference = reference;
            this.repeats = repeats == null ? new double[0] : repeats.clone();
            this.returnReference = returnReference;
            repaint();
        }

        double referenceForTest() { return reference; }
        int repeatCountForTest() { return repeats.length; }
        double returnReferenceForTest() { return returnReference; }

        private int pixel(double point, int x, int width) {
            double clamped = Math.max(min, Math.min(max, point));
            return x + (int) Math.round(((clamped - min) / (max - min)) * width);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int x = 12, y = 10, w = Math.max(1, getWidth() - 24), h = Math.max(12, getHeight() - 20);
                Color bg = UIManager.getColor("ProgressBar.background");
                if (bg == null) bg = getBackground().darker();
                Color fg = UIManager.getColor("ProgressBar.foreground");
                if (fg == null) fg = getForeground();
                Color live = UIManager.getColor("Label.foreground");
                if (live == null) live = getForeground();
                g.setColor(bg);
                g.fillRoundRect(x, y, w, h, 10, 10);

                // SETTLING only: dashed pre-event TPS return reference. It is
                // deliberately distinct from the solid opening-reference marker.
                if (Double.isFinite(returnReference)) {
                    int px = pixel(returnReference, x, w);
                    Color settle = UIManager.getColor("Label.disabledForeground");
                    if (settle == null) settle = fg;
                    g.setColor(settle);
                    g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_MITER, 10f,
                            new float[]{7f, 4f}, 0f));
                    g.drawLine(px, y, px, y + h);
                }

                // First usable movement: dominant full-height reference marker.
                if (Double.isFinite(reference)) {
                    int px = pixel(reference, x, w);
                    g.setColor(fg);
                    g.setStroke(new BasicStroke(5f));
                    g.drawLine(px, y, px, y + h);
                }

                // Later completed movements: shorter lower-half markers so they
                // cannot be mistaken for the reference the driver is repeating.
                g.setColor(fg);
                g.setStroke(new BasicStroke(2.5f));
                for (double repeat : repeats) {
                    if (!Double.isFinite(repeat)) continue;
                    int px = pixel(repeat, x, w);
                    g.drawLine(px, y + h / 2, px, y + h);
                }

                // Live TPS/RPM cursor stays thin and is not a stored target.
                if (Double.isFinite(value)) {
                    int px = pixel(value, x, w);
                    g.setColor(live);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawLine(px, y - 2, px, y + h + 2);
                }
                g.setColor(live);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(x, y, Math.max(0, w - 1), Math.max(0, h - 1), 10, 10);
            } finally {
                g.dispose();
            }
        }
    }
}
