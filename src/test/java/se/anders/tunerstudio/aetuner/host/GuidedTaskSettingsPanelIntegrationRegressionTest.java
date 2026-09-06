package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedCapturePanel;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import javax.swing.JButton;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuidedTaskSettingsPanelIntegrationRegressionTest {
    private static final String CFG = "Main Controller";

    private GuidedTaskSettingsPanelIntegrationRegressionTest() { }

    public static void main(String[] args) throws Exception {
        broadStagedTaskPlanUsesExistingApplyRestoreAndCannotCrossTasks();
        System.out.println("GuidedTaskSettingsPanelIntegrationRegressionTest passed");
    }

    private static void broadStagedTaskPlanUsesExistingApplyRestoreAndCannotCrossTasks()
            throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.scalars.put(key(CFG, AeParameterNames.WALL_TAU), Double.valueOf(1.00));
        backend.scalars.put(key(CFG, AeParameterNames.WALL_BETA), Double.valueOf(0.30));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);

        GuidedCapturePanel panel = new GuidedCapturePanel();
        invoke(panel, "selectTuningTaskForTest",
                new Class<?>[]{GuidedTuningRecipe.class}, GuidedTuningRecipe.WALL_WETTING);
        invoke(panel, "setProjectSnapshotForTest",
                new Class<?>[]{AeProjectSnapshot.class}, snapshot());
        setField(panel, "applyCoordinator", coordinator);
        invoke(panel, "refresh", new Class<?>[0]);

        JButton review = (JButton) getField(panel, "reviewTaskSettings");
        require(review.isEnabled(),
                "validated task settings editor was not available immediately after working-tune baseline/coordinator setup");
        require(review.getText().contains("Task Settings"),
                "Guided did not expose a visible task-settings review control");

        ProposalWritePlan broad = new ProposalWritePlan(
                "task-settings-wall-wetting",
                "Wall Wetting task settings",
                CFG,
                "Operator-reviewed physically validated task settings; 2 changed value(s) across 2 controller parameter(s).",
                Arrays.asList(
                        ProposalWritePlan.Change.scalar(
                                AeParameterNames.WALL_TAU, 1.00, 1.10,
                                "Wall tau", ""),
                        ProposalWritePlan.Change.scalar(
                                AeParameterNames.WALL_BETA, 0.30, 0.35,
                                "Wall beta", "")));
        setField(panel, "stagedTaskSettingsPlan", broad);
        setField(panel, "stagedTaskSettingsTask", GuidedTuningRecipe.WALL_WETTING);
        invoke(panel, "refresh", new Class<?>[0]);

        JButton apply = (JButton) getField(panel, "applyProposal");
        require(apply.isEnabled(),
                "two-setting staged task plan did not enable the existing Apply Current Proposal control");
        String proposalText = ((javax.swing.JTextArea) getField(panel, "proposal")).getText();
        require(proposalText.contains("STAGED TASK SETTINGS")
                        && proposalText.contains("Wall tau")
                        && proposalText.contains("Wall beta"),
                "Guided review did not surface the complete staged multi-setting plan");

        invoke(panel, "applyCurrentProposal", new Class<?>[0]);
        requireClose(1.10, backend.scalar(AeParameterNames.WALL_TAU),
                "broad task Apply did not update Wall tau");
        requireClose(0.35, backend.scalar(AeParameterNames.WALL_BETA),
                "broad task Apply did not update Wall beta");
        require(coordinator.applyDepth() == 1,
                "two related settings were not retained as one restore transaction");
        require(getField(panel, "stagedTaskSettingsPlan") == null,
                "successfully applied staged task plan remained pending after verification");
        require((Boolean) getField(panel, "workingTuneReadRequiredAfterApply"),
                "broad task Apply did not require a fresh working-tune baseline afterward");

        invoke(panel, "restorePreviousApply", new Class<?>[0]);
        requireClose(1.00, backend.scalar(AeParameterNames.WALL_TAU),
                "Restore did not recover original Wall tau");
        requireClose(0.30, backend.scalar(AeParameterNames.WALL_BETA),
                "Restore did not recover original Wall beta");
        require(coordinator.applyDepth() == 0,
                "broad task restore record remained after exact restore");

        setField(panel, "stagedTaskSettingsPlan", broad);
        setField(panel, "stagedTaskSettingsTask", GuidedTuningRecipe.WALL_WETTING);
        invoke(panel, "selectTuningTaskForTest",
                new Class<?>[]{GuidedTuningRecipe.class},
                GuidedTuningRecipe.WALL_WETTING_ADVANCED);
        require(getField(panel, "stagedTaskSettingsPlan") == null
                        && getField(panel, "stagedTaskSettingsTask") == null,
                "staged task settings crossed into another Guided task after navigation");
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                CFG,
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, true, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static Object invoke(Object target, String name,
                                 Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FakeBackend implements ProposalApplyCoordinator.Backend {
        final Map<String, Double> scalars = new LinkedHashMap<String, Double>();

        @Override
        public double readScalar(String configurationName, String parameterName) {
            Double value = scalars.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing scalar " + parameterName);
            return value.doubleValue();
        }

        @Override
        public double[][] readArray(String configurationName, String parameterName) {
            throw new IllegalStateException("no arrays expected");
        }

        @Override
        public void writeScalar(String configurationName, String parameterName, double value) {
            scalars.put(key(configurationName, parameterName), Double.valueOf(value));
        }

        @Override
        public void writeArray(String configurationName, String parameterName,
                               double[][] values) {
            throw new IllegalStateException("no arrays expected");
        }

        double scalar(String parameterName) {
            return readScalar(CFG, parameterName);
        }
    }

    private static String key(String configurationName, String parameterName) {
        return configurationName + "::" + parameterName;
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
