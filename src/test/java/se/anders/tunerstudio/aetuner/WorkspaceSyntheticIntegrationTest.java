package se.anders.tunerstudio.aetuner;

import se.anders.tunerstudio.aetuner.guided.GuidedCapturePanel;
import se.anders.tunerstudio.aetuner.guided.GuidedFocusHub;
import se.anders.tunerstudio.aetuner.guided.GuidedFocusWindow;
import se.anders.tunerstudio.aetuner.guided.MapEstimateFocusSnapshot;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Real-Swing synthetic coverage for the current AE Tuner workspace structure. */
public final class WorkspaceSyntheticIntegrationTest {
    private WorkspaceSyntheticIntegrationTest() { }

    public static void main(String[] args) {
        int exit = 1;
        AeTunerPlugin plugin = null;
        JFrame frame = null;
        try {
            File out = outputDirectory();
            Files.createDirectories(out.toPath());

            plugin = new AeTunerPlugin();
            JComponent panel = plugin.getPluginPanel();
            frame = show(panel, 1366, 768);

            JTabbedPane root = (JTabbedPane) field(plugin, "rootTabs");
            requireTitles(root, "Overview", "Guided Tuning", "Passive Analysis",
                    "Evidence / Diagnostics");
            require(root.getSelectedIndex() == 0,
                    "plugin must open on the generic Overview workspace");
            require(findButton(panel, "Open Guided Tuning") == null
                            && findButton(panel, "Open Passive Analysis") == null,
                    "Overview retained duplicate lower navigation buttons");
            render(panel, new File(out, "workspace-overview-1366.png"));

            selectTab(root, 2, frame);
            JTabbedPane passiveTabs = findTabsWithTitle(panel, "Setup / Calibration");
            require(passiveTabs != null,
                    "Passive Analysis did not expose Setup / Calibration");
            require(passiveTabs.getTabCount() == 2,
                    "Passive Analysis must expose only Overview and Setup / Calibration");
            requireTitles(passiveTabs, "Overview", "Setup / Calibration");
            require(indexOf(passiveTabs, "Technical details") < 0,
                    "retired Passive Technical details tab is still visible");
            selectTab(passiveTabs, 1, frame);
            require(findButton(passiveTabs.getSelectedComponent(),
                            "Start TPS noise calibration") != null,
                    "Passive TPS noise calibration was not moved into Setup / Calibration");
            render(panel, new File(out, "workspace-passive-setup-1366.png"));

            selectTab(root, 3, frame);
            JTabbedPane diagnostics = findTabsWithTitle(panel, "Audio Cue Lab");
            require(diagnostics != null,
                    "Evidence / Diagnostics did not expose Audio Cue Lab");
            requireTitles(diagnostics, "Overview", "Channels / Runtime",
                    "Audio Cue Lab", "Recovery / Audit");
            selectTab(diagnostics, 2, frame);
            require(findButton(diagnostics.getSelectedComponent(), "Test all cues") != null,
                    "Audio Cue Lab actions are not reachable on their dedicated diagnostics tab");
            render(panel, new File(out, "workspace-evidence-audio-1366.png"));

            selectTab(root, 1, frame);
            GuidedCapturePanel guided = (GuidedCapturePanel) field(plugin, "guidedPanel");
            assertGuidedActionReachability(frame, guided, 1366);
            assertGuidedActionReachability(frame, guided, 1024);
            assertGuidedActionReachability(frame, guided, 820);
            resize(frame, 1366, 768);
            render(panel, new File(out, "workspace-guided-1366.png"));
            assertGuidedFocusWindow(plugin, panel, frame, out);

            List<String> result = new ArrayList<String>();
            result.add("Workspace synthetic integration: passed");
            result.add("Plugin: " + plugin.getDisplayName() + " " + plugin.getVersion());
            result.add("Root tabs: Overview | Guided Tuning | Passive Analysis | Evidence / Diagnostics");
            result.add("Passive tabs: Overview | Setup / Calibration");
            result.add("Evidence tabs: Overview | Channels / Runtime | Audio Cue Lab | Recovery / Audit");
            result.add("Guided Restore/Reconnect horizontal reachability: 1366 / 1024 / 820 PASS");
            result.add("Guided Focus: modeless MAP Estimate heat map open/hide/reopen PASS");
            Files.write(new File(out, "result.txt").toPath(), result,
                    StandardCharsets.UTF_8);

            System.out.println("WorkspaceSyntheticIntegrationTest passed");
            exit = 0;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
        } finally {
            final AeTunerPlugin closePlugin = plugin;
            final JFrame closeFrame = frame;
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        if (closePlugin != null) closePlugin.close();
                        if (closeFrame != null) closeFrame.dispose();
                        for (Window window : Window.getWindows()) window.dispose();
                    }
                });
            } catch (Throwable ignored) { }
        }
        System.exit(exit);
    }

    private static void assertGuidedFocusWindow(final AeTunerPlugin plugin,
                                                final JComponent panel,
                                                final JFrame frame,
                                                final File out) throws Exception {
        GuidedFocusHub.publishMapEstimateSetup(
                MapEstimateFocusSnapshot.setup(focusSnapshot(), 20, null),
                "Synthetic MAP Estimate focus target");

        final AtomicReference<JButton> buttonRef = new AtomicReference<JButton>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                buttonRef.set(findButton(panel, "Guided Focus"));
            }
        });
        final JButton focusButton = buttonRef.get();
        require(focusButton != null, "Guided Focus button is not reachable on Guided Tuning");

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { focusButton.doClick(); }
        });
        GuidedFocusWindow focus = (GuidedFocusWindow) field(plugin, "guidedFocusWindow");
        require(focus != null && focus.isDisplayable() && focus.isVisible(),
                "Guided Focus did not open a visible modeless window");
        require(focus.getModalityType() == Dialog.ModalityType.MODELESS,
                "Guided Focus unexpectedly blocks the TunerStudio workspace");
        require(frame.isVisible(), "main workspace disappeared when Guided Focus opened");

        JTable heatMap = findTable(focus);
        require(heatMap != null, "Guided Focus did not expose the MAP Estimate heat map");
        require(heatMap.getRowCount() == 2 && heatMap.getColumnCount() == 3,
                "Guided Focus heat map did not reflect the synthetic 2x2 MAP Estimate axes");
        render(focus.getRootPane(), new File(out, "workspace-guided-focus-map-estimate.png"));

        final GuidedFocusWindow first = focus;
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                first.dispatchEvent(new WindowEvent(first, WindowEvent.WINDOW_CLOSING));
            }
        });
        require(!first.isVisible() && first.isDisplayable(),
                "closing Guided Focus must hide, not dispose, the retained-session view");
        require(frame.isVisible(), "closing Guided Focus affected the main workspace");

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { focusButton.doClick(); }
        });
        GuidedFocusWindow reopened = (GuidedFocusWindow) field(plugin, "guidedFocusWindow");
        require(reopened == first && reopened.isVisible(),
                "Guided Focus did not reuse the hidden modeless window on reopen");
        JTable reopenedHeatMap = findTable(reopened);
        require(reopenedHeatMap != null
                        && reopenedHeatMap.getRowCount() == 2
                        && reopenedHeatMap.getColumnCount() == 3,
                "Guided Focus lost retained heat-map state after hide/reopen");
    }

    private static AeProjectSnapshot focusSnapshot() {
        return new AeProjectSnapshot(
                "synthetic-focus",
                new double[]{1.0}, new double[]{20.0}, new double[][]{{0.0}},
                new double[]{1000.0}, new double[]{1.0},
                0.0, 0.0, new double[0], new double[0],
                false, false, "none", false, true, false, false,
                new double[0][0], new double[0][0],
                new double[]{1500.0, 3000.0},
                new double[]{10.0, 20.0},
                new double[][]{{45.0, 50.0}, {55.0, 58.0}},
                new double[]{1500.0, 3000.0}, new double[]{0.10, 0.20});
    }

    private static void assertGuidedActionReachability(JFrame frame,
                                                        GuidedCapturePanel guided,
                                                        int width) throws Exception {
        resize(frame, width, 768);
        final AtomicReference<JButton> restoreRef = new AtomicReference<JButton>();
        final AtomicReference<JButton> reconnectRef = new AtomicReference<JButton>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                restoreRef.set(findButton(guided, "Restore Previous Apply"));
                reconnectRef.set(findButton(guided, "Reconnect"));
            }
        });
        JButton restore = restoreRef.get();
        JButton reconnect = reconnectRef.get();
        require(restore != null && reconnect != null,
                "Guided action buttons are missing at " + width + " px");
        assertInsideWidth(guided, restore, "Restore Previous Apply", width);
        assertInsideWidth(guided, reconnect, "Reconnect", width);
        require(restore.getY() >= 0 && reconnect.getY() >= 0,
                "Guided action row produced invalid vertical geometry at " + width + " px");
        System.out.println("GUIDED_ACTION_WIDTH " + width + " passed");
    }

    private static void assertInsideWidth(final JComponent root, final JButton button,
                                          final String name, final int width)
            throws Exception {
        final AtomicReference<Rectangle> boundsRef = new AtomicReference<Rectangle>();
        final int[] rootWidth = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                boundsRef.set(SwingUtilities.convertRectangle(
                        button.getParent(), button.getBounds(), root));
                rootWidth[0] = root.getWidth();
            }
        });
        Rectangle bounds = boundsRef.get();
        require(bounds.x >= 0 && bounds.x + bounds.width <= rootWidth[0],
                name + " is horizontally clipped at " + width + " px: "
                        + bounds + " rootWidth=" + rootWidth[0]);
    }

    private static JFrame show(final JComponent panel, final int width,
                               final int height) throws Exception {
        final AtomicReference<JFrame> result = new AtomicReference<JFrame>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("AE Tuner synthetic workspace");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setContentPane(panel);
                frame.setSize(new Dimension(width, height));
                frame.setLocation(0, 0);
                frame.setVisible(true);
                frame.validate();
                result.set(frame);
            }
        });
        return result.get();
    }

    private static void selectTab(final JTabbedPane tabs, final int index,
                                  final JFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                tabs.setSelectedIndex(index);
                tabs.doLayout();
                frame.validate();
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() { frame.validate(); }
        });
    }

    private static void resize(final JFrame frame, final int width,
                               final int height) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame.setSize(new Dimension(width, height));
                frame.validate();
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() { frame.validate(); }
        });
    }

    private static void render(final JComponent panel, final File file)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    int width = Math.max(1, panel.getWidth());
                    int height = Math.max(1, panel.getHeight());
                    BufferedImage image = new BufferedImage(
                            width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        panel.printAll(graphics);
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

    private static JTabbedPane findTabsWithTitle(Component root, String title) {
        if (root instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) root;
            if (indexOf(tabs, title) >= 0) return tabs;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JTabbedPane found = findTabsWithTitle(child, title);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JTable findTable(Component root) {
        if (root instanceof JTable) return (JTable) root;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JTable found = findTable(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton findButton(Component root, String text) {
        if (root instanceof JButton && text.equals(((JButton) root).getText())) {
            return (JButton) root;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void requireTitles(JTabbedPane tabs, String... expected) {
        require(tabs.getTabCount() == expected.length,
                "unexpected tab count: " + tabs.getTabCount());
        for (int i = 0; i < expected.length; i++) {
            require(expected[i].equals(tabs.getTitleAt(i)),
                    "tab " + i + " expected '" + expected[i]
                            + "' but was '" + tabs.getTitleAt(i) + "'");
        }
    }

    private static int indexOf(JTabbedPane tabs, String title) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (title.equals(tabs.getTitleAt(i))) return i;
        }
        return -1;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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
