package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

/** Permanent contract for the structural v0.19 prototype-to-production port. */
public final class GuidedProductionUiIntegrationRegressionTest {
    private static final String FEATURE_PROPERTY = "ae.tuner.guided.ui.v019";

    private GuidedProductionUiIntegrationRegressionTest() { }

    public static void main(String[] args) throws Exception {
        catalogAuthorityIsExact();
        availabilityFailsClosedFromWorkingTune();
        structuralShellMatchesPrototypeAuthority();
        defaultOnWithExplicitLegacyFallback();
        themeContractRemainsDistinct();
        System.out.println("GuidedProductionUiIntegrationRegressionTest passed");
    }

    private static void catalogAuthorityIsExact() {
        GuidedProductionTask[] tasks = GuidedProductionTask.values();
        String[] names = new String[]{
                "TPS Movement / Timing", "Threshold / Sensitivity", "Decel Detection",
                "Fuel Response / Decay", "Scaling / Compensation", "Completion / Handoff",
                "TPS AE Validation", "Decel Fuel Response",
                "MAP Estimate", "Blend Duration", "Decel MAP Prediction",
                "Wall Film / Tau & Beta", "Advanced Wall Model", "Film Validation",
                "Global Pulse / Inhibit", "Event Strength", "Operating Conditions",
                "Residual Validation", "Ignition Retard"
        };
        GuidedProductionTask.Group[] groups = new GuidedProductionTask.Group[]{
                GuidedProductionTask.Group.AE_FOUNDATION,
                GuidedProductionTask.Group.AE_FOUNDATION,
                GuidedProductionTask.Group.AE_FOUNDATION,
                GuidedProductionTask.Group.TPS_AE,
                GuidedProductionTask.Group.TPS_AE,
                GuidedProductionTask.Group.TPS_AE,
                GuidedProductionTask.Group.TPS_AE,
                GuidedProductionTask.Group.TPS_AE,
                GuidedProductionTask.Group.MAP_PREDICT,
                GuidedProductionTask.Group.MAP_PREDICT,
                GuidedProductionTask.Group.MAP_PREDICT,
                GuidedProductionTask.Group.WALL_WETTING,
                GuidedProductionTask.Group.WALL_WETTING,
                GuidedProductionTask.Group.WALL_WETTING,
                GuidedProductionTask.Group.INSTANT_FUEL,
                GuidedProductionTask.Group.INSTANT_FUEL,
                GuidedProductionTask.Group.INSTANT_FUEL,
                GuidedProductionTask.Group.INSTANT_FUEL,
                GuidedProductionTask.Group.OPTIONAL_TRANSIENT
        };
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
        require(tasks.length == 19, "v0.19 sidebar must contain the full 19-task production baseline");
        for (int i = 0; i < tasks.length; i++) {
            require(names[i].equals(tasks[i].displayName), "task name/order drift at " + i);
            require(groups[i] == tasks[i].group, "task ownership drift for " + tasks[i].displayName);
            require(tasks[i].firstSliceBound == bound.contains(tasks[i]),
                    "production binding drift for " + tasks[i].displayName);
        }
        String[] groupNames = new String[]{
                "AE Foundation", "TPS AE", "MAP Predict", "Wall Wetting",
                "Instant Fuel", "Optional Transient"
        };
        GuidedProductionTask.Group[] actual = GuidedProductionTask.Group.values();
        require(actual.length == groupNames.length, "sidebar group count changed");
        for (int i = 0; i < actual.length; i++) {
            require(groupNames[i].equals(actual[i].displayName), "group name/order drift at " + i);
            require(!"Decel / Tip-out".equals(actual[i].displayName),
                    "retired generic Decel / Tip-out group returned");
        }
        require(GuidedProductionTask.INSTANT_PULSE.productionRecipe == GuidedTuningRecipe.INSTANT_FUEL_SETUP,
                "Global Pulse / Inhibit lost complete Instant setup ownership");
        require(GuidedProductionTask.INSTANT_VALIDATION.productionRecipe == GuidedTuningRecipe.INSTANT_FUEL,
                "Residual Validation lost the production residual evidence route");
        require(GuidedProductionTask.DECEL_DETECTION.productionRecipe == GuidedTuningRecipe.DECEL_DETECTION,
                "Decel Detection lost the production falling-TPS evidence route");
        require(GuidedProductionTask.IGNITION_RETARD.productionRecipe == null,
                "UI port invented production authority for Ignition Retard");
    }

    private static void availabilityFailsClosedFromWorkingTune() {
        requireStatus(GuidedProductionTask.TPS_MOVEMENT_TIMING, null, "READ TUNE");
        AeProjectSnapshot enabled = snapshot(true, true, true, true);
        for (GuidedProductionTask task : new GuidedProductionTask[]{
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
                GuidedProductionTask.INSTANT_VALIDATION}) {
            requireAvailable(task, enabled);
        }
        requireStatus(GuidedProductionTask.DECEL_DETECTION,
                snapshotWithoutDecel(true, true, true, true), "SETTING UNAVAILABLE");
        requireStatus(GuidedProductionTask.DECEL_FUEL_RESPONSE, enabled, "PLANNED");
        requireStatus(GuidedProductionTask.DECEL_MAP_PREDICTION, enabled, "PLANNED");
        requireStatus(GuidedProductionTask.IGNITION_RETARD, enabled, "STATE UNRESOLVED");

        AeProjectSnapshot disabled = snapshot(false, false, false, false);
        requireAvailable(GuidedProductionTask.TPS_MOVEMENT_TIMING, disabled);
        requireAvailable(GuidedProductionTask.THRESHOLD_SENSITIVITY, disabled);
        requireAvailable(GuidedProductionTask.DECEL_DETECTION, disabled);
        requireStatus(GuidedProductionTask.TPS_FUEL_RESPONSE, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.TPS_SCALING, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.TPS_COMPLETION, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.TPS_VALIDATION, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.MAP_ESTIMATE, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.BLEND_DURATION, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.WALL_FILM, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.WALL_ADVANCED, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.WALL_VALIDATION, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.INSTANT_PULSE, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.INSTANT_EVENT_STRENGTH, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.INSTANT_CONDITIONS, disabled, "OFF IN TUNE");
        requireStatus(GuidedProductionTask.INSTANT_VALIDATION, disabled, "OFF IN TUNE");
    }

    private static void structuralShellMatchesPrototypeAuthority() throws Exception {
        GuidedFocusHub.clear();
        final GuidedCapturePanel production = new GuidedCapturePanel();
        final JPanel legacy = new JPanel();
        final GuidedProductionWorkspacePanel[] holder = new GuidedProductionWorkspacePanel[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                holder[0] = new GuidedProductionWorkspacePanel(legacy, production);
            }
        });
        final GuidedProductionWorkspacePanel workspace = holder[0];
        try {
            require("Prepare".equals(workspace.visibleStageForTest()),
                    "prototype workflow must open on Prepare");
            assertSixStages();
            require(workspace.stageCardCountForTest() == 6
                            && workspace.mainUsesTwoByThreeCardsForTest(),
                    "main workflow must retain the v0.19 six-card 2x3 structure");
            require(!workspace.legacySurfaceRenderedForTest()
                            && !isDescendant(workspace, legacy),
                    "normal v0.19 path rendered the legacy Guided surface");
            require(workspace.passiveAnalysisButtonForTest() != null
                            && workspace.diagnosticsButtonForTest() != null,
                    "v0.19 sidebar lost pop-out utility actions");

            final AeProjectSnapshot tune = snapshot(true, true, true, true);
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    production.setProjectSnapshotForTest(tune);
                    workspace.setCurrentTune(tune);
                }
            });
            require("Prepare".equals(workspace.visibleStageForTest()),
                    "reading Working Tune must not skip the prototype Prepare step");
            require(countBound(workspace) == 16,
                    "expected sixteen active production recipe bindings");
            require(workspace.taskCountForTest() == 19,
                    "secondary validation must not alter the 19-task production sidebar");
            require(!production.applyCurrentProposalEnabledForTest(),
                    "presentation construction/selection invented Apply authority");

            select(workspace, GuidedProductionTask.THRESHOLD_SENSITIVITY);
            require(production.selectedTuningTaskForTest().contains("Threshold / Sensitivity"),
                    "Threshold did not delegate to the existing production selector");
            require("Prepare".equals(workspace.visibleStageForTest()),
                    "task selection must restart the prototype workflow at Prepare");

            JComponentText prepareInfo = new JComponentText(workspace.prepareInfoViewForTest());
            JComponentText captureInfo = new JComponentText(workspace.captureInfoViewForTest());
            require(prepareInfo.contains("acceleration threshold")
                            && prepareInfo.contains("ordinary/noisy pedal movement"),
                    "Threshold Prepare Info is still generic instead of task-specific");
            require(captureInfo.contains("small intentional openings")
                            && captureInfo.contains("Capture writes: NONE"),
                    "Threshold Capture Info is still generic instead of task-specific");

            require("Continue".equals(workspace.prepareButtonForTest().getText())
                            && workspace.prepareButtonForTest().isEnabled(),
                    "loaded Threshold Prepare did not expose a usable Continue action");
            click(workspace.prepareButtonForTest());
            require("Capture".equals(workspace.visibleStageForTest()),
                    "Continue button did not advance visible workflow to Capture");
            require(workspace.captureButtonForTest().getText().length() > 0,
                    "Capture stage did not expose the production Start action after Continue");

            select(workspace, GuidedProductionTask.DECEL_DETECTION);
            require(production.selectedTuningTaskForTest().contains("Decel Detection / Threshold"),
                    "Decel Detection did not delegate to its production recipe");
            require(!production.applyCurrentProposalEnabledForTest(),
                    "selecting Decel Detection manufactured Apply authority before Review");

            select(workspace, GuidedProductionTask.TPS_FUEL_RESPONSE);
            require(production.selectedTuningTaskForTest().contains("Fuel by Engine Cycle"),
                    "TPS Fuel Response did not delegate to its production recipe");
            select(workspace, GuidedProductionTask.TPS_SCALING);
            require(production.selectedTuningTaskForTest().contains("RPM / Temperature Compensation"),
                    "TPS Scaling did not delegate to its full baseline recipe");
            select(workspace, GuidedProductionTask.TPS_COMPLETION);
            require(production.selectedTuningTaskForTest().contains("Completion / Closed-Loop Handoff"),
                    "TPS Completion did not delegate to its full baseline recipe");

            select(workspace, GuidedProductionTask.WALL_FILM);
            require(production.selectedTuningTaskForTest().contains("Model / Base Tau-Beta"),
                    "Wall Film did not delegate to its production recipe");
            select(workspace, GuidedProductionTask.WALL_ADVANCED);
            require(production.selectedTuningTaskForTest().contains("Advanced Tau/Beta Mapping"),
                    "Advanced Wall Model did not delegate to its full baseline recipe");

            select(workspace, GuidedProductionTask.INSTANT_PULSE);
            require(production.selectedTuningTaskForTest().contains("Global Pulse / Inhibit"),
                    "Global Pulse / Inhibit did not delegate to Instant setup ownership");
            select(workspace, GuidedProductionTask.INSTANT_EVENT_STRENGTH);
            require(production.selectedTuningTaskForTest().contains("Event Strength"),
                    "Instant Event Strength did not delegate to its production recipe");
            select(workspace, GuidedProductionTask.INSTANT_CONDITIONS);
            require(production.selectedTuningTaskForTest().contains("Operating-Condition Multipliers"),
                    "Instant Operating Conditions did not delegate to its production recipe");
            select(workspace, GuidedProductionTask.INSTANT_VALIDATION);
            require(production.selectedTuningTaskForTest().contains("Residual Lean-Hole Validation"),
                    "Instant Residual Validation did not delegate to the residual evidence recipe");

            select(workspace, GuidedProductionTask.MAP_ESTIMATE);
            require(production.selectedTuningTaskForTest().contains("MAP Estimate Table"),
                    "MAP Estimate did not delegate to production");
            JButton validation = workspace.mapValidationToggleForTest();
            require(validation.isVisible(),
                    "MAP Predict secondary validation action is missing from the MAP workflow");
            click(validation);
            require(workspace.mapValidationActiveForTest()
                            && production.selectedTuningTaskForTest().contains("Transient Validation"),
                    "MAP Predict secondary validation did not route to existing production validation");
            require(workspace.taskCountForTest() == 19,
                    "MAP validation altered the accepted 19-task sidebar");
            click(validation);
            require(!workspace.mapValidationActiveForTest()
                            && production.selectedTuningTaskForTest().contains("MAP Estimate Table"),
                    "return from MAP validation did not restore the selected MAP task");

            select(workspace, GuidedProductionTask.BLEND_DURATION);
            require(production.selectedTuningTaskForTest().contains("Blend Duration"),
                    "Blend Duration did not delegate to production");

            String before = production.selectedTuningTaskForTest();
            select(workspace, GuidedProductionTask.DECEL_FUEL_RESPONSE);
            require(before.equals(production.selectedTuningTaskForTest()),
                    "still-planned Decel Fuel task bypassed fail-closed routing");
            require(!production.applyCurrentProposalEnabledForTest(),
                    "planned/presentation routing manufactured an Apply plan");
        } finally {
            production.terminateForClose();
            production.releaseAfterClose();
            GuidedFocusHub.clear();
        }
    }

    private static void defaultOnWithExplicitLegacyFallback() throws Exception {
        String originalFeature = System.getProperty(FEATURE_PROPERTY);
        String originalRecovery = System.getProperty("ae.tuner.recovery.dir");
        Path recovery = Files.createTempDirectory("ae-tuner-v019-structural-regression");
        System.setProperty("ae.tuner.recovery.dir", recovery.toString());
        try {
            System.clearProperty(FEATURE_PROPERTY);
            require(GuidedProductionWorkspacePanel.featureEnabled(),
                    "v0.19 production presentation must be default-on");
            AeTunerPlugin ported = new AeTunerPlugin();
            try {
                GuidedProductionWorkspacePanel wrapper =
                        (GuidedProductionWorkspacePanel) field(ported, "guidedProductionWorkspace");
                JTabbedPane tabs = (JTabbedPane) field(ported, "rootTabs");
                Component panel = (Component) field(ported, "panel");
                require(wrapper != null && wrapper.getParent() == panel,
                        "default plugin did not install v0.19 workspace directly as its host");
                require(tabs.getParent() == null && tabs.getTabCount() == 0,
                        "old Overview/Guided/Passive/Evidence tab shell leaked into default v0.19 UI");
            } finally { ported.close(); }

            System.setProperty(FEATURE_PROPERTY, "false");
            require(!GuidedProductionWorkspacePanel.featureEnabled(),
                    "explicit false no longer disables the structural v0.19 workspace");
            AeTunerPlugin legacyPlugin = new AeTunerPlugin();
            try {
                Object wrapper = field(legacyPlugin, "guidedProductionWorkspace");
                JScrollPane scroll = (JScrollPane) field(legacyPlugin, "guidedWorkspaceScroll");
                JTabbedPane tabs = (JTabbedPane) field(legacyPlugin, "rootTabs");
                require(wrapper == null && tabs.getTabCount() == 4
                                && tabs.getComponentAt(1) == scroll,
                        "explicit false no longer preserves the exact legacy Guided fallback");
            } finally { legacyPlugin.close(); }
        } finally {
            if (originalFeature == null) System.clearProperty(FEATURE_PROPERTY);
            else System.setProperty(FEATURE_PROPERTY, originalFeature);
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
                            && !AeUiTheme.taskAvailableBg().equals(AeUiTheme.taskDisabledBg())
                            && !AeUiTheme.taskSelectedText().equals(AeUiTheme.taskDisabledText()),
                    "Dark Side selected/available/unavailable hierarchy collapsed");
        } finally { AeUiTheme.set(original); }
    }

    private static void assertSixStages() throws Exception {
        Class<?> stageClass = Class.forName(
                "se.anders.tunerstudio.aetuner.guided.GuidedProductionWorkspacePanel$Stage");
        Object[] stages = stageClass.getEnumConstants();
        String[] expected = new String[]{"Prepare", "Capture", "Guided Focus", "Review", "Apply", "Result"};
        Field title = stageClass.getDeclaredField("title");
        title.setAccessible(true);
        require(stages.length == expected.length, "six-stage workflow count changed");
        for (int i = 0; i < stages.length; i++) {
            require(expected[i].equals((String) title.get(stages[i])),
                    "six-stage workflow order changed at " + i);
        }
    }

    private static int countBound(GuidedProductionWorkspacePanel workspace) {
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

    private static void click(final JButton button) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { button.doClick(); }
        });
    }

    private static boolean isDescendant(Container root, Component target) {
        if (root == target) return true;
        for (Component child : root.getComponents()) {
            if (child == target) return true;
            if (child instanceof Container && isDescendant((Container) child, target)) return true;
        }
        return false;
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static final class JComponentText {
        private final Component root;
        JComponentText(Component root) { this.root = root; }
        boolean contains(String expected) { return containsText(root, expected); }
    }

    private static boolean containsText(Component root, String expected) {
        if (root instanceof JLabel && ((JLabel) root).getText() != null
                && ((JLabel) root).getText().contains(expected)) return true;
        if (root instanceof javax.swing.AbstractButton
                && ((javax.swing.AbstractButton) root).getText() != null
                && ((javax.swing.AbstractButton) root).getText().contains(expected)) return true;
        if (root instanceof JTextComponent && ((JTextComponent) root).getText() != null
                && ((JTextComponent) root).getText().contains(expected)) return true;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                if (containsText(child, expected)) return true;
            }
        }
        return false;
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

    private static AeProjectSnapshot snapshotWithoutDecel(boolean tpsAe, boolean wallWetting,
                                                           boolean instantFuel, boolean mapEstimate) {
        return new AeProjectSnapshot(
                "guided-v019-no-decel",
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
                mapEstimate ? new double[]{0.20} : new double[0]);
    }

    private static void requireAvailable(GuidedProductionTask task, AeProjectSnapshot snapshot) {
        GuidedTaskAvailability state = GuidedTaskAvailabilityAdapter.evaluate(task, snapshot);
        require(state.clickable && "AVAILABLE".equals(state.status),
                task.displayName + " should be AVAILABLE but was " + state.status);
    }

    private static void requireClickableStatus(GuidedProductionTask task, AeProjectSnapshot snapshot,
                                               String expected) {
        GuidedTaskAvailability state = GuidedTaskAvailabilityAdapter.evaluate(task, snapshot);
        require(state.clickable && expected.equals(state.status),
                task.displayName + " expected clickable " + expected + " but was " + state.status);
        require(state.reason.length() > 0, task.displayName + " status reason is empty");
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
