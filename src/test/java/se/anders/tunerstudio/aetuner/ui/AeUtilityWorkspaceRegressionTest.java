package se.anders.tunerstudio.aetuner.ui;

import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueController;
import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueLabPanel;
import se.anders.tunerstudio.aetuner.passive.AeTunerPanel;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Method;

/** Permanent presentation contract for the secondary v0.19 utility workspaces. */
public final class AeUtilityWorkspaceRegressionTest {
    private AeUtilityWorkspaceRegressionTest() { }

    public static void main(String[] args) throws Exception {
        passiveUsesFocusedSectionNavigation();
        diagnosticsUsesFocusedSectionNavigation();
        sharedShellTracksLightAndDarkSide();
        System.out.println("AeUtilityWorkspaceRegressionTest passed");
    }

    private static void passiveUsesFocusedSectionNavigation() throws Exception {
        final AeTunerPanel[] holder = new AeTunerPanel[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { holder[0] = new AeTunerPanel(); }
        });
        AeTunerPanel panel = holder[0];
        try {
            require(intMethod(panel, "passiveSectionCountForTest") == 5,
                    "Passive Analysis must expose five focused v0.19 sections");
            String[] expected = {"Overview", "Event Preview", "Session Notes",
                    "Session Guidance", "Setup / Calibration"};
            for (int i = 0; i < expected.length; i++) {
                require(expected[i].equals(stringMethod(panel,
                                "passiveSectionTitleForTest", new Class<?>[]{int.class}, i)),
                        "Passive section order/title drift at " + i);
            }
            require("overview".equals(stringMethod(panel, "passiveSelectedSectionForTest")),
                    "Passive Analysis must open on Overview");
            voidMethod(panel, "showPassiveNotesForTest");
            require("notes".equals(stringMethod(panel, "passiveSelectedSectionForTest")),
                    "Passive notes action did not route to Session Notes");
            voidMethod(panel, "showPassiveGuidanceForTest");
            require("guidance".equals(stringMethod(panel, "passiveSelectedSectionForTest")),
                    "Passive next-step action did not route to Session Guidance");
            require(!containsTabbedPane(panel),
                    "Passive v0.19 utility surface still renders nested legacy tab chrome");
        } finally {
            panel.disposePanel();
        }
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

    private static void sharedShellTracksLightAndDarkSide() throws Exception {
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

    private static int intMethod(Object owner, String name) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return ((Integer) method.invoke(owner)).intValue();
    }

    private static String stringMethod(Object owner, String name) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (String) method.invoke(owner);
    }

    private static String stringMethod(Object owner, String name,
                                       Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return (String) method.invoke(owner, args);
    }

    private static void voidMethod(Object owner, String name) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(owner);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
