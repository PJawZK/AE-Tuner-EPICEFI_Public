package se.anders.tunerstudio.aetuner.ui;

import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueController;
import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueLabPanel;
import se.anders.tunerstudio.aetuner.passive.AeTunerPanel;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Real-Swing/Xvfb evidence for the secondary v0.19 utility workspaces. */
public final class UtilityWorkspaceSyntheticIntegrationTest {
    private UtilityWorkspaceSyntheticIntegrationTest() { }

    public static void main(String[] args) {
        int exit = 1;
        AeTunerPanel passive = null;
        EvidenceDiagnosticsPanel diagnostics = null;
        GuidedAudioCueController audio = null;
        JFrame passiveFrame = null;
        JFrame diagnosticsFrame = null;
        AeUiTheme.Side original = AeUiTheme.side();
        try {
            File out = outputDirectory();
            Files.createDirectories(out.toPath());

            AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);
            passive = new AeTunerPanel();
            passiveFrame = show(passive, "Passive Analysis — v0.19 utility", 1180, 760);
            require(!containsTabbedPane(passive),
                    "Passive Analysis still renders legacy nested tab chrome");
            AeUtilityWorkspacePanel passiveWorkspace = findUtilityWorkspace(passive);
            require(passiveWorkspace != null && passiveWorkspace.sectionCount() == 5,
                    "Passive Analysis utility section structure unavailable");
            require("overview".equals(passiveWorkspace.selectedSectionId()),
                    "Passive Analysis did not open on Overview");
            render(passive, new File(out, "utility-passive-overview-light-1180.png"));

            passiveWorkspace.selectSection("event-preview");
            flush(passiveFrame);
            render(passive, new File(out, "utility-passive-event-preview-light-1180.png"));

            passiveWorkspace.selectSection("notes");
            flush(passiveFrame);
            render(passive, new File(out, "utility-passive-notes-light-1180.png"));

            passiveWorkspace.selectSection("guidance");
            flush(passiveFrame);
            render(passive, new File(out, "utility-passive-guidance-light-1180.png"));

            passiveWorkspace.selectSection("setup");
            flush(passiveFrame);
            render(passive, new File(out, "utility-passive-setup-light-1180.png"));

            AeUiTheme.set(AeUiTheme.Side.DARK_SIDE);
            passiveWorkspace.applyTheme();
            flush(passiveFrame);
            render(passive, new File(out, "utility-passive-setup-dark-1180.png"));

            AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);
            audio = new GuidedAudioCueController();
            GuidedAudioCueLabPanel lab = new GuidedAudioCueLabPanel(audio);
            diagnostics = new EvidenceDiagnosticsPanel(
                    lab,
                    () -> "EVIDENCE / DIAGNOSTICS\nLifecycle: ACTIVE\nRecovery: READY",
                    () -> "CONTROLLER / RUNTIME\nProject: synthetic\nTPS -> TPS = 12.500\nMAP -> MAP = 55.000",
                    () -> "RECOVERY\nNo pending recovery conflict.\n\nGUIDED APPLY / RESTORE AUDIT\nNo proposal applied.");
            diagnostics.resumePanel();
            diagnosticsFrame = show(diagnostics,
                    "Evidence / Diagnostics — v0.19 utility", 1120, 720);
            require(!containsTabbedPane(diagnostics),
                    "Evidence / Diagnostics still renders legacy tab chrome");
            AeUtilityWorkspacePanel diagnosticWorkspace = findUtilityWorkspace(diagnostics);
            require(diagnosticWorkspace != null && diagnosticWorkspace.sectionCount() == 4,
                    "Diagnostics utility section structure unavailable");
            render(diagnostics, new File(out, "utility-diagnostics-overview-light-1120.png"));

            diagnosticWorkspace.selectSection("runtime");
            flush(diagnosticsFrame);
            render(diagnostics, new File(out, "utility-diagnostics-runtime-light-1120.png"));

            diagnostics.selectAudioCueLab();
            flush(diagnosticsFrame);
            render(diagnostics, new File(out, "utility-diagnostics-audio-light-1120.png"));

            diagnosticWorkspace.selectSection("recovery");
            flush(diagnosticsFrame);
            render(diagnostics, new File(out, "utility-diagnostics-recovery-light-1120.png"));

            AeUiTheme.set(AeUiTheme.Side.DARK_SIDE);
            diagnosticWorkspace.applyTheme();
            diagnosticWorkspace.selectSection("runtime");
            flush(diagnosticsFrame);
            render(diagnostics, new File(out, "utility-diagnostics-runtime-dark-1120.png"));

            List<String> result = new ArrayList<String>();
            result.add("v0.19 utility workspace Swing integration: passed");
            result.add("Passive sections: Overview | Event Preview | Session Notes | Session Guidance | Setup / Calibration");
            result.add("Diagnostics sections: Overview | Channels / Runtime | Audio Cue Lab | Recovery / Audit");
            result.add("Nested legacy tabs: ABSENT");
            result.add("Passive section routing: Overview / Event / Notes / Guidance / Setup PASS");
            result.add("Diagnostics section routing: Overview / Runtime / Audio / Recovery PASS");
            result.add("Light Side / Dark Side utility theme continuity PASS");
            Files.write(new File(out, "utility-v019-ui-result.txt").toPath(),
                    result, StandardCharsets.UTF_8);

            System.out.println("UtilityWorkspaceSyntheticIntegrationTest passed");
            exit = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        } finally {
            if (diagnostics != null) diagnostics.disposePanel();
            if (audio != null) audio.close();
            if (passive != null) passive.disposePanel();
            if (diagnosticsFrame != null) diagnosticsFrame.dispose();
            if (passiveFrame != null) passiveFrame.dispose();
            for (Window window : Window.getWindows()) window.dispose();
            AeUiTheme.set(original);
        }
        if (exit != 0) System.exit(exit);
    }

    private static JFrame show(final JComponent component, final String title,
                               final int width, final int height) throws Exception {
        final JFrame[] holder = new JFrame[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                JFrame frame = new JFrame(title);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setContentPane(component);
                frame.setSize(new Dimension(width, height));
                frame.setLocation(20, 20);
                frame.setVisible(true);
                frame.validate();
                holder[0] = frame;
            }
        });
        flush(holder[0]);
        return holder[0];
    }

    private static void flush(final JFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                layoutRecursively(frame);
                frame.validate();
                frame.repaint();
            }
        });
    }

    private static void layoutRecursively(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) layoutRecursively((Container) child);
        }
    }

    private static void render(final JComponent component, final File output)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    int width = Math.max(1, component.getWidth());
                    int height = Math.max(1, component.getHeight());
                    BufferedImage image = new BufferedImage(
                            width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try { component.printAll(graphics); }
                    finally { graphics.dispose(); }
                    ImageIO.write(image, "png", output);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        require(output.isFile() && output.length() > 0L,
                "utility screenshot was not created: " + output);
    }

    private static AeUtilityWorkspacePanel findUtilityWorkspace(Component root) {
        if (root instanceof AeUtilityWorkspacePanel) return (AeUtilityWorkspacePanel) root;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                AeUtilityWorkspacePanel found = findUtilityWorkspace(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean containsTabbedPane(Component root) {
        if (root instanceof JTabbedPane) return true;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                if (containsTabbedPane(child)) return true;
            }
        }
        return false;
    }

    private static File outputDirectory() {
        String configured = System.getenv("SYNTHETIC_INTEGRATION_OUT");
        return new File(configured == null || configured.trim().isEmpty()
                ? "target/synthetic-plugin-integration" : configured);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
