package se.anders.tunerstudio.aetuner.proposal;

import java.util.Arrays;

public final class ProposalWritePlanRegressionTest {
    private ProposalWritePlanRegressionTest() { }

    public static void main(String[] args) {
        reviewAndManifestPreserveExactTargets();
        reversedManifestSwapsOnlyBeforeAfter();
        distinctArrayCellsInOneParameterAreAllowed();
        duplicateArrayCellIsRejected();
        mixedKindsForOneParameterAreRejected();
        exactNoOpChangeIsRejected();
        System.out.println("ProposalWritePlanRegressionTest passed");
    }

    private static void reviewAndManifestPreserveExactTargets() {
        ProposalWritePlan plan = new ProposalWritePlan(
                "blend",
                "Predictive MAP Blend Duration",
                "Main Controller",
                "2450 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "predictiveMapBlendDurationValues",
                        1,
                        0.26,
                        0.54,
                        "2450 RPM",
                        "s")));

        require(plan.reviewText().contains("2450 RPM: 0.26 s -> 0.54 s"),
                "review text lost the exact proposal values");
        String json = plan.verificationManifestJson();
        require(json.contains("\"parameter\": \"predictiveMapBlendDurationValues\""),
                "manifest lost the exact parameter name");
        require(json.contains("\"index\": 1"),
                "manifest lost the exact array-cell index");
        require(json.contains("\"before\": 0.26"),
                "manifest lost the exact baseline value");
        require(json.contains("\"after\": 0.54"),
                "manifest lost the exact proposed value");
    }

    private static void reversedManifestSwapsOnlyBeforeAfter() {
        ProposalWritePlan plan = new ProposalWritePlan(
                "blend",
                "Predictive MAP Blend Duration",
                "Main Controller",
                "2450 RPM",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "predictiveMapBlendDurationValues",
                        1,
                        0.26,
                        0.54,
                        "2450 RPM",
                        "s")));
        ProposalWritePlan reversed = plan.reversed("Restore 2450 RPM");
        ProposalWritePlan.Change change = reversed.getChanges().get(0);

        require("blend".equals(reversed.getRecipeId()),
                "restore plan changed the recipe identity");
        require("Main Controller".equals(reversed.getConfigurationName()),
                "restore plan changed the controller configuration");
        require(change.flatIndex == 1,
                "restore plan changed the selected table cell");
        requireClose(0.54, change.expectedValue,
                "restore baseline should be the value that was applied");
        requireClose(0.26, change.proposedValue,
                "restore proposal should be the original baseline");

        String json = reversed.verificationManifestJson();
        require(json.contains("\"context\": \"Restore 2450 RPM\""),
                "restore manifest did not identify the restore operation");
        require(json.contains("\"before\": 0.54"),
                "restore manifest did not swap the before value");
        require(json.contains("\"after\": 0.26"),
                "restore manifest did not swap the after value");
    }

    private static void distinctArrayCellsInOneParameterAreAllowed() {
        ProposalWritePlan plan = new ProposalWritePlan(
                "map-estimate",
                "MAP Estimate",
                "Main Controller",
                "two-cell future contract",
                Arrays.asList(
                        ProposalWritePlan.Change.arrayCell(
                                "mapEstimateTable", 7, 70.0, 74.0,
                                "cell 7", "kPa"),
                        ProposalWritePlan.Change.arrayCell(
                                "mapEstimateTable", 8, 72.0, 76.0,
                                "cell 8", "kPa")));
        require(plan.changeCount() == 2,
                "distinct cells in one table must remain valid for future multi-cell recipes");
        require(plan.verificationManifestJson().contains("\"index\": 7")
                        && plan.verificationManifestJson().contains("\"index\": 8"),
                "multi-cell manifest did not retain both distinct allowed targets");
    }

    private static void duplicateArrayCellIsRejected() {
        boolean rejected = false;
        try {
            new ProposalWritePlan(
                    "map-estimate",
                    "MAP Estimate",
                    "Main Controller",
                    "duplicate target test",
                    Arrays.asList(
                            ProposalWritePlan.Change.arrayCell(
                                    "mapEstimateTable", 7, 70.0, 74.0,
                                    "cell 7 first", "kPa"),
                            ProposalWritePlan.Change.arrayCell(
                                    "mapEstimateTable", 7, 70.0, 76.0,
                                    "cell 7 second", "kPa")));
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage() != null
                    && expected.getMessage().contains("same target");
        }
        require(rejected,
                "one proposal must not be allowed to declare the same array cell twice");
    }

    private static void mixedKindsForOneParameterAreRejected() {
        boolean rejected = false;
        try {
            new ProposalWritePlan(
                    "malformed",
                    "Malformed proposal",
                    "Main Controller",
                    "mixed-kind test",
                    Arrays.asList(
                            ProposalWritePlan.Change.scalar(
                                    "sameParameter", 1.0, 2.0,
                                    "scalar", ""),
                            ProposalWritePlan.Change.arrayCell(
                                    "sameParameter", 0, 1.0, 2.0,
                                    "array cell", "")));
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage() != null
                    && expected.getMessage().contains("both scalar and array");
        }
        require(rejected,
                "one controller parameter must not have conflicting target kinds in one plan");
    }

    private static void exactNoOpChangeIsRejected() {
        boolean rejected = false;
        try {
            ProposalWritePlan.Change.arrayCell(
                    "predictiveMapBlendDurationValues", 1,
                    0.54, 0.54, "2450 RPM", "s");
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage() != null
                    && expected.getMessage().contains("actual value change");
        }
        require(rejected,
                "proposal write plans must not contain exact no-op targets");
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
