package se.anders.tunerstudio.aetuner.guided;

/** Regression for strict +TPS target-band candidate progress. */
public final class EngagementManeuverQualityPolicyRegressionTest {
    private EngagementManeuverQualityPolicyRegressionTest() { }

    public static void main(String[] args) {
        exampleAmplitudesMapToExpectedQuality();
        onlyStrictTargetBandIsComparable();
        invalidGeometryIsRejected();
        System.out.println("EngagementManeuverQualityPolicyRegressionTest passed");
    }

    private static void exampleAmplitudesMapToExpectedQuality() {
        requireQuality(9.8, EngagementManeuverQualityPolicy.Quality.EXCELLENT);
        requireQuality(7.5, EngagementManeuverQualityPolicy.Quality.GOOD);
        requireQuality(14.0, EngagementManeuverQualityPolicy.Quality.GOOD);
        requireQuality(6.0, EngagementManeuverQualityPolicy.Quality.USABLE);
        requireQuality(25.0, EngagementManeuverQualityPolicy.Quality.DIAGNOSTIC_ONLY);
        requireQuality(30.0, EngagementManeuverQualityPolicy.Quality.DIAGNOSTIC_ONLY);
    }

    private static void onlyStrictTargetBandIsComparable() {
        EngagementManeuverQualityPolicy.Assessment excellent = assess(9.8);
        require(excellent.comparable(),
                "strict target-band maneuver was not comparable");

        EngagementManeuverQualityPolicy.Assessment lowerEdge = assess(8.0);
        EngagementManeuverQualityPolicy.Assessment upperEdge = assess(12.0);
        require(lowerEdge.comparable() && upperEdge.comparable(),
                "+10 TPS +/-2 target edges did not count");

        EngagementManeuverQualityPolicy.Assessment justLow = assess(7.9);
        EngagementManeuverQualityPolicy.Assessment justHigh = assess(12.1);
        require(!justLow.comparable() && !justHigh.comparable(),
                "maneuver outside +10 TPS +/-2 advanced candidate progress");

        EngagementManeuverQualityPolicy.Assessment usable = assess(6.0);
        require(!usable.comparable()
                        && usable.reason.contains("outside the strict counting target band"),
                "off-target usable maneuver did not remain diagnostic-only");

        EngagementManeuverQualityPolicy.Assessment diagnostic = assess(25.0);
        require(!diagnostic.comparable()
                        && diagnostic.reason.contains("does not advance candidate comparison"),
                "diagnostic overshoot did not retain evidence while withholding progress");
    }

    private static void invalidGeometryIsRejected() {
        EngagementManeuverQualityPolicy.Assessment wrongDirection =
                EngagementManeuverQualityPolicy.assess(
                        10.0, 9.0, 20.0, 18.0, 22.0, 10.0);
        require(wrongDirection.quality == EngagementManeuverQualityPolicy.Quality.REJECT,
                "wrong-direction movement was not rejected");

        EngagementManeuverQualityPolicy.Assessment nonFinite =
                EngagementManeuverQualityPolicy.assess(
                        10.0, Double.NaN, 20.0, 18.0, 22.0, 10.0);
        require(nonFinite.quality == EngagementManeuverQualityPolicy.Quality.REJECT,
                "non-finite maneuver geometry was not rejected");

        EngagementManeuverQualityPolicy.Assessment tiny = assess(2.0);
        require(tiny.quality == EngagementManeuverQualityPolicy.Quality.REJECT,
                "tiny start-condition motion was not rejected");

        EngagementManeuverQualityPolicy.Assessment extreme = assess(36.0);
        require(extreme.quality == EngagementManeuverQualityPolicy.Quality.REJECT,
                "implausibly large movement was not rejected");
    }

    private static EngagementManeuverQualityPolicy.Assessment assess(double delta) {
        return EngagementManeuverQualityPolicy.assess(
                10.0, 10.0 + delta,
                20.0, 18.0, 22.0, 10.0);
    }

    private static void requireQuality(double delta,
                                       EngagementManeuverQualityPolicy.Quality expected) {
        EngagementManeuverQualityPolicy.Assessment assessment = assess(delta);
        require(assessment.quality == expected,
                "+" + delta + " %TPS classified as " + assessment.quality
                        + " instead of " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
