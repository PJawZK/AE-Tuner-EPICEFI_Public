package se.anders.tunerstudio.aetuner.guided;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Permanent gate for disconnected production/runtime compatibility residue. */
public final class RuntimeResidueCleanupRegressionTest {
    private static final Path MAIN = Paths.get("src/main/java/se/anders/tunerstudio/aetuner");
    private static final Path TEST = Paths.get("src/test/java/se/anders/tunerstudio/aetuner");

    private RuntimeResidueCleanupRegressionTest() { }

    public static void main(String[] args) throws Exception {
        disconnectedCompatibilityTypesStayAbsent();
        retiredPassiveRuntimeStaysAbsent();
        recoveryCompatibilityResidueStaysAbsent();
        retiredUiHelpersStayAbsent();
        blendDurationUsesPhysicalResponseAuthority();
        foundationFocusHasNoHiddenQuietPanel();
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
        requireMissing("host/GuidedWorkingTuneSurfaceCache.java",
                "Guided working-tune cache compatibility bridge returned");
        requireMissing("passive/MapEstimateCollector.java",
                "Passive MAP Estimate compatibility facade returned");

        requireClassMissing("se.anders.tunerstudio.aetuner.guided.BlendDurationFirmwareReplay");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.EngagementDetectorEvidence");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.EngagementQuietCalibrationPanel");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.EngagementQuietCalibrationControl");
        requireClassMissing("se.anders.tunerstudio.aetuner.model.EventSummary");
        requireClassMissing("se.anders.tunerstudio.aetuner.host.GuidedWorkingTuneSurfaceCache");
        requireClassMissing("se.anders.tunerstudio.aetuner.passive.MapEstimateCollector");
    }

    private static void retiredPassiveRuntimeStaysAbsent() throws Exception {
        require(!Files.exists(MAIN.resolve("passive")),
                "retired Passive production package returned");
        require(!Files.exists(TEST.resolve("passive")),
                "retired Passive regression/synthetic test package returned");

        String plugin = source("AeTunerPlugin.java");
        require(!plugin.contains("AeTunerPanel")
                        && !plugin.contains("rootTabs")
                        && !plugin.contains("passiveAnalysisWindow")
                        && !plugin.contains("sampleDispatcherForPassivePanel")
                        && plugin.contains("GuidedLiveSampleSource liveSource")
                        && plugin.contains("new EvidenceRecoveryManager(guidedPanel)"),
                "plugin shell regained Passive/tab runtime ownership or lost Guided-only live/recovery ownership");

        String workspace = source("guided/GuidedV019WorkspacePanel.java");
        require(!workspace.contains("Passive Analysis…")
                        && !workspace.contains("openPassiveAnalysis"),
                "retired Passive Analysis control returned to the production workspace");

        requireClassMissing("se.anders.tunerstudio.aetuner.passive.AeTunerPanel");
        requireClassMissing("se.anders.tunerstudio.aetuner.passive.SessionMonitor");
        requireClassMissing("se.anders.tunerstudio.aetuner.passive.CoherentLiveSampleAssembler");
        requireClassMissing("se.anders.tunerstudio.aetuner.passive.RecommendationHistory");
        requireClassMissing("se.anders.tunerstudio.aetuner.passive.TpsNoiseCalibration");
        requireClassMissing("se.anders.tunerstudio.aetuner.passive.PassivePackageRetired");
    }

    private static void recoveryCompatibilityResidueStaysAbsent() throws Exception {
        requireMissing("recovery/SessionExportSupport.java",
                "recovery SessionExportSupport compatibility facade returned");
        String snapshot = source("recovery/EvidenceRecoverySnapshot.java");
        require(!snapshot.contains("class Passive"),
                "retired Passive recovery payload returned");
        String manager = source("recovery/EvidenceRecoveryManager.java");
        String store = source("recovery/EvidenceRecoveryStore.java");
        require(!manager.contains("startupFinalizationScheduled")
                        && !manager.contains("finalizeRun(")
                        && !store.contains("static void finalizeRun"),
                "obsolete recovery finalization lifecycle returned");
    }

    private static void retiredUiHelpersStayAbsent() {
        requireMissing("ui/StableTabbedPane.java",
                "unused StableTabbedPane helper returned");
        requireMissing("ui/ViewportWidthPanel.java",
                "unused ViewportWidthPanel helper returned");
        requireMissing("ui/WrappingColumnPanel.java",
                "unused WrappingColumnPanel helper returned");
        requireMissing("ui/NestedScrollWheelHandoff.java",
                "unused NestedScrollWheelHandoff helper returned");
    }

    private static void blendDurationUsesPhysicalResponseAuthority() throws Exception {
        String session = source("guided/BlendDurationGuidedSession.java");
        require(!session.contains("BlendDurationFirmwareReplay")
                        && session.contains("PhysicalMapResponseMeasurement physicalResponse")
                        && session.contains("physicalResponse.begin(")
                        && session.contains("physicalResponse.observe(")
                        && session.contains("physicalResponse.isComplete()")
                        && session.contains("physicalResponse.durationSeconds()")
                        && session.contains("mapCatchup.observePredictionGap(sample)")
                        && session.contains("mapCatchup.beginCatchup(")
                        && session.contains("mapCatchup.observeCatchup(sample)")
                        && session.contains("mapCatchup.effectiveMapModelConsistent()")
                        && !session.contains("complete(caught, mapCatchup.catchupDurationSeconds())")
                        && !session.contains("mapCatchup.timedOut(sample"),
                "Blend Duration no longer keeps physical timing on PhysicalMapResponseMeasurement while retaining MapCatchupMeasurement as live prediction diagnostics");
    }

    private static void foundationFocusHasNoHiddenQuietPanel() throws Exception {
        String focus = source("guided/EngagementDetectionGuidedFocusPanel.java");
        require(!focus.contains("EngagementQuietCalibrationPanel")
                        && !focus.contains("quietCalibrationDetails")
                        && focus.contains("capture is read-only")
                        && focus.contains("No exact TPS target"),
                "Foundation 1 Focus regained hidden compatibility UI or lost read-only driver guidance");
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
