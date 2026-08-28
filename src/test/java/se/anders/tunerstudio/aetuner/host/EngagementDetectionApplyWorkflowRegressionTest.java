package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.EngagementDetectionSettingProposal;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dev20 qualification gate for the first Engagement / Detection controller
 * representation: ordinary scalar Delta Window.
 */
public final class EngagementDetectionApplyWorkflowRegressionTest {
    private EngagementDetectionApplyWorkflowRegressionTest() { }

    public static void main(String[] args) {
        deltaWindowPlanIsExactAndNotAutomatic();
        scalarApplyReadbackRestoreRoundTrip();
        staleDeltaWindowBaselineBlocksBeforeWrite();
        System.out.println("EngagementDetectionApplyWorkflowRegressionTest passed");
    }

    private static void deltaWindowPlanIsExactAndNotAutomatic() {
        AeProjectSnapshot snapshot = snapshot(25.0);
        require(EngagementDetectionSettingProposal.deltaWindow(snapshot, 25.0) == null,
                "unchanged Delta Window must not create an Apply plan");

        ProposalWritePlan plan =
                EngagementDetectionSettingProposal.deltaWindow(snapshot, 24.0);
        require(plan != null && plan.changeCount() == 1,
                "explicit temporary Delta Window should create exactly one change");
        ProposalWritePlan.Change change = plan.getChanges().get(0);
        require(change.kind == ProposalWritePlan.Kind.SCALAR,
                "Delta Window qualification must use scalar controller representation");
        require(AeParameterNames.TPS_AE_DELTA_WINDOW_MS.equals(change.parameterName),
                "Delta Window plan targeted the wrong controller parameter");
        requireClose(25.0, change.expectedValue,
                "plan lost the exact working-tune baseline");
        requireClose(24.0, change.proposedValue,
                "plan lost the explicit requested temporary value");
        require(plan.getContext().contains("not an automatic tuning recommendation"),
                "write qualification was incorrectly presented as a tuning recommendation");
        require(!plan.verificationManifestJson().toLowerCase(java.util.Locale.ROOT)
                        .contains("burn"),
                "verification manifest must not contain burn authority");
    }

    private static void scalarApplyReadbackRestoreRoundTrip() {
        FakeBackend backend = new FakeBackend();
        backend.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(25.0));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = EngagementDetectionSettingProposal.deltaWindow(
                snapshot(25.0), 24.0);

        ProposalApplyCoordinator.ApplyResult applied = coordinator.apply(plan);
        require(applied.success,
                "valid Delta Window scalar plan did not Apply/readback successfully: "
                        + applied.message);
        requireClose(24.0, backend.scalar(AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                "Delta Window working value did not change");
        require(backend.writeCount == 1,
                "one-scalar detector proposal should issue exactly one write");
        require(applied.message.contains("read-back PASS")
                        && applied.message.contains("no burn"),
                "Apply result did not preserve readback/no-burn contract");
        require(coordinator.applyDepth() == 1,
                "successful detector Apply did not create one Restore record");

        ProposalApplyCoordinator.ApplyResult restored = coordinator.restorePreviousApply();
        require(restored.success && restored.restore,
                "Delta Window Restore failed: " + restored.message);
        requireClose(25.0, backend.scalar(AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                "Restore did not recover exact Delta Window baseline");
        require(backend.writeCount == 2,
                "Apply plus Restore should issue two scalar writes total");
        require(coordinator.applyDepth() == 0,
                "successful Restore did not clear detector write history");
    }

    private static void staleDeltaWindowBaselineBlocksBeforeWrite() {
        FakeBackend backend = new FakeBackend();
        backend.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(26.0));
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan stale = EngagementDetectionSettingProposal.deltaWindow(
                snapshot(25.0), 24.0);

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(stale);
        require(!result.success,
                "stale detector baseline must block Apply");
        require(backend.writeCount == 0,
                "stale detector plan wrote before baseline validation");
        requireClose(26.0, backend.scalar(AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                "stale rejection changed the working tune");
    }

    private static AeProjectSnapshot snapshot(double deltaWindowMs) {
        return new AeProjectSnapshot(
                "cfg",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0,
                new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", deltaWindowMs, 0.050, true, 0.10);
    }

    private static final class FakeBackend
            implements ProposalApplyCoordinator.Backend {
        final Map<String, Double> scalars = new LinkedHashMap<String, Double>();
        int writeCount;

        @Override
        public double readScalar(String configurationName, String parameterName) {
            Double value = scalars.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing scalar " + parameterName);
            return value.doubleValue();
        }

        @Override
        public double[][] readArray(String configurationName, String parameterName) {
            throw new IllegalStateException("array access is not expected in scalar qualification");
        }

        @Override
        public void writeScalar(String configurationName, String parameterName, double value) {
            writeCount++;
            scalars.put(key(configurationName, parameterName), Double.valueOf(value));
        }

        @Override
        public void writeArray(String configurationName, String parameterName, double[][] values) {
            throw new IllegalStateException("array write is not expected in scalar qualification");
        }

        double scalar(String parameterName) {
            return readScalar("cfg", parameterName);
        }
    }

    private static String key(String configurationName, String parameterName) {
        return configurationName + "::" + parameterName;
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
