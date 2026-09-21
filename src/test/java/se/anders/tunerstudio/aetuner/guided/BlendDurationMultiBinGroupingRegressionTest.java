package se.anders.tunerstudio.aetuner.guided;

public final class BlendDurationMultiBinGroupingRegressionTest {
    private BlendDurationMultiBinGroupingRegressionTest() { }

    public static void main(String[] args) {
        samePhysicalConditionsNeverMergeAcrossLatchedBins();
        allArmedBinsMustReachTarget();
        System.out.println("BlendDurationMultiBinGroupingRegressionTest passed");
    }

    private static void samePhysicalConditionsNeverMergeAcrossLatchedBins() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        BlendDurationCaptureConfig at1500 = config(1500.0);
        BlendDurationCaptureConfig at2600 = config(2600.0);
        groups.assign(attempt(1, 1510.0, at1500));
        groups.assign(attempt(2, 1520.0, at1500));
        groups.assign(attempt(3, 2590.0, at2600));
        require(groups.bestGroupCountForBin(1500.0) == 2, "1500 cohort lost its events");
        require(groups.bestGroupCountForBin(2600.0) == 1, "2600 event merged into 1500 cohort");
        require(groups.groupCount() == 2, "latched RPM bins did not force separate cohorts");
    }

    private static void allArmedBinsMustReachTarget() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        for (int i = 0; i < 3; i++) groups.assign(attempt(i + 1, 1500.0 + i, config(1500.0)));
        require(!groups.targetsReached(new double[]{1500.0, 2600.0}, 3),
                "session completed while an armed bin had no evidence");
        for (int i = 0; i < 3; i++) groups.assign(attempt(i + 10, 2600.0 + i, config(2600.0)));
        require(groups.targetsReached(new double[]{1500.0, 2600.0}, 3),
                "session did not complete after both armed bins reached target");
    }

    private static BlendDurationCaptureConfig config(double eventBin) {
        return new BlendDurationCaptureConfig(eventBin, 20.0, 3, 0, false,
                new double[]{1500.0, 2600.0}, new double[]{0.12, 0.22},
                new double[]{1500.0, 2600.0});
    }

    private static BlendDurationAttempt attempt(int number, double baseRpm,
                                                BlendDurationCaptureConfig settings) {
        return new BlendDurationAttempt(number, 0.20, baseRpm,
                50.0, 10.0, 30.0, 15.0, "STABLE", settings,
                Integer.MAX_VALUE, Integer.MIN_VALUE, false, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
