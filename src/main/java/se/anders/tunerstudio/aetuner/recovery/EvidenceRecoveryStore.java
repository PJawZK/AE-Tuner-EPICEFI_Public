package se.anders.tunerstudio.aetuner.recovery;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.guided.GuidedRuntimeReportSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local-only atomic persistence for current Guided evidence recovery. */
final class EvidenceRecoveryStore {
    static final String DISMISSED = ".dismissed";
    static final String INFO = "RECOVERY_INFO.txt";
    private static final String GUIDED_PREFIX = "guided-";

    private final Path root;
    private final Path runDirectory;

    EvidenceRecoveryStore(Path root, String runId) {
        this.root = root;
        this.runDirectory = root.resolve(EvidenceRecoverySnapshot.safeKey(runId, "run"));
    }

    Path root() { return root; }
    Path runDirectory() { return runDirectory; }

    void writeGuided(EvidenceRecoverySnapshot.Guided snapshot) throws IOException {
        if (snapshot == null) return;
        Path directory = runDirectory.resolve(GUIDED_PREFIX + snapshot.sessionKey);
        Files.createDirectories(directory);
        writeAtomic(directory.resolve("guided-report-recovery.txt"),
                GuidedRuntimeReportSupport.append(snapshot.reportText));
        writeAtomic(directory.resolve("guided-events-recovery.csv"), snapshot.csvText);
        writeAtomic(directory.resolve("GUIDED_SESSION_INFO.txt"),
                "Plugin version: " + AeTunerPlugin.VERSION + "\n"
                        + "Session key: " + snapshot.sessionKey + "\n"
                        + "Completed outcomes: " + snapshot.recordCount + "\n"
                        + "Updated: " + now() + "\n"
                        + "Read-only local recovery; no ECU value was changed.\n");
    }

    void writeRunInfo(String reason, int guidedRecords) throws IOException {
        Files.createDirectories(runDirectory);
        writeAtomic(runDirectory.resolve(INFO),
                "AE Tuner automatic Guided evidence recovery\n"
                        + "Plugin version: " + AeTunerPlugin.VERSION + "\n"
                        + "Checkpoint reason: " + safe(reason) + "\n"
                        + "Guided outcomes captured: " + guidedRecords + "\n"
                        + "Updated: " + now() + "\n\n"
                        + "These files are local recovery copies. They do not modify ECU RAM or flash.\n"
                        + "Use Guided Export for the final evidence package.\n");
    }

    static Path newestUndismissedRecovery(Path root, Path exclude) {
        if (root == null || !Files.isDirectory(root)) return null;
        List<Path> candidates = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "run-*")) {
            for (Path path : stream) {
                if (!Files.isDirectory(path) || path.equals(exclude)
                        || Files.exists(path.resolve(DISMISSED))) continue;
                if (Files.exists(path.resolve(INFO)) || hasGuidedEvidenceDirectory(path)) {
                    candidates.add(path);
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        if (candidates.isEmpty()) return null;
        Collections.sort(candidates, (left, right) -> {
            try {
                return Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left));
            } catch (IOException ex) {
                return right.toString().compareTo(left.toString());
            }
        });
        return candidates.get(0);
    }

    private static boolean hasGuidedEvidenceDirectory(Path run) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(run)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)
                        && path.getFileName().toString().startsWith(GUIDED_PREFIX)) return true;
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    static void dismiss(Path directory) throws IOException {
        if (directory == null) return;
        Files.write(directory.resolve(DISMISSED),
                ("Dismissed: " + now() + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static void cleanup(Path root, int keepRuns) {
        if (root == null || !Files.isDirectory(root)) return;
        List<Path> runs = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "run-*")) {
            for (Path path : stream) if (Files.isDirectory(path)) runs.add(path);
        } catch (IOException ignored) {
            return;
        }
        Collections.sort(runs, (left, right) -> {
            try {
                return Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left));
            } catch (IOException ex) {
                return right.toString().compareTo(left.toString());
            }
        });
        for (int i = Math.max(1, keepRuns); i < runs.size(); i++) deleteTree(runs.get(i));
    }

    private static void deleteTree(Path root) {
        try {
            List<Path> paths = new ArrayList<Path>();
            Files.walk(root).forEach(paths::add);
            Collections.sort(paths, Comparator.reverseOrder());
            for (Path path : paths) Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Retention cleanup must never prevent plugin startup.
        }
    }

    static void writeAtomic(Path target, String text) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = temporary(target);
        Files.write(temporary, (text == null ? "" : text).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        moveAtomic(temporary, target);
    }

    private static Path temporary(Path target) {
        return target.resolveSibling(target.getFileName().toString() + ".tmp");
    }

    private static void moveAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
    }
}
