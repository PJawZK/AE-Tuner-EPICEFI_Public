package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.guided.GuidedRuntimeReportSupport;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class SessionExportSupportRegressionTest {
    private SessionExportSupportRegressionTest() { }

    public static void main(String[] args) throws Exception {
        visibleFolderLayoutSupportsCurrentGuidedAndValidationEvidence();
        guidedSessionAndMethodEvidenceShareOneRoot();
        retiredPassiveExportIdentityStaysRetired();
        finalGuidedReportsReceiveRuntimeDiagnosticsAtPersistenceBoundary();
        finalFolderAppearsOnlyAfterSuccessfulFinish();
        cleanupNeverPublishesAnIncompleteFolder();
        System.out.println("SessionExportSupportRegressionTest passed");
    }

    private static void visibleFolderLayoutSupportsCurrentGuidedAndValidationEvidence()
            throws Exception {
        File selectedParent = Files.createTempDirectory("ae-export-layout-").toFile();
        try {
            File root = SessionExportSupport.exportRootUnder(selectedParent);
            require("AE Tuner Export".equals(root.getName()),
                    "selected parent did not resolve to the visible AE Tuner Export root");
            require(new File(root, "Guided Evidence").equals(
                            SessionExportSupport.systemDirectory(root, "guided")),
                    "Guided exports no longer have their evidence folder");
            require(new File(root, "Apply Restore Validation").equals(
                            SessionExportSupport.systemDirectory(root, "validation")),
                    "physical Apply/Restore validation evidence does not have a visible dedicated folder");
            require(new File(root, "Last Session").equals(
                            SessionExportSupport.lastSessionDirectory(root)),
                    "automatic previous-session evidence does not share the visible export root");
            require(root.equals(SessionExportSupport.exportRootUnder(root)),
                    "choosing the AE Tuner Export folder itself created a duplicate nested root");
        } finally {
            deleteRecursively(selectedParent);
        }
    }

    private static void guidedSessionAndMethodEvidenceShareOneRoot() throws Exception {
        File selectedParent = Files.createTempDirectory("ae-guided-export-layout-").toFile();
        try {
            File root = SessionExportSupport.exportRootUnder(selectedParent);
            File expected = new File(root, "Guided Evidence");
            require(expected.equals(SessionExportSupport.systemDirectory(root, "guided")),
                    "controlled Guided session did not resolve to Guided Evidence");
            require(expected.equals(SessionExportSupport.systemDirectory(root, "Guided Session")),
                    "legacy Guided Session name did not resolve to Guided Evidence");
            require(expected.equals(SessionExportSupport.systemDirectory(root, "Guided method evidence")),
                    "Guided method evidence still routes to a parallel Session folder");
            require(expected.equals(SessionExportSupport.systemDirectory(root, "Guided Evidence")),
                    "canonical Guided Evidence name did not resolve to its own root");
        } finally {
            deleteRecursively(selectedParent);
        }
    }

    private static void retiredPassiveExportIdentityStaysRetired() throws Exception {
        File selectedParent = Files.createTempDirectory("ae-retired-passive-export-").toFile();
        try {
            File root = SessionExportSupport.exportRootUnder(selectedParent);
            require(new File(root, "Session").equals(
                            SessionExportSupport.systemDirectory(root, "passive")),
                    "retired Passive export identity regained a dedicated folder");
            require(!"Passive Session".equals(
                            SessionExportSupport.systemDirectory(root, "passive").getName()),
                    "retired Passive Session compatibility folder returned");
        } finally {
            deleteRecursively(selectedParent);
        }
    }

    private static void finalGuidedReportsReceiveRuntimeDiagnosticsAtPersistenceBoundary()
            throws Exception {
        File parent = Files.createTempDirectory("ae-guided-report-runtime-").toFile();
        try {
            File report = new File(parent, "guided-report.txt");
            File method = new File(parent, "guided-method-report.txt");
            File evidence = new File(parent, "guided-events.csv");

            SessionExportSupport.writeTextAtomic(report, "guided report\n");
            SessionExportSupport.writeTextAtomic(method, "guided method report\n");
            SessionExportSupport.writeTextAtomic(evidence, "a,b\n1,2\n");

            String reportText = read(report);
            String methodText = read(method);
            String evidenceText = read(evidence);
            require(reportText.startsWith("guided report\n")
                            && reportText.contains(GuidedRuntimeReportSupport.HEADING),
                    "normal Guided report is missing runtime performance diagnostics");
            require(methodText.startsWith("guided method report\n")
                            && methodText.contains(GuidedRuntimeReportSupport.HEADING),
                    "Guided method report is missing runtime performance diagnostics");
            require(count(reportText, GuidedRuntimeReportSupport.HEADING) == 1
                            && count(methodText, GuidedRuntimeReportSupport.HEADING) == 1,
                    "runtime diagnostics were appended more than once");
            require("a,b\n1,2\n".equals(evidenceText),
                    "runtime diagnostics leaked into non-report evidence payloads");
        } finally {
            deleteRecursively(parent);
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
            String report = read(new File(published, "guided-report.txt"));
            require(report.startsWith("report\n")
                            && report.contains(GuidedRuntimeReportSupport.HEADING),
                    "published Guided report lost persistence-boundary runtime diagnostics");
        } finally {
            deleteRecursively(parent);
        }
    }

    private static void cleanupNeverPublishesAnIncompleteFolder() throws Exception {
        File parent = Files.createTempDirectory("ae-session-export-fail-").toFile();
        try {
            SessionExportSupport.StagedFolder staged =
                    SessionExportSupport.stageSessionFolder(parent, "guided");
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

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static int count(String text, String token) {
        int result = 0;
        int from = 0;
        while (true) {
            int next = text.indexOf(token, from);
            if (next < 0) return result;
            result++;
            from = next + token.length();
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
