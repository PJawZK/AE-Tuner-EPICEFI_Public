package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.nio.file.Path;
import java.nio.file.Paths;
import se.anders.tunerstudio.aetuner.proposal.SessionExportSupport;

/** Resolves persistent Guided learned-state storage separately from session/recovery evidence. */
public final class MapEstimateMemoryPaths {
    public static final String MEMORY_FOLDER_NAME = "Memory";
    private static final String MEMORY_ROOT_PROPERTY = "ae.tuner.memory.dir";

    private MapEstimateMemoryPaths() { }

    public static Path memoryDirectory() {
        String override = System.getProperty(MEMORY_ROOT_PROPERTY);
        if (override != null && override.trim().length() > 0) {
            return Paths.get(override.trim());
        }
        return SessionExportSupport.exportRoot().toPath().resolve(MEMORY_FOLDER_NAME);
    }

    public static MapEstimateMemoryStore store() {
        return new MapEstimateMemoryStore(memoryDirectory());
    }
}
