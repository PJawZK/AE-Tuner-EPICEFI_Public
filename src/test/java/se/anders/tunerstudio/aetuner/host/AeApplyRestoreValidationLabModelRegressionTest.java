package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AeApplyRestoreValidationLabModelRegressionTest {
    private AeApplyRestoreValidationLabModelRegressionTest() { }

    public static void main(String[] args) throws Exception {
        auditedTargetCountAndSharedTaskAliasesRemainStable();
        skipCanBeRecordedWithoutControllerMutation();
        passRequiresVerifiedApplyAndRestoreRoundTrip();
        completedEvidenceSurvivesBrowsingAndResetsOnlyOnRetest();
        failedApplyCannotBeMarkedPass();
        System.out.println("AeApplyRestoreValidationLabModelRegressionTest passed");
    }

    private static void auditedTargetCountAndSharedTaskAliasesRemainStable() {
        Fixture fixture = new Fixture();
        AeApplyRestoreValidationLabModel model = fixture.model;
        require(model.totalCount() == 816,
                "validation Lab model lost audited 816-target physical inventory");
        AeApplyRestoreValidationTargets.Target shared =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_CYCLE_CYCLE_BINS, 0);
        require(shared != null, "shared TPS AE cycle-axis physical target missing");
        require(model.targetsForTask(GuidedTuningRecipe.TPS_AE).contains(shared),
                "shared physical target missing from Fuel by Engine Cycle task");
        require(model.targetsForTask(GuidedTuningRecipe.TPS_AE_COMPLETION).contains(shared),
                "shared physical target missing from Completion task");
        require(model.recordFor(shared) == model.recordFor(shared),
                "shared task aliases must resolve one physical result record");
    }

    private static void skipCanBeRecordedWithoutControllerMutation() throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);
        AeApplyRestoreValidationLabModel.Record record = fixture.model.select(target);
        requireClose(50.0, record.getOriginalValue(), "Lab did not capture original before SKIP");
        fixture.model.markSelected(
                AeApplyRestoreValidationLabModel.ValidationStatus.SKIP,
                "physical test deferred");
        require(record.getStatus() == AeApplyRestoreValidationLabModel.ValidationStatus.SKIP,
                "SKIP status was not retained");
        require("physical test deferred".equals(record.getNote()),
                "SKIP note was not retained");
        require(fixture.io.writeCount == 0,
                "recording SKIP must not mutate the working tune");
    }

    private static void passRequiresVerifiedApplyAndRestoreRoundTrip() throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);
        AeApplyRestoreValidationLabModel.Record record = fixture.model.select(target);
        fixture.model.setTemporaryValue(60.0);
        require(fixture.model.applyTemporary().success,
                "Lab model failed valid temporary Apply");
        requireThrows(() -> fixture.model.markSelected(
                        AeApplyRestoreValidationLabModel.ValidationStatus.PASS, "too early"),
                "Lab allowed PASS while temporary value remained applied");
        require(fixture.model.restoreOriginal().success,
                "Lab model failed exact original Restore");
        fixture.model.markSelected(
                AeApplyRestoreValidationLabModel.ValidationStatus.PASS,
                "physical round trip verified");
        require(record.getStatus() == AeApplyRestoreValidationLabModel.ValidationStatus.PASS,
                "verified round trip did not permit PASS");
        requireClose(60.0, record.getApplyReadback(),
                "Lab did not retain Apply readback");
        requireClose(50.0, record.getRestoreReadback(),
                "Lab did not retain Restore readback");
        require(fixture.model.summaryText().contains("PASS 1"),
                "Lab summary did not update PASS count");
    }

    private static void completedEvidenceSurvivesBrowsingAndResetsOnlyOnRetest()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_BURN_SKIP_INITIAL),
                Double.valueOf(2.0));
        AeApplyRestoreValidationTargets.Target first =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);
        AeApplyRestoreValidationTargets.Target second =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_BURN_SKIP_INITIAL, -1);

        AeApplyRestoreValidationLabModel.Record record = fixture.model.select(first);
        fixture.model.setTemporaryValue(60.0);
        require(fixture.model.applyTemporary().success, "setup Apply failed");
        require(fixture.model.restoreOriginal().success, "setup Restore failed");
        fixture.model.markSelected(
                AeApplyRestoreValidationLabModel.ValidationStatus.PASS, "keep me");

        fixture.model.select(second);
        fixture.model.select(first);
        require(record.getStatus() == AeApplyRestoreValidationLabModel.ValidationStatus.PASS,
                "browsing back to completed target erased PASS status");
        require("keep me".equals(record.getNote()),
                "browsing back to completed target erased note");
        requireClose(50.0, record.getOriginalValue(),
                "browsing back erased recorded original");
        requireClose(60.0, record.getTemporaryValue(),
                "browsing back erased recorded temporary value");
        requireClose(60.0, record.getApplyReadback(),
                "browsing back erased Apply readback");
        requireClose(50.0, record.getRestoreReadback(),
                "browsing back erased Restore readback");

        fixture.model.setTemporaryValue(70.0);
        require(record.getStatus() == AeApplyRestoreValidationLabModel.ValidationStatus.NOT_TESTED,
                "starting an explicit re-test did not reset prior PASS status");
        require(record.getNote().length() == 0,
                "starting an explicit re-test did not clear prior note");
        require(!Double.isFinite(record.getApplyReadback())
                        && !Double.isFinite(record.getRestoreReadback()),
                "starting an explicit re-test retained stale old readbacks");
        requireClose(50.0, record.getOriginalValue(),
                "re-test did not capture current original from engine");
        requireClose(70.0, record.getTemporaryValue(),
                "re-test did not retain new temporary value");
    }

    private static void failedApplyCannotBeMarkedPass() throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);
        AeApplyRestoreValidationLabModel.Record record = fixture.model.select(target);
        fixture.model.setTemporaryValue(60.0);
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(55.0));
        require(!fixture.model.applyTemporary().success,
                "stale Lab Apply unexpectedly succeeded");
        require(record.getStatus() == AeApplyRestoreValidationLabModel.ValidationStatus.FAIL,
                "failed Apply did not mark physical target FAIL");
        requireThrows(() -> fixture.model.markSelected(
                        AeApplyRestoreValidationLabModel.ValidationStatus.PASS, "override"),
                "failed physical Apply could be manually overwritten to PASS without round trip");
    }

    private static final class Fixture {
        final FakeIo io = new FakeIo();
        final ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(io);
        final AeApplyRestoreValidationEngine engine =
                new AeApplyRestoreValidationEngine(io, coordinator, "cfg");
        final AeApplyRestoreValidationLabModel model =
                new AeApplyRestoreValidationLabModel(engine);
    }

    private static final class FakeIo
            implements ProposalApplyCoordinator.Backend,
                       AeApplyRestoreValidationEngine.ValueReader {
        final Map<String, Double> scalars = new LinkedHashMap<String, Double>();
        final Map<String, double[][]> arrays = new LinkedHashMap<String, double[][]>();
        int writeCount;

        @Override
        public double read(String configurationName,
                           AeApplyRestoreValidationTargets.Target target) {
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
