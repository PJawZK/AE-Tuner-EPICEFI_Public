package se.anders.tunerstudio.aetuner.recovery;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class EvidenceRecoveryStoreRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("ae-tuner-recovery-store");
        EvidenceRecoveryStore store = new EvidenceRecoveryStore(root, "run-test");

        List<TransientEvent> first = new ArrayList<TransientEvent>();
        first.add(event(1, 1.0, 4.0, 9.0));
        EvidenceRecoverySnapshot.Passive passiveOne =
                new EvidenceRecoverySnapshot.Passive(
                        "passive-test", 1L, first, "passive report one\n");
        store.writePassive(passiveOne, 0);
        store.writeRunInfo("first checkpoint", 1, 0);

        List<TransientEvent> second = new ArrayList<TransientEvent>(first);
        second.add(event(2, 2.0, 6.0, 12.0));
        EvidenceRecoverySnapshot.Passive passiveTwo =
                new EvidenceRecoverySnapshot.Passive(
                        "passive-test", 2L, second, "passive report two\n");
        store.writePassive(passiveTwo, 1);

        EvidenceRecoverySnapshot.Guided guided =
                new EvidenceRecoverySnapshot.Guided(
                        "guided-test", 2,
                        "guided report\n", "guided_header\nrow\n");
        store.writeGuided(guided);
        store.writeRunInfo("second checkpoint", 2, 2);
        store.finalizePassiveCsvFiles();

        Path passiveDirectory = store.runDirectory().resolve("passive-passive-test");
        Path combined = passiveDirectory.resolve("passive-events-recovery.csv");
        require(Files.exists(combined), "combined passive CSV was not created");
        String csv = new String(Files.readAllBytes(combined), StandardCharsets.UTF_8);
        require(count(csv, "event_index") == 1,
                "combined passive CSV must contain exactly one header");
        require(csv.contains("1,"), "first passive event missing");
        require(csv.contains("2,"), "second passive event missing");
        require(new String(Files.readAllBytes(
                        passiveDirectory.resolve("passive-report-recovery.txt")),
                        StandardCharsets.UTF_8).contains("report two"),
                "latest passive report was not atomically replaced");

        Path guidedDirectory = store.runDirectory().resolve("guided-guided-test");
        require(Files.exists(guidedDirectory.resolve("guided-report-recovery.txt")),
                "guided recovery report missing");
        require(Files.exists(guidedDirectory.resolve("guided-events-recovery.csv")),
                "guided recovery CSV missing");
        require(Files.exists(store.runDirectory().resolve(EvidenceRecoveryStore.INFO)),
                "run recovery information missing");

        Path discovered = EvidenceRecoveryStore.newestUndismissedRecovery(root, null);
        require(store.runDirectory().equals(discovered),
                "startup recovery discovery did not return the current run");
        EvidenceRecoveryStore.dismiss(discovered);
        require(EvidenceRecoveryStore.newestUndismissedRecovery(root, null) == null,
                "dismissed recovery was still surfaced");

        Path crashRun = root.resolve("run-crash-style");
        Path crashPassive = crashRun.resolve("passive-crash-session");
        Path crashChunks = crashPassive.resolve("chunks");
        Files.createDirectories(crashChunks);
        EventCsvWriter.write(crashChunks.resolve(
                "passive-events-000001-000001.csv").toFile(), first);
        require(crashRun.equals(EvidenceRecoveryStore.newestUndismissedRecovery(
                        root, null)),
                "crash-style evidence without run info was not discovered");
        EvidenceRecoveryStore.finalizeRun(crashRun);
        require(Files.exists(crashPassive.resolve("passive-events-recovery.csv")),
                "startup finalization did not create a combined crash-recovery CSV");

        System.out.println("EvidenceRecoveryStoreRegressionTest passed");
    }

    private static TransientEvent event(int index, double seconds,
                                      double startTps, double endTps) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(seconds, startTps, 50.0, 2000.0));
        samples.add(sample(seconds + 0.1, endTps, 70.0, 2100.0));
        return new TransientEvent(index, true,
                "MAP Predict + Wall Wetting event", "accepted", samples, true);
    }

    private static LiveSample sample(double seconds, double tps,
                                     double map, double rpm) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 1.0);
        values.put(ChannelRole.WALL_CORRECTION, 0.1);
        return new LiveSample((long) (seconds * 1000000000L), seconds,
                values, 50.0, 100.0);
    }

    private static int count(String text, String token) {
        int result = 0;
        int from = 0;
        while (true) {
            int next = text.indexOf(token, from);
            if (next < 0) {
                return result;
            }
            result++;
            from = next + token.length();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
