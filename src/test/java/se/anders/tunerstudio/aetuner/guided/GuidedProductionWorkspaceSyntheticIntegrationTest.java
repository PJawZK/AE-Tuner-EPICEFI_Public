package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real-Swing/Xvfb gate for the permanent Guided-only v0.19 production UI. */
public final class GuidedProductionWorkspaceSyntheticIntegrationTest {
    private GuidedProductionWorkspaceSyntheticIntegrationTest() { }

    public static void main(String[] args) {
        int exit = 1;
        AeTunerPlugin plugin = null;
        JFrame frame = null;
        List<JFrame> evidenceFrames = new ArrayList<JFrame>();
        String oldRecovery = System.getProperty("ae.tuner.recovery.dir");
        AeUiTheme.Side oldTheme = AeUiTheme.side();
        try {
            File out = outputDirectory();
            Files.createDirectories(out.toPath());
            Path recovery = Files.createTempDirectory("ae-tuner-guided-only-swing");
            System.setProperty("ae.tuner.recovery.dir", recovery.toString());
            AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);

            plugin = new AeTunerPlugin();
            JComponent pluginPanel = plugin.getPluginPanel();
            frame = show(pluginPanel, 1366, 768);
            flushLayout(frame);

            final GuidedProductionWorkspacePanel workspace =
                    (GuidedProductionWorkspacePanel)field(plugin, "guidedProductionWorkspace");
            final GuidedCapturePanel production =
                    (GuidedCapturePanel)field(plugin, "guidedPanel");

            require(workspace != null && workspace.getParent() == pluginPanel,
                    "plugin did not directly host the v0.19 production workspace");
            require(!declaresField(AeTunerPlugin.class, "rootTabs"),
                    "legacy four-tab host returned");
            require(!declaresField(AeTunerPlugin.class, "passiveAnalysisWindow"),
                    "retired Passive pop-out returned");
            require(!containsText(workspace, "Passive Analysis…"),
                    "retired Passive Analysis control is rendered");
            require(workspace.stageCardCountForTest() == 6
                            && workspace.mainUsesTwoByThreeCardsForTest(),
                    "v0.19 six-card workflow structure changed");
            require(workspace.taskCountForTest() == 19,
                    "production sidebar no longer exposes 19 tasks");
            require("Prepare".equals(workspace.visibleStageForTest()),
                    "workflow did not open on Prepare");
            require(workspace.exportSessionButtonForTest() != null
                            && workspace.diagnosticsButtonForTest() != null,
                    "Guided export/diagnostics actions are missing");

            final AeProjectSnapshot tune = snapshot();
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    production.setProjectSnapshotForTest(tune);
                    workspace.setCurrentTune(tune);
                }
            });
            flushLayout(frame);
            require(countAvailable(workspace) == 16,
                    "expected sixteen production-bound tasks");
            require(!production.applyCurrentProposalEnabledForTest(),
                    "UI construction manufactured Apply authority");

            render(pluginPanel, new File(out, "workspace-guided-v019-light-1366.png"));
            assertInsideFrame(workspace, frame, 1366);

            click(workspace.prepareButtonForTest());
            require("Capture".equals(workspace.visibleStageForTest()),
                    "Prepare Continue did not advance to Capture");
            render(pluginPanel, new File(out, "workspace-guided-v019-capture-light-1366.png"));

            selectTask(workspace, GuidedProductionTask.THRESHOLD_SENSITIVITY);
            JComponent prepareInfo = workspace.prepareInfoViewForTest();
            JComponent captureInfo = workspace.captureInfoViewForTest();
            require(containsText(prepareInfo, "acceleration threshold")
                            && containsText(captureInfo, "Capture writes: NONE"),
                    "Threshold task-specific guidance regressed");
            evidenceFrames.add(renderStage(prepareInfo, 900, 590,
                    new File(out, "workspace-guided-v019-threshold-prepare-info-light-900.png")));
            evidenceFrames.add(renderStage(captureInfo, 900, 590,
                    new File(out, "workspace-guided-v019-threshold-capture-info-light-900.png")));

            evidenceFrames.add(renderFocus(workspace,
                    GuidedProductionTask.TPS_MOVEMENT_TIMING,
                    "GuidedV019TpsTimingFocus",
                    new File(out, "workspace-guided-v019-tps-focus-light-1260.png")));
            evidenceFrames.add(renderFocus(workspace,
                    GuidedProductionTask.THRESHOLD_SENSITIVITY,
                    "GuidedV019ThresholdFocus",
                    new File(out, "workspace-guided-v019-threshold-focus-light-1260.png")));
            evidenceFrames.add(renderFocus(workspace,
                    GuidedProductionTask.DECEL_DETECTION,
                    "GuidedV019DecelDetectionFocus",
                    new File(out, "workspace-guided-v019-decel-detection-focus-light-1260.png")));
            evidenceFrames.add(renderFocus(workspace,
                    GuidedProductionTask.MAP_ESTIMATE,
                    "GuidedV019MapEstimateFocus",
                    new File(out, "workspace-guided-v019-map-focus-light-1260.png")));
            evidenceFrames.add(renderFocus(workspace,
                    GuidedProductionTask.BLEND_DURATION,
                    "GuidedV019BlendDurationFocus",
                    new File(out, "workspace-guided-v019-blend-focus-light-1260.png")));

            selectTask(workspace, GuidedProductionTask.MAP_ESTIMATE);
            JComponent review = workspace.reviewViewForTest();
            require(containsText(review, "Review Results")
                            && containsText(review, "MORE EVIDENCE"),
                    "Review no longer fails closed on incomplete evidence");
            evidenceFrames.add(renderStage(review, 1260, 700,
                    new File(out, "workspace-guided-v019-review-light-1260.png")));

            JComponent applyView = workspace.applyViewForTest();
            require(containsText(applyView, "Guided Apply")
                            && containsText(applyView, "NO WRITE AUTHORITY"),
                    "Apply view exposed authority without a ProposalWritePlan");
            evidenceFrames.add(renderStage(applyView, 1260, 700,
                    new File(out, "workspace-guided-v019-apply-no-write-light-1260.png")));

            JComponent resultView = workspace.resultViewForTest(false);
            require(containsText(resultView, "EVIDENCE INCOMPLETE")
                            && containsText(resultView, "continue Capture"),
                    "Result no longer routes incomplete evidence back to Capture");
            evidenceFrames.add(renderStage(resultView, 1180, 680,
                    new File(out, "workspace-guided-v019-result-incomplete-light-1180.png")));

            click(workspace.diagnosticsButtonForTest());
            JDialog diagnosticsDialog = (JDialog)field(plugin, "evidenceDiagnosticsWindow");
            require(diagnosticsDialog != null && diagnosticsDialog.isVisible(),
                    "Evidence / Diagnostics did not open");
            flushLayout(diagnosticsDialog);
            render((JComponent)diagnosticsDialog.getContentPane(),
                    new File(out, "workspace-guided-v019-diagnostics-popout-light-1120.png"));
            require(GuidedDialogLifecycle.liveCount() >= 1,
                    "open Guided diagnostics dialog was not lifecycle-owned");
            diagnosticsDialog.dispose();
            flushEdt();

            AeUiTheme.set(AeUiTheme.Side.DARK_SIDE);
            workspace.setCurrentTune(tune);
            resize(frame, 1024, 768);
            render(pluginPanel, new File(out, "workspace-guided-v019-dark-1024.png"));
            assertInsideFrame(workspace, frame, 1024);
            resize(frame, 820, 768);
            render(pluginPanel, new File(out, "workspace-guided-v019-dark-820.png"));
            assertInsideFrame(workspace, frame, 820);

            String before = production.selectedTuningTaskForTest();
            selectTask(workspace, GuidedProductionTask.DECEL_FUEL_RESPONSE);
            require(before.equals(production.selectedTuningTaskForTest()),
                    "planned Decel task bypassed fail-closed routing");

            List<String> resultLines = new ArrayList<String>();
            resultLines.add("Guided v0.19 structural production Swing parity: passed");
            resultLines.add("Direct host: legacy tabs and Passive pop-out absent PASS");
            resultLines.add("Prototype main structure: task selector + stepper + 2x3 six-card workflow PASS");
            resultLines.add("Task ownership: 19 visible / 16 production bindings clickable");
            resultLines.add("Passive Analysis control: ABSENT from production component tree PASS");
            resultLines.add("Evidence/Diagnostics: lifecycle-owned pop-out PASS");
            resultLines.add("TPS / Threshold / Decel / MAP Estimate / Blend Focus routes PASS");
            resultLines.add("Review / Apply / Result: incomplete evidence remains fail-closed PASS");
            resultLines.add("Still-planned Decel tasks: visible / fail-closed routing PASS");
            resultLines.add("Widths: 1366 / 1024 / 820 PASS");
            Files.write(new File(out, "guided-v019-ui-result.txt").toPath(),
                    resultLines, StandardCharsets.UTF_8);

            System.out.println("GuidedProductionWorkspaceSyntheticIntegrationTest passed");
            exit = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        } finally {
            for (JFrame evidenceFrame : evidenceFrames) {
                if (evidenceFrame != null) evidenceFrame.dispose();
            }
            GuidedDialogLifecycle.disposeAll();
            if (frame != null) frame.dispose();
            if (plugin != null) plugin.close();
            AeUiTheme.set(oldTheme);
            if (oldRecovery == null) System.clearProperty("ae.tuner.recovery.dir");
            else System.setProperty("ae.tuner.recovery.dir", oldRecovery);
        }
        if (exit != 0) System.exit(exit);
    }

    private static JFrame renderFocus(GuidedProductionWorkspacePanel workspace,
                                      GuidedProductionTask task,
                                      String expectedClass,
                                      File output) throws Exception {
        JComponent focus = workspace.focusViewForTest(task);
        require(expectedClass.equals(focus.getClass().getSimpleName()),
                task.displayName + " routed to " + focus.getClass().getSimpleName());
        require(!"GuidedV019UnsupportedFocus".equals(focus.getClass().getSimpleName()),
                task.displayName + " routed to unsupported Focus");
        return renderStage(focus, 1260, 720, output);
    }

    private static GuidedProductionTask[] fullBaselineTasks() {
        return new GuidedProductionTask[]{
                GuidedProductionTask.TPS_MOVEMENT_TIMING,
                GuidedProductionTask.THRESHOLD_SENSITIVITY,
                GuidedProductionTask.DECEL_DETECTION,
                GuidedProductionTask.TPS_FUEL_RESPONSE,
                GuidedProductionTask.TPS_SCALING,
                GuidedProductionTask.TPS_COMPLETION,
                GuidedProductionTask.TPS_VALIDATION,
                GuidedProductionTask.MAP_ESTIMATE,
                GuidedProductionTask.BLEND_DURATION,
                GuidedProductionTask.WALL_FILM,
                GuidedProductionTask.WALL_ADVANCED,
                GuidedProductionTask.WALL_VALIDATION,
                GuidedProductionTask.INSTANT_PULSE,
                GuidedProductionTask.INSTANT_EVENT_STRENGTH,
                GuidedProductionTask.INSTANT_CONDITIONS,
                GuidedProductionTask.INSTANT_VALIDATION
        };
    }

    private static int countAvailable(GuidedProductionWorkspacePanel workspace) {
        int count = 0;
        for (GuidedProductionTask task : fullBaselineTasks()) {
            if (workspace.taskAvailableForTest(task)) count++;
        }
        return count;
    }

    private static void selectTask(final GuidedProductionWorkspacePanel workspace,
                                   final GuidedProductionTask task) throws Exception {
        final Method method = GuidedProductionWorkspacePanel.class.getDeclaredMethod(
                "selectTask", GuidedProductionTask.class);
        method.setAccessible(true);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try { method.invoke(workspace, task); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }
        });
    }

    private static void click(final AbstractButton button) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { button.doClick(); }
        });
        flushEdt();
    }

    private static JFrame show(final JComponent content, final int width, final int height)
            throws Exception {
        final JFrame[] frame = new JFrame[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                frame[0] = new JFrame("AE Tuner Guided-only synthetic");
                frame[0].setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame[0].setContentPane(content);
                frame[0].setSize(width, height);
                frame[0].setLocation(20, 20);
                frame[0].setVisible(true);
            }
        });
        return frame[0];
    }

    private static JFrame renderStage(final JComponent content, final int width,
                                      final int height, final File output) throws Exception {
        final JFrame frame = show(content, width, height);
        flushLayout(frame);
        render(content, output);
        return frame;
    }

    private static void resize(final JFrame frame, final int width, final int height)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                frame.setSize(width, height);
                frame.validate();
            }
        });
        flushEdt();
    }

    private static void flushLayout(final Container container) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                container.invalidate();
                container.validate();
                container.doLayout();
            }
        });
        flushEdt();
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { }
        });
    }

    private static void render(final JComponent component, final File file) throws Exception {
        final BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                Dimension size = component.getSize();
                int width = Math.max(1, size.width);
                int height = Math.max(1, size.height);
                image[0] = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image[0].createGraphics();
                try { component.printAll(g); }
                finally { g.dispose(); }
            }
        });
        ImageIO.write(image[0], "png", file);
    }

    private static void assertInsideFrame(Component component, JFrame frame, int width) {
        require(component.getWidth() <= frame.getContentPane().getWidth() + 2,
                "workspace overflow at width " + width + ": " + component.getWidth()
                        + " > " + frame.getContentPane().getWidth());
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static boolean declaresField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException expected) {
            return false;
        }
    }

    private static boolean containsText(Component root, String expected) {
        if (root instanceof JLabel && ((JLabel)root).getText() != null
                && ((JLabel)root).getText().contains(expected)) return true;
        if (root instanceof AbstractButton && ((AbstractButton)root).getText() != null
                && ((AbstractButton)root).getText().contains(expected)) return true;
        if (root instanceof JTextComponent && ((JTextComponent)root).getText() != null
                && ((JTextComponent)root).getText().contains(expected)) return true;
        if (root instanceof Container) {
            for (Component child : ((Container)root).getComponents()) {
                if (containsText(child, expected)) return true;
            }
        }
        return false;
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "guided-v019-synthetic",
                new double[]{1.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.0},
                0.0, 0.0, new double[0], new double[0],
                true, true, "basic", true, true, false, false,
                new double[0][0], new double[0][0],
                new double[]{1500.0}, new double[]{20.0}, new double[][]{{55.0}},
                new double[]{1500.0, 2600.0, 3800.0, 5000.0},
                new double[]{0.30, 0.26, 0.24, 0.18},
                "Dual stride, newest", 30.0, 0.05,
                false, true, 0.35,
                new double[]{1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 4500.0},
                new double[]{0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00},
                12.0);
    }

    private static File outputDirectory() {
        String configured = System.getenv("SYNTHETIC_INTEGRATION_OUT");
        return new File(configured == null || configured.trim().length() == 0
                ? "target/synthetic-plugin-integration" : configured.trim());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
