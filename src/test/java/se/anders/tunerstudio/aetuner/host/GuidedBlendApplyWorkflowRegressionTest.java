package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedBlendProposalTestSupport;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cross-layer regression for the real planned vehicle workflow:
 * Guided Blend proposal -> generic write plan -> apply -> next-point apply ->
 * LIFO restore. Only the TunerStudio parameter backend is faked.
 */
public final class GuidedBlendApplyWorkflowRegressionTest {
    private static final String CFG = "Main Controller";
    private static final String BLEND = AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES;

    private GuidedBlendApplyWorkflowRegressionTest() { }

    public static void main(String[] args) {
        sequential600Then2450UsesRealProposalPlansAndRestoresExactly();
        System.out.println("GuidedBlendApplyWorkflowRegressionTest passed");
    }

    private static void sequential600Then2450UsesRealProposalPlansAndRestoresExactly() {
        FakeBackend backend = new FakeBackend();
        backend.arrays.put(key(CFG, BLEND),
                new double[][]{{0.18, 0.26, 0.22, 0.18}});
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);

        ProposalWritePlan at600 = GuidedBlendProposalTestSupport.buildWritePlan(
                snapshot(new double[]{0.18, 0.26, 0.22, 0.18}),
                0,
                Double.valueOf(0.20), Double.valueOf(0.21), Double.valueOf(0.22));
        require(at600.changeCount() == 1,
                "600 RPM Guided proposal did not declare exactly one target");
        ProposalWritePlan.Change change600 = at600.getChanges().get(0);
        require(change600.flatIndex == 0,
                "600 RPM Guided proposal targeted the wrong Blend Duration cell");
        requireClose(0.18, change600.expectedValue,
                "600 RPM proposal lost its working-tune baseline");
        requireClose(0.24, change600.proposedValue,
                "600 RPM proposal did not preserve margin plus 0.02-second controller-grid ceiling");
        require(coordinator.apply(at600).success,
                "600 RPM Guided proposal failed generic apply");
        assertCurve(backend, 0.24, 0.26, 0.22, 0.18,
                "600 RPM apply changed the wrong working-tune values");

        // The second proposal is built from a fresh snapshot containing the
        // first explicit apply, matching GuidedCapturePanel's new-session rule.
        ProposalWritePlan at2450 = GuidedBlendProposalTestSupport.buildWritePlan(
                snapshot(new double[]{0.24, 0.26, 0.22, 0.18}),
                1,
                Double.valueOf(0.50), Double.valueOf(0.52), Double.valueOf(0.54));
        ProposalWritePlan.Change change2450 = at2450.getChanges().get(0);
        require(change2450.flatIndex == 1,
                "2450 RPM Guided proposal targeted the wrong Blend Duration cell");
        requireClose(0.26, change2450.expectedValue,
                "2450 RPM proposal lost its freshly-read baseline");
        requireClose(0.54, change2450.proposedValue,
                "2450 RPM proposal produced the wrong representable final value");
        require(coordinator.apply(at2450).success,
                "2450 RPM Guided proposal failed generic apply");
        assertCurve(backend, 0.24, 0.54, 0.22, 0.18,
                "2450 RPM apply damaged another Blend Duration point");
        require(coordinator.applyDepth() == 2,
                "sequential Guided applies did not retain both restore records");
        require(at2450.verificationManifestJson().contains("\"index\": 1")
                        && at2450.verificationManifestJson().contains("\"before\": 0.26")
                        && at2450.verificationManifestJson().contains("\"after\": 0.54"),
                "2450 RPM apply manifest lost exact MSQ allowlist semantics");

        ProposalWritePlan restore2450 = coordinator.previousApplyPlan().reversed(
                "Restore 2450 RPM");
        require(coordinator.restorePreviousApply().success,
                "2450 RPM restore failed");
        assertCurve(backend, 0.24, 0.26, 0.22, 0.18,
                "2450 RPM restore damaged the retained 600 RPM apply");
        require(restore2450.verificationManifestJson().contains("\"before\": 0.54")
                        && restore2450.verificationManifestJson().contains("\"after\": 0.26"),
                "restore manifest did not reverse the 2450 RPM allowlist");

        require(coordinator.restorePreviousApply().success,
                "600 RPM restore failed");
        assertCurve(backend, 0.18, 0.26, 0.22, 0.18,
                "final restore did not recover the original Blend Duration curve");
        require(coordinator.applyDepth() == 0,
                "restore history was not empty after reversing both explicit applies");
        require(backend.writeCount == 4,
                "two applies plus two restores should issue exactly four array updates");
    }

    private static AeProjectSnapshot snapshot(double[] blendValues) {
        return new AeProjectSnapshot(
                CFG,
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0,
                new double[0], new double[0],
                false, false, "off", false, true,
                false, false,
                new double[0][0], new double[0][0],
                new double[]{1000.0}, new double[]{10.0},
                new double[][]{{50.0}},
                new double[]{600.0, 2450.0, 4350.0, 6200.0},
                blendValues);
    }

    private static void assertCurve(FakeBackend backend,
                                    double v600, double v2450,
                                    double v4350, double v6200,
                                    String message) {
        double[][] curve = backend.arrays.get(key(CFG, BLEND));
        if (curve == null || curve.length != 1 || curve[0].length != 4) {
            throw new AssertionError(message + ": curve shape changed");
        }
        requireClose(v600, curve[0][0], message + " at 600 RPM");
        requireClose(v2450, curve[0][1], message + " at 2450 RPM");
        requireClose(v4350, curve[0][2], message + " at 4350 RPM");
        requireClose(v6200, curve[0][3], message + " at 6200 RPM");
    }

    private static final class FakeBackend
            implements ProposalApplyCoordinator.Backend {
        final Map<String, double[][]> arrays =
                new LinkedHashMap<String, double[][]>();
        int writeCount;

        @Override
        public double readScalar(String configurationName, String parameterName) {
            throw new IllegalStateException("no scalar parameters expected");
        }

        @Override
        public double[][] readArray(String configurationName, String parameterName) {
            double[][] value = arrays.get(key(configurationName, parameterName));
            if (value == null) {
                throw new IllegalStateException("missing array " + parameterName);
            }
            return cloneTable(value);
        }

        @Override
        public void writeScalar(String configurationName, String parameterName,
                                double value) {
            throw new IllegalStateException("no scalar writes expected");
        }

        @Override
        public void writeArray(String configurationName, String parameterName,
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
