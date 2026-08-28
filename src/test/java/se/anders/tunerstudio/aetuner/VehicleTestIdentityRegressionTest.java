package se.anders.tunerstudio.aetuner;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

public final class VehicleTestIdentityRegressionTest {
    private static final String EXPECTED_VERSION = "0.4.2-rc.1";
    private static final String EXPECTED_PUBLIC_REPOSITORY =
            "https://github.com/PJawZK/AE-Tuner-EPICEFI_Public";

    public static void main(String[] args) {
        assertEquals(EXPECTED_VERSION, AeTunerPlugin.VERSION,
                "source version must identify the release-candidate build");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains(EXPECTED_VERSION),
                "banner must include the exact release-candidate version");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("RELEASE CANDIDATE"),
                "release-candidate banner must not masquerade as accepted");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("PUBLIC TEST"),
                "release-candidate banner must identify the public-test boundary");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("guarded Apply/Restore"),
                "release-candidate banner must preserve the guarded working-tune mutation boundary");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("NO BURN"),
                "banner must preserve the no-burn boundary");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.length() <= 130,
                "release-candidate banner regressed to a long line likely to clip at the physical 1366 px test width");
        assertTrue(!AeTunerPlugin.VEHICLE_TEST_BANNER.contains("physically validated"),
                "release candidate must not claim physical validation it has not received");
        assertEquals(EXPECTED_PUBLIC_REPOSITORY, AeTunerPlugin.PUBLIC_REPOSITORY_URL,
                "public repository constant changed unexpectedly");

        java.nio.file.Path recoveryRoot;
        try {
            recoveryRoot = java.nio.file.Files.createTempDirectory("ae-tuner-recovery-identity");
        } catch (java.io.IOException ex) {
            throw new AssertionError("could not create recovery test directory", ex);
        }
        System.setProperty("ae.tuner.recovery.dir", recoveryRoot.toString());
        AeTunerPlugin plugin = new AeTunerPlugin();
        try {
            assertEquals(AeTunerPlugin.VEHICLE_TEST_BANNER,
                    plugin.getVehicleTestBannerForTest(),
                    "visible Guided Tuning banner must match the source identity");
            assertEquals(EXPECTED_VERSION, plugin.getVersion(),
                    "plugin API version must match the release-candidate identity");
            assertEquals(EXPECTED_PUBLIC_REPOSITORY, plugin.getHelpUrl(),
                    "TunerStudio About plugin/help metadata must expose the public repository URL");
            assertTrue(plugin.areGuidedSoundCuesEnabledForTest(),
                    "guided sound controller must default ON");
            assertTrue(plugin.isGuidedSoundCheckboxSelectedForTest(),
                    "visible sound checkbox must default selected");
            assertTrue(plugin.guidedAudioStatusForTest().contains("Audio Cue Lab"),
                    "audio status must direct the operator to stationary cue verification");
            assertTrue(plugin.audioCueLabRowCountForTest()
                            == GuidedAudioCueController.Cue.values().length,
                    "Audio Cue Lab must expose every assignable workflow cue");
            assertTrue(plugin.evidenceDiagnosticsTabCountForTest() == 4,
                    "Evidence / Diagnostics must expose four distinct diagnostic surfaces");
            assertEquals("Overview", plugin.evidenceDiagnosticsTabTitleForTest(0),
                    "diagnostics Overview tab changed unexpectedly");
            assertEquals("Channels / Runtime", plugin.evidenceDiagnosticsTabTitleForTest(1),
                    "diagnostics runtime tab changed unexpectedly");
            assertEquals("Audio Cue Lab", plugin.evidenceDiagnosticsTabTitleForTest(2),
                    "diagnostics Audio Cue Lab tab changed unexpectedly");
            assertEquals("Recovery / Audit", plugin.evidenceDiagnosticsTabTitleForTest(3),
                    "diagnostics recovery/audit tab changed unexpectedly");
            assertTrue(!plugin.areVehicleTestOverridesEnabledForTest(),
                    "vehicle-test overrides must default OFF");
            assertTrue(!plugin.lifecycleActiveForTest(),
                    "construction alone must not activate the host lifecycle");
            assertTrue(!plugin.shownOnceForTest(),
                    "construction alone must not pretend the host panel was shown");
            assertTrue(plugin.presentationSuspendedForTest(),
                    "presentation work must remain suspended before first host display");
            assertTrue(!plugin.guidedControllerPreparedForTest(),
                    "Guided controller/read-write path must be deferred before Guided Tuning is selected");
        } finally {
            plugin.close();
            System.clearProperty("ae.tuner.recovery.dir");
        }

        System.out.println("VehicleTestIdentityRegressionTest passed");
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
