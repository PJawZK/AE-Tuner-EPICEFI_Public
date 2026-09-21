package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

/** Permanent contract for the Guided-only v0.19 production host. */
public final class GuidedProductionUiIntegrationRegressionTest {
    private static final String RETIRED_FEATURE_PROPERTY = "ae.tuner.guided.ui.v019";

    private GuidedProductionUiIntegrationRegressionTest() { }

    public static void main(String[] args) throws Exception {
        catalogAuthorityIsExact();
        availabilityFailsClosedFromWorkingTune();
        structuralShellIsGuidedOnly();
        v019HostIsPermanent();
        themeContractRemainsDistinct();
        System.out.println("GuidedProductionUiIntegrationRegressionTest passed");
    }

    private static void catalogAuthorityIsExact() {
        GuidedProductionTask[] tasks = GuidedProductionTask.values();
        require(tasks.length == 19, "v0.19 sidebar must contain the full 19-task production baseline");
        EnumSet<GuidedProductionTask> bound = EnumSet.of(
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
                GuidedProductionTask.INSTANT_VALIDATION);
        int actualBound = 0;
        for (GuidedProductionTask task : tasks) {
            require(task.firstSliceBound == bound.contains(task),
                    "production binding drift for " + task.displayName);
            if (task.firstSliceBound) actualBound++;
        }
        require(actualBound == 16, "expected sixteen production-bound v0.19 tasks");
        require(GuidedProductionTask.INSTANT_PULSE.productionRecipe
                        == GuidedTuningRecipe.INSTANT_FUEL_SETUP,
                "Global Pulse / Inhibit lost Instant setup ownership");
        require(GuidedProductionTask.DECEL_DETECTION.productionRecipe
                        == GuidedTuningRecipe.DECEL_DETECTION,
                "Decel Detection lost falling-TPS evidence ownership");
        require(GuidedProductionTask.IGNITION_RETARD.productionRecipe == null,
                "UI port invented production authority for Ignition Retard");
    }

    private static void availabilityFailsClosedFromWorkingTune() {
        requireStatus(GuidedProductionTask.TPS_MOVEMENT_TIMING, null, "READ TUNE");
        AeProjectSnapshot enabled = snapshot(true, true, true, true);
        requireAvailable(GuidedProductionTask.TPS_MOVEMENT_TIMING, enabled);
        requireAvailable(GuidedProductionTask.THRESHOLD_SENSITIVITY, enabled);
        requireAvailable(GuidedProductionTask.DECEL_DETECTION, enabled);
        requireAvailable(GuidedProductionTask.MAP_ESTIMATE, enabled);
        requireAvailable(GuidedProductionTask.BLEND_DURATION, enabled);
        requireAvailable(GuidedProductionTask.WALL_FILM, enabled);
        requireAvailable(GuidedProductionTask.INSTANT_PULSE, enabled);
        requireStatus(GuidedProductionTask.DECEL_FUEL_RESPONSE, enabled, "PLANNED");
        requireStatus(GuidedProductionTask.DECEL_MAP_PREDICTION, enabled, "PLANNED");
        requireStatus(GuidedProductionTask.IGNITION_RETARD, enabled, "STATE UNRESOLVED");

        AeProjectSnapshot disabled = snapshot(false, false, false, false);
        requireAvailable(GuidedProductionTask.TPS_MOVEMENT_TIMING, disabled);
        requireStatus(GuidedProductionTask.MAP_ESTIMATE, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.BLEND_DURATION, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.WALL_FILM, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.INSTANT_PULSE, disabled, "OFF IN TUNE");
    }

    private static void structuralShellIsGuidedOnly() throws Exception {
        GuidedFocusHub.clear();
        final GuidedCapturePanel production = new GuidedCapturePanel();
        final GuidedProductionWorkspacePanel[] holder = new GuidedProductionWorkspacePanel[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                holder[0] = new GuidedProductionWorkspacePanel(production);
            }
        });
        GuidedProductionWorkspacePanel workspace = holder[0];
        try {
            require("Prepare".equals(workspace.visibleStageForTest()),
                    "v0.19 workflow must open on Prepare");
            require(workspace.stageCardCountForTest() == 6
                            && workspace.mainUsesTwoByThreeCardsForTest(),
                    "main workflow must retain the six-card v0.19 structure");
            require(!containsText(workspace, "Passive Analysis…"),
                    "retired Passive Analysis label leaked into production component tree");
            require(workspace.exportSessionButtonForTest() != null
                            && workspace.diagnosticsButtonForTest() != null,
                    "Guided export or diagnostics action is missing");
            require("Export Current Session…".equals(
                            workspace.exportSessionButtonForTest().getText()),
                    "current-session export identity drifted");

            final AeProjectSnapshot tune = snapshot(true, true, true, true);
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    production.setProjectSnapshotForTest(tune);
                    workspace.setCurrentTune(tune);
                }
            });
            require(workspace.taskCountForTest() == 19,
                    "production task count changed");
            require(countAvailable(workspace) == 16,
                    "expected sixteen available production bindings");
            require(!production.applyCurrentProposalEnabledForTest(),
                    "presentation construction manufactured Apply authority");

            select(workspace, GuidedProductionTask.THRESHOLD_SENSITIVITY);
            require(production.selectedTuningTaskForTest().contains("Threshold / Sensitivity"),
                    "Threshold did not delegate to production selector");
            JComponentText prepareInfo = new JComponentText(workspace.prepareInfoViewForTest());
            JComponentText captureInfo = new JComponentText(workspace.captureInfoViewForTest());
            require(prepareInfo.contains("acceleration threshold")
                            && prepareInfo.contains("ordinary/noisy pedal movement"),
                    "Threshold Prepare Info lost task-specific content");
            require(captureInfo.contains("small intentional openings")
                            && captureInfo.contains("Capture writes: NONE"),
                    "Threshold Capture Info lost read-only guidance");

            select(workspace, GuidedProductionTask.BLEND_DURATION);
            require(production.selectedTuningTaskForTest().contains("Blend Duration"),
                    "Blend Duration did not delegate to production");
            require(!production.applyCurrentProposalEnabledForTest(),
                    "Blend selection manufactured an Apply proposal");
        } finally {
            workspace.disposeOwnedDialogs();
            production.terminateForClose();
            production.releaseAfterClose();
            GuidedFocusHub.clear();
        }
    }

    private static void v019HostIsPermanent() throws Exception {
        String originalFeature = System.getProperty(RETIRED_FEATURE_PROPERTY);
        String originalRecovery = System.getProperty("ae.tuner.recovery.dir");
        Path recovery = Files.createTempDirectory("ae-tuner-guided-only-host");
        System.setProperty("ae.tuner.recovery.dir", recovery.toString());
        try {
            System.setProperty(RETIRED_FEATURE_PROPERTY, "false");
            require(GuidedProductionWorkspacePanel.featureEnabled(),
                    "retired feature flag still disables the production v0.19 host");
            require(!declaresField(AeTunerPlugin.class, "rootTabs"),
                    "legacy four-tab host field returned");
            require(!declaresField(AeTunerPlugin.class, "passiveAnalysisWindow"),
                    "Passive Analysis pop-out field returned");

            AeTunerPlugin plugin = new AeTunerPlugin();
            Object wrapper = field(plugin, "guidedProductionWorkspace");
            Component panel = (Component)field(plugin, "panel");
            require(wrapper instanceof GuidedProductionWorkspacePanel
                            && ((GuidedProductionWorkspacePanel)wrapper).getParent() == panel,
                    "plugin does not directly host the permanent v0.19 workspace");
            require(!containsText((Component)wrapper, "Passive Analysis…"),
                    "plugin production host exposes retired Passive UI");
            plugin.close();
        } finally {
            if (originalFeature == null) System.clearProperty(RETIRED_FEATURE_PROPERTY);
            else System.setProperty(RETIRED_FEATURE_PROPERTY, originalFeature);
            if (originalRecovery == null) System.clearProperty("ae.tuner.recovery.dir");
            else System.setProperty("ae.tuner.recovery.dir", originalRecovery);
        }
    }

    private static void themeContractRemainsDistinct() {
        AeUiTheme.Side original = AeUiTheme.side();
        try {
            AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);
            require(!AeUiTheme.taskAvailableBg().equals(AeUiTheme.taskDisabledBg()),
                    "Light Side availability hierarchy collapsed");
            AeUiTheme.set(AeUiTheme.Side.DARK_SIDE);
            require(!AeUiTheme.taskSelectedBg().equals(AeUiTheme.taskAvailableBg())
                            && !AeUiTheme.taskAvailableBg().equals(AeUiTheme.taskDisabledBg()),
                    "Dark Side hierarchy collapsed");
        } finally {
            AeUiTheme.set(original);
        }
    }

    private static int countAvailable(GuidedProductionWorkspacePanel workspace) {
        int count = 0;
        for (GuidedProductionTask task : GuidedProductionTask.values()) {
            if (workspace.taskAvailableForTest(task)) count++;
        }
        return count;
    }

    private static void select(final GuidedProductionWorkspacePanel workspace,
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
        if (root instanceof javax.swing.AbstractButton
                && ((javax.swing.AbstractButton)root).getText() != null
                && ((javax.swing.AbstractButton)root).getText().contains(expected)) return true;
        if (root instanceof JTextComponent && ((JTextComponent)root).getText() != null
                && ((JTextComponent)root).getText().contains(expected)) return true;
        if (root instanceof Container) {
            for (Component child : ((Container)root).getComponents()) {
                if (containsText(child, expected)) return true;
            }
        }
        return false;
    }

    private static final class JComponentText {
        private final Component root;
        JComponentText(Component root) { this.root = root; }
        boolean contains(String expected) { return containsText(root, expected); }
    }

    private static AeProjectSnapshot snapshot(boolean tpsAe, boolean wallWetting,
                                              boolean instantFuel, boolean mapEstimate) {
        return new AeProjectSnapshot(
                "guided-v019-structural",
                new double[]{1.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.0},
                0.0, 0.0, new double[0], new double[0],
                tpsAe, wallWetting, wallWetting ? "basic" : "none",
                instantFuel, mapEstimate, false, false,
                new double[0][0], new double[0][0],
                mapEstimate ? new double[]{1500.0} : new double[0],
                mapEstimate ? new double[]{20.0} : new double[0],
                mapEstimate ? new double[][]{{55.0}} : new double[0][0],
                mapEstimate ? new double[]{1500.0} : new double[0],
                mapEstimate ? new double[]{0.20} : new double[0],
                "Dual stride, newest", 30.0, 0.05,
                false, true, 0.35,
                new double[]{1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 4500.0},
                new double[]{0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00},
                12.0);
    }

    private static void requireAvailable(GuidedProductionTask task, AeProjectSnapshot snapshot) {
        GuidedTaskAvailability state = GuidedTaskAvailabilityAdapter.evaluate(task, snapshot);
        require(state.clickable && "AVAILABLE".equals(state.status),
                task.displayName + " should be AVAILABLE but was " + state.status);
    }

    private static void requireStatus(GuidedProductionTask task, AeProjectSnapshot snapshot,
                                      String expected) {
        GuidedTaskAvailability state = GuidedTaskAvailabilityAdapter.evaluate(task, snapshot);
        require(!state.clickable && expected.equals(state.status),
                task.displayName + " expected " + expected + " but was " + state.status);
        require(state.reason.length() > 0, task.displayName + " status reason is empty");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
