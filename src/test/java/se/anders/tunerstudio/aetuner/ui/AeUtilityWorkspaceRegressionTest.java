package se.anders.tunerstudio.aetuner.ui;

import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueController;
import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueLabPanel;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;

/** Permanent presentation contract for the retained Guided diagnostics utility shell. */
public final class AeUtilityWorkspaceRegressionTest {
    private AeUtilityWorkspaceRegressionTest() { }

    public static void main(String[] args) throws Exception {
        diagnosticsUsesFocusedSectionNavigation();
        sharedShellTracksLightAndDarkSide();
        System.out.println("AeUtilityWorkspaceRegressionTest passed");
    }

    private static void diagnosticsUsesFocusedSectionNavigation() throws Exception {
        GuidedAudioCueController controller = new GuidedAudioCueController();
        GuidedAudioCueLabPanel lab = new GuidedAudioCueLabPanel(controller);
        EvidenceDiagnosticsPanel panel = new EvidenceDiagnosticsPanel(
                lab, () -> "health", () -> "runtime", () -> "recovery");
        try {
            require(panel.tabCountForTest() == 4,
                    "Evidence / Diagnostics must expose four focused sections");
            String[] expected = {"Overview", "Channels / Runtime",
                    "Audio Cue Lab", "Recovery / Audit"};
            for (int i = 0; i < expected.length; i++) {
                require(expected[i].equals(panel.tabTitleForTest(i)),
                        "Diagnostics section order/title drift at " + i);
            }
            require("overview".equals(panel.selectedSectionForTest()),
                    "Diagnostics must open on Overview");
            panel.selectAudioCueLab();
            require("audio".equals(panel.selectedSectionForTest()),
                    "Audio Cue Lab action did not route to the Audio section");
            require(!containsTabbedPane(panel),
                    "Diagnostics v0.19 utility surface still renders legacy tab chrome");
        } finally {
            panel.disposePanel();
            controller.close();
        }
    }

    private static void sharedShellTracksLightAndDarkSide() {
        AeUiTheme.Side original = AeUiTheme.side();
        try {
            AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);
            AeUtilityWorkspacePanel shell = new AeUtilityWorkspacePanel("Utility", "Theme test");
            shell.addSection("one", "One", "First", new JPanel());
            shell.addSection("two", "Two", "Second", new JPanel());
            shell.applyTheme();
            Color light = shell.getBackground();

            AeUiTheme.set(AeUiTheme.Side.DARK_SIDE);
            shell.applyTheme();
            Color dark = shell.getBackground();
            require(!light.equals(dark),
                    "utility workspace did not follow Light Side / Dark Side palette");
            require("one".equals(shell.selectedSectionId()),
                    "theme switch changed utility section selection");
        } finally {
            AeUiTheme.set(original);
        }
    }

    private static boolean containsTabbedPane(Component component) {
        if (component instanceof JTabbedPane) return true;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (containsTabbedPane(child)) return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
