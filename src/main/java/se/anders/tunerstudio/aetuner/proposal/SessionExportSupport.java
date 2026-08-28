package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.model.TransientEvent;

import java.awt.Component;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;

/** Shared safe writer for Passive and Guided session exports. */
public final class SessionExportSupport {
    static final String EXPORT_ROOT_NAME = "AE Tuner Export";
    static final String GUIDED_FOLDER_NAME = "Guided Session";
    static final String PASSIVE_FOLDER_NAME = "Passive Session";
    static final String LAST_SESSION_FOLDER_NAME = "Last Session";
    private static final String EXPORT_ROOT_PROPERTY = "ae.tuner.export.dir";
    private static final String PREF_EXPORT_ROOT = "exportRoot";
    private static final Preferences PREFERENCES =
            Preferences.userNodeForPackage(SessionExportSupport.class);

    private SessionExportSupport() { }

    /**
     * Normal user exports share one visible root:
     * AE Tuner Export/Guided Session and AE Tuner Export/Passive Session.
     * The chosen root is remembered so automatic recovery can use the sibling
     * AE Tuner Export/Last Session location on the next plugin startup.
     */
    public static File chooseParent(Component parent, String systemName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose parent folder for " + EXPORT_ROOT_NAME);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        File currentRoot = exportRoot();
        File currentParent = currentRoot.getParentFile();
        if (currentParent != null && currentParent.isDirectory()) {
            chooser.setCurrentDirectory(currentParent);
        }
        chooser.setSelectedFile(currentRoot);

        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File root = exportRootUnder(chooser.getSelectedFile());
        File systemFolder = systemDirectory(root, systemName);
        try {
            ensureDirectory(root);
            ensureDirectory(systemFolder);
            rememberExportRoot(root);
            return systemFolder;
        } catch (IOException ex) {
            return null;
        }
    }

    /** Visible root used by automatic recovery when no test override is set. */
    public static File exportRoot() {
        String override = System.getProperty(EXPORT_ROOT_PROPERTY);
        if (override != null && override.trim().length() > 0) {
            return new File(override.trim());
        }
        String remembered = "";
        try {
            remembered = PREFERENCES.get(PREF_EXPORT_ROOT, "");
        } catch (RuntimeException ignored) {
            // Preferences are convenience only; export must still work without them.
        }
        if (remembered != null && remembered.trim().length() > 0) {
            return new File(remembered.trim());
        }
        String home = System.getProperty("user.home", ".");
        return new File(home, EXPORT_ROOT_NAME);
    }

    /** Visible sibling used for automatic previous-session recovery evidence. */
    public static File lastSessionDirectory() {
        return lastSessionDirectory(exportRoot());
    }

    static File exportRootUnder(File selected) {
        if (selected == null) {
            String home = System.getProperty("user.home", ".");
            return new File(home, EXPORT_ROOT_NAME);
        }
        if (EXPORT_ROOT_NAME.equalsIgnoreCase(selected.getName())) {
            return selected;
        }
        return new File(selected, EXPORT_ROOT_NAME);
    }

    static File systemDirectory(File root, String systemName) {
        String value = systemName == null ? "" : systemName.trim();
        String folder;
        if ("guided".equalsIgnoreCase(value)) {
            folder = GUIDED_FOLDER_NAME;
        } else if ("passive".equalsIgnoreCase(value)) {
            folder = PASSIVE_FOLDER_NAME;
        } else {
            folder = "Session";
        }
        return new File(root, folder);
    }

    static File lastSessionDirectory(File root) {
        return new File(root, LAST_SESSION_FOLDER_NAME);
    }

    private static void rememberExportRoot(File root) {
        if (root == null) return;
        try {
            PREFERENCES.put(PREF_EXPORT_ROOT, root.getAbsolutePath());
        } catch (RuntimeException ignored) {
            // A denied preferences store must never make the actual export fail.
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory.isDirectory()) return;
        if (directory.exists() || !directory.mkdirs()) {
            throw new IOException("Could not create export folder: " + directory);
        }
    }

    public static StagedFolder stageSessionFolder(File parent, String systemName)
            throws IOException {
        if (parent == null) throw new IOException("No export parent folder selected");
        if (!parent.isDirectory()) {
            throw new IOException("Selected export parent is not a folder: " + parent);
        }
        String safeSystem = systemName == null ? "session"
                : systemName.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-");
        String base = "ae-tuner-" + safeSystem + "-session-"
                + AeTunerPlugin.VERSION + "-" + timestamp();
        File target = new File(parent, base);
        int suffix = 2;
        while (target.exists()
                || new File(parent, "." + target.getName() + ".tmp").exists()) {
            target = new File(parent, base + "-" + suffix++);
        }
        File staging = new File(parent, "." + target.getName() + ".tmp");
        if (!staging.mkdirs()) {
            throw new IOException("Could not create temporary export folder: " + staging);
        }
        return new StagedFolder(staging, target);
    }

    public static void writeTextAtomic(File target, String text) throws IOException {
        Path temporary = temporaryPath(target);
        try {
            BufferedWriter writer = Files.newBufferedWriter(
                    temporary, StandardCharsets.UTF_8);
            try {
                writer.write(text == null ? "" : text);
            } finally {
                writer.close();
            }
            promote(temporary, target.toPath());
        } catch (IOException ex) {
            Files.deleteIfExists(temporary);
            throw ex;
        }
    }

    public static void writeEventsCsvAtomic(File target,
                                             List<TransientEvent> events)
            throws IOException {
        Path temporary = temporaryPath(target);
        try {
            EventCsvWriter.write(temporary.toFile(), events);
            promote(temporary, target.toPath());
        } catch (IOException ex) {
            Files.deleteIfExists(temporary);
            throw ex;
        }
    }

    public static double elapsedMillis(long startedNano) {
        return Math.max(0L, System.nanoTime() - startedNano) / 1000000.0;
    }

    private static Path temporaryPath(File target) throws IOException {
        File parent = target == null ? null : target.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IOException("Export folder is unavailable");
        }
        Path temporary = new File(parent, "." + target.getName() + ".tmp").toPath();
        Files.deleteIfExists(temporary);
        return temporary;
    }

    private static void promote(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    public static final class StagedFolder {
        private final File staging;
        private final File target;
        private boolean finished;

        private StagedFolder(File staging, File target) {
            this.staging = staging;
            this.target = target;
        }

        public File file(String name) {
            return new File(staging, name);
        }

        public File finish() throws IOException {
            if (finished) return target;
            try {
                Files.move(staging.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(staging.toPath(), target.toPath());
            }
            finished = true;
            return target;
        }

        public File target() {
            return target;
        }

        public void cleanup() {
            if (finished || !staging.exists()) return;
            File[] children = staging.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isFile()) child.delete();
                }
            }
            staging.delete();
        }
    }
}
