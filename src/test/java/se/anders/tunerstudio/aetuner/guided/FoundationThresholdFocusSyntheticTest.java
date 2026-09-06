package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;
import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusBridge;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Real-Swing gate proving Foundation 2 routes to its dedicated event-level Focus card. */
public final class FoundationThresholdFocusSyntheticTest {
    private FoundationThresholdFocusSyntheticTest() { }

    public static void main(String[] args) {
        int exit = 1;
        GuidedFocusWindow window = null;
        try {
            final File out = outputDirectory();
            if (!out.isDirectory() && !out.mkdirs()) {
                throw new IllegalStateException("Could not create synthetic output: " + out);
            }

            FoundationThresholdFocusBridge.reset();
            List<LiveSample> evidence = evidence();
            FoundationThresholdFocusBridge.replaceEvidence(snapshot(), evidence);
            GuidedFocusHub.publish(GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                    GuidedCaptureState.COMPLETE, (MapEstimateFocusModel) null,
                    "Synthetic Threshold / Sensitivity Focus");

            window = new GuidedFocusWindow(null);
            final GuidedFocusWindow focus = window;
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    focus.setSize(new Dimension(1180, 720));
                    GuidedFocusHub.snapshot().refresh(focus);
                    focus.openWindow();
                    focus.validate();
                }
            });

            require("foundation-threshold".equals(focus.visibleCardForTest()),
                    "Foundation 2 still routed to the generic coach card");
            FoundationThresholdGuidedFocusPanel panel = focus.foundationThresholdPanelForTest();
            require(panel.driverViewForTest(), "Foundation 2 Focus did not open in Driver View");
            require(!panel.hasRootScrollForTest(), "Foundation 2 Driver View gained a root scrollbar");
            require(panel.recommendationTextForTest().contains("READY FOR REVIEW"),
                    "Foundation 2 event-level synthetic evidence did not reach recommendation-ready state");
            require(panel.eventCountsTextForTest().contains("NORMAL CORRECTIONS")
                            && panel.eventCountsTextForTest().contains("ACCELERATION OPENINGS"),
                    "Foundation 2 Driver View still exposes abstract Ordinary/Deliberate class language");
            require(panel.separationTextForTest().contains("SEPARATION VALID"),
                    "3+3 event completion did not expose separation as a distinct Driver state");
            require(!panel.eventBarsPaintStringsForTest(),
                    "critical Foundation 2 Driver text regressed into the small event progress bars");
            render(focus.getRootPane(), new File(out,
                    "workspace-guided-focus-foundation-threshold-driver-1180.png"));

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    focus.foundationThresholdPanelForTest().setDriverView(false);
                    focus.validate();
                }
            });
            require(panel.tableForTest().getRowCount() == 8,
                    "Foundation 2 details did not expose all eight threshold RPM bins");
            require(panel.reviewTextForTest().contains("RPM-BIN EVENT DECISIONS"),
                    "Foundation 2 details lost evaluator-backed event evidence text");
            require(panel.reviewTextForTest().contains("quiet p99 is diagnostic only")
                            && panel.reviewTextForTest().contains("Semantic authority: Guided Focus"),
                    "Foundation 2 Focus lost frozen calibration / guided semantic authority");
            require(panel.reviewTextForTest().contains("Normal Correction")
                            && panel.reviewTextForTest().contains("Acceleration Opening"),
                    "Foundation 2 review did not adopt physical maneuver terminology");
            render(focus.getRootPane(), new File(out,
                    "workspace-guided-focus-foundation-threshold-details-1180.png"));

            System.out.println("FOUNDATION_THRESHOLD_FOCUS_ROUTE dedicated event-level card passed");
            System.out.println("FoundationThresholdFocusSyntheticTest passed");
            exit = 0;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
        } finally {
            final GuidedFocusWindow closeWindow = window;
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override public void run() {
                        if (closeWindow != null) closeWindow.disposeWindow();
                        GuidedFocusHub.clear();
                    }
                });
            } catch (Throwable ignored) { }
        }
        System.exit(exit);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "threshold-focus-synthetic",
                new double[0], new double[0], new double[0][0],
                new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500},
                new double[]{0.30, 1.00, 0.30, 0.30, 1.00, 0.30, 0.30, 0.30},
                0.10, 0.0, new double[0], new double[0],
                true, false, "off", false, false,
                false, false, new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static List<LiveSample> evidence() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = 0.0;
        for (int i = 0; i < 90; i++) {
            samples.add(sample(seconds, 1500.0, 12.0,
                    0.020 + (i % 3) * 0.002, 1.0, 0.20));
            seconds += 0.020;
        }
        seconds += 0.25;
        for (int event = 0; event < 4; event++) {
            samples.add(sample(seconds, 1500.0, 12.0 + event * 0.03,
                    0.100 + event * 0.004, 1.0, 3.0));
            seconds += 0.25;
            samples.add(sample(seconds, 3000.0, 12.0 + event * 0.03,
                    0.110 + event * 0.004, 1.0, 3.2));
            seconds += 0.25;
        }
        for (int event = 0; event < 4; event++) {
            double tps = 15.0 + event * 0.10;
            samples.add(sample(seconds, 1500.0, tps,
                    0.42 + event * 0.01, 1.0, 18.0));
            samples.add(sample(seconds + 0.05, 1500.0, tps + 0.7,
                    0.58 + event * 0.01, 1.0, 4.0));
            samples.add(sample(seconds + 0.10, 1500.0, tps + 0.75,
                    0.54 + event * 0.01, 1.0, 1.0));
            seconds += 0.40;
            tps = 16.0 + event * 0.10;
            samples.add(sample(seconds, 3000.0, tps,
                    0.45 + event * 0.01, 1.0, 20.0));
            samples.add(sample(seconds + 0.05, 3000.0, tps + 0.8,
                    0.62 + event * 0.01, 1.0, 4.5));
            samples.add(sample(seconds + 0.10, 3000.0, tps + 0.85,
                    0.57 + event * 0.01, 1.0, 1.0));
            seconds += 0.40;
        }
        return samples;
    }

    private static LiveSample sample(double seconds, double rpm, double tps, double delta,
                                     double threshold, double tpsRate) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, tpsRate, 0.0);
    }

    private static void render(final JComponent component, final File file)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    int width = Math.max(1, component.getWidth());
                    int height = Math.max(1, component.getHeight());
                    BufferedImage image = new BufferedImage(
                            width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        component.printAll(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    ImageIO.write(image, "png", file);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        require(file.isFile() && file.length() > 0L,
                "synthetic screenshot was not created: " + file);
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
