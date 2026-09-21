package se.anders.tunerstudio.aetuner;

import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueController;
import se.anders.tunerstudio.aetuner.host.BuildIdentity;

public final class VehicleTestIdentityRegressionTest {
    private static final String EXPECTED_VERSION = "0.4.5";
    private static final String EXPECTED_PUBLIC_REPOSITORY =
            "https://github.com/PJawZK/AE-Tuner-EPICEFI_Public";

    public static void main(String[] args) {
        assertEquals(EXPECTED_VERSION, BuildIdentity.VERSION,
                "small build-identity authority must identify the exact public release");
        assertEquals(EXPECTED_VERSION, AeTunerPlugin.VERSION,
                "source version must identify the exact public release");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains(EXPECTED_VERSION),
                "banner must include the exact public release version");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("PUBLIC RELEASE"),
                "internal candidate banner must identify the vehicle-test boundary");
        assertTrue(!AeTunerPlugin.VEHICLE_TEST_BANNER.contains("RELEASE CANDIDATE"),
                "internal public release must not masquerade as the published release candidate");
        assertTrue(!AeTunerPlugin.VEHICLE_TEST_BANNER.contains("PUBLIC TEST"),
                "internal public release must not masquerade as the published public-test artifact");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("guarded Apply/Restore"),
                "public release banner must preserve the guarded working-tune mutation boundary");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.contains("NO BURN"),
                "banner must preserve the no-burn boundary");
        assertTrue(AeTunerPlugin.VEHICLE_TEST_BANNER.length() <= 130,
                "public release banner regressed to a long line likely to clip at the physical 1366 px test width");
        assertTrue(!AeTunerPlugin.VEHICLE_TEST_BANNER.contains("physically validated"),
                "public release must not claim broad physical validation beyond the scoped Foundation evidence");
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
            assertEquals(EXPECTED_VERSION, plugin.getVersion(),
                    "plugin API version must match the public release identity");
            assertEquals(EXPECTED_PUBLIC_REPOSITORY, plugin.getHelpUrl(),
                    "TunerStudio About plugin/help metadata must expose the public repository URL");
            assertTrue(plugin.areGuidedSoundCuesEnabledForTest(),
                    "guided sound controller must default ON");
            assertTrue(plugin.guidedAudioStatusForTest().contains("Audio Cue Lab"),
                    "audio status must direct the operator to stationary cue verification");
            assertTrue(plugin.audioCueLabRowCountForTest()
                            == GuidedAudioCueController.Cue.values().length,
                    "Audio Cue Lab must expose every assignable workflow cue");
            assertTrue(plugin.evidenceDiagnosticsTabCountForTest() == 4,
                    "Evidence / Diagnostics must preserve four diagnostic surfaces inside its utility pop-out");
            assertEquals("Overview", plugin.evidenceDiagnosticsTabTitleForTest(0),
                    "diagnostics Overview section changed unexpectedly");
            assertEquals("Channels / Runtime", plugin.evidenceDiagnosticsTabTitleForTest(1),
                    "diagnostics runtime section changed unexpectedly");
            assertEquals("Audio Cue Lab", plugin.evidenceDiagnosticsTabTitleForTest(2),
                    "diagnostics Audio Cue Lab section changed unexpectedly");
            assertEquals("Recovery / Audit", plugin.evidenceDiagnosticsTabTitleForTest(3),
                    "diagnostics recovery/audit section changed unexpectedly");
            assertTrue(!plugin.lifecycleActiveForTest(),
                    "construction alone must not activate the host lifecycle");
            assertTrue(!plugin.shownOnceForTest(),
                    "construction alone must not pretend the host panel was shown");
            assertTrue(plugin.presentationSuspendedForTest(),
                    "presentation work must remain suspended before first host display");
            assertTrue(!plugin.guidedControllerPreparedForTest(),
                    "controller/read-write path must remain deferred before host initialize/show");
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
