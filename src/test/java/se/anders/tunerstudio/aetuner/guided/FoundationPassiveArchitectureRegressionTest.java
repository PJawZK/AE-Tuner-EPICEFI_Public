package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.EngagementDetectionMethodModule;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Permanent architecture gate for the retired Foundation 1 controlled sweep. */
public final class FoundationPassiveArchitectureRegressionTest {
    private static final String SWEEP_RUNTIME =
            "se.anders.tunerstudio.aetuner.guided.EngagementDeltaWindowSweepRuntime";
    private static final String LIFECYCLE_GUARD =
            "se.anders.tunerstudio.aetuner.guided.EngagementDeltaWindowLifecycleGuard";

    private FoundationPassiveArchitectureRegressionTest() { }

    public static void main(String[] args) throws Exception {
        retiredClassesCannotLoad();
        mainSourceCannotReferenceRetiredClasses();
        passiveFocusHasNoSweepStateOrApi();
        foundationCaptureDeclaresNoExplicitRoadWrite();
        System.out.println("FoundationPassiveArchitectureRegressionTest passed");
    }

    private static void retiredClassesCannotLoad() {
        requireClassAbsent(SWEEP_RUNTIME);
        requireClassAbsent(LIFECYCLE_GUARD);
    }

    private static void mainSourceCannotReferenceRetiredClasses() throws IOException {
        Path main = Paths.get("src", "main", "java");
        require(!containsJavaText(main, "EngagementDeltaWindowSweepRuntime"),
                "production source still references the retired controlled-sweep runtime");
        require(!containsJavaText(main, "EngagementDeltaWindowLifecycleGuard"),
                "production source still references the retired controlled-sweep lifecycle guard");
    }

    private static void passiveFocusHasNoSweepStateOrApi() {
        for (Field field : EngagementFocusModel.class.getDeclaredFields()) {
            require(!field.getName().toLowerCase(java.util.Locale.ROOT).contains("sweep"),
                    "EngagementFocusModel regained sweep state: " + field.getName());
        }
        for (Field field : EngagementDetectionGuidedFocusPanel.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("sweep")
                            && !name.contains("requestedDeltaWindow".toLowerCase(java.util.Locale.ROOT))
                            && !name.contains("rpmStartingPoint".toLowerCase(java.util.Locale.ROOT)),
                    "passive Focus regained controlled-sweep/manual candidate state: " + field.getName());
        }
        for (Method method : EngagementDetectionGuidedFocusPanel.class.getDeclaredMethods()) {
            require(!method.getName().toLowerCase(java.util.Locale.ROOT).contains("sweep"),
                    "passive Focus regained a sweep-specific API: " + method.getName());
        }
    }

    private static void foundationCaptureDeclaresNoExplicitRoadWrite() {
        EngagementDetectionMethodModule module = new EngagementDetectionMethodModule();
        require(module.explicitSettingWritePlan(null) == null,
                "Foundation 1 capture regained an explicit controller write plan");
        require(module.setupGuidance().contains("No temporary timing write occurs during capture"),
                "Foundation 1 setup no longer states the read-only capture boundary");
        require(module.accumulationPlan().contains("No controller writes occur during capture"),
                "Foundation 1 accumulation plan no longer states the read-only capture boundary");
    }

    private static boolean containsJavaText(Path root, String needle) throws IOException {
        if (!Files.isDirectory(root)) return false;
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .anyMatch(path -> contains(path, needle));
        }
    }

    private static boolean contains(Path path, String needle) {
        try {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return text.contains(needle);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not inspect " + path, ex);
        }
    }

    private static void requireClassAbsent(String className) {
        try {
            Class.forName(className);
            throw new AssertionError("retired Foundation 1 class is still loadable: " + className);
        } catch (ClassNotFoundException expected) {
            // Required: controlled-sweep production classes must not ship.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
