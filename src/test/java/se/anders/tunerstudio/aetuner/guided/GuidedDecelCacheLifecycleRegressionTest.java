package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.*;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** Decel live cache retirement is separate from operator-owned historical staging. */
public final class GuidedDecelCacheLifecycleRegressionTest {
    public static void main(String[] args) throws Exception {
        DecelDetectionMethodModule module = (DecelDetectionMethodModule)
                GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION);
        try {
            GuidedAeMethodModules.clearLifecycleCaches();
            String initialLive = field(module, "lastEvidenceReview").toString();
            AeProjectSnapshot tune = stage();
            module.reviewedWritePlan(tune, Collections.emptyList());
            require(!field(module, "lastEvidenceReview").equals(initialLive)
                            && !field(module, "lastHistoricalReview").toString().isEmpty(),
                    "fixture did not populate both Decel reviews");
            GuidedAeMethodModules.clearLifecycleCaches();
            require(field(module, "lastEvidenceReview").equals(initialLive)
                            && (Boolean) field(module, "reviewDirty")
                            && field(module, "lastHistoricalReview").toString().isEmpty(),
                    "final module-cache retirement leaked live or historical Decel review");
            require(DecelHistoricalThresholdProposalContext.active(),
                    "module cache cleanup silently discarded independently staged evidence");
            require(module.explicitSettingWritePlan(tune) != null,
                    "retained historical evidence could not be freshly evaluated");
            DecelHistoricalThresholdProposalContext.clear();
            require(module.explicitSettingWritePlan(tune) == null
                            && field(module, "lastHistoricalReview").toString().isEmpty(),
                    "cleared historical staging remained authoritative");
            stage();
            require(module.explicitSettingWritePlan(null) == null
                            && !DecelHistoricalThresholdProposalContext.active(),
                    "missing/replaced Working Tune kept historical authority");
            bridgeResetClearsStaging();
        } finally {
            DecelHistoricalResultContext.clear();
            DecelHistoricalThresholdProposalContext.clear();
            GuidedV019HistoricalReplayContext.clear();
            GuidedAeMethodModules.clearLifecycleCaches();
        }
        System.out.println("GuidedDecelCacheLifecycleRegressionTest passed");
    }

    private static void bridgeResetClearsStaging() throws Exception {
        AtomicReference<GuidedCapturePanel> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(new GuidedCapturePanel()));
        GuidedCapturePanel panel = ref.get();
        try {
            GuidedV019ProductionBridge bridge = new GuidedV019ProductionBridge(panel);
            SwingUtilities.invokeAndWait(() -> bridge.select(GuidedProductionTask.DECEL_DETECTION));
            stage();
            SwingUtilities.invokeAndWait(bridge::resetSession);
            require(!DecelHistoricalThresholdProposalContext.active()
                            && !DecelHistoricalResultContext.available(),
                    "Decel Reset Session retained historical result or proposal authority");
        } finally { SwingUtilities.invokeAndWait(panel::disposePanel); }
    }
    private static AeProjectSnapshot stage() throws Exception {
        Method stage = GuidedHistoricalRecoveryLifecycleRegressionTest.class.getDeclaredMethod("stageHistorical");
        stage.setAccessible(true);
        return (AeProjectSnapshot) stage.invoke(null);
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
