package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.recovery.EvidenceRecoverySnapshot;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** A queued export must capture its package before bridge retirement can reset evidence. */
public final class GuidedPendingExportRetirementRegressionTest {
    public static void main(String[] args) throws Exception {
        pendingExportBlocksRetirement(false);
        pendingExportBlocksRetirement(true);
        System.out.println("GuidedPendingExportRetirementRegressionTest passed");
    }

    private static void pendingExportBlocksRetirement(boolean reset) throws Exception {
        AtomicReference<GuidedCapturePanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new GuidedCapturePanel()));
        GuidedCapturePanel panel = panelRef.get();
        GuidedV019ProductionBridge bridge = new GuidedV019ProductionBridge(panel);
        GuidedMethodProbeSession probe = (GuidedMethodProbeSession) field(panel, "probeSession").get(panel);
        probe.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT));
        for (int i = 0; i < 3; i++) probe.accept(GuidedReviewPerformanceCharacterization.sample(1 + i * .01, true));
        probe.finish();
        String expectedCsv = probe.csvText();
        Field pending = field(panel, "exportInProgress");
        pending.set(panel, true);
        field(panel, "probeEvidenceExported").set(panel, false);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<EvidenceRecoverySnapshot.Guided> future = worker.submit(() -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("queued worker timed out");
                return panel.recoverySnapshot();
            });
            require(entered.await(5, TimeUnit.SECONDS), "worker not queued");
            SwingUtilities.invokeAndWait(() -> {
                if (reset) bridge.resetSession();
                else bridge.select(GuidedProductionTask.MAP_ESTIMATE);
            });
            require(probe.sampleCount() == 3 && probe.module().recipe() == GuidedTuningRecipe.MAP_PREDICT,
                    "bridge discarded queued export evidence");
            require(!(Boolean) field(panel, "probeEvidenceExported").get(panel),
                    "bridge marked pending evidence exported before capture");
            release.countDown();
            EvidenceRecoverySnapshot.Guided snapshot = future.get(5, TimeUnit.SECONDS);
            require(snapshot != null && ((Integer) field(snapshot, "recordCount").get(snapshot)) == 3
                    && field(snapshot, "csvText").get(snapshot).equals(expectedCsv),
                    "queued package did not retain its original evidence");
            pending.set(panel, false);
            SwingUtilities.invokeAndWait(() -> bridge.select(GuidedProductionTask.MAP_ESTIMATE));
            require(!probe.hasEvidence(), "completed export permanently blocked retirement");
            require(field(snapshot, "csvText").get(snapshot).equals(expectedCsv), "retirement changed captured package");
        } finally {
            release.countDown();
            worker.shutdownNow();
            pending.set(panel, false);
            SwingUtilities.invokeAndWait(panel::disposePanel);
        }
    }

    private static Field field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
