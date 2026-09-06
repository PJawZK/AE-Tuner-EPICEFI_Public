package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProposalApplyCoordinatorBitSelectionRegressionTest {
    private ProposalApplyCoordinatorBitSelectionRegressionTest() { }

    public static void main(String[] args) {
        genericBooleanBitUsesOptionApiAndRestores();
        genericEnumUsesOptionApiAndRestores();
        staleGenericBitIsBlockedBeforeWrite();
        invalidFrozenEnumStateIsBlockedWithoutWrite();
        numericScalarPathRemainsNumeric();
        System.out.println("ProposalApplyCoordinatorBitSelectionRegressionTest passed");
    }

    private static void genericBooleanBitUsesOptionApiAndRestores() {
        FakeBackend backend = new FakeBackend();
        backend.addOption("cfg", AeParameterNames.WALL_WETTING_AE_ENABLED,
                0, "false", "true");
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = scalarPlan(
                AeParameterNames.WALL_WETTING_AE_ENABLED,
                0.0, 1.0, "Enable Wall Wetting");

        ProposalApplyCoordinator.ApplyResult applied = coordinator.apply(plan);
        require(applied.success, "generic boolean bit apply failed: " + applied.message);
        require(backend.optionWriteCount == 1,
                "generic boolean bit did not use option-selection write API");
        require(backend.scalarWriteCount == 0,
                "generic boolean bit incorrectly used numeric scalar write API");
        require(backend.scalarReadCount == 0,
                "generic boolean bit incorrectly read the shared word as scalar");
        require(backend.optionIndex("cfg", AeParameterNames.WALL_WETTING_AE_ENABLED) == 1,
                "generic boolean bit did not reach requested option");

        ProposalApplyCoordinator.ApplyResult restored = coordinator.restorePreviousApply();
        require(restored.success && restored.restore,
                "generic boolean bit restore failed: " + restored.message);
        require(backend.optionWriteCount == 2,
                "generic boolean bit restore did not use option-selection API");
        require(backend.optionIndex("cfg", AeParameterNames.WALL_WETTING_AE_ENABLED) == 0,
                "generic boolean bit restore did not recover exact original state");
    }

    private static void genericEnumUsesOptionApiAndRestores() {
        FakeBackend backend = new FakeBackend();
        backend.addOption("cfg", AeParameterNames.WALL_MODEL_TYPE,
                0, "Basic (constants)", "Advanced (tables)");
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = scalarPlan(
                AeParameterNames.WALL_MODEL_TYPE,
                0.0, 1.0, "Wall fueling model type");

        ProposalApplyCoordinator.ApplyResult applied = coordinator.apply(plan);
        require(applied.success, "generic enum apply failed: " + applied.message);
        require(backend.optionWriteCount == 1,
                "generic enum did not use option-selection API");
        require(backend.scalarWriteCount == 0,
                "generic enum incorrectly used numeric scalar API");
        require(backend.optionIndex("cfg", AeParameterNames.WALL_MODEL_TYPE) == 1,
                "generic enum did not select Advanced state");
        require(coordinator.restorePreviousApply().success,
                "generic enum restore failed");
        require(backend.optionIndex("cfg", AeParameterNames.WALL_MODEL_TYPE) == 0,
                "generic enum restore did not recover Basic state");
    }

    private static void staleGenericBitIsBlockedBeforeWrite() {
        FakeBackend backend = new FakeBackend();
        backend.addOption("cfg", AeParameterNames.USE_MAP_ESTIMATE_DURING_DECEL,
                1, "false", "true");
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan stale = scalarPlan(
                AeParameterNames.USE_MAP_ESTIMATE_DURING_DECEL,
                0.0, 1.0, "Use MAP estimate during decel");

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(stale);
        require(!result.success, "stale generic bit must be blocked");
        require(backend.optionWriteCount == 0,
                "stale generic bit wrote before stale check completed");
        require(backend.scalarWriteCount == 0,
                "stale generic bit fell through to scalar write path");
        require(coordinator.applyDepth() == 0,
                "stale generic bit created restore history");
    }

    private static void invalidFrozenEnumStateIsBlockedWithoutWrite() {
        FakeBackend backend = new FakeBackend();
        backend.addOption("cfg", AeParameterNames.TPS_AE_DETECT_MODE,
                4,
                "Max step (legacy)", "Max step, timed", "Window span",
                "Rise from floor", "Dual stride, newest", "INVALID", "INVALID", "INVALID");
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan invalid = scalarPlan(
                AeParameterNames.TPS_AE_DETECT_MODE,
                4.0, 5.0, "Engagement model");

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(invalid);
        require(!result.success, "frozen INVALID enum state must not apply");
        require(backend.optionWriteCount == 0,
                "invalid enum state reached TunerStudio write API");
        require(backend.optionIndex("cfg", AeParameterNames.TPS_AE_DETECT_MODE) == 4,
                "blocked invalid enum proposal altered the live option");
        require(coordinator.applyDepth() == 0,
                "blocked invalid enum state entered restore history");
    }

    private static void numericScalarPathRemainsNumeric() {
        FakeBackend backend = new FakeBackend();
        backend.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = scalarPlan(
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                50.0, 60.0, "Delta Window");

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
        require(result.success, "ordinary numeric scalar apply regressed: " + result.message);
        require(backend.scalarWriteCount == 1,
                "ordinary numeric scalar no longer uses numeric write API");
        require(backend.optionWriteCount == 0,
                "ordinary numeric scalar incorrectly used option-selection API");
        requireClose(60.0,
                backend.scalars.get(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS)).doubleValue(),
                "ordinary numeric scalar wrote wrong value");
    }

    private static ProposalWritePlan scalarPlan(String parameterName,
                                                double before, double after,
                                                String label) {
        return new ProposalWritePlan(
                "bit-selection-regression", "Bit selection regression", "cfg", "",
                Arrays.asList(ProposalWritePlan.Change.scalar(
                        parameterName, before, after, label, "")));
    }

    private static final class FakeBackend implements ProposalApplyCoordinator.Backend {
        final Map<String, Double> scalars = new LinkedHashMap<String, Double>();
        final Map<String, OptionState> options = new LinkedHashMap<String, OptionState>();
        int scalarReadCount;
        int scalarWriteCount;
        int optionWriteCount;

        void addOption(String configurationName, String parameterName,
                       int currentIndex, String... labels) {
            options.put(key(configurationName, parameterName),
                    new OptionState(currentIndex, Arrays.asList(labels)));
        }

        int optionIndex(String configurationName, String parameterName) {
            OptionState state = options.get(key(configurationName, parameterName));
            if (state == null) throw new IllegalStateException("missing option state");
            return state.currentIndex;
        }

        @Override
        public double readScalar(String configurationName, String parameterName) {
            scalarReadCount++;
            Double value = scalars.get(key(configurationName, parameterName));
            if (value == null) {
                throw new AssertionError(
                        "canonical bit/enum incorrectly reached scalar read path: " + parameterName);
            }
            return value.doubleValue();
        }

        @Override
        public String readOption(String configurationName, String parameterName) {
            OptionState state = requireOption(configurationName, parameterName);
            return state.labels.get(state.currentIndex);
        }

        @Override
        public List<String> readOptionDescriptions(String configurationName,
                                                   String parameterName) {
            return requireOption(configurationName, parameterName).labels;
        }

        @Override
        public double[][] readArray(String configurationName, String parameterName) {
            throw new UnsupportedOperationException("array path not used by this regression");
        }

        @Override
        public void writeScalar(String configurationName, String parameterName, double value) {
            scalarWriteCount++;
            scalars.put(key(configurationName, parameterName), Double.valueOf(value));
        }

        @Override
        public void writeOptionIndex(String configurationName, String parameterName,
                                     int optionIndex) {
            OptionState state = requireOption(configurationName, parameterName);
            if (optionIndex < 0 || optionIndex >= state.labels.size()) {
                throw new IllegalArgumentException("option index outside fake live options");
            }
            optionWriteCount++;
            state.currentIndex = optionIndex;
        }

        @Override
        public void writeArray(String configurationName, String parameterName,
                               double[][] values) {
            throw new UnsupportedOperationException("array path not used by this regression");
        }

        private OptionState requireOption(String configurationName, String parameterName) {
            OptionState state = options.get(key(configurationName, parameterName));
            if (state == null) throw new IllegalStateException("missing option " + parameterName);
            return state;
        }
    }

    private static final class OptionState {
        int currentIndex;
        final List<String> labels;

        OptionState(int currentIndex, List<String> labels) {
            this.currentIndex = currentIndex;
            this.labels = labels;
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
