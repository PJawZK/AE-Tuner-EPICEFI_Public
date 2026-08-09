package se.anders.tunerstudio.aetuner.recovery;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

/** Local-only atomic/chunked persistence for automatic evidence recovery. */
final class EvidenceRecoveryStore {
    static final String DISMISSED = ".dismissed";
    static final String INFO = "RECOVERY_INFO.txt";
    private static final String PASSIVE_PREFIX = "passive-";
    private static final String GUIDED_PREFIX = "guided-";

    private final Path root;
    private final Path runDirectory;

    EvidenceRecoveryStore(Path root, String runId) {
        this.root = root;
        this.runDirectory = root.resolve(EvidenceRecoverySnapshot.safeKey(runId, "run"));
    }

    Path root() {
        return root;
    }

    Path runDirectory() {
        return runDirectory;
    }

    void writePassive(EvidenceRecoverySnapshot.Passive snapshot,
                      int firstUnwrittenEvent) throws IOException {
        if (snapshot == null) {
            return;
        }
        Path directory = runDirectory.resolve(PASSIVE_PREFIX + snapshot.sessionKey);
        Files.createDirectories(directory);
        writeAtomic(directory.resolve("passive-report-recovery.txt"),
                snapshot.reportText);

        int from = Math.max(0, Math.min(firstUnwrittenEvent, snapshot.events.size()));
        if (from < snapshot.events.size()) {
            Path chunks = directory.resolve("chunks");
            Files.createDirectories(chunks);
            List<TransientEvent> next = new ArrayList<TransientEvent>(
                    snapshot.events.subList(from, snapshot.events.size()));
            String name = String.format(Locale.US,
                    "passive-events-%06d-%06d.csv", from + 1,
                    snapshot.events.size());
            Path target = chunks.resolve(name);
            Path temporary = temporary(target);
            EventCsvWriter.write(temporary.toFile(), next);
            moveAtomic(temporary, target);
        }
        writeAtomic(directory.resolve("PASSIVE_SESSION_INFO.txt"),
                "Plugin version: " + AeTunerPlugin.VERSION + "\n"
                        + "Session key: " + snapshot.sessionKey + "\n"
                        + "Captured events: " + snapshot.events.size() + "\n"
                        + "Revision: " + snapshot.revision + "\n"
                        + "Updated: " + now() + "\n"
                        + "Read-only local recovery; no ECU value was changed.\n");
    }

    void writeGuided(EvidenceRecoverySnapshot.Guided snapshot) throws IOException {
        if (snapshot == null) {
            return;
        }
        Path directory = runDirectory.resolve(GUIDED_PREFIX + snapshot.sessionKey);
        Files.createDirectories(directory);
        writeAtomic(directory.resolve("guided-report-recovery.txt"),
                snapshot.reportText);
        writeAtomic(directory.resolve("guided-events-recovery.csv"),
                snapshot.csvText);
        writeAtomic(directory.resolve("GUIDED_SESSION_INFO.txt"),
                "Plugin version: " + AeTunerPlugin.VERSION + "\n"
                        + "Session key: " + snapshot.sessionKey + "\n"
                        + "Completed outcomes: " + snapshot.recordCount + "\n"
                        + "Updated: " + now() + "\n"
                        + "Read-only local recovery; no ECU value was changed.\n");
    }

    void writeRunInfo(String reason, int passiveEvents, int guidedRecords)
            throws IOException {
        Files.createDirectories(runDirectory);
        writeAtomic(runDirectory.resolve(INFO),
                "AE Tuner automatic evidence recovery\n"
                        + "Plugin version: " + AeTunerPlugin.VERSION + "\n"
                        + "Checkpoint reason: " + safe(reason) + "\n"
                        + "Passive events captured: " + passiveEvents + "\n"
                        + "Guided outcomes captured: " + guidedRecords + "\n"
                        + "Updated: " + now() + "\n\n"
                        + "These files are local recovery copies. They do not modify ECU RAM or flash.\n"
                        + "Use the normal Save buttons for the final evidence package.\n");
    }

    void finalizePassiveCsvFiles() throws IOException {
        finalizeRun(runDirectory);
    }

    static void finalizeRun(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(
                directory, PASSIVE_PREFIX + "*")) {
            for (Path passiveDirectory : directories) {
                if (Files.isDirectory(passiveDirectory)) {
                    combineChunks(passiveDirectory);
                }
            }
        }
    }

    private static void combineChunks(Path passiveDirectory) throws IOException {
        Path chunks = passiveDirectory.resolve("chunks");
        if (!Files.isDirectory(chunks)) {
            return;
        }
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(chunks, "*.csv")) {
            for (Path file : stream) {
                files.add(file);
            }
        }
        if (files.isEmpty()) {
            return;
        }
        Collections.sort(files, Comparator.comparing(path -> path.getFileName().toString()));
        Path target = passiveDirectory.resolve("passive-events-recovery.csv");
        Path temporary = temporary(target);
        try (BufferedWriter writer = Files.newBufferedWriter(temporary,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            boolean headerWritten = false;
            for (Path chunk : files) {
                try (BufferedReader reader = Files.newBufferedReader(chunk,
                        StandardCharsets.UTF_8)) {
                    String line;
                    boolean first = true;
                    while ((line = reader.readLine()) != null) {
                        if (first) {
                            first = false;
                            if (headerWritten) {
                                continue;
                            }
                            headerWritten = true;
                        }
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
        }
        moveAtomic(temporary, target);
    }

    static Path newestUndismissedRecovery(Path root, Path exclude) {
        if (root == null || !Files.isDirectory(root)) {
            return null;
        }
        List<Path> candidates = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "run-*")) {
            for (Path path : stream) {
                if (!Files.isDirectory(path) || path.equals(exclude)
                        || Files.exists(path.resolve(DISMISSED))) {
                    continue;
                }
                if (Files.exists(path.resolve(INFO)) || hasEvidenceDirectory(path)) {
                    candidates.add(path);
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Collections.sort(candidates, (left, right) -> {
            try {
                return Files.getLastModifiedTime(right).compareTo(
                        Files.getLastModifiedTime(left));
            } catch (IOException ex) {
                return right.toString().compareTo(left.toString());
            }
        });
        return candidates.get(0);
    }

    private static boolean hasEvidenceDirectory(Path run) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(run)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (Files.isDirectory(path)
                        && (name.startsWith(PASSIVE_PREFIX)
                        || name.startsWith(GUIDED_PREFIX))) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    static void dismiss(Path directory) throws IOException {
        if (directory == null) {
            return;
        }
        Files.write(directory.resolve(DISMISSED),
                ("Dismissed: " + now() + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static void cleanup(Path root, int keepRuns) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        List<Path> runs = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "run-*")) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    runs.add(path);
                }
            }
        } catch (IOException ignored) {
            return;
        }
        Collections.sort(runs, (left, right) -> {
            try {
                return Files.getLastModifiedTime(right).compareTo(
                        Files.getLastModifiedTime(left));
            } catch (IOException ex) {
                return right.toString().compareTo(left.toString());
            }
        });
        for (int i = Math.max(1, keepRuns); i < runs.size(); i++) {
            deleteTree(runs.get(i));
        }
    }

    private static void deleteTree(Path root) {
        try {
            List<Path> paths = new ArrayList<Path>();
            Files.walk(root).forEach(paths::add);
            Collections.sort(paths, Comparator.reverseOrder());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Retention cleanup must never prevent plugin startup.
        }
    }

    static void writeAtomic(Path target, String text) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = temporary(target);
        Files.write(temporary, (text == null ? "" : text)
                        .getBytes(StandardCharsets.UTF_8),
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
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .format(new Date());
    }
}
