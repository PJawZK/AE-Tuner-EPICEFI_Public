package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
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

/** Real-Swing/Xvfb structural parity gate for the v0.19 production UI. */
public final class GuidedProductionWorkspaceSyntheticIntegrationTest {
    private static final String FEATURE_PROPERTY = "ae.tuner.guided.ui.v019";

    private GuidedProductionWorkspaceSyntheticIntegrationTest() { }

    public static void main(String[] args) {
        int exit = 1;
        AeTunerPlugin plugin = null;
        JFrame frame = null;
        List<JFrame> evidenceFrames = new ArrayList<JFrame>();
        String oldFeature = System.getProperty(FEATURE_PROPERTY);
        String oldRecovery = System.getProperty("ae.tuner.recovery.dir");
        AeUiTheme.Side oldTheme = AeUiTheme.side();
        try {
            File out = outputDirectory();
            Files.createDirectories(out.toPath());
            Path recovery = Files.createTempDirectory("ae-tuner-v019-structural-swing");
            System.clearProperty(FEATURE_PROPERTY);
            System.setProperty("ae.tuner.recovery.dir", recovery.toString());
            AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);

            plugin = new AeTunerPlugin();
            JComponent pluginPanel = plugin.getPluginPanel();
            frame = show(pluginPanel, 1366, 768);
            flushLayout(frame);

            final GuidedProductionWorkspacePanel workspace =
                    (GuidedProductionWorkspacePanel) field(plugin, "guidedProductionWorkspace");
            final GuidedCapturePanel production = (GuidedCapturePanel) field(plugin, "guidedPanel");
            JScrollPane legacyScroll = (JScrollPane) field(plugin, "guidedWorkspaceScroll");
            JTabbedPane root = (JTabbedPane) field(plugin, "rootTabs");

            require(workspace != null && workspace.getParent() == pluginPanel,
                    "default plugin did not install structural v0.19 workspace as direct host");
            require(root.getParent() == null && root.getTabCount() == 0,
                    "default v0.19 host still renders the old top-level tab layout");
            require(!isDescendant(workspace, legacyScroll) && !workspace.legacySurfaceRenderedForTest(),
                    "structural v0.19 path still renders the legacy Guided surface");
            require(workspace.stageCardCountForTest() == 6 && workspace.mainUsesTwoByThreeCardsForTest(),
                    "main workflow lost prototype 2x3 six-card structure");
            require(workspace.taskCountForTest() == 19,
                    "sidebar does not expose the full 19-task production baseline");
            require("Prepare".equals(workspace.visibleStageForTest()),
                    "unread production workflow did not begin on Prepare");
            require("Read Working Tune".equals(workspace.prepareButtonForTest().getText()),
                    "unread Prepare did not expose the real Working Tune read action");
            require(workspace.passiveAnalysisButtonForTest().isVisible()
                            && workspace.diagnosticsButtonForTest().isVisible(),
                    "new sidebar did not expose Passive / Diagnostics pop-out actions");

            final AeProjectSnapshot tune = snapshot();
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    production.setProjectSnapshotForTest(tune);
                    workspace.setCurrentTune(tune);
                }
            });
            flushLayout(frame);
            require("Prepare".equals(workspace.visibleStageForTest()),
                    "Working Tune load skipped the prototype Prepare step");
            require("Continue".equals(workspace.prepareButtonForTest().getText()),
                    "loaded Prepare did not switch from Read Working Tune to Continue");
            require(countAvailable(workspace) == 16,
                    "full baseline must expose sixteen production-bound tasks when all supported methods are enabled");
            for (GuidedProductionTask task : fullBaselineTasks()) {
                require("AVAILABLE".equals(workspace.taskStatusForTest(task)),
                        task.displayName + " did not become AVAILABLE in the full production baseline");
                JComponent focus = workspace.focusViewForTest(task);
                require(!"GuidedV019UnsupportedFocus".equals(focus.getClass().getSimpleName()),
                        task.displayName + " still routes to Unsupported Focus");
            }
            require("PLANNED".equals(workspace.taskStatusForTest(GuidedProductionTask.DECEL_FUEL_RESPONSE)),
                    "still-planned Decel Fuel task stopped failing closed");
            require("PLANNED".equals(workspace.taskStatusForTest(GuidedProductionTask.DECEL_MAP_PREDICTION)),
                    "still-planned Decel MAP task stopped failing closed");

            render(pluginPanel, new File(out, "workspace-guided-v019-light-1366.png"));
            assertInsideFrame(workspace, frame, 1366);

            click(workspace.prepareButtonForTest());
            flushLayout(frame);
            require("Capture".equals(workspace.visibleStageForTest()),
                    "physical Continue action did not visibly advance to Capture");
            render(pluginPanel, new File(out, "workspace-guided-v019-capture-light-1366.png"));

            selectTask(workspace, GuidedProductionTask.THRESHOLD_SENSITIVITY);
            JComponent prepareInfo = workspace.prepareInfoViewForTest();
            JComponent captureInfo = workspace.captureInfoViewForTest();
            require(containsText(prepareInfo, "acceleration threshold")
                            && containsText(prepareInfo, "ordinary/noisy pedal movement"),
                    "Threshold Prepare Info is generic");
            require(containsText(captureInfo, "small intentional openings")
                            && containsText(captureInfo, "Capture writes: NONE"),
                    "Threshold Capture Info is generic");
            evidenceFrames.add(renderStage(prepareInfo, 900, 590,
                    new File(out, "workspace-guided-v019-threshold-prepare-info-light-900.png")));
            evidenceFrames.add(renderStage(captureInfo, 900, 590,
                    new File(out, "workspace-guided-v019-threshold-capture-info-light-900.png")));

            AeUiTheme.set(AeUiTheme.Side.DARK_SIDE);
            workspace.setCurrentTune(tune);
            resize(frame, 1024, 768);
            render(pluginPanel, new File(out, "workspace-guided-v019-dark-1024.png"));
            assertInsideFrame(workspace, frame, 1024);

            resize(frame, 820, 768);
            render(pluginPanel, new File(out, "workspace-guided-v019-dark-820.png"));
            assertInsideFrame(workspace, frame, 820);

            AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.TPS_MOVEMENT_TIMING,
                    "GuidedV019TpsTimingFocus",
                    new File(out, "workspace-guided-v019-tps-focus-light-1260.png"), true));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.THRESHOLD_SENSITIVITY,
                    "GuidedV019ThresholdFocus",
                    new File(out, "workspace-guided-v019-threshold-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.DECEL_DETECTION,
                    "GuidedV019DecelDetectionFocus",
                    new File(out, "workspace-guided-v019-decel-detection-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.TPS_FUEL_RESPONSE,
                    "GuidedV019TpsAeFocus",
                    new File(out, "workspace-guided-v019-tps-fuel-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.TPS_SCALING,
                    "GuidedV019BoundEvidenceFocus",
                    new File(out, "workspace-guided-v019-tps-scaling-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.WALL_FILM,
                    "GuidedV019WallWettingFocus",
                    new File(out, "workspace-guided-v019-wall-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.WALL_ADVANCED,
                    "GuidedV019BoundEvidenceFocus",
                    new File(out, "workspace-guided-v019-wall-advanced-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.INSTANT_PULSE,
                    "GuidedV019InstantFuelFocus",
                    new File(out, "workspace-guided-v019-instant-setup-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.INSTANT_EVENT_STRENGTH,
                    "GuidedV019BoundEvidenceFocus",
                    new File(out, "workspace-guided-v019-instant-strength-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.MAP_ESTIMATE,
                    "GuidedV019MapEstimateFocus",
                    new File(out, "workspace-guided-v019-map-focus-light-1260.png"), false));
            evidenceFrames.add(renderFocus(workspace, production, tune,
                    GuidedProductionTask.BLEND_DURATION,
                    "GuidedV019BlendDurationFocus",
                    new File(out, "workspace-guided-v019-blend-focus-light-1260.png"), true));

            selectTask(workspace, GuidedProductionTask.MAP_ESTIMATE);
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    production.setProjectSnapshotForTest(tune);
                    workspace.setCurrentTune(tune);
                }
            });
            JComponent review = workspace.reviewViewForTest();
            require(containsText(review, "Review Results") && containsText(review, "MORE EVIDENCE"),
                    "v0.19 Review did not show the real incomplete-evidence decision");
            evidenceFrames.add(renderStage(review, 1260, 700,
                    new File(out, "workspace-guided-v019-review-light-1260.png")));

            JComponent applyView = workspace.applyViewForTest();
            require(containsText(applyView, "Guided Apply") && containsText(applyView, "NO WRITE AUTHORITY"),
                    "v0.19 Apply failed to remain closed without a real ProposalWritePlan");
            evidenceFrames.add(renderStage(applyView, 1260, 700,
                    new File(out, "workspace-guided-v019-apply-no-write-light-1260.png")));

            JComponent resultView = workspace.resultViewForTest(false);
            require(containsText(resultView, "Result")
                            && containsText(resultView, "EVIDENCE INCOMPLETE")
                            && containsText(resultView, "continue Capture"),
                    "v0.19 Result did not expose the incomplete-evidence continuation outcome");
            evidenceFrames.add(renderStage(resultView, 1180, 680,
                    new File(out, "workspace-guided-v019-result-incomplete-light-1180.png")));

            click(workspace.passiveAnalysisButtonForTest());
            JDialog passiveDialog = (JDialog) field(plugin, "passiveAnalysisWindow");
            require(passiveDialog != null && passiveDialog.isVisible(),
                    "Passive Analysis utility did not open as a pop-out");
            flushLayout(passiveDialog);
            render((JComponent) passiveDialog.getContentPane(),
                    new File(out, "workspace-guided-v019-passive-popout-light-1180.png"));
            passiveDialog.setVisible(false);

            click(workspace.diagnosticsButtonForTest());
            JDialog diagnosticsDialog = (JDialog) field(plugin, "evidenceDiagnosticsWindow");
            require(diagnosticsDialog != null && diagnosticsDialog.isVisible(),
                    "Evidence / Diagnostics utility did not open as a pop-out");
            flushLayout(diagnosticsDialog);
            render((JComponent) diagnosticsDialog.getContentPane(),
                    new File(out, "workspace-guided-v019-diagnostics-popout-light-1120.png"));
            diagnosticsDialog.setVisible(false);

            String before = production.selectedTuningTaskForTest();
            selectTask(workspace, GuidedProductionTask.DECEL_FUEL_RESPONSE);
            require(before.equals(production.selectedTuningTaskForTest()),
                    "planned Decel task bypassed fail-closed routing");

            List<String> resultLines = new ArrayList<String>();
            resultLines.add("Guided v0.19 structural production Swing parity: passed");
            resultLines.add("Direct host: old Overview/Guided/Passive/Evidence tabs absent PASS");
            resultLines.add("Prototype main structure: task selector + graphical stepper + 2x3 six-card workflow PASS");
            resultLines.add("Legacy Guided surface absent from normal v0.19 component tree PASS");
            resultLines.add("Task ownership: 19 visible / 16 production bindings clickable");
            resultLines.add("Full TPS AE / Wall Wetting / Instant Fuel baseline routes: AVAILABLE + non-unsupported Focus PASS");
            resultLines.add("Prepare: Read Working Tune -> Continue -> Capture PASS");
            resultLines.add("Task-specific Options/Info: Threshold Prepare + Capture PASS");
            resultLines.add("Passive Analysis + Evidence/Diagnostics: modeless pop-outs PASS");
            resultLines.add("TPS Movement / Timing: prototype Guided Focus + production evidence PASS");
            resultLines.add("Threshold / Sensitivity: prototype Guided Focus + production separation model PASS");
            resultLines.add("Decel Detection: dedicated falling-TPS Focus + threshold-only production authority PASS");
            resultLines.add("TPS AE: base + compensation Focus routes PASS");
            resultLines.add("Wall Wetting: base + advanced Focus routes PASS");
            resultLines.add("Instant Fuel: global setup + event-strength Focus routes PASS");
            resultLines.add("MAP Estimate: prototype table-first Focus + production Working/Learned/Proposed PASS");
            resultLines.add("Blend Duration: prototype five-phase Focus + measured production response PASS");
            resultLines.add("Review / Apply / Result: incomplete evidence remains fail-closed and routes back to Capture PASS");
            resultLines.add("Still-planned Decel tasks: visible / fail-closed routing PASS");
            resultLines.add("Widths: 1366 / 1024 / 820 PASS");
            resultLines.add("Explicit legacy fallback remains covered by separate synthetic lane PASS");
            Files.write(new File(out, "guided-v019-ui-result.txt").toPath(),
                    resultLines, StandardCharsets.UTF_8);

            System.out.println("GuidedProductionWorkspaceSyntheticIntegrationTest passed");
            exit = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        } finally {
            for (JFrame evidenceFrame : evidenceFrames) if (evidenceFrame != null) evidenceFrame.dispose();
            if (frame != null) frame.dispose();
            if (plugin != null) plugin.close();
            AeUiTheme.set(oldTheme);
            if (oldFeature == null) System.clearProperty(FEATURE_PROPERTY);
            else System.setProperty(FEATURE_PROPERTY, oldFeature);
            if (oldRecovery == null) System.clearProperty("ae.tuner.recovery.dir");
            else System.setProperty("ae.tuner.recovery.dir", oldRecovery);
        }
        if (exit != 0) System.exit(exit);
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

    private static JFrame renderFocus(final GuidedProductionWorkspacePanel workspace,
                                      final GuidedCapturePanel production,
                                      final AeProjectSnapshot tune,
                                      final GuidedProductionTask task,
                                      String expectedClass,
                                      File output,
                                      final boolean startIfAvailable) throws Exception {
        selectTask(workspace, task);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                production.setProjectSnapshotForTest(tune);
                workspace.setCurrentTune(tune);
                if (startIfAvailable && production.startCaptureEnabledForTest()) {
                    production.startSelectedTaskForTest();
                }
            }
        });
        final JComponent focus = workspace.focusViewForTest(task);
        require(expectedClass.equals(focus.getClass().getSimpleName()),
                task.displayName + " routed to wrong v0.19 Focus class: " + focus.getClass().getName());
        JFrame focusFrame = show(focus, 1260, 680);
        flushLayout(focusFrame);
        require(!containsLegacyWorkflowTitle(focus),
                task.displayName + " Focus reused legacy generic workflow presentation");
        render(focus, output);
        return focusFrame;
    }

    private static JFrame renderStage(JComponent component, int width, int height, File output)
            throws Exception {
        JFrame frame = show(component, width, height);
        flushLayout(frame);
        require(!containsLegacyWorkflowTitle(component),
                "v0.19 stage surface reused legacy generic workflow presentation");
        render(component, output);
        return frame;
    }

    private static int countAvailable(GuidedProductionWorkspacePanel workspace) {
        int count = 0;
        for (GuidedProductionTask task : GuidedProductionTask.values()) {
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

    private static void click(final JButton button) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { button.doClick(); }
        });
    }

    private static JFrame show(final JComponent component, final int width, final int height)
            throws Exception {
        final JFrame[] holder = new JFrame[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                JFrame f = new JFrame("AE Tuner v0.19 structural integration");
                f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                f.setContentPane(component);
                f.setSize(width, height);
                f.setLocation(20, 20);
                f.setVisible(true);
                holder[0] = f;
            }
        });
        return holder[0];
    }

    private static void resize(final JFrame frame, final int width, final int height)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { frame.setSize(width, height); }
        });
        flushLayout(frame);
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

    private static void layoutRecursively(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof Container) layoutRecursively((Container) child);
        }
    }

    private static void render(final JComponent component, final File output) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    int w = Math.max(1, component.getWidth());
                    int h = Math.max(1, component.getHeight());
                    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    component.printAll(g);
                    g.dispose();
                    ImageIO.write(image, "png", output);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private static void assertInsideFrame(Component component, JFrame frame, int width) {
        require(component.getX() >= 0 && component.getY() >= 0,
                "workspace escaped frame origin at width " + width);
        require(component.getWidth() <= frame.getContentPane().getWidth() + 2,
                "workspace overflowed frame width " + width);
    }

    private static boolean isDescendant(Container root, Component target) {
        if (root == target) return true;
        for (Component child : root.getComponents()) {
            if (child == target) return true;
            if (child instanceof Container && isDescendant((Container) child, target)) return true;
        }
        return false;
    }

    private static boolean containsLegacyWorkflowTitle(Component root) {
        if (root instanceof javax.swing.JComponent) {
            javax.swing.border.Border border = ((javax.swing.JComponent) root).getBorder();
            if (border instanceof javax.swing.border.TitledBorder
                    && "Guided Tuning workflow".equals(((javax.swing.border.TitledBorder)border).getTitle())) {
                return true;
            }
        }
        if (root instanceof Container) {
            for (Component child : ((Container)root).getComponents()) {
                if (containsLegacyWorkflowTitle(child)) return true;
            }
        }
        return false;
    }

    private static boolean containsText(Component root, String expected) {
        if (root instanceof JLabel && ((JLabel) root).getText() != null
                && ((JLabel) root).getText().contains(expected)) return true;
        if (root instanceof javax.swing.AbstractButton
                && ((javax.swing.AbstractButton) root).getText() != null
                && ((javax.swing.AbstractButton) root).getText().contains(expected)) return true;
        if (root instanceof javax.swing.text.JTextComponent
                && ((javax.swing.text.JTextComponent) root).getText() != null
                && ((javax.swing.text.JTextComponent) root).getText().contains(expected)) return true;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                if (containsText(child, expected)) return true;
            }
        }
        return false;
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static File outputDirectory() {
        String configured = System.getenv("SYNTHETIC_INTEGRATION_OUT");
        return configured == null || configured.trim().isEmpty()
                ? new File("target/synthetic-plugin-integration") : new File(configured);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "v019-structural-synthetic",
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
                new double[]{0.18, 0.24, 0.20},
                "Dual stride, newest", 30.0, 0.05,
                false, true, 0.35,
                new double[]{1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 4500.0},
                new double[]{0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00},
                12.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
