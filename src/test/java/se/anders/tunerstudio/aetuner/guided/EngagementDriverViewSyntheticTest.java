package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Real-Swing screenshots for the passive Foundation 1 driver instrument view. */
public final class EngagementDriverViewSyntheticTest {
    private EngagementDriverViewSyntheticTest() { }

    public static void main(String[] args) throws Exception {
        File out = outputDirectory();
        if (!out.isDirectory() && !out.mkdirs()) {
            throw new IllegalStateException("Could not create synthetic output directory " + out);
        }

        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(6);
        EngagementFocusModel.resetPresentationCacheForTest();
        AeProjectSnapshot snapshot = snapshot();
        EngagementDetectionWriteSelection.resetForTest();
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);

        LiveSample latest = null;
        double seconds = 0.0;
        long index = 0L;

        // Quiet ordinary baseline. Passive Foundation 1 learns its own bounded
        // onset floor from normal samples; there is no explicit calibration step.
        for (int i = 0; i < 20; i++) {
            seconds += 0.025;
            latest = sample(++index, seconds, 1790.0, 6.2,
                    0.2, 1.0, 0.2, false);
            EngagementPassiveCapture.accept(latest);
        }

        // One natural moderate opening, roughly +14 TPS, then the first natural
        // reversal. This should be stored as comparable evidence without any
        // exact target, target-acquisition READY gate, timing write, or hold requirement.
        double[] tps = new double[]{7.0, 10.0, 14.0, 18.0, 20.0, 19.4};
        for (int i = 0; i < tps.length; i++) {
            seconds += 0.025;
            boolean detector = i >= 2;
            latest = sample(++index, seconds, 1790.0, tps[i],
                    detector ? 2.0 : 0.5, 1.0,
                    i == 0 ? 30.0 : 18.0, detector);
            EngagementPassiveCapture.accept(latest);
        }
        require(EngagementPassiveCapture.snapshot().comparableEvents == 1,
                "synthetic passive fixture did not store its natural opening as comparable evidence");
        require(EngagementPassiveCapture.snapshot().settling,
                "synthetic passive fixture did not enter SETTLING after the first physical event");

        final EngagementDetectionGuidedFocusPanel panel =
                new EngagementDetectionGuidedFocusPanel();
        final LiveSample settlingSample = latest;
        final EngagementFocusModel settlingModel = EngagementFocusModel.build(
                snapshot, settlingSample, GuidedCaptureState.CAPTURING,
                1, 6, 26, 26);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                panel.setDriverView(true);
                panel.updateModel(settlingModel);
            }
        });
        require(panel.driverInstructionForTest().contains("REPOSITION AS NEEDED")
                        && panel.detectorStateForTest().contains("NEW ROAD TPS / RPM"),
                "passive driver view did not coach automatic road re-anchoring");
        require(panel.driverRpmTextForTest().contains("1790")
                        && !panel.driverRpmTextForTest().contains("band"),
                "passive driver view still presents an RPM start-target band");
        require(panel.driverTpsTextForTest().contains("19.4")
                        && panel.driverTpsTextForTest().contains("REPOSITION / HOLD STEADY")
                        && !panel.driverTpsTextForTest().toLowerCase(java.util.Locale.ROOT)
                                .contains("opening target"),
                "passive driver view did not distinguish re-anchoring from an opening target");
        require(!Double.isFinite(panel.driverReturnMarkerForTest()),
                "passive driver TPS gauge still rendered the stale pre-event TPS as a return target");
        require(Double.isFinite(EngagementPassiveCapture.snapshot().settleBaselineTps)
                        && Math.abs(EngagementPassiveCapture.snapshot().settleBaselineTps - 6.2) < 0.25,
                "passive collector snapshot did not expose the settling return reference to the UI");
        require(panel.selectedSignalPercentForTest() > 0
                        && panel.sweepTargetTextForTest().contains("not a hard target"),
                "passive driver view did not expose comparable-set progress/reference-only contract");
        require(!panel.settingsToggleVisibleForTest()
                        && !panel.settingsPanelVisibleForTest()
                        && !panel.requestedDeltaWindowEnabledForTest(),
                "passive driver view exposed retired timing experiment controls");

        // This synthetic path chooses to hold the same TPS again, but .11 no
        // longer requires it. Any new steady road TPS/RPM point may be learned
        // before READY; this only verifies the ordinary re-arm completion path.
        for (int i = 0; i < 24; i++) {
            seconds += 0.025;
            latest = sample(++index, seconds, 1790.0, 6.2,
                    0.2, 1.0, 0.2, false);
            EngagementPassiveCapture.accept(latest);
        }
        require(EngagementPassiveCapture.snapshot().readyForMovement(),
                "synthetic passive fixture did not re-arm after TPS settled");
        require(EngagementPassiveCapture.snapshot().comparableEvents == 1,
                "settling/re-arm incorrectly created another physical event");

        final LiveSample readySample = latest;
        final EngagementFocusModel readyModel = EngagementFocusModel.build(
                snapshot, readySample, GuidedCaptureState.CAPTURING,
                1, 6, 50, 50);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { panel.updateModel(readyModel); }
        });
        require(panel.driverInstructionForTest().contains("REPEAT APPROXIMATELY THE REFERENCE MOVEMENT"),
                "passive driver view did not return to the minimal natural-repeat instruction after settling");
        require(panel.sweepTargetTextForTest().contains("not a hard target"),
                "re-armed passive driver view turned the visual reference into a hard target");
        require(Double.isFinite(panel.driverReferenceMarkerForTest()),
                "re-armed passive driver view lost the first full-height TPS reference marker");
        require(!Double.isFinite(panel.driverReturnMarkerForTest())
                        && !panel.driverTpsTextForTest().contains("RETURN"),
                "settling return reference remained visible after READY re-armed");

        // Complete a compact three-event set over representative pre-event RPM
        // and prove the Driver view visibly promotes 1/2 Delta Window ->
        // 2/2 Sample Length instead of hiding Sample Length in proposal metadata.
        EngagementPassiveCapture.configureTarget(3);
        double[] eventRpms = new double[]{2050.0, 2350.0};
        for (double eventRpm : eventRpms) {
            for (int i = 0; i < 12; i++) {
                seconds += 0.025;
                latest = sample(++index, seconds, eventRpm, 6.2,
                        0.2, 1.0, 0.2, false);
                EngagementPassiveCapture.accept(latest);
            }
            double[] opening = new double[]{7.0, 10.0, 14.0, 18.0, 20.0, 19.4};
            for (int i = 0; i < opening.length; i++) {
                seconds += 0.025;
                boolean detector = i >= 2;
                latest = sample(++index, seconds, eventRpm, opening[i],
                        detector ? 2.0 : 0.5, 1.0,
                        i == 0 ? 30.0 : 18.0, detector);
                EngagementPassiveCapture.accept(latest);
            }
            for (int i = 0; i < 24; i++) {
                seconds += 0.025;
                latest = sample(++index, seconds, eventRpm, 6.2,
                        0.2, 1.0, 0.2, false);
                EngagementPassiveCapture.accept(latest);
            }
        }
        require(EngagementPassiveCapture.snapshot().complete(),
                "synthetic timing-promotion fixture did not complete");
        final LiveSample completeSample = latest;
        final EngagementFocusModel completeModel = EngagementFocusModel.build(
                snapshot, completeSample, GuidedCaptureState.COMPLETE,
                3, 3, 140, 140);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { panel.updateModel(completeModel); }
        });
        require(panel.driverInstructionForTest().contains("1/2 DELTA WINDOW")
                        && panel.driverInstructionForTest().contains("2/2 SAMPLE LENGTH")
                        && panel.detectorStateForTest().contains("TIMING PAIR COMPLETE"),
                "completed Foundation 1 Driver view did not visibly promote Delta Window to Sample Length");

        renderWidth(panel, out, 1180, 720, true);
        renderWidth(panel, out, 1024, 650, false);
        renderWidth(panel, out, 820, 600, false);
        renderDetailsWindow(out, readyModel);
        System.out.println("EngagementDriverViewSyntheticTest passed");
    }

    private static void renderWidth(final EngagementDetectionGuidedFocusPanel panel,
                                    File out, final int width, final int height,
                                    final boolean verifyGaugeGeometry)
            throws Exception {
        final JFrame[] frame = new JFrame[1];
        final boolean[] gaugeGeometryOk = new boolean[]{true};
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                JFrame f = new JFrame("Passive Foundation Driver View synthetic " + width);
                f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                f.setContentPane(panel);
                f.setSize(new Dimension(width, height));
                f.setVisible(true);
                f.validate();
                if (verifyGaugeGeometry) gaugeGeometryOk[0] = gaugesHaveUsableGeometry(panel);
                frame[0] = f;
            }
        });
        final File file = new File(out,
                "workspace-guided-focus-engagement-driver-" + width + ".png");
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    BufferedImage image = new BufferedImage(
                            Math.max(1, panel.getWidth()),
                            Math.max(1, panel.getHeight()),
                            BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    try { panel.printAll(g); }
                    finally { g.dispose(); }
                    ImageIO.write(image, "png", file);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { frame[0].dispose(); }
        });
        require(file.isFile() && file.length() > 0L,
                "passive Foundation driver screenshot missing at " + width + " px");
        require(!verifyGaugeGeometry || gaugeGeometryOk[0],
                "RPM/TPS gauges do not have usable visible geometry");
        System.out.println("ENGAGEMENT_DRIVER_WIDTH " + width + " passed");
    }

    private static void renderDetailsWindow(File out,
                                            final EngagementFocusModel model)
            throws Exception {
        final GuidedFocusWindow[] window = new GuidedFocusWindow[1];
        final boolean[] passiveDetailsOk = new boolean[]{false};
        final boolean[] driverToggleOk = new boolean[]{false};
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                GuidedFocusWindow w = new GuidedFocusWindow(null);
                w.setSize(new Dimension(1180, 720));
                w.update(GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                        GuidedCaptureState.CAPTURING, null, model, "");
                JCheckBox toggle = findCheckBox(w, "Driver view");
                driverToggleOk[0] = toggle != null && toggle.isSelected();
                if (toggle != null && toggle.isSelected()) toggle.doClick();
                w.setVisible(true);
                w.validate();
                EngagementDetectionGuidedFocusPanel engagement =
                        w.engagementPanelForTest();
                passiveDetailsOk[0] = !w.engagementCalibrationDetailsForTest().isVisible()
                        && !engagement.settingsToggleVisibleForTest()
                        && !engagement.settingsPanelVisibleForTest()
                        && !engagement.requestedDeltaWindowEnabledForTest()
                        && engagement.sweepTargetTextForTest().contains("not a hard target");
                window[0] = w;
            }
        });

        final File file = new File(out,
                "workspace-guided-focus-engagement-details-1180.png");
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    GuidedFocusWindow w = window[0];
                    BufferedImage image = new BufferedImage(
                            Math.max(1, w.getWidth()),
                            Math.max(1, w.getHeight()),
                            BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    try { w.printAll(g); }
                    finally { g.dispose(); }
                    ImageIO.write(image, "png", file);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { window[0].disposeWindow(); }
        });
        require(driverToggleOk[0],
                "synthetic Focus window did not start in Driver view");
        require(passiveDetailsOk[0],
                "passive Foundation Details revived calibration/sweep controls or hard-target UI");
        require(file.isFile() && file.length() > 0L,
                "passive Foundation Details screenshot missing");
        System.out.println("ENGAGEMENT_DETAILS_LAYOUT 1180 passed");
    }

    private static boolean gaugesHaveUsableGeometry(Container root) {
        List<JComponent> gauges = new ArrayList<JComponent>();
        collectBandGauges(root, gauges);
        if (gauges.size() != 2) return false;
        for (JComponent gauge : gauges) {
            if (!gauge.isVisible() || gauge.getWidth() <= 40 || gauge.getHeight() <= 30) {
                return false;
            }
        }
        return true;
    }

    private static void collectBandGauges(Container root, List<JComponent> out) {
        for (Component component : root.getComponents()) {
            if (component instanceof JComponent
                    && "BandGauge".equals(component.getClass().getSimpleName())) {
                out.add((JComponent) component);
            }
            if (component instanceof Container) {
                collectBandGauges((Container) component, out);
            }
        }
    }

    private static JCheckBox findCheckBox(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox
                    && text.equals(((JCheckBox) component).getText())) {
                return (JCheckBox) component;
            }
            if (component instanceof Container) {
                JCheckBox nested = findCheckBox((Container) component, text);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static LiveSample sample(long index, double seconds,
                                     double rpm, double tps,
                                     double delta, double threshold,
                                     double tpsRate, boolean detectorActive) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, delta);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detectorActive ? 1.0 : 0.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
        return new LiveSample(index, seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "engagement-driver-synthetic",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0, new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static File outputDirectory() {
        String configured = System.getenv("SYNTHETIC_INTEGRATION_OUT");
        return new File(configured == null || configured.length() == 0
                ? "target/synthetic-plugin-integration" : configured);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
