package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Xvfb evidence gate for implemented production recipes that intentionally use
 * the shared v0.19 evidence Focus presentation rather than a dedicated numerical
 * Focus model. Dedicated TPS Fuel, Wall Base and Instant Setup Focus views are
 * covered by the workspace synthetic gate and must not be forced through this
 * generic presentation. No synthetic evidence/proposal/write is created here.
 */
public final class GuidedV019BoundEvidenceFocusSyntheticTest {
    private GuidedV019BoundEvidenceFocusSyntheticTest() { }

    public static void main(String[] args) {
        int exit = 1;
        GuidedCapturePanel production = null;
        List<JFrame> frames = new ArrayList<JFrame>();
        String oldRecovery = System.getProperty("ae.tuner.recovery.dir");
        AeUiTheme.Side oldTheme = AeUiTheme.side();
        try {
            File out = outputDirectory();
            Files.createDirectories(out.toPath());
            Path recovery = Files.createTempDirectory("ae-tuner-v019-bound-evidence");
            System.setProperty("ae.tuner.recovery.dir", recovery.toString());
            AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);

            production = new GuidedCapturePanel();
            final GuidedCapturePanel p = production;
            final AeProjectSnapshot tune = snapshot();
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() { p.setProjectSnapshotForTest(tune); }
            });

            GuidedProductionTask[] generic = new GuidedProductionTask[]{
                    GuidedProductionTask.TPS_SCALING,
                    GuidedProductionTask.TPS_COMPLETION,
                    GuidedProductionTask.TPS_VALIDATION,
                    GuidedProductionTask.WALL_ADVANCED,
                    GuidedProductionTask.WALL_VALIDATION,
                    GuidedProductionTask.INSTANT_EVENT_STRENGTH,
                    GuidedProductionTask.INSTANT_CONDITIONS,
                    GuidedProductionTask.INSTANT_VALIDATION
            };
            for (GuidedProductionTask task : generic) {
                frames.add(renderTask(production, task,
                        new File(out, "workspace-guided-v019-bound-"
                                + task.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                                + "-light-1260.png")));
            }

            require(!"GuidedV019BoundEvidenceFocus".equals(
                            GuidedV019FocusViews.create(GuidedProductionTask.TPS_FUEL_RESPONSE, null, null)
                                    .getClass().getSimpleName()),
                    "dedicated TPS Fuel Focus was collapsed into the generic bound-evidence view");
            require(!"GuidedV019BoundEvidenceFocus".equals(
                            GuidedV019FocusViews.create(GuidedProductionTask.WALL_FILM, null, null)
                                    .getClass().getSimpleName()),
                    "dedicated Wall Base Focus was collapsed into the generic bound-evidence view");
            require(!"GuidedV019BoundEvidenceFocus".equals(
                            GuidedV019FocusViews.create(GuidedProductionTask.INSTANT_PULSE, null, null)
                                    .getClass().getSimpleName()),
                    "dedicated Instant Setup Focus was collapsed into the generic bound-evidence view");

            System.out.println("GuidedV019BoundEvidenceFocusSyntheticTest passed");
            exit = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        } finally {
            for (JFrame frame : frames) if (frame != null) frame.dispose();
            if (production != null) {
                production.terminateForClose();
                production.releaseAfterClose();
            }
            GuidedFocusHub.clear();
            AeUiTheme.set(oldTheme);
            if (oldRecovery == null) System.clearProperty("ae.tuner.recovery.dir");
            else System.setProperty("ae.tuner.recovery.dir", oldRecovery);
        }
        if (exit != 0) System.exit(exit);
    }

    private static JFrame renderTask(final GuidedCapturePanel production,
                                     final GuidedProductionTask task,
                                     final File output) throws Exception {
        require(task.productionRecipe != null && task.firstSliceBound,
                task.displayName + " is not an available production binding");
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { production.selectTuningTaskForTest(task.productionRecipe); }
        });
        final JComponent focus = GuidedV019FocusViews.create(task, null, null);
        require("GuidedV019BoundEvidenceFocus".equals(focus.getClass().getSimpleName()),
                task.displayName + " did not route to the production-bound v0.19 evidence Focus");
        require(!containsLegacyWorkflowTitle(focus),
                task.displayName + " reused the legacy generic Guided presentation");
        require(containsText(focus, "DO THIS NOW")
                        && containsText(focus, "WHAT COUNTS AS USEFUL EVIDENCE")
                        && containsText(focus, "Production session"),
                task.displayName + " lost the explicit maneuver/evidence boundary");
        require(containsText(focus, expectedCueFragment(task)),
                task.displayName + " did not show its task-specific vehicle maneuver");
        require(!containsText(focus, "Follow the active production guidance and repeat comparable events."),
                task.displayName + " regressed to the old generic no-instruction fallback");
        require(!containsText(focus, "EARLY RESIDUAL WINDOW"),
                task.displayName + " still shows the old Instant-specific placeholder illustration");

        GuidedV019FocusBase base = (GuidedV019FocusBase) focus;
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        boolean complete = state != null && state.recipe == task.productionRecipe
                && state.captureState == GuidedCaptureState.COMPLETE;
        require(base.review.isEnabled() == complete,
                task.displayName + " Review gate no longer follows real production COMPLETE state");

        JFrame frame = show(focus, 1260, 680);
        flushLayout(frame);
        render(focus, output);
        return frame;
    }

    private static String expectedCueFragment(GuidedProductionTask task) {
        switch (task) {
            case TPS_SCALING: return "standardized opening";
            case TPS_COMPLETION: return "complete transient/recovery period";
            case TPS_VALIDATION: return "small/medium openings";
            case WALL_ADVANCED: return "standardized paired event";
            case WALL_VALIDATION: return "paired tip-in/tip-out";
            case INSTANT_EVENT_STRENGTH: return "SMALL, MEDIUM and LARGE openings";
            case INSTANT_CONDITIONS: return "standardized event";
            case INSTANT_VALIDATION: return "specific sharp event classes";
            default: throw new IllegalArgumentException("No expected cue for " + task);
        }
    }

    private static JFrame show(final JComponent component, final int width, final int height)
            throws Exception {
        final JFrame[] holder = new JFrame[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                JFrame frame = new JFrame("AE Tuner v0.19 bound evidence Focus");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setContentPane(component);
                frame.setSize(width, height);
                frame.setLocation(20, 20);
                frame.setVisible(true);
                holder[0] = frame;
            }
        });
        return holder[0];
    }

    private static void flushLayout(final Container root) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                layoutRecursively(root);
                root.validate();
                root.repaint();
            }
        });
    }

    private static void layoutRecursively(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container) layoutRecursively((Container) child);
        }
    }

    private static void render(final JComponent component, final File output) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    BufferedImage image = new BufferedImage(
                            Math.max(1, component.getWidth()),
                            Math.max(1, component.getHeight()),
                            BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    component.printAll(graphics);
                    graphics.dispose();
                    ImageIO.write(image, "png", output);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private static boolean containsLegacyWorkflowTitle(Component root) {
        if (root instanceof JComponent) {
            javax.swing.border.Border border = ((JComponent) root).getBorder();
            if (border instanceof javax.swing.border.TitledBorder
                    && "Guided Tuning workflow".equals(
                    ((javax.swing.border.TitledBorder) border).getTitle())) return true;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                if (containsLegacyWorkflowTitle(child)) return true;
            }
        }
        return false;
    }

    private static boolean containsText(Component root, String text) {
        if (root instanceof javax.swing.JLabel) {
            String value = ((javax.swing.JLabel) root).getText();
            if (value != null && value.contains(text)) return true;
        }
        if (root instanceof javax.swing.text.JTextComponent) {
            String value = ((javax.swing.text.JTextComponent) root).getText();
            if (value != null && value.contains(text)) return true;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                if (containsText(child, text)) return true;
            }
        }
        return false;
    }

    private static File outputDirectory() {
        String configured = System.getenv("SYNTHETIC_INTEGRATION_OUT");
        return configured == null || configured.trim().isEmpty()
                ? new File("target/synthetic-plugin-integration") : new File(configured);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "v019-bound-evidence-synthetic",
                new double[]{1.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.0},
                0.0, 0.0, new double[0], new double[0],
                true, true, "basic", true, true, false, false,
                new double[0][0], new double[0][0],
                new double[]{1500.0, 3000.0, 5000.0},
                new double[]{10.0, 30.0, 60.0},
                new double[][]{
                        {50.0, 55.0, 60.0},
                        {65.0, 75.0, 85.0},
                        {90.0, 105.0, 120.0}},
                new double[]{1500.0, 3000.0, 5000.0},
                new double[]{0.18, 0.24, 0.20});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
