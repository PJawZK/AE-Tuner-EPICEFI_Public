package se.anders.tunerstudio.aetuner;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

public final class VehicleTestIdentityRegressionTest {
    private static final String EXPECTED_VERSION = "0.4.0";

    public static void main(String[] args) {
        assertEquals(EXPECTED_VERSION, AeTunerPlugin.VERSION,
                "source version must identify the accepted baseline");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains(EXPECTED_VERSION),
                "banner must include the exact accepted version");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("read-only"),
                "banner must preserve the read-only boundary");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("physically validated"),
                "banner must identify the physically validated baseline");
        assertTrue(!AeTunerPlugin.VEHICLE_TEST_BANNER.contains("unaccepted"),
                "accepted banner must not identify the build as unaccepted");
        assertTrue(!AeTunerPlugin.VEHICLE_TEST_BANNER.contains("vehicle-test"),
                "accepted banner must not retain candidate identity wording");

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
                    "visible Guided Capture banner must match the source identity");
            assertEquals(EXPECTED_VERSION, plugin.getVersion(),
                    "plugin API version must match the accepted identity");
            assertTrue(plugin.areGuidedSoundCuesEnabledForTest(),
                    "guided sound controller must default ON");
            assertTrue(plugin.isGuidedSoundCheckboxSelectedForTest(),
                    "visible sound checkbox must default selected");
            assertTrue(plugin.guidedAudioStatusForTest().contains("Audio Cue Lab"),
                    "audio status must direct the operator to stationary cue verification");
            assertTrue(plugin.audioCueLabRowCountForTest()
                            == GuidedAudioCueController.Cue.values().length,
                    "Audio Cue Lab must expose every assignable workflow cue");
            assertTrue(!plugin.areVehicleTestOverridesEnabledForTest(),
                    "vehicle-test overrides must default OFF");
            assertTrue(!plugin.lifecycleActiveForTest(),
                    "construction alone must not activate the host lifecycle");
            assertTrue(!plugin.shownOnceForTest(),
                    "construction alone must not pretend the host panel was shown");
            assertTrue(plugin.presentationSuspendedForTest(),
                    "presentation work must remain suspended before first host display");
            assertTrue(!plugin.guidedControllerPreparedForTest(),
                    "Guided controller/read path must be deferred before Guided is selected");
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
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
