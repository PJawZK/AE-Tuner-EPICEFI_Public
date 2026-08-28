package se.anders.tunerstudio.aetuner.proposal;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class SessionExportSupportRegressionTest {
    private SessionExportSupportRegressionTest() { }

    public static void main(String[] args) throws Exception {
        visibleFolderLayoutIsSharedByAllSessionEvidence();
        finalFolderAppearsOnlyAfterSuccessfulFinish();
        cleanupNeverPublishesAnIncompleteFolder();
        System.out.println("SessionExportSupportRegressionTest passed");
    }

    private static void visibleFolderLayoutIsSharedByAllSessionEvidence()
            throws Exception {
        File selectedParent = Files.createTempDirectory("ae-export-layout-").toFile();
        try {
            File root = SessionExportSupport.exportRootUnder(selectedParent);
            require("AE Tuner Export".equals(root.getName()),
                    "selected parent did not resolve to the visible AE Tuner Export root");
            require(new File(root, "Guided Session").equals(
                            SessionExportSupport.systemDirectory(root, "guided")),
                    "Guided exports no longer have their own folder");
            require(new File(root, "Passive Session").equals(
                            SessionExportSupport.systemDirectory(root, "passive")),
                    "Passive exports no longer have their own folder");
            require(new File(root, "Last Session").equals(
                            SessionExportSupport.lastSessionDirectory(root)),
                    "automatic previous-session evidence does not share the visible export root");
            require(root.equals(SessionExportSupport.exportRootUnder(root)),
                    "choosing the AE Tuner Export folder itself created a duplicate nested root");
        } finally {
            deleteRecursively(selectedParent);
        }
    }

    private static void finalFolderAppearsOnlyAfterSuccessfulFinish() throws Exception {
        File parent = Files.createTempDirectory("ae-session-export-").toFile();
        try {
            SessionExportSupport.StagedFolder staged =
                    SessionExportSupport.stageSessionFolder(parent, "guided");
            File finalFolder = staged.target();
            require(!finalFolder.exists(),
                    "final session folder appeared before staged export finished");

            File stagedReport = staged.file("guided-report.txt");
            File stagedCsv = staged.file("guided-events.csv");
            SessionExportSupport.writeTextAtomic(stagedReport, "report\n");
            SessionExportSupport.writeTextAtomic(stagedCsv, "a,b\n1,2\n");
            require(!finalFolder.exists(),
                    "writing staged files published the final folder too early");
            require(stagedReport.isFile() && stagedCsv.isFile(),
                    "completed staged files were not retained for folder promotion");

            File published = staged.finish();
            require(published.equals(finalFolder) && published.isDirectory(),
                    "successful finish did not publish the final session folder");
            require(new File(published, "guided-report.txt").isFile()
                            && new File(published, "guided-events.csv").isFile(),
                    "published session folder lost completed staged files");
            String report = new String(Files.readAllBytes(
                    new File(published, "guided-report.txt").toPath()),
                    StandardCharsets.UTF_8);
            require("report\n".equals(report),
                    "atomic staged text write changed report content");
        } finally {
            deleteRecursively(parent);
        }
    }

    private static void cleanupNeverPublishesAnIncompleteFolder() throws Exception {
        File parent = Files.createTempDirectory("ae-session-export-fail-").toFile();
        try {
            SessionExportSupport.StagedFolder staged =
                    SessionExportSupport.stageSessionFolder(parent, "passive");
            File finalFolder = staged.target();
            File stagingFolder = staged.file("partial.txt").getParentFile();
            SessionExportSupport.writeTextAtomic(
                    staged.file("partial.txt"), "partial\n");
            require(stagingFolder.isDirectory() && !finalFolder.exists(),
                    "partial export was not isolated in the temporary folder");

            staged.cleanup();
            require(!finalFolder.exists(),
                    "cleanup incorrectly promoted a failed session export");
            require(!stagingFolder.exists(),
                    "cleanup retained the handled failed-export staging folder");
        } finally {
            deleteRecursively(parent);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
