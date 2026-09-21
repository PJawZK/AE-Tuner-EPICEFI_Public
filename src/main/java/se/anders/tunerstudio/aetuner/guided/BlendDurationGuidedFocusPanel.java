package se.anders.tunerstudio.aetuner.guided;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;

/** Driver-first Focus card for Guided MAP Predict / Blend Duration capture. */
public final class BlendDurationGuidedFocusPanel extends JPanel {
    private static final String DRIVER = "driver";
    private static final String DETAILS = "details";

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel instruction = new JLabel("No Blend Duration capture active.", SwingConstants.CENTER);
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar eventProgress = new JProgressBar();
    private final JProgressBar rpmProgress = new JProgressBar(0, 1000);
    private final JProgressBar tpsProgress = new JProgressBar(0, 1000);
    private final JProgressBar mapProgress = new JProgressBar(0, 1000);
    private final JLabel rpmText = new JLabel("RPM: n/a", SwingConstants.CENTER);
    private final JLabel tpsText = new JLabel("TPS step: n/a", SwingConstants.CENTER);
    private final JLabel mapText = new JLabel("Physical MAP response: waiting", SwingConstants.CENTER);
    private final JLabel comparability = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel lastResult = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel methodBoundary = new JLabel(
            "MEASUREMENT VALIDATION ONLY — NUMERICAL BLEND DURATION APPLY IS WITHHELD",
            SwingConstants.CENTER);
    private final JTextArea details = new JTextArea();
    private BlendDurationFocusModel model = BlendDurationFocusModel.setup();

    public BlendDurationGuidedFocusPanel() {
        super(new BorderLayout());
        buildUi();
        updateModel(model, "");
    }

    private void buildUi() {
        JPanel driver = new JPanel();
        driver.setLayout(new BoxLayout(driver, BoxLayout.Y_AXIS));
        driver.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        instruction.setFont(instruction.getFont().deriveFont(Font.BOLD, 25f));
        instruction.setAlignmentX(CENTER_ALIGNMENT);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 15f));
        status.setAlignmentX(CENTER_ALIGNMENT);
        eventProgress.setStringPainted(true);
        eventProgress.setAlignmentX(CENTER_ALIGNMENT);
        eventProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        driver.add(instruction);
        driver.add(spacer(12));
        driver.add(status);
        driver.add(spacer(12));
        driver.add(eventProgress);
        driver.add(spacer(14));
        driver.add(metricPanel("RPM TARGET", rpmText, rpmProgress));
        driver.add(spacer(9));
        driver.add(metricPanel("PEDAL STEP", tpsText, tpsProgress));
        driver.add(spacer(9));
        driver.add(metricPanel("PHYSICAL MAP RESPONSE", mapText, mapProgress));
        driver.add(spacer(14));
        comparability.setAlignmentX(CENTER_ALIGNMENT);
        driver.add(comparability);
        driver.add(spacer(8));
        lastResult.setAlignmentX(CENTER_ALIGNMENT);
        driver.add(lastResult);
        driver.add(spacer(10));
        methodBoundary.setAlignmentX(CENTER_ALIGNMENT);
        methodBoundary.setFont(methodBoundary.getFont().deriveFont(Font.BOLD));
        driver.add(methodBoundary);

        details.setEditable(false);
        details.setLineWrap(false);
        details.setWrapStyleWord(false);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        details.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        body.add(driver, DRIVER);
        body.add(new JScrollPane(details), DETAILS);
        add(body, BorderLayout.CENTER);
    }

    private static JPanel metricPanel(String title, JLabel text, JProgressBar bar) {
        JPanel panel = new JPanel(new BorderLayout(8, 3));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(500, 24));
        panel.add(text, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        return panel;
    }

    private static JPanel spacer(int height) {
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setMaximumSize(new Dimension(1, height));
        spacer.setPreferredSize(new Dimension(1, height));
        return spacer;
    }

    public void updateModel(BlendDurationFocusModel next, String fallbackGuidance) {
        model = next == null ? BlendDurationFocusModel.setup() : next;
        instruction.setText(html(model.instruction));
        status.setText(html(model.status));

        eventProgress.setMinimum(0);
        eventProgress.setMaximum(Math.max(1, model.targetEvents));
        eventProgress.setValue(Math.min(model.targetEvents, model.matchingEvents));
        eventProgress.setString("MATCHING EVENTS " + model.matchingEvents + "/" + model.targetEvents
                + " — " + model.repeatability);

        updateRpm();
        updateTps();
        updateMap();
        comparability.setText(html(model.comparabilityHint));
        lastResult.setText(html(shortResult(model.lastResult)));
        details.setText(model.detail
                + (fallbackGuidance == null || fallbackGuidance.length() == 0
                ? "" : "\n\nMethod guidance\n" + fallbackGuidance));
        details.setCaretPosition(0);
    }

    public void setDriverView(boolean driver) {
        cards.show(body, driver ? DRIVER : DETAILS);
    }

    private void updateRpm() {
        if (!Double.isFinite(model.liveRpm) || !Double.isFinite(model.targetRpm)) {
            rpmProgress.setValue(0);
            rpmProgress.setString("AUTO LATCH — WAITING FOR AN ARMED BIN");
            rpmText.setText(Double.isFinite(model.liveRpm)
                    ? "Live " + f0(model.liveRpm) + " RPM — move near one armed table bin"
                    : "RPM: waiting for live signal");
            return;
        }
        double tolerance = Math.max(1.0, model.rpmTolerance);
        double fraction = 1.0 - Math.min(1.0, Math.abs(model.rpmError) / tolerance);
        rpmProgress.setValue((int) Math.round(fraction * 1000.0));
        boolean latched = model.captureState == GuidedCaptureState.READY
                || model.captureState == GuidedCaptureState.OPENING_PENDING
                || model.captureState == GuidedCaptureState.CAPTURING
                || model.captureState == GuidedCaptureState.ACCEPTED
                || model.captureState == GuidedCaptureState.WARNING
                || model.captureState == GuidedCaptureState.EXCLUDED
                || model.captureState == GuidedCaptureState.RETURNING;
        rpmProgress.setString(latched
                ? "LATCHED FOR THIS EVENT"
                : model.rpmInRange ? "AUTO CANDIDATE — HOLD STEADY" : "MOVE TOWARD CANDIDATE");
        rpmText.setText(String.format(java.util.Locale.ROOT,
                "%s target %.0f RPM — live %.0f — entry window ±%.0f",
                latched ? "LATCHED" : "CANDIDATE", model.targetRpm,
                model.liveRpm, tolerance));
    }

    private void updateTps() {
        if (!Double.isFinite(model.liveTpsStep)) {
            tpsProgress.setValue(0);
            tpsProgress.setString("waiting for opening");
            tpsText.setText("Suggested +" + f0(model.desiredTpsStep)
                    + " (guide only) — usable +" + f0(model.tpsStepLow)
                    + " to +" + f0(model.tpsStepHigh));
            return;
        }
        double fraction;
        if (model.liveTpsStep < model.tpsStepLow) {
            fraction = Math.max(0.0, model.liveTpsStep / Math.max(1.0, model.tpsStepLow));
        } else if (model.liveTpsStep <= model.tpsStepHigh) {
            fraction = 1.0;
        } else {
            fraction = Math.max(0.0, 1.0
                    - (model.liveTpsStep - model.tpsStepHigh)
                    / Math.max(1.0, model.tpsStepHigh));
        }
        tpsProgress.setValue((int) Math.round(Math.min(1.0, fraction) * 1000.0));
        tpsProgress.setString(model.tpsStepInRange
                ? "USABLE STEP — HOLD"
                : (model.liveTpsStep < model.tpsStepLow
                    ? "OPEN A LITTLE MORE" : "ABOVE BROAD ROAD RANGE"));
        tpsText.setText("Current step +" + f1(model.liveTpsStep)
                + " — usable +" + f0(model.tpsStepLow) + " to +" + f0(model.tpsStepHigh)
                + "; repeatability groups similar steps");
    }

    private void updateMap() {
        if (!Double.isFinite(model.liveMap)) {
            mapProgress.setValue(0);
            mapProgress.setString("waiting for MAP");
            mapText.setText("Physical MAP response: waiting for measured MAP");
            return;
        }
        if (!model.softPlateauAcquired || !Double.isFinite(model.physicalBaselineMap)) {
            mapProgress.setValue(0);
            mapProgress.setString("waiting for stable pedal hold");
            mapText.setText("Measured " + f1(model.liveMap)
                    + " kPa — physical timing begins after the pedal settles");
            return;
        }
        if (model.physicalResponseComplete && Double.isFinite(model.physicalResponseSeconds)) {
            mapProgress.setValue(1000);
            mapProgress.setString("PHYSICAL RESPONSE MEASURED");
            mapText.setText("20→80 response " + millis(model.physicalResponseSeconds)
                    + " — physical step +" + f1(model.physicalMapStep) + " kPa"
                    + (model.boundedLateWindow ? " (bounded estimate)" : ""));
            return;
        }
        double elapsed = Double.isFinite(model.physicalObservationSeconds)
                ? model.physicalObservationSeconds : 0.0;
        double fraction = Math.min(0.95, Math.max(0.0, elapsed / 1.20));
        mapProgress.setValue((int)Math.round(fraction * 1000.0));
        mapProgress.setString("HOLD — OBSERVING REAL MAP");
        double rise = model.liveMap - model.physicalBaselineMap;
        mapText.setText("Measured " + f1(model.liveMap) + " kPa — baseline "
                + f1(model.physicalBaselineMap) + " — current rise "
                + (Double.isFinite(rise) && rise >= 0.0 ? "+" : "") + f1(rise) + " kPa");
    }

    private static String shortResult(String text) {
        if (text == null || text.length() == 0) return "";
        String first = text.split("\\r?\\n", 2)[0];
        if (first.startsWith("VALID ROAD EVENT")) return first + " — repeat under similar road conditions";
        if (first.startsWith("EVENT EXCLUDED")) return first + " — see Details for the physical reason";
        if (first.startsWith("RETURN TO NORMAL THROTTLE")) return first;
        return first;
    }

    private static String html(String text) {
        if (text == null || text.length() == 0) return " ";
        return "<html><div style='text-align:center'>" + escape(text) + "</div></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    private static String f0(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.0f", value) : "n/a";
    }
    private static String f1(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.1f", value) : "n/a";
    }
    private static String millis(double seconds) {
        return Double.isFinite(seconds)
                ? String.format(java.util.Locale.ROOT, "~%.0f ms", seconds * 1000.0) : "n/a";
    }

    String instructionForTest() { return instruction.getText(); }
    String statusForTest() { return status.getText(); }
    String eventProgressForTest() { return eventProgress.getString(); }
    String rpmTextForTest() { return rpmText.getText(); }
    String tpsTextForTest() { return tpsText.getText(); }
    String mapTextForTest() { return mapText.getText(); }
    String detailsForTest() { return details.getText(); }
    BlendDurationFocusModel modelForTest() { return model; }
}
