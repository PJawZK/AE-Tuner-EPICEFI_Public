package se.anders.tunerstudio.aetuner.guided;

/** Locks the audible meaning of the default Guided cue families. */
public final class GuidedAudioQualitySemanticsRegressionTest {
    public static void main(String[] args) {
        GuidedAudioProfile profile = GuidedAudioProfile.defaults();
        GuidedAudioProfile.Setting detector = profile.get(
                GuidedAudioCueController.Cue.TARGET_ACQUIRED);
        GuidedAudioProfile.Setting accepted = profile.get(
                GuidedAudioCueController.Cue.ACCEPTED);
        GuidedAudioProfile.Setting excluded = profile.get(
                GuidedAudioCueController.Cue.EXCLUDED);

        require(detector.pattern == GuidedAudioProfile.Pattern.SINGLE,
                "ECU detector reaction must be a neutral single cue");
        require(detector.startHz < accepted.startHz,
                "ECU detector reaction must not sound higher/more positive than Guided acceptance");
        require(GuidedAudioCueController.Cue.TARGET_ACQUIRED.toString().contains("not counted"),
                "ECU detector cue label does not explicitly distinguish it from counted evidence");

        require(accepted.pattern == GuidedAudioProfile.Pattern.THREE_ASCENDING,
                "counted Guided maneuver must use a melodic ascending pattern");
        require(accepted.startHz >= 1100.0 && accepted.endHz > accepted.startHz,
                "counted Guided maneuver must occupy the high ascending pitch family");

        require(excluded.pattern == GuidedAudioProfile.Pattern.FALLING_CHIRP,
                "non-counted maneuver must use a falling pattern");
        require(excluded.startHz <= 700.0 && excluded.endHz < excluded.startHz,
                "non-counted maneuver must occupy the low falling pitch family");
        require(accepted.startHz - excluded.startHz >= 450.0,
                "accepted/non-counted default pitch families are not separated enough");

        require(GuidedAudioCueController.cueForQuality(
                        EngagementManeuverQualityPolicy.Quality.EXCELLENT)
                        == GuidedAudioCueController.Cue.ACCEPTED,
                "strict target-band Excellent must produce counted audio");
        require(GuidedAudioCueController.cueForQuality(
                        EngagementManeuverQualityPolicy.Quality.GOOD)
                        == GuidedAudioCueController.Cue.EXCLUDED,
                "off-target Good must not produce counted audio");
        require(GuidedAudioCueController.cueForQuality(
                        EngagementManeuverQualityPolicy.Quality.USABLE)
                        == GuidedAudioCueController.Cue.EXCLUDED,
                "off-target Usable must not produce counted audio");
        require(GuidedAudioCueController.cueForQuality(
                        EngagementManeuverQualityPolicy.Quality.DIAGNOSTIC_ONLY)
                        == GuidedAudioCueController.Cue.EXCLUDED,
                "Diagnostic-only must produce non-counted audio");
        require(GuidedAudioCueController.cueForQuality(
                        EngagementManeuverQualityPolicy.Quality.REJECT)
                        == GuidedAudioCueController.Cue.EXCLUDED,
                "Reject must produce non-counted audio");

        System.out.println("GuidedAudioQualitySemanticsRegressionTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
