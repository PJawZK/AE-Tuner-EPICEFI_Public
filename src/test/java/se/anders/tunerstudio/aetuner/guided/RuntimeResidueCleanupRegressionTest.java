package se.anders.tunerstudio.aetuner.guided;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Permanent gate for disconnected production/runtime compatibility residue. */
public final class RuntimeResidueCleanupRegressionTest {
    private static final Path MAIN = Paths.get("src/main/java/se/anders/tunerstudio/aetuner");

    private RuntimeResidueCleanupRegressionTest() { }

    public static void main(String[] args) throws Exception {
        disconnectedCompatibilityTypesStayAbsent();
        blendDurationUsesLiveCatchupAuthority();
        foundationFocusHasNoHiddenQuietPanel();
        transientEventRemainsTheEventAuthority();
        System.out.println("RuntimeResidueCleanupRegressionTest passed");
    }

    private static void disconnectedCompatibilityTypesStayAbsent() throws Exception {
        requireMissing("guided/BlendDurationFirmwareReplay.java",
                "disconnected Blend Duration replay helper returned");
        requireMissing("guided/EngagementDetectorEvidence.java",
                "disconnected five-detector evidence helper returned");
        requireMissing("guided/EngagementQuietCalibrationPanel.java",
                "hidden quiet calibration compatibility panel returned");
        requireMissing("guided/EngagementQuietCalibrationControl.java",
                "orphaned quiet calibration UI facade returned");
        requireMissing("model/EventSummary.java",
                "TransientEvent compatibility alias returned");

        requireClassMissing("se.anders.tunerstudio.aetuner.guided.BlendDurationFirmwareReplay");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.EngagementDetectorEvidence");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.EngagementQuietCalibrationPanel");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.EngagementQuietCalibrationControl");
        requireClassMissing("se.anders.tunerstudio.aetuner.model.EventSummary");
    }

    private static void blendDurationUsesLiveCatchupAuthority() throws Exception {
        String session = source("guided/BlendDurationGuidedSession.java");
        require(!session.contains("BlendDurationFirmwareReplay")
                        && session.contains("mapCatchup.observePredictionGap(sample)")
                        && session.contains("mapCatchup.beginCatchup(")
                        && session.contains("mapCatchup.observeCatchup(sample)")
                        && session.contains("mapCatchup.catchSample()")
                        && session.contains("mapCatchup.effectiveMapModelConsistent()"),
                "Blend Duration no longer keeps prediction-target/catch-up/Effective-MAP authority on the live MapCatchupMeasurement path");
    }

    private static void foundationFocusHasNoHiddenQuietPanel() throws Exception {
        String focus = source("guided/EngagementDetectionGuidedFocusPanel.java");
        require(!focus.contains("EngagementQuietCalibrationPanel")
                        && !focus.contains("quietCalibrationDetails")
                        && focus.contains("capture is read-only")
                        && focus.contains("No exact TPS target"),
                "Foundation 1 Focus regained hidden compatibility UI or lost passive read-only guidance");
    }

    private static void transientEventRemainsTheEventAuthority() throws Exception {
        String panel = source("passive/AeTunerPanel.java");
        require(panel.contains("List<TransientEvent> capturedEvents")
                        && !panel.contains("EventSummary"),
                "passive event storage no longer uses TransientEvent directly");
    }

    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(MAIN.resolve(relative)), StandardCharsets.UTF_8);
    }

    private static void requireMissing(String relative, String message) {
        require(!Files.exists(MAIN.resolve(relative)), message);
    }

    private static void requireClassMissing(String name) throws Exception {
        try {
            Class.forName(name);
            throw new AssertionError("retired runtime residue is still loadable: " + name);
        } catch (ClassNotFoundException expected) {
            // Expected permanent architecture state.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
