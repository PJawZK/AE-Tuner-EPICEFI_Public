package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Permanent gate for the completed MAP Estimate old-to-new architecture migration. */
public final class MapEstimateArchitectureMigrationRegressionTest {
    private static final Path MAIN = Paths.get("src/main/java");

    private MapEstimateArchitectureMigrationRegressionTest() { }

    public static void main(String[] args) throws Exception {
        retiredCompatibilityTypesStayAbsent();
        productionFocusUsesLearnedModel();
        hubHasNoLegacySnapshotAdapter();
        probeSessionUsesDirectCollectorControllerPath();
        System.out.println("MapEstimateArchitectureMigrationRegressionTest passed");
    }

    private static void retiredCompatibilityTypesStayAbsent() throws Exception {
        require(!Files.exists(MAIN.resolve(
                        "se/anders/tunerstudio/aetuner/guided/MapEstimateGuidedFocusPanel.java")),
                "legacy same-package MAP Estimate Focus panel returned");
        require(!Files.exists(MAIN.resolve(
                        "se/anders/tunerstudio/aetuner/guided/MapEstimateFocusSnapshot.java")),
                "legacy MAP Estimate Focus Snapshot returned");
        require(!Files.exists(MAIN.resolve(
                        "se/anders/tunerstudio/aetuner/guided/mapestimate/MapEstimateGuidedProbeRoute.java")),
                "duplicate MAP Estimate probe route returned");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.MapEstimateGuidedFocusPanel");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.MapEstimateFocusSnapshot");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedProbeRoute");
    }

    private static void productionFocusUsesLearnedModel() throws Exception {
        String source = source("se/anders/tunerstudio/aetuner/guided/GuidedFocusWindow.java");
        require(source.contains(
                        "import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;")
                        && source.contains(
                        "import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel;")
                        && source.contains("private final MapEstimateGuidedFocusPanel mapEstimate =")
                        && source.contains("mapEstimate.updateModel(mapEstimateModel)"),
                "production Guided Focus no longer routes MAP Estimate through the learned model/panel");
    }

    private static void hubHasNoLegacySnapshotAdapter() throws Exception {
        String hub = source("se/anders/tunerstudio/aetuner/guided/GuidedFocusHub.java");
        String plugin = source("se/anders/tunerstudio/aetuner/AeTunerPlugin.java");
        require(!hub.contains("MapEstimateFocusSnapshot")
                        && !hub.contains("legacySetupModel")
                        && hub.contains("publishMapEstimateSetup(AeProjectSnapshot snapshot")
                        && hub.contains("MapEstimateFocusModel.build("),
                "Guided Focus hub regained the retired MAP Estimate Snapshot adapter");
        require(!plugin.contains("MapEstimateFocusSnapshot")
                        && plugin.contains("GuidedFocusHub.publishMapEstimateSetup(")
                        && plugin.contains("overviewSnapshot, 20, 115.0"),
                "host idle MAP Focus setup no longer enters the new-model hub directly");
    }

    private static void probeSessionUsesDirectCollectorControllerPath() throws Exception {
        String source = source(
                "se/anders/tunerstudio/aetuner/guided/GuidedMethodProbeSession.java");
        require(!source.contains("mapEstimateFocusSnapshot(")
                        && !source.contains("MapEstimateFocusSnapshot")
                        && !source.contains("MapEstimateGuidedProbeRoute")
                        && source.contains("new MapEstimateCollector()")
                        && source.contains("new MapEstimateGuidedController(MapEstimateMemoryPaths.store())")
                        && source.contains("boolean stableAccepted = mapEstimateCollector.addSample(sample)")
                        && source.contains("mapEstimateGuided.acceptStable("),
                "real MAP Estimate probe session lost the validated collector -> learned-controller route");
    }

    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(MAIN.resolve(relative)), StandardCharsets.UTF_8);
    }

    private static void requireClassMissing(String name) throws Exception {
        try {
            Class.forName(name);
            throw new AssertionError("retired MAP Estimate type is still loadable: " + name);
        } catch (ClassNotFoundException expected) {
            // Expected permanent architecture state.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
