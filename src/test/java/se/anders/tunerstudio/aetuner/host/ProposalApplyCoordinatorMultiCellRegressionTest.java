package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Proves one reviewed plan may safely mutate several cells of one table. */
public final class ProposalApplyCoordinatorMultiCellRegressionTest {
    private ProposalApplyCoordinatorMultiCellRegressionTest() { }

    public static void main(String[] args) {
        multiCellPlanAppliesOnceAndRestoresWholeOriginalArray();
        System.out.println("ProposalApplyCoordinatorMultiCellRegressionTest passed");
    }

    private static void multiCellPlanAppliesOnceAndRestoresWholeOriginalArray() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key("cfg", "table"), new double[][]{
                {1.00, 1.00, 0.90, 0.70},
                {1.00, 1.00, 0.90, 0.70}
        });
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = new ProposalWritePlan(
                "table-multi", "Reviewed table update", "cfg", "three reviewed cells",
                Arrays.asList(
                        ProposalWritePlan.Change.arrayCell(
                                "table", 1, 1.00, 0.95, "r0c1", "mult"),
                        ProposalWritePlan.Change.arrayCell(
                                "table", 2, 0.90, 0.75, "r0c2", "mult"),
                        ProposalWritePlan.Change.arrayCell(
                                "table", 6, 0.90, 0.80, "r1c2", "mult")));

        ProposalApplyCoordinator.ApplyResult applied = coordinator.apply(plan);
        require(applied.success, "valid multi-cell reviewed plan did not apply");
        require(backend.writeCount == 1,
                "several cells of one parameter must be coalesced into one array write");
        double[][] changed = backend.arrays.get(key("cfg", "table"));
        requireClose(1.00, changed[0][0], "undeclared r0c0 changed");
        requireClose(0.95, changed[0][1], "declared r0c1 not applied");
        requireClose(0.75, changed[0][2], "declared r0c2 not applied");
        requireClose(0.70, changed[0][3], "undeclared r0c3 changed");
        requireClose(1.00, changed[1][0], "undeclared r1c0 changed");
        requireClose(1.00, changed[1][1], "undeclared r1c1 changed");
        requireClose(0.80, changed[1][2], "declared r1c2 not applied");
        requireClose(0.70, changed[1][3], "undeclared r1c3 changed");
        require(coordinator.applyDepth() == 1,
                "one multi-cell plan must create one restore-stack entry");

        ProposalApplyCoordinator.ApplyResult restored = coordinator.restorePreviousApply();
        require(restored.success && restored.restore,
                "multi-cell plan did not Restore Previous Apply");
        require(backend.writeCount == 2,
                "multi-cell restore should perform one whole-array restore write");
        double[][] original = backend.arrays.get(key("cfg", "table"));
        requireClose(1.00, original[0][0], "restore damaged r0c0");
        requireClose(1.00, original[0][1], "restore did not recover r0c1");
        requireClose(0.90, original[0][2], "restore did not recover r0c2");
        requireClose(0.70, original[0][3], "restore damaged r0c3");
        requireClose(1.00, original[1][0], "restore damaged r1c0");
        requireClose(1.00, original[1][1], "restore damaged r1c1");
        requireClose(0.90, original[1][2], "restore did not recover r1c2");
        requireClose(0.70, original[1][3], "restore damaged r1c3");
        require(coordinator.applyDepth() == 0,
                "multi-cell Restore did not clear its restore-stack entry");
    }

    private static final class FakeBackend implements ProposalApplyCoordinator.Backend {
        final Map<String, double[][]> arrays = new LinkedHashMap<String, double[][]>();
        int writeCount;

        @Override public double readScalar(String configurationName, String parameterName) {
            throw new UnsupportedOperationException("scalar not used");
        }

        @Override public double[][] readArray(String configurationName, String parameterName) {
            double[][] value = arrays.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing array " + parameterName);
            return cloneTable(value);
        }

        @Override public void writeScalar(String configurationName, String parameterName, double value) {
            throw new UnsupportedOperationException("scalar not used");
        }

        @Override public void writeArray(String configurationName, String parameterName,
                                         double[][] values) {
            writeCount++;
            arrays.put(key(configurationName, parameterName), cloneTable(values));
        }
    }

    private static String key(String configurationName, String parameterName) {
        return configurationName + "::" + parameterName;
    }

    private static double[][] cloneTable(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) copy[i] = values[i].clone();
        return copy;
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
