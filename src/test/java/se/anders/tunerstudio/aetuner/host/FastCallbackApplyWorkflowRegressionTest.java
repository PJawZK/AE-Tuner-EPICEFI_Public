package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.EngagementDetectionSettingProposal;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Arrays;
import java.util.List;

/** Qualification contract for tpsAeFastCallback as a one-bit TunerStudio selection. */
public final class FastCallbackApplyWorkflowRegressionTest {
    private FastCallbackApplyWorkflowRegressionTest() { }

    public static void main(String[] args) {
        proposalUsesLogicalZeroOneOnly();
        liveBitSelectionApplyRestoreRoundTrip();
        staleLiveBitSelectionBaselineBlocksBeforeWrite();
        System.out.println("FastCallbackApplyWorkflowRegressionTest passed");
    }

    private static void proposalUsesLogicalZeroOneOnly() {
        AeProjectSnapshot snapshot = snapshot(true);
        require(EngagementDetectionSettingProposal.fastCallback(snapshot, true) == null,
                "unchanged Fast Callback must not create a proposal");
        ProposalWritePlan plan = EngagementDetectionSettingProposal.fastCallback(snapshot, false);
        require(plan != null && plan.changeCount() == 1,
                "ON -> OFF should create exactly one Fast Callback change");
        ProposalWritePlan.Change change = plan.getChanges().get(0);
        require(AeParameterNames.TPS_AE_FAST_CALLBACK.equals(change.parameterName),
                "Fast Callback plan targeted wrong parameter");
        requireClose(1.0, change.expectedValue, "Fast Callback baseline");
        requireClose(0.0, change.proposedValue, "Fast Callback request");
        require(plan.getContext().contains("ON -> OFF")
                        && plan.getContext().contains("live bit-selection option contract"),
                "Fast Callback plan lost representation/safety context");
    }

    /** Live option strings deliberately do not resemble ON/OFF. */
    private static void liveBitSelectionApplyRestoreRoundTrip() {
        FakeBackend backend = new FakeBackend(1);
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = EngagementDetectionSettingProposal.fastCallback(
                snapshot(true), false);

        ProposalApplyCoordinator.ApplyResult applied = coordinator.apply(plan);
        require(applied.success,
                "Fast Callback bit selection did not Apply/readback: " + applied.message);
        require(backend.currentIndex == 0,
                "Fast Callback did not switch to logical option index 0 / OFF");
        require(backend.options.get(0).equals(backend.option),
                "Fast Callback did not use exact live TunerStudio OFF option");
        require(backend.optionWriteCount == 1,
                "one Fast Callback change should issue one option write");
        require(backend.scalarReadCount == 0 && backend.scalarWriteCount == 0,
                "Fast Callback must never use scalar access to the shared U32 bit word");

        ProposalApplyCoordinator.ApplyResult restored = coordinator.restorePreviousApply();
        require(restored.success && restored.restore,
                "Fast Callback Restore failed: " + restored.message);
        require(backend.currentIndex == 1,
                "Fast Callback Restore did not recover logical option index 1 / ON");
        require(backend.options.get(1).equals(backend.option),
                "Restore did not use exact live TunerStudio ON option");
        require(backend.optionWriteCount == 2,
                "Fast Callback Apply + Restore should issue two option writes");
        require(backend.scalarReadCount == 0 && backend.scalarWriteCount == 0,
                "Fast Callback Restore must not touch the containing scalar word");
    }

    private static void staleLiveBitSelectionBaselineBlocksBeforeWrite() {
        FakeBackend backend = new FakeBackend(0);
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = EngagementDetectionSettingProposal.fastCallback(
                snapshot(true), false);

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
        require(!result.success, "stale Fast Callback baseline must block Apply");
        require(backend.optionWriteCount == 0,
                "stale Fast Callback proposal wrote before preflight");
        require(backend.currentIndex == 0,
                "stale Fast Callback rejection altered the live option");
        require(backend.scalarReadCount == 0 && backend.scalarWriteCount == 0,
                "Fast Callback stale check must use live bit-selection API");
    }

    private static AeProjectSnapshot snapshot(boolean fastCallback) {
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
                "Dual stride, newest", 25.0, 0.050, fastCallback, 0.10);
    }

    private static final class FakeBackend implements ProposalApplyCoordinator.Backend {
        final List<String> options = Arrays.asList("TS-SLOW-CALLBACK", "TS-FAST-CALLBACK");
        String option;
        int currentIndex;
        int optionWriteCount;
        int scalarReadCount;
        int scalarWriteCount;

        FakeBackend(int initialIndex) {
            currentIndex = initialIndex;
            option = options.get(initialIndex);
        }

        @Override public double readScalar(String configurationName, String parameterName) {
            scalarReadCount++;
            throw new IllegalStateException(
                    "scalar read is forbidden for Fast Callback qualification");
        }

        @Override public String readOption(String configurationName, String parameterName) {
            require(AeParameterNames.TPS_AE_FAST_CALLBACK.equals(parameterName),
                    "unexpected option-read parameter " + parameterName);
            return option;
        }

        @Override public List<String> readOptionDescriptions(
                String configurationName, String parameterName) {
            require(AeParameterNames.TPS_AE_FAST_CALLBACK.equals(parameterName),
                    "unexpected option-list parameter " + parameterName);
            return options;
        }

        @Override public double[][] readArray(String configurationName, String parameterName) {
            throw new IllegalStateException("array access not expected");
        }

        @Override public void writeScalar(String configurationName, String parameterName,
                                          double value) {
            scalarWriteCount++;
            throw new IllegalStateException(
                    "scalar write is forbidden for Fast Callback qualification");
        }

        @Override public void writeOptionIndex(String configurationName, String parameterName,
                                               int optionIndex) {
            require(AeParameterNames.TPS_AE_FAST_CALLBACK.equals(parameterName),
                    "unexpected option-write parameter " + parameterName);
            require(optionIndex == 0 || optionIndex == 1,
                    "Fast Callback emitted non-boolean option index " + optionIndex);
            optionWriteCount++;
            currentIndex = optionIndex;
            option = options.get(optionIndex);
        }

        @Override public void writeArray(String configurationName, String parameterName,
                                         double[][] values) {
            throw new IllegalStateException("array write not expected");
        }
    }

    private static void requireClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
