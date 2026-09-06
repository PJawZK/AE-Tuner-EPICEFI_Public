package se.anders.tunerstudio.aetuner.host;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AeApplyRestoreValidationEngineRegressionTest {
    private AeApplyRestoreValidationEngineRegressionTest() { }

    public static void main(String[] args) throws Exception {
        scalarRoundTripUsesOneChangePlanAndExactRestore();
        arrayCellRoundTripPreservesEveryOtherCell();
        staleSelectionIsBlockedBeforeWrite();
        invalidTemporaryValueNeverReachesApplyCoordinator();
        independentReadbackMismatchTriggersSafetyRestore();
        System.out.println("AeApplyRestoreValidationEngineRegressionTest passed");
    }

    private static void scalarRoundTripUsesOneChangePlanAndExactRestore() throws Exception {
        FakeIo io = new FakeIo();
        io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(io);
        AeApplyRestoreValidationEngine engine =
                new AeApplyRestoreValidationEngine(io, coordinator, "cfg");
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);

        requireClose(50.0, engine.selectTarget(target),
                "selection did not capture exact original scalar");
        requireClose(60.0, engine.setTemporaryValue(60.0),
                "valid temporary scalar was rejected");
        AeApplyRestoreValidationEngine.OperationResult applied = engine.applyTemporary();
        require(applied.success, "scalar validation apply failed: " + applied.message);
        requireClose(60.0, applied.readbackValue,
                "scalar independent Apply readback was wrong");
        require(engine.isTemporaryApplied(),
                "engine did not mark successful temporary Apply as requiring Restore");
        require(coordinator.previousApplyPlan() != null
                        && coordinator.previousApplyPlan().changeCount() == 1,
                "validation engine must generate exactly one ProposalWritePlan change");
        requireThrows(() -> {
            try {
                engine.selectTarget(AeApplyRestoreValidationTargets.find(
                        AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES, 0));
            } catch (Exception ex) {
                if (ex instanceof RuntimeException) throw (RuntimeException) ex;
                throw new RuntimeException(ex);
            }
        }, "engine allowed navigation to another target while temporary value was applied");

        AeApplyRestoreValidationEngine.OperationResult restored = engine.restoreOriginal();
        require(restored.success && restored.restore,
                "scalar validation Restore failed: " + restored.message);
        requireClose(50.0, restored.readbackValue,
                "scalar Restore did not recover exact captured original");
        require(!engine.isTemporaryApplied(),
                "engine still reports a temporary value after successful Restore");
        require(engine.canSelectAnotherTarget(),
                "engine did not release target lock after exact Restore");
    }

    private static void arrayCellRoundTripPreservesEveryOtherCell() throws Exception {
        FakeIo io = new FakeIo();
        io.arrays.put(key("cfg", AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES),
                new double[][]{{0.18, 0.26, 0.22, 0.18}});
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(io);
        AeApplyRestoreValidationEngine engine =
                new AeApplyRestoreValidationEngine(io, coordinator, "cfg");
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES, 1);

        requireClose(0.26, engine.selectTarget(target),
                "array selection did not capture selected cell");
        engine.setTemporaryValue(0.54);
        AeApplyRestoreValidationEngine.OperationResult applied = engine.applyTemporary();
        require(applied.success, "array-cell validation Apply failed: " + applied.message);
        double[][] live = io.arrays.get(key("cfg", AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES));
        requireClose(0.18, live[0][0], "array Apply changed preceding cell");
        requireClose(0.54, live[0][1], "array Apply missed selected cell");
        requireClose(0.22, live[0][2], "array Apply changed following cell");
        requireClose(0.18, live[0][3], "array Apply changed last cell");

        AeApplyRestoreValidationEngine.OperationResult restored = engine.restoreOriginal();
        require(restored.success, "array-cell validation Restore failed: " + restored.message);
        live = io.arrays.get(key("cfg", AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES));
        requireClose(0.26, live[0][1], "array-cell Restore missed exact original");
        requireClose(0.18, live[0][0], "array Restore damaged preceding cell");
        requireClose(0.22, live[0][2], "array Restore damaged following cell");
    }

    private static void staleSelectionIsBlockedBeforeWrite() throws Exception {
        FakeIo io = new FakeIo();
        io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(io);
        AeApplyRestoreValidationEngine engine =
                new AeApplyRestoreValidationEngine(io, coordinator, "cfg");
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);

        engine.selectTarget(target);
        engine.setTemporaryValue(60.0);
        io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(55.0));
        AeApplyRestoreValidationEngine.OperationResult result = engine.applyTemporary();
        require(!result.success, "stale captured original must block validation Apply");
        require(io.writeCount == 0,
                "stale validation target reached production write boundary");
        require(!engine.isTemporaryApplied(),
                "blocked stale validation target incorrectly requires Restore");
    }

    private static void invalidTemporaryValueNeverReachesApplyCoordinator() throws Exception {
        FakeIo io = new FakeIo();
        io.arrays.put(key("cfg", AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES),
                new double[][]{{0.18, 0.26, 0.22, 0.18}});
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(io);
        AeApplyRestoreValidationEngine engine =
                new AeApplyRestoreValidationEngine(io, coordinator, "cfg");
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES, 0);

        engine.selectTarget(target);
        requireThrows(() -> engine.setTemporaryValue(0.19),
                "off-grid temporary controller value was accepted");
        require(io.writeCount == 0,
                "invalid temporary value reached controller write path");
        require(coordinator.applyDepth() == 0,
                "invalid temporary value created production restore history");
    }

    private static void independentReadbackMismatchTriggersSafetyRestore() throws Exception {
        FakeIo io = new FakeIo();
        io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(io);
        AeApplyRestoreValidationEngine engine =
                new AeApplyRestoreValidationEngine(io, coordinator, "cfg");
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);

        engine.selectTarget(target);
        engine.setTemporaryValue(60.0);
        io.distortNextIndependentRead = true;
        io.distortedIndependentValue = 59.0;
        AeApplyRestoreValidationEngine.OperationResult result = engine.applyTemporary();
        require(!result.success,
                "independent post-Apply mismatch must fail physical validation");
        require(result.message.contains("safety restore PASS"),
                "independent mismatch did not report successful safety restore: " + result.message);
        requireClose(50.0,
                io.scalars.get(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS)).doubleValue(),
                "safety recovery did not restore exact original working-tune value");
        require(!engine.isTemporaryApplied(),
                "engine remained locked after verified automatic safety recovery");
        require(coordinator.applyDepth() == 0,
                "safety recovery left a stale validation restore record");
    }

    private static final class FakeIo
            implements ProposalApplyCoordinator.Backend,
                       AeApplyRestoreValidationEngine.ValueReader {
        final Map<String, Double> scalars = new LinkedHashMap<String, Double>();
        final Map<String, double[][]> arrays = new LinkedHashMap<String, double[][]>();
        int writeCount;
        boolean distortNextIndependentRead;
        double distortedIndependentValue;

        @Override
        public double read(String configurationName,
                           AeApplyRestoreValidationTargets.Target target) {
            if (distortNextIndependentRead) {
                distortNextIndependentRead = false;
                return distortedIndependentValue;
            }
            if (target.isIndexed()) {
                return getFlat(requireArray(configurationName, target.getControllerName()),
                        target.getFlatIndex());
            }
            return requireScalar(configurationName, target.getControllerName());
        }

        @Override
        public double readScalar(String configurationName, String parameterName) {
            return requireScalar(configurationName, parameterName);
        }

        @Override
        public double[][] readArray(String configurationName, String parameterName) {
            return cloneTable(requireArray(configurationName, parameterName));
        }

        @Override
        public void writeScalar(String configurationName, String parameterName, double value) {
            writeCount++;
            scalars.put(key(configurationName, parameterName), Double.valueOf(value));
        }

        @Override
        public void writeArray(String configurationName, String parameterName,
                               double[][] values) {
            writeCount++;
            arrays.put(key(configurationName, parameterName), cloneTable(values));
        }

        private double requireScalar(String configurationName, String parameterName) {
            Double value = scalars.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing scalar " + parameterName);
            return value.doubleValue();
        }

        private double[][] requireArray(String configurationName, String parameterName) {
            double[][] value = arrays.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing array " + parameterName);
            return value;
        }
    }

    private static String key(String configurationName, String parameterName) {
        return configurationName + "::" + parameterName;
    }

    private static double getFlat(double[][] values, int targetIndex) {
        int index = 0;
        for (double[] row : values) {
            if (row == null) continue;
            for (double value : row) {
                if (index == targetIndex) return value;
                index++;
            }
        }
        throw new IllegalArgumentException("flat index outside fake array: " + targetIndex);
    }

    private static double[][] cloneTable(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i] == null ? new double[0] : values[i].clone();
        }
        return copy;
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected
        } catch (IllegalStateException expected) {
            // expected
        }
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
