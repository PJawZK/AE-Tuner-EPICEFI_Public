package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;
import se.anders.tunerstudio.aetuner.proposal.EngagementDetectionSettingProposal;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Arrays;
import java.util.List;

/** Qualification contract for tpsAeDetectMode as a TunerStudio bit selection. */
public final class EngagementModelApplyWorkflowRegressionTest {
    private EngagementModelApplyWorkflowRegressionTest() { }

    public static void main(String[] args) {
        enumMappingMatchesCurrentIni();
        enumPlanIsExactAndNotAutomatic();
        liveBitSelectionApplyRestoreRoundTrip();
        staleLiveBitSelectionBaselineBlocksBeforeWrite();
        System.out.println("EngagementModelApplyWorkflowRegressionTest passed");
    }

    private static void enumMappingMatchesCurrentIni() {
        require(EngagementModelOption.MAX_STEP_LEGACY.controllerValue() == 0,
                "legacy model code changed");
        require(EngagementModelOption.MAX_STEP_TIMED.controllerValue() == 1,
                "timed model code changed");
        require(EngagementModelOption.WINDOW_SPAN.controllerValue() == 2,
                "span model code changed");
        require(EngagementModelOption.RISE_FROM_FLOOR.controllerValue() == 3,
                "floor model code changed");
        require(EngagementModelOption.DUAL_STRIDE_NEWEST.controllerValue() == 4,
                "Dual stride model code changed");
        require(EngagementModelOption.fromControllerText("Dual stride, newest")
                        == EngagementModelOption.DUAL_STRIDE_NEWEST,
                "Dual stride label did not resolve");
        require(EngagementModelOption.fromControllerText("1")
                        == EngagementModelOption.MAX_STEP_TIMED,
                "numeric enum text did not resolve");
        require(EngagementModelOption.fromControllerValue(5) == null,
                "invalid INI enum slot unexpectedly became selectable");
    }

    private static void enumPlanIsExactAndNotAutomatic() {
        AeProjectSnapshot snapshot = snapshot();
        require(EngagementDetectionSettingProposal.engagementModel(
                        snapshot, EngagementModelOption.DUAL_STRIDE_NEWEST) == null,
                "unchanged Engagement Model must not create a proposal");

        ProposalWritePlan plan = EngagementDetectionSettingProposal.engagementModel(
                snapshot, EngagementModelOption.MAX_STEP_TIMED);
        require(plan != null && plan.changeCount() == 1,
                "temporary Timed model should create exactly one change");
        ProposalWritePlan.Change change = plan.getChanges().get(0);
        require(change.kind == ProposalWritePlan.Kind.SCALAR,
                "enum remains a numeric named target at proposal layer");
        require(AeParameterNames.TPS_AE_DETECT_MODE.equals(change.parameterName),
                "enum plan targeted the wrong parameter");
        requireClose(4.0, change.expectedValue, "enum baseline code");
        requireClose(1.0, change.proposedValue, "enum requested code");
        require(plan.getContext().contains("Dual stride, newest (4)")
                        && plan.getContext().contains("Max step, timed (1)")
                        && plan.getContext().contains("not an automatic tuning recommendation"),
                "enum review context lost model meaning or qualification boundary");
    }

    /**
     * The live strings intentionally do NOT match EngagementModelOption displayName().
     * This catches the physical failure where AE Tuner synthesized a label rather
     * than passing back one of ControllerParameter.getOptionDescriptions().
     */
    private static void liveBitSelectionApplyRestoreRoundTrip() {
        FakeBackend backend = new FakeBackend(4);
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = EngagementDetectionSettingProposal.engagementModel(
                snapshot(), EngagementModelOption.MAX_STEP_TIMED);

        ProposalApplyCoordinator.ApplyResult applied = coordinator.apply(plan);
        require(applied.success,
                "live bit-selection enum did not Apply/readback: " + applied.message);
        require(backend.currentIndex == 1,
                "enum live option did not change to index 1 / Timed");
        require(backend.options.get(1).equals(backend.option),
                "coordinator did not use the exact live TunerStudio Timed option");
        require(backend.optionWriteCount == 1,
                "one enum change should issue one indexed bit-option write");
        require(backend.scalarReadCount == 0 && backend.scalarWriteCount == 0,
                "Engagement Model must never use scalar read/write API");

        ProposalApplyCoordinator.ApplyResult restored = coordinator.restorePreviousApply();
        require(restored.success && restored.restore,
                "enum Restore failed: " + restored.message);
        require(backend.currentIndex == 4,
                "Restore did not recover live option index 4 / Dual stride");
        require(backend.options.get(4).equals(backend.option),
                "Restore did not use the exact live TunerStudio Dual stride option");
        require(backend.optionWriteCount == 2,
                "enum Apply + Restore should issue two indexed bit-option writes");
        require(backend.scalarReadCount == 0 && backend.scalarWriteCount == 0,
                "Restore must not fall back to scalar bitfield access");
    }

    private static void staleLiveBitSelectionBaselineBlocksBeforeWrite() {
        FakeBackend backend = new FakeBackend(3);
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan plan = EngagementDetectionSettingProposal.engagementModel(
                snapshot(), EngagementModelOption.MAX_STEP_TIMED);

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
        require(!result.success, "stale enum baseline must block Apply");
        require(backend.optionWriteCount == 0,
                "stale enum proposal wrote before live-option preflight");
        require(backend.currentIndex == 3,
                "stale enum rejection altered the bit selection");
        require(backend.scalarReadCount == 0 && backend.scalarWriteCount == 0,
                "stale check must use live bit-selection API, not scalar access");
    }

    private static AeProjectSnapshot snapshot() {
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
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static final class FakeBackend implements ProposalApplyCoordinator.Backend {
        final List<String> options = Arrays.asList(
                "TS-LIVE-0", "TS-LIVE-1", "TS-LIVE-2", "TS-LIVE-3", "TS-LIVE-4");
        String option;
        int currentIndex;
        int optionReadCount;
        int optionListReadCount;
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
                    "scalar read is forbidden for Engagement Model qualification");
        }

        @Override public String readOption(String configurationName, String parameterName) {
            optionReadCount++;
            require(AeParameterNames.TPS_AE_DETECT_MODE.equals(parameterName),
                    "unexpected option-read parameter " + parameterName);
            return option;
        }

        @Override public List<String> readOptionDescriptions(
                String configurationName, String parameterName) {
            optionListReadCount++;
            require(AeParameterNames.TPS_AE_DETECT_MODE.equals(parameterName),
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
                    "scalar write is forbidden for Engagement Model qualification");
        }

        @Override public void writeOptionIndex(String configurationName, String parameterName,
                                               int optionIndex) {
            require(AeParameterNames.TPS_AE_DETECT_MODE.equals(parameterName),
                    "unexpected option-write parameter " + parameterName);
            require(optionIndex >= 0 && optionIndex < options.size(),
                    "coordinator emitted invalid live option index " + optionIndex);
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
