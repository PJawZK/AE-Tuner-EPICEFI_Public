package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.guided.method.*;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.recovery.EvidenceRecoveryManager;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Real plugin lifecycle with independently retained historical review state. */
public final class GuidedHistoricalRecoveryLifecycleRegressionTest {
    public static void main(String[] args) throws Exception {
        run(false);
        run(true);
        System.out.println("GuidedHistoricalRecoveryLifecycleRegressionTest passed");
    }

    private static void run(boolean failedSave) throws Exception {
        Path root = failedSave ? Files.createTempFile("historical-recovery-failure", ".tmp")
                : Files.createTempDirectory("historical-recovery");
        String previous = System.getProperty("ae.tuner.recovery.dir");
        // Recovery manager captures its root in the plugin constructor.
        // Set the fixture override before construction, not just initialize().
        System.setProperty("ae.tuner.recovery.dir", root.toString());
        AeTunerPlugin plugin = new AeTunerPlugin();
        try { SwingUtilities.invokeAndWait(() -> plugin.initialize(null)); }
        finally {
            if (previous == null) System.clearProperty("ae.tuner.recovery.dir");
            else System.setProperty("ae.tuner.recovery.dir", previous);
        }
        EvidenceRecoveryManager manager = (EvidenceRecoveryManager) field(plugin, "recoveryManager");
        GuidedCapturePanel panel = (GuidedCapturePanel) field(plugin, "guidedPanel");
        GuidedMethodProbeSession probe = (GuidedMethodProbeSession) field(panel, "probeSession");
        probe.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.INSTANT_FUEL));
        java.util.EnumMap<se.anders.tunerstudio.aetuner.model.ChannelRole, Double> values =
                new java.util.EnumMap<>(se.anders.tunerstudio.aetuner.model.ChannelRole.class);
        for (se.anders.tunerstudio.aetuner.model.ChannelRole role :
                se.anders.tunerstudio.aetuner.model.ChannelRole.values()) values.put(role, 1.0);
        for (int i = 0; i < 3; i++) probe.accept(new se.anders.tunerstudio.aetuner.model.LiveSample(
                1000000000L + i * 10000000L, 1.0 + i * .01, values, 0.0, 0.0));
        AeProjectSnapshot tune = stageHistorical();
        Object result = DecelHistoricalResultContext.latest();
        Object review = field(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION),
                "lastHistoricalReview");
        require(!review.toString().isEmpty(), "fixture has no historical module review");
        CountDownLatch release = new CountDownLatch(1);
        ScheduledExecutorService worker = null;
        try {
            onEdt(plugin, "resumeAfterShow");
            worker = worker(manager);
            onEdt(plugin, "suspendForHide");
            require(worker.awaitTermination(10, TimeUnit.SECONDS), "hide recovery stalled");
            retained(tune, result);
            onEdt(plugin, "resumeAfterShow");
            retained(tune, result);
            worker = worker(manager);
            CountDownLatch entered = new CountDownLatch(1);
            worker.submit(() -> {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            require(entered.await(5, TimeUnit.SECONDS), "blocker did not enter");
            SwingUtilities.invokeAndWait(plugin::close);
            retained(tune, result);
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(plugin::close);
            ScheduledExecutorService finalWorker = worker(manager);
            if (finalWorker != null) require(finalWorker.awaitTermination(10, TimeUnit.SECONDS),
                    "final recovery stalled");
        }
        require(!DecelHistoricalResultContext.available(), "historical result survived retirement");
        require(DecelHistoricalResultContext.targetedRpmBins().length == 0,
                "targeted capture survived retirement");
        require(!DecelHistoricalThresholdProposalContext.active(), "staged proposal survived retirement");
        require(GuidedV019HistoricalReplayContext.workingTune() == null, "Working Tune reference leaked");
        require(field(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION),
                "lastHistoricalReview").toString().isEmpty(), "historical module review leaked");
        if (failedSave) require(manager.statusText().startsWith("Automatic recovery failed:"),
                "failure was not reported");
    }

    private static void retained(AeProjectSnapshot tune, Object result) {
        require(DecelHistoricalResultContext.latest() == result, "historical result cleared before retirement");
        require(DecelHistoricalThresholdProposalContext.active(), "historical staging cleared early");
        require(GuidedV019HistoricalReplayContext.workingTune() == tune, "historical tune cleared early");
    }

    private static AeProjectSnapshot stageHistorical() throws Exception {
        // Reuse the established deterministic physical-event fixture without adding production APIs.
        Class<?> fixtureClass = DecelHistoricalThresholdProposalRegressionTest.class;
        Method snapshot = fixtureClass.getDeclaredMethod("snapshot", double.class, double[].class,
                String.class, double.class);
        snapshot.setAccessible(true);
        AeProjectSnapshot tune = (AeProjectSnapshot) snapshot.invoke(null, .05,
                new double[]{1000,1500,2000,2500,3000,3500,4000,4500}, "Dual stride, newest", 30.0);
        Class<?> signal = Class.forName(fixtureClass.getName() + "$Signal");
        Method fixture = fixtureClass.getDeclaredMethod("fixture", AeProjectSnapshot.class, signal);
        fixture.setAccessible(true);
        Object matching = signal.getEnumConstants()[0];
        DecelHistoricalReplayService.Result replay = (DecelHistoricalReplayService.Result)
                field(fixture.invoke(null, tune, matching), "replay");
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, replay);
        require(eligibility.promotable, "fixture is not promotable");
        GuidedV019HistoricalReplayContext.publish(tune);
        DecelHistoricalResultContext.publish(DecelHistoricalResultModel.build(null, tune, replay, eligibility));
        DecelHistoricalThresholdProposalContext.stage(eligibility, tune);
        GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION).explicitSettingWritePlan(tune);
        return tune;
    }

    private static ScheduledExecutorService worker(EvidenceRecoveryManager manager) throws Exception {
        return (ScheduledExecutorService) field(manager, "executor");
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static void onEdt(AeTunerPlugin plugin, String name) throws Exception {
        Method method = AeTunerPlugin.class.getDeclaredMethod(name);
        method.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try { method.invoke(plugin); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
