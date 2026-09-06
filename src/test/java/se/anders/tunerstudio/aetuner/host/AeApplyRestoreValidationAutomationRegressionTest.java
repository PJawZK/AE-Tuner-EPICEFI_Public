package se.anders.tunerstudio.aetuner.host;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AeApplyRestoreValidationAutomationRegressionTest {
    private AeApplyRestoreValidationAutomationRegressionTest() { }

    public static void main(String[] args) throws Exception {
        automaticValuesUseNearestExactRepresentableAlternative();
        successfulAutomaticRoundTripRecordsPassAndRestoresOriginal();
        independentApplyMismatchRecordsFailOnlyAfterSafetyRestore();
        unreadablePrewriteTargetRecordsSkipWithoutMutation();
        restoreFailureHaltsBeforeAnotherTargetCanRun();
        System.out.println("AeApplyRestoreValidationAutomationRegressionTest passed");
    }

    private static void automaticValuesUseNearestExactRepresentableAlternative() {
        requireClose(0.0, auto(AeParameterNames.USE_MAP_ESTIMATE_DURING_TRANSIENT, -1, 1.0),
                "boolean automation did not choose alternate valid option");
        requireClose(0.0, auto(AeParameterNames.WALL_MODEL_TYPE, -1, 1.0),
                "enum automation did not choose alternate valid option");
        requireClose(0.999, auto(AeParameterNames.DELTA_TPS_AVERAGE_ALPHA, -1, 1.0),
                "F32 boundary automation did not reverse by one display step");
        requireClose(0.761, auto(AeParameterNames.TPS_DECEL_THRESHOLD_VALUES, 0, 0.760),
                "scaled U16 automation did not move exactly one storage step");
        requireClose(1.01, auto(AeParameterNames.WALL_TAU_TABLE, 0, 1.0),
                "table-cell automation did not move exactly one storage step");
    }

    private static void successfulAutomaticRoundTripRecordsPassAndRestoresOriginal()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(25.0));
        AeApplyRestoreValidationTargets.Target target = target(
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);

        AeApplyRestoreValidationAutomation.StepResult result =
                fixture.automation.validateOne(target);
        require(result.status == AeApplyRestoreValidationAutomation.StepStatus.PASS,
                "successful automated round trip did not report PASS");
        require(result.safeToContinue,
                "successful automated round trip was not safe to continue");
        requireClose(25.0, fixture.io.scalar(AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                "automatic PASS did not restore exact original working-tune value");
        AeApplyRestoreValidationLabModel.Record record = fixture.model.recordFor(target);
        require(record.getStatus() == AeApplyRestoreValidationLabModel.ValidationStatus.PASS,
                "automatic PASS was not retained in Lab evidence");
        requireClose(26.0, record.getTemporaryValue(),
                "automatic scalar value did not use one exact U08 step");
        requireClose(26.0, record.getApplyReadback(),
                "automatic PASS did not retain Apply readback");
        requireClose(25.0, record.getRestoreReadback(),
                "automatic PASS did not retain Restore readback");
        require(fixture.io.writeCount == 2,
                "automatic PASS must perform exactly one Apply and one Restore write");
    }

    private static void independentApplyMismatchRecordsFailOnlyAfterSafetyRestore()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(25.0));
        fixture.io.corruptIndependentReadAfterFirstWrite = true;
        AeApplyRestoreValidationTargets.Target target = target(
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);

        AeApplyRestoreValidationAutomation.StepResult result =
                fixture.automation.validateOne(target);
        require(result.status == AeApplyRestoreValidationAutomation.StepStatus.FAIL,
                "independent Apply mismatch did not report FAIL");
        require(result.safeToContinue,
                "Apply mismatch with successful safety Restore should be safe to continue");
        require(!fixture.model.isTemporaryApplied(),
                "Apply mismatch safety Restore left temporary state active");
        requireClose(25.0, fixture.io.scalar(AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                "Apply mismatch safety Restore did not recover original value");
        require(fixture.model.recordFor(target).getStatus()
                        == AeApplyRestoreValidationLabModel.ValidationStatus.FAIL,
                "Apply mismatch did not remain recorded FAIL");
    }

    private static void unreadablePrewriteTargetRecordsSkipWithoutMutation() {
        Fixture fixture = new Fixture();
        fixture.io.throwIndependentRead = true;
        AeApplyRestoreValidationTargets.Target target = target(
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);

        AeApplyRestoreValidationAutomation.StepResult result =
                fixture.automation.validateOne(target);
        require(result.status == AeApplyRestoreValidationAutomation.StepStatus.SKIP,
                "unreadable pre-write target did not record SKIP");
        require(result.safeToContinue,
                "pre-write SKIP should permit continuing to next target");
        require(fixture.io.writeCount == 0,
                "pre-write SKIP mutated controller state");
        require(fixture.model.recordFor(target).getStatus()
                        == AeApplyRestoreValidationLabModel.ValidationStatus.SKIP,
                "pre-write SKIP was not retained in Lab evidence");
    }

    private static void restoreFailureHaltsBeforeAnotherTargetCanRun() throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(25.0));
        fixture.io.failWriteNumber = 2;
        AeApplyRestoreValidationTargets.Target target = target(
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);

        AeApplyRestoreValidationAutomation.StepResult result =
                fixture.automation.validateOne(target);
        require(result.status == AeApplyRestoreValidationAutomation.StepStatus.HALT,
                "Restore failure did not halt automation");
        require(!result.safeToContinue,
                "Restore failure incorrectly allowed next target");
        require(fixture.model.isTemporaryApplied(),
                "Restore failure lost lifecycle responsibility for active temporary value");
        require(fixture.model.recordFor(target).getStatus()
                        == AeApplyRestoreValidationLabModel.ValidationStatus.FAIL,
                "Restore failure did not mark current physical target FAIL");
    }

    private static double auto(String controllerName, int flatIndex, double original) {
        return AeValidationValuePolicy.chooseAutomaticTemporaryValue(
                target(controllerName, flatIndex), original);
    }

    private static AeApplyRestoreValidationTargets.Target target(
            String controllerName, int flatIndex) {
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(controllerName, flatIndex);
        if (target == null) throw new AssertionError("missing target " + controllerName);
        return target;
    }

    private static final class Fixture {
        final FakeIo io = new FakeIo();
        final ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(io);
        final AeApplyRestoreValidationEngine engine =
                new AeApplyRestoreValidationEngine(io, coordinator, "cfg");
        final AeApplyRestoreValidationLabModel model =
                new AeApplyRestoreValidationLabModel(engine);
        final AeApplyRestoreValidationAutomation automation =
                new AeApplyRestoreValidationAutomation(model);
    }

    private static final class FakeIo
            implements ProposalApplyCoordinator.Backend,
                       AeApplyRestoreValidationEngine.ValueReader {
        final Map<String, Double> scalars = new LinkedHashMap<String, Double>();
        final Map<String, double[][]> arrays = new LinkedHashMap<String, double[][]>();
        int writeCount;
        int failWriteNumber;
        boolean throwIndependentRead;
        boolean corruptIndependentReadAfterFirstWrite;

        @Override
        public double read(String configurationName,
                           AeApplyRestoreValidationTargets.Target target) {
            if (throwIndependentRead) {
                throw new IllegalStateException("synthetic target read unavailable");
            }
            double value;
            if (target.isIndexed()) {
                value = getFlat(requireArray(configurationName, target.getControllerName()),
                        target.getFlatIndex());
            } else {
                value = requireScalar(configurationName, target.getControllerName());
            }
            if (corruptIndependentReadAfterFirstWrite && writeCount == 1) {
                corruptIndependentReadAfterFirstWrite = false;
                return value + 1.0;
            }
            return value;
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
        public void writeScalar(String configurationName, String parameterName, double value)
                throws Exception {
            writeCount++;
            if (failWriteNumber > 0 && writeCount == failWriteNumber) {
                throw new Exception("synthetic write failure " + writeCount);
            }
            scalars.put(key(configurationName, parameterName), Double.valueOf(value));
        }

        @Override
        public void writeArray(String configurationName, String parameterName,
                               double[][] values) throws Exception {
            writeCount++;
            if (failWriteNumber > 0 && writeCount == failWriteNumber) {
                throw new Exception("synthetic write failure " + writeCount);
            }
            arrays.put(key(configurationName, parameterName), cloneTable(values));
        }

        double scalar(String parameterName) {
            return requireScalar("cfg", parameterName);
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

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
