package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProposalApplyCoordinatorRegressionTest {
    private ProposalApplyCoordinatorRegressionTest() { }

    public static void main(String[] args) {
        exactArrayCellApplyPreservesEverythingElse();
        staleProposalIsBlockedBeforeAnyWrite();
        secondPreflightBlocksRaceBeforeWrite();
        restoreRequiresAppliedStateAndRestoresExactly();
        sequentialAppliesRestoreInLifoOrder();
        partialFailureRollsBackEarlierWrites();
        readbackMismatchRollsBackWrittenState();
        scalarMutationBeforeExceptionIsRolledBack();
        partialArrayMutationBeforeExceptionIsRolledBack();
        rollbackFailureDoesNotSkipOtherParameters();
        rollbackReadFailureDoesNotSkipOtherVerification();
        rollbackExceptionWithMatchingReadbackIsVerified();
        failedRestoreRecoversAppliedStateAndRetainsHistory();
        System.out.println("ProposalApplyCoordinatorRegressionTest passed");
    }

    private static void exactArrayCellApplyPreservesEverythingElse() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key("cfg", "blend"),
                new double[][]{{0.18, 0.26, 0.22, 0.18}});
        ProposalApplyCoordinator coordinator =
                new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = new ProposalWritePlan(
                "blend", "Predictive MAP Blend Duration", "cfg", "2450 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "blend", 1, 0.26, 0.54, "2450 RPM", "s")));

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
        require(result.success, "valid explicit proposal should apply");
        double[][] actual = backend.arrays.get(key("cfg", "blend"));
        requireClose(0.18, actual[0][0], "600 point changed unexpectedly");
        requireClose(0.54, actual[0][1], "selected 2450 point was not written");
        requireClose(0.22, actual[0][2], "4350 point changed unexpectedly");
        requireClose(0.18, actual[0][3], "6200 point changed unexpectedly");
        require(backend.writeCount == 1,
                "single-parameter proposal should issue exactly one array update");
        require(coordinator.applyDepth() == 1,
                "successful apply should create one restore-stack entry");
        require(result.message.contains("no burn"),
                "success text must state that no burn was requested");
    }

    private static void staleProposalIsBlockedBeforeAnyWrite() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key("cfg", "blend"),
                new double[][]{{0.18, 0.30, 0.22, 0.18}});
        ProposalApplyCoordinator coordinator =
                new ProposalApplyCoordinator(backend);
        ProposalWritePlan stale = new ProposalWritePlan(
                "blend", "Predictive MAP Blend Duration", "cfg", "2450 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "blend", 1, 0.26, 0.54, "2450 RPM", "s")));

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(stale);
        require(!result.success, "stale proposal must be blocked");
        require(backend.writeCount == 0,
                "stale proposal must be rejected before any write");
        require(coordinator.applyDepth() == 0,
                "blocked stale proposal must not create restore history");
        requireClose(0.30, backend.arrays.get(key("cfg", "blend"))[0][1],
                "stale preflight altered the working value");
    }

    private static void secondPreflightBlocksRaceBeforeWrite() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key("cfg", "blend"),
                new double[][]{{0.18, 0.26, 0.22, 0.18}});
        backend.distortReadNumber = 2;
        backend.distortParameter = "blend";
        backend.distortFlatIndex = 1;
        backend.distortValue = 0.30;

        ProposalApplyCoordinator coordinator =
                new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = new ProposalWritePlan(
                "blend", "Predictive MAP Blend Duration", "cfg", "2450 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "blend", 1, 0.26, 0.54, "2450 RPM", "s")));

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
        require(!result.success,
                "a changed value between prepare and the mutation boundary must block apply");
        require(backend.writeCount == 0,
                "second-preflight race must be detected before any write");
        require(coordinator.applyDepth() == 0,
                "second-preflight rejection must not create restore history");
        requireClose(0.26, backend.arrays.get(key("cfg", "blend"))[0][1],
                "simulated race read altered the underlying working tune");
    }

    private static void restoreRequiresAppliedStateAndRestoresExactly() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key("cfg", "blend"),
                new double[][]{{0.18, 0.26, 0.22, 0.18}});
        ProposalApplyCoordinator coordinator =
                new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = new ProposalWritePlan(
                "blend", "Predictive MAP Blend Duration", "cfg", "2450 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "blend", 1, 0.26, 0.54, "2450 RPM", "s")));

        require(coordinator.apply(plan).success, "setup apply failed");
        require(coordinator.canRestorePreviousApply(),
                "successful apply should expose restore");
        require(coordinator.previousApplyPlan() == plan,
                "restore preview did not expose the exact most recent write plan");
        ProposalApplyCoordinator.ApplyResult restored =
                coordinator.restorePreviousApply();
        require(restored.success && restored.restore,
                "restore should succeed when the applied value is unchanged");
        requireClose(0.26, backend.arrays.get(key("cfg", "blend"))[0][1],
                "restore did not recover exact baseline value");
        require(!coordinator.canRestorePreviousApply(),
                "completed only restore must empty the restore stack");

        require(coordinator.apply(plan).success, "second setup apply failed");
        backend.arrays.get(key("cfg", "blend"))[0][1] = 0.50;
        int writesBefore = backend.writeCount;
        ProposalApplyCoordinator.ApplyResult blocked =
                coordinator.restorePreviousApply();
        require(!blocked.success,
                "restore must be blocked after a manual intervening change");
        require(backend.writeCount == writesBefore,
                "stale restore must not write anything");
        requireClose(0.50, backend.arrays.get(key("cfg", "blend"))[0][1],
                "blocked restore overwrote the intervening manual value");
        require(coordinator.applyDepth() == 1,
                "blocked restore must retain its restore record for operator review");
    }

    private static void sequentialAppliesRestoreInLifoOrder() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key("cfg", "blend"),
                new double[][]{{0.18, 0.26, 0.22, 0.18}});
        ProposalApplyCoordinator coordinator =
                new ProposalApplyCoordinator(backend);

        ProposalWritePlan first = new ProposalWritePlan(
                "blend", "Predictive MAP Blend Duration", "cfg", "600 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "blend", 0, 0.18, 0.20, "600 RPM", "s")));
        ProposalWritePlan second = new ProposalWritePlan(
                "blend", "Predictive MAP Blend Duration", "cfg", "2450 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "blend", 1, 0.26, 0.54, "2450 RPM", "s")));

        require(coordinator.apply(first).success,
                "first sequential proposal failed");
        require(coordinator.apply(second).success,
                "second sequential proposal failed");
        require(coordinator.applyDepth() == 2,
                "sequential explicit applies must preserve both restore records");
        require(coordinator.previousApplyPlan() == second,
                "Restore Previous Apply must point at the most recent proposal");

        require(coordinator.restorePreviousApply().success,
                "latest sequential proposal did not restore");
        double[][] afterSecondRestore = backend.arrays.get(key("cfg", "blend"));
        requireClose(0.20, afterSecondRestore[0][0],
                "restoring the second apply damaged the earlier 600 RPM apply");
        requireClose(0.26, afterSecondRestore[0][1],
                "second apply did not restore its own 2450 RPM baseline");
        require(coordinator.applyDepth() == 1,
                "first restore should leave the earlier apply on the stack");
        require(coordinator.previousApplyPlan() == first,
                "restore stack did not expose the earlier proposal next");

        require(coordinator.restorePreviousApply().success,
                "earlier sequential proposal did not restore");
        double[][] restored = backend.arrays.get(key("cfg", "blend"));
        requireClose(0.18, restored[0][0],
                "final restore did not recover original 600 RPM value");
        requireClose(0.26, restored[0][1],
                "final restore damaged original 2450 RPM value");
        require(coordinator.applyDepth() == 0,
                "all sequential applies were not removed after LIFO restores");
    }

    private static void partialFailureRollsBackEarlierWrites() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key("cfg", "first"), new double[][]{{1.0, 2.0}});
        backend.scalars.put(key("cfg", "second"), Double.valueOf(3.0));
        backend.failParameter = "second";
        ProposalApplyCoordinator coordinator =
                new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = new ProposalWritePlan(
                "multi", "Transaction test", "cfg", "",
                Arrays.asList(
                        ProposalWritePlan.Change.arrayCell(
                                "first", 1, 2.0, 4.0, "first[1]", ""),
                        ProposalWritePlan.Change.scalar(
                                "second", 3.0, 6.0, "second", "")));

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
        require(!result.success, "injected second write failure should fail apply");
        requireClose(2.0, backend.arrays.get(key("cfg", "first"))[0][1],
                "earlier array write was not rolled back");
        requireClose(3.0, backend.scalars.get(key("cfg", "second")).doubleValue(),
                "failed scalar parameter should remain at baseline");
        require(coordinator.applyDepth() == 0,
                "failed transaction must not enter restore history");
    }

    private static void readbackMismatchRollsBackWrittenState() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key("cfg", "blend"),
                new double[][]{{0.18, 0.26, 0.22, 0.18}});
        // Reads: prepare=1, second preflight=2, post-write verification=3.
        backend.distortReadNumber = 3;
        backend.distortParameter = "blend";
        backend.distortFlatIndex = 1;
        backend.distortValue = 0.50;

        ProposalApplyCoordinator coordinator =
                new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = new ProposalWritePlan(
                "blend", "Predictive MAP Blend Duration", "cfg", "2450 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "blend", 1, 0.26, 0.54, "2450 RPM", "s")));

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
        require(!result.success,
                "post-write readback mismatch must fail the explicit apply");
        require(result.message.contains("rollback PASS"),
                "readback mismatch did not report verified rollback: " + result.message);
        require(backend.writeCount == 2,
                "failed write verification should perform one apply write plus one rollback write");
        requireClose(0.26, backend.arrays.get(key("cfg", "blend"))[0][1],
                "readback failure did not restore the original working-tune value");
        require(coordinator.applyDepth() == 0,
                "readback-failed apply must not enter restore history");
    }

    private static ProposalWritePlan scalarPlan() {
        return new ProposalWritePlan("scalar", "Scalar test", "cfg", "",
                Arrays.asList(ProposalWritePlan.Change.scalar("first", 3.0, 6.0, "first", "")));
    }

    private static ProposalWritePlan twoScalarPlan() {
        return new ProposalWritePlan("multi", "Transaction test", "cfg", "",
                Arrays.asList(
                        ProposalWritePlan.Change.scalar("first", 3.0, 6.0, "first", ""),
                        ProposalWritePlan.Change.scalar("second", 4.0, 8.0, "second", "")));
    }

    private static void scalarMutationBeforeExceptionIsRolledBack() {
        FakeBackend backend = new FakeBackend() {
            @Override public void writeScalar(String cfg, String name, double value) {
                super.writeScalar(cfg, name, value);
                if (writeCount == 1) throw new IllegalStateException("mutation before failure");
            }
        };
        backend.scalars.put(key("cfg", "first"), 3.0);
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(scalarPlan());
        require(!result.success, "throwing write must remain a failed Apply");
        requireClose(3.0, backend.readScalar("cfg", "first"), "throwing scalar write escaped rollback");
        require(result.message.contains("rollback PASS"), "scalar rollback was not verified");
        require(coordinator.applyDepth() == 0, "failed Apply created restore history");
    }

    private static void partialArrayMutationBeforeExceptionIsRolledBack() {
        FakeBackend backend = new FakeBackend() {
            @Override public void writeArray(String cfg, String name, double[][] values) {
                if (writeCount == 0) {
                    writeCount++;
                    arrays.get(key(cfg, name))[0][0] = values[0][0];
                    throw new IllegalStateException("partial array update");
                }
                super.writeArray(cfg, name, values);
            }
        };
        backend.arrays.put(key("cfg", "table"), new double[][]{{1.0, 2.0, 9.0}});
        ProposalWritePlan plan = new ProposalWritePlan("array", "Array test", "cfg", "",
                Arrays.asList(
                        ProposalWritePlan.Change.arrayCell("table", 0, 1.0, 4.0, "cell 0", ""),
                        ProposalWritePlan.Change.arrayCell("table", 1, 2.0, 5.0, "cell 1", "")));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
        require(!result.success && result.message.contains("rollback PASS"), "partial update was not recovered");
        double[][] actual = backend.readArray("cfg", "table");
        requireClose(1.0, actual[0][0], "partial mutation escaped rollback");
        requireClose(2.0, actual[0][1], "unwritten target changed");
        requireClose(9.0, actual[0][2], "undeclared cell changed");
        require(coordinator.applyDepth() == 0, "partial failure created restore history");
    }

    private static void rollbackFailureDoesNotSkipOtherParameters() {
        final StringBuilder writes = new StringBuilder();
        FakeBackend backend = new FakeBackend() {
            @Override public void writeScalar(String cfg, String name, double value) {
                writes.append(name).append(':').append(value).append(';');
                if (name.equals("second") && value == 4.0) {
                    throw new IllegalStateException("rollback unavailable");
                }
                super.writeScalar(cfg, name, value);
                if (name.equals("second")) throw new IllegalStateException("mutation before failure");
            }
        };
        backend.scalars.put(key("cfg", "first"), 3.0);
        backend.scalars.put(key("cfg", "second"), 4.0);
        backend.scalars.put(key("cfg", "third"), 5.0);
        java.util.List<ProposalWritePlan.Change> changes =
                new java.util.ArrayList<ProposalWritePlan.Change>(twoScalarPlan().getChanges());
        changes.add(ProposalWritePlan.Change.scalar("third", 5.0, 10.0, "third", ""));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(
                new ProposalWritePlan("multi", "Transaction test", "cfg", "", changes));
        require(!result.success, "injected failure should fail Apply");
        requireClose(3.0, backend.readScalar("cfg", "first"), "one rollback failure skipped earlier parameter");
        requireClose(8.0, backend.readScalar("cfg", "second"), "fixture lost unresolved parameter");
        require(result.message.contains("ROLLBACK COULD NOT BE VERIFIED") && result.message.contains("second"),
                "unresolved parameter was not identified: " + result.message);
        require(!result.message.contains("rollback PASS"), "unresolved rollback reported success");
        require(coordinator.applyDepth() == 0, "failed Apply created history");
        requireClose(5.0, backend.readScalar("cfg", "third"), "unattempted parameter changed");
        require(writes.toString().equals("first:6.0;second:8.0;second:4.0;first:3.0;"),
                "rollback order or attempted-target scope changed: " + writes);
    }

    private static void rollbackReadFailureDoesNotSkipOtherVerification() {
        final boolean[] secondVerified = {false};
        FakeBackend backend = new FakeBackend() {
            @Override public void writeScalar(String cfg, String name, double value) {
                super.writeScalar(cfg, name, value);
                if (writeCount == 2) throw new IllegalStateException("mutation before failure");
            }
            @Override public double readScalar(String cfg, String name) {
                if (writeCount >= 4) {
                    if (name.equals("first")) throw new IllegalStateException("readback unavailable");
                    if (name.equals("second")) secondVerified[0] = true;
                }
                return super.readScalar(cfg, name);
            }
        };
        backend.scalars.put(key("cfg", "first"), 3.0);
        backend.scalars.put(key("cfg", "second"), 4.0);
        ProposalApplyCoordinator.ApplyResult result = new ProposalApplyCoordinator(backend).apply(twoScalarPlan());
        require(secondVerified[0], "one failed readback skipped remaining verification");
        require(result.message.contains("ROLLBACK COULD NOT BE VERIFIED") && result.message.contains("first"),
                "unreadable parameter was not reported: " + result.message);
        requireClose(3.0, backend.scalars.get(key("cfg", "first")), "first rollback was not attempted");
        requireClose(4.0, backend.scalars.get(key("cfg", "second")), "second rollback was not attempted");
    }

    private static void rollbackExceptionWithMatchingReadbackIsVerified() {
        FakeBackend backend = new FakeBackend() {
            @Override public void writeScalar(String cfg, String name, double value) {
                super.writeScalar(cfg, name, value);
                throw new IllegalStateException("host exception after mutation");
            }
        };
        backend.scalars.put(key("cfg", "first"), 3.0);
        ProposalApplyCoordinator.ApplyResult result = new ProposalApplyCoordinator(backend).apply(scalarPlan());
        require(!result.success && result.message.contains("rollback PASS"),
                "matching final readback should establish recovery despite host exception: " + result.message);
        require(result.message.contains("first"), "rollback write exception lost its parameter context");
        requireClose(3.0, backend.readScalar("cfg", "first"), "baseline was not recovered");
    }

    private static void failedRestoreRecoversAppliedStateAndRetainsHistory() {
        FakeBackend backend = new FakeBackend() {
            @Override public void writeScalar(String cfg, String name, double value) {
                super.writeScalar(cfg, name, value);
                if (writeCount == 2) throw new IllegalStateException("Restore mutation before failure");
            }
        };
        backend.scalars.put(key("cfg", "first"), 3.0);
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        require(coordinator.apply(scalarPlan()).success, "setup Apply failed");
        ProposalApplyCoordinator.ApplyResult result = coordinator.restorePreviousApply();
        require(!result.success && result.restore && result.message.contains("rollback PASS"),
                "failed Restore was not rolled back");
        requireClose(6.0, backend.readScalar("cfg", "first"), "failed Restore did not recover applied state");
        require(coordinator.applyDepth() == 1, "failed Restore discarded its record");
        require(coordinator.restorePreviousApply().success, "explicit retry after verified rollback should restore");
        requireClose(3.0, backend.readScalar("cfg", "first"), "successful Restore lost original baseline");
        require(coordinator.applyDepth() == 0, "successful Restore retained stale history");
    }

    private static class FakeBackend
            implements ProposalApplyCoordinator.Backend {
        final Map<String, double[][]> arrays =
                new LinkedHashMap<String, double[][]>();
        final Map<String, Double> scalars =
                new LinkedHashMap<String, Double>();
        int writeCount;
        int arrayReadCount;
        int distortReadNumber;
        String distortParameter = "";
        int distortFlatIndex = -1;
        double distortValue;
        String failParameter = "";

        @Override
        public double readScalar(String configurationName, String parameterName) {
            Double value = scalars.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing scalar");
            return value.doubleValue();
        }

        @Override
        public double[][] readArray(String configurationName, String parameterName) {
            double[][] value = arrays.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing array");
            arrayReadCount++;
            double[][] copy = cloneTable(value);
            if (arrayReadCount == distortReadNumber
                    && parameterName.equals(distortParameter)) {
                setFlat(copy, distortFlatIndex, distortValue);
            }
            return copy;
        }

        @Override
        public void writeScalar(String configurationName, String parameterName,
                                double value) {
            if (parameterName.equals(failParameter)) {
                failParameter = "";
                throw new IllegalStateException("injected write failure");
            }
            writeCount++;
            scalars.put(key(configurationName, parameterName), Double.valueOf(value));
        }

        @Override
        public void writeArray(String configurationName, String parameterName,
                               double[][] values) {
            if (parameterName.equals(failParameter)) {
                failParameter = "";
                throw new IllegalStateException("injected write failure");
            }
            writeCount++;
            arrays.put(key(configurationName, parameterName), cloneTable(values));
        }
    }

    private static String key(String configurationName, String parameterName) {
        return configurationName + "::" + parameterName;
    }

    private static void setFlat(double[][] values, int targetIndex, double value) {
        int index = 0;
        for (int row = 0; row < values.length; row++) {
            for (int column = 0; column < values[row].length; column++) {
                if (index == targetIndex) {
                    values[row][column] = value;
                    return;
                }
                index++;
            }
        }
        throw new IllegalArgumentException("flat index outside test array: " + targetIndex);
    }

    private static double[][] cloneTable(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i] == null ? new double[0] : values[i].clone();
        }
        return copy;
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
