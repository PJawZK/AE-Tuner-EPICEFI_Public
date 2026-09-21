package se.anders.tunerstudio.aetuner.recovery;

import se.anders.tunerstudio.aetuner.guided.GuidedRuntimeReportSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EvidenceRecoveryStoreRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("ae-tuner-recovery-store");
        EvidenceRecoveryStore store = new EvidenceRecoveryStore(root, "run-test");

        EvidenceRecoverySnapshot.Guided first =
                new EvidenceRecoverySnapshot.Guided(
                        "guided-test", 1,
                        "guided report one\n",
                        "record_index,value\n1,first\n");
        store.writeGuided(first);
        store.writeRunInfo("first checkpoint", 1);

        EvidenceRecoverySnapshot.Guided second =
                new EvidenceRecoverySnapshot.Guided(
                        "guided-test", 2,
                        "guided report two\n",
                        "record_index,value\n1,first\n2,second\n");
        store.writeGuided(second);
        store.writeRunInfo("second checkpoint", 2);

        Path guidedDirectory = store.runDirectory().resolve("guided-guided-test");
        Path reportPath = guidedDirectory.resolve("guided-report-recovery.txt");
        Path csvPath = guidedDirectory.resolve("guided-events-recovery.csv");
        Path sessionInfoPath = guidedDirectory.resolve("GUIDED_SESSION_INFO.txt");

        require(Files.exists(reportPath), "guided recovery report missing");
        require(Files.exists(csvPath), "guided recovery CSV missing");
        require(Files.exists(sessionInfoPath), "guided recovery session info missing");
        require(Files.exists(store.runDirectory().resolve(EvidenceRecoveryStore.INFO)),
                "run recovery information missing");

        String report = read(reportPath);
        require(report.contains("guided report two"),
                "latest Guided report was not atomically replaced");
        require(!report.contains("guided report one"),
                "older Guided report content survived atomic replacement");
        require(report.contains(GuidedRuntimeReportSupport.HEADING),
                "Guided recovery report is missing runtime performance diagnostics");

        String csv = read(csvPath);
        require(count(csv, "record_index") == 1,
                "Guided recovery CSV must contain exactly one header");
        require(csv.contains("1,first"), "first Guided record missing");
        require(csv.contains("2,second"), "second Guided record missing");

        String sessionInfo = read(sessionInfoPath);
        require(sessionInfo.contains("Completed outcomes: 2"),
                "Guided session info did not persist the latest outcome count");
        String runInfo = read(store.runDirectory().resolve(EvidenceRecoveryStore.INFO));
        require(runInfo.contains("Guided outcomes captured: 2"),
                "run recovery information did not persist the latest Guided count");

        Path discovered = EvidenceRecoveryStore.newestUndismissedRecovery(root, null);
        require(store.runDirectory().equals(discovered),
                "startup recovery discovery did not return the current Guided run");
        EvidenceRecoveryStore.dismiss(discovered);
        require(EvidenceRecoveryStore.newestUndismissedRecovery(root, null) == null,
                "dismissed recovery was still surfaced");

        Path crashRun = root.resolve("run-crash-style");
        Path crashGuided = crashRun.resolve("guided-crash-session");
        Files.createDirectories(crashGuided);
        Path crashCsv = crashGuided.resolve("guided-events-recovery.csv");
        Files.write(crashCsv,
                "record_index,value\n1,crash\n".getBytes(StandardCharsets.UTF_8));
        require(crashRun.equals(EvidenceRecoveryStore.newestUndismissedRecovery(
                        root, null)),
                "crash-style Guided evidence without run info was not discovered");
        require(Files.exists(crashCsv),
                "startup discovery must preserve already-atomic Guided recovery evidence");

        System.out.println("EvidenceRecoveryStoreRegressionTest passed");
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
