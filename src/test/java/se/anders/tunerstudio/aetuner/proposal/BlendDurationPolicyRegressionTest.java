package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.Arrays;
import java.util.Collections;

/** Deterministic coverage for the shared passive/guided proposal policy. */
public final class BlendDurationPolicyRegressionTest {
    public static void main(String[] args) {
        insufficientEvidenceStaysWithheld();
        mediumAndHighConfidenceUseTheSameGates();
        wideEvidenceStaysWithheld();
        outlierFilteringUsesRawDurations();
        marginClampAndRoundingAreFinalOnly();
        invalidValuesDoNotCreateEvidence();
        System.out.println("BlendDurationPolicyRegressionTest passed");
    }

    private static void insufficientEvidenceStaysWithheld() {
        BlendDurationPolicy.Evaluation result = BlendDurationPolicy.evaluate(
                Arrays.asList(Double.valueOf(0.50), Double.valueOf(0.52)));
        assertEquals(BlendDurationPolicy.Confidence.INSUFFICIENT,
                result.confidence, "two retained events must be insufficient");
        assertFalse(result.eligible, "insufficient evidence must be withheld");
        assertTrue(Double.isNaN(result.proposedValue),
                "withheld evidence must not expose a final proposal");
    }

    private static void mediumAndHighConfidenceUseTheSameGates() {
        BlendDurationPolicy.Evaluation medium = BlendDurationPolicy.evaluate(
                Arrays.asList(Double.valueOf(0.48), Double.valueOf(0.52),
                        Double.valueOf(0.56)));
        assertEquals(BlendDurationPolicy.Confidence.MEDIUM,
                medium.confidence, "three tight events should be medium confidence");
        assertTrue(medium.eligible, "medium confidence must be eligible");
        assertClose(0.54, medium.proposedValue, 0.0000001,
                "median plus final-only margin should produce 0.54 s");

        BlendDurationPolicy.Evaluation high = BlendDurationPolicy.evaluate(
                Arrays.asList(Double.valueOf(0.50), Double.valueOf(0.51),
                        Double.valueOf(0.52), Double.valueOf(0.53),
                        Double.valueOf(0.54)));
        assertEquals(BlendDurationPolicy.Confidence.HIGH,
                high.confidence, "five tightly clustered events should be high confidence");
        assertTrue(high.eligible, "high confidence must be eligible");
    }

    private static void wideEvidenceStaysWithheld() {
        BlendDurationPolicy.Evaluation result = BlendDurationPolicy.evaluate(
                Arrays.asList(Double.valueOf(0.40), Double.valueOf(0.50),
                        Double.valueOf(0.62)));
        assertEquals(BlendDurationPolicy.Confidence.LOW,
                result.confidence, "range above 0.18 s must remain low confidence");
        assertFalse(result.eligible, "wide evidence must be withheld");
    }

    private static void outlierFilteringUsesRawDurations() {
        BlendDurationPolicy.Evaluation result = BlendDurationPolicy.evaluate(
                Arrays.asList(Double.valueOf(0.50), Double.valueOf(0.51),
                        Double.valueOf(0.52), Double.valueOf(0.53),
                        Double.valueOf(1.40)));
        assertEquals(4, result.stats.retainedCount,
                "strong high outlier must be removed before statistics");
        assertEquals(1, result.stats.outlierCount,
                "one statistical outlier should be recorded");
        assertClose(0.515, result.stats.median, 0.0000001,
                "retained median must remain raw and unbounded");
        assertClose(0.54, result.proposedValue, 0.0000001,
                "only the eligible final proposal should receive margin and rounding");
    }

    private static void marginClampAndRoundingAreFinalOnly() {
        assertClose(0.08, BlendDurationPolicy.finalProposal(0.01), 0.0000001,
                "lower bound applies only to final proposal");
        assertClose(0.80, BlendDurationPolicy.finalProposal(1.40), 0.0000001,
                "upper bound applies only to final proposal");
        assertClose(0.55, BlendDurationPolicy.finalProposal(0.529594391),
                0.0000001, "proposal must add 0.02 then round to two decimals");
    }

    private static void invalidValuesDoNotCreateEvidence() {
        BlendDurationPolicy.Evaluation result = BlendDurationPolicy.evaluate(
                Arrays.asList(null, Double.valueOf(Double.NaN),
                        Double.valueOf(-0.1), Double.valueOf(0.0)));
        assertEquals(0, result.stats.retainedCount,
                "invalid values must not become retained evidence");
        assertEquals(BlendDurationPolicy.Confidence.INSUFFICIENT,
                result.confidence, "invalid-only evidence must be insufficient");

        BlendDurationPolicy.Evaluation empty = BlendDurationPolicy.evaluate(
                Collections.<Double>emptyList());
        assertEquals(0, empty.stats.retainedCount,
                "empty input must remain empty");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertClose(double expected, double actual,
                                    double tolerance, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }
}
