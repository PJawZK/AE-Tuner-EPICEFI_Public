package se.anders.tunerstudio.aetuner;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Permanent regression for the Phase F package-organization boundary. */
public final class PhaseFPackageArchitectureTest {
    private PhaseFPackageArchitectureTest() { }

    public static void main(String[] args) throws Exception {
        rootKeepsOnlyTunerStudioEntrypoint();
        sevenSubsystemPackagesExist();
        keyRuntimeTypesLiveInTheirOwningPackages();
        oldFlatRuntimeTypesAreGone();
        System.out.println("PhaseFPackageArchitectureTest passed");
    }

    private static void rootKeepsOnlyTunerStudioEntrypoint() throws Exception {
        require("se.anders.tunerstudio.aetuner.AeTunerPlugin".equals(AeTunerPlugin.class.getName()),
                "TunerStudio ApplicationPlugin FQCN changed");
        Path root = Paths.get("src/main/java/se/anders/tunerstudio/aetuner");
        List<String> rootJava = new ArrayList<String>();
        DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.java");
        try {
            for (Path path : stream) rootJava.add(path.getFileName().toString());
        } finally {
            stream.close();
        }
        Collections.sort(rootJava);
        require(rootJava.size() == 1 && "AeTunerPlugin.java".equals(rootJava.get(0)),
                "root package must contain only AeTunerPlugin.java, found " + rootJava);
    }

    private static void sevenSubsystemPackagesExist() {
        String[] packages = new String[]{
                "host", "passive", "guided", "model", "proposal", "recovery", "ui"
        };
        for (String name : packages) {
            Path dir = Paths.get("src/main/java/se/anders/tunerstudio/aetuner", name);
            require(Files.isDirectory(dir), "missing Phase F package directory " + name);
        }
    }

    private static void keyRuntimeTypesLiveInTheirOwningPackages() throws Exception {
        assertLoadable("se.anders.tunerstudio.aetuner.host.AeControllerBridge");
        assertLoadable("se.anders.tunerstudio.aetuner.passive.AeTunerPanel");
        assertLoadable("se.anders.tunerstudio.aetuner.guided.GuidedCapturePanel");
        assertLoadable("se.anders.tunerstudio.aetuner.guided.GuidedSampleDispatcher");
        assertLoadable("se.anders.tunerstudio.aetuner.model.LiveSample");
        assertLoadable("se.anders.tunerstudio.aetuner.model.TransientEvent");
        assertLoadable("se.anders.tunerstudio.aetuner.proposal.MapBlendSuggestion");
        assertLoadable("se.anders.tunerstudio.aetuner.recovery.EvidenceRecoveryManager");
        assertLoadable("se.anders.tunerstudio.aetuner.ui.WrapLayout");
    }

    private static void oldFlatRuntimeTypesAreGone() {
        assertMissing("se.anders.tunerstudio.aetuner.AeTunerPanel");
        assertMissing("se.anders.tunerstudio.aetuner.GuidedCapturePanel");
        assertMissing("se.anders.tunerstudio.aetuner.GuidedSampleDispatcher");
        assertMissing("se.anders.tunerstudio.aetuner.LiveSample");
        assertMissing("se.anders.tunerstudio.aetuner.TransientEvent");
    }

    private static void assertLoadable(String name) throws Exception {
        Class.forName(name);
    }

    private static void assertMissing(String name) {
        try {
            Class.forName(name);
            throw new AssertionError("old flat-package class is still compiled: " + name);
        } catch (ClassNotFoundException expected) {
            // Desired Phase F architecture.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
