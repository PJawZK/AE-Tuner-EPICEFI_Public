package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;

/** Real-Swing gate proving Blend Duration routes to its dedicated Driver Focus card. */
public final class BlendDurationFocusSyntheticTest {
    private BlendDurationFocusSyntheticTest() { }

    public static void main(String[] args) {
        int exit = 1;
        GuidedFocusWindow window = null;
        try {
            final File out = outputDirectory();
            if (!out.isDirectory() && !out.mkdirs()) {
                throw new IllegalStateException("Could not create synthetic output: " + out);
            }

            GuidedFocusHub.clear();
            BlendDurationGuidedSession session = new BlendDurationGuidedSession();
            session.start(new BlendDurationCaptureConfig(
                    2600.0, 20.0, 5, 0, false,
                    new double[]{1500.0, 2600.0, 3800.0, 5000.0},
                    new double[]{0.08, 0.26, 0.24, 0.18}));
            for (int i = 0; i < 24; i++) {
                double seconds = i * 0.05;
                session.accept(sample(seconds, 2580.0 + i * 1.5,
                        50.0 + i * 0.02, 8.0 + i * 0.005));
            }

            GuidedFocusHub.State state = GuidedFocusHub.snapshot();
            require(state.recipe == GuidedTuningRecipe.BLEND_DURATION
                            && state.blendDuration != null
                            && state.blendDuration.phase == BlendDurationFocusModel.DriverPhase.OPEN_AND_SETTLE,
                    "Blend Duration synthetic session did not reach dedicated READY/opening Focus state");

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

            require("blend-duration".equals(focus.visibleCardForTest()),
                    "Blend Duration still routes to generic coach card");
            BlendDurationGuidedFocusPanel panel = focus.blendDurationPanelForTest();
            require(panel.instructionForTest().contains("OPEN ONCE")
                            && panel.eventProgressForTest().contains("MATCHING EVENTS 0/5")
                            && panel.rpmTextForTest().contains("target 2600")
                            && panel.tpsTextForTest().contains("usable +10 to +40"),
                    "Blend Duration Driver Focus lost one-action/progress coaching");
            render(focus.getRootPane(), new File(out,
                    "workspace-guided-focus-blend-duration-driver-1180.png"));

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    focus.setSize(new Dimension(820, 520));
                    focus.validate();
                }
            });
            require(panel.instructionForTest().contains("OPEN ONCE")
                            && panel.eventProgressForTest().contains("MATCHING EVENTS"),
                    "Blend Duration critical Driver guidance disappeared at 820px width");
            render(focus.getRootPane(), new File(out,
                    "workspace-guided-focus-blend-duration-driver-820.png"));

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    focus.setSize(new Dimension(1180, 720));
                    focus.blendDurationPanelForTest().setDriverView(false);
                    focus.validate();
                }
            });
            require(panel.detailsForTest().contains("Engineering checks")
                            && panel.detailsForTest().contains("Comparability")
                            && panel.detailsForTest().contains("Numerical Blend Duration Apply remains intentionally withheld"),
                    "Blend Duration Details lost engineering diagnostics/no-Apply boundary");
            render(focus.getRootPane(), new File(out,
                    "workspace-guided-focus-blend-duration-details-1180.png"));

            System.out.println("BLEND_DURATION_FOCUS_ROUTE dedicated Driver/Details card passed");
            System.out.println("BlendDurationFocusSyntheticTest passed");
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

    private static LiveSample sample(double seconds, double rpm, double map, double tps) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, map);
        values.put(ChannelRole.EFFECTIVE_MAP, map);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static void render(final JComponent component, final File file) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    int width = Math.max(1, component.getWidth());
                    int height = Math.max(1, component.getHeight());
                    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
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
