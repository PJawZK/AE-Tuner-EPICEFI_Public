package se.anders.tunerstudio.aetuner;

import se.anders.tunerstudio.aetuner.guided.GuidedCapturePanel;
import se.anders.tunerstudio.aetuner.guided.GuidedDialogLifecycle;
import se.anders.tunerstudio.aetuner.guided.GuidedLiveSampleSource;
import se.anders.tunerstudio.aetuner.recovery.EvidenceRecoveryManager;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Regression coverage for the current Guided-only runtime lifecycle. */
public final class RuntimeOverhaulRegressionTest {
    private RuntimeOverhaulRegressionTest() { }

    public static void main(String[] args) throws Exception {
        sameControllerInitializeIsIdempotent();
        hiddenHostAttachmentCannotDestroyInitializedLifecycle();
        hiddenPresentationStopsGuidedRuntimeResources();
        edtCloseCannotWaitOnDeferredRecoveryCheckpoint();
        retiredValidationLabCannotOwnLifecycle();
        System.out.println("RuntimeOverhaulRegressionTest passed");
    }

    private static void sameControllerInitializeIsIdempotent() throws Exception {
        Path recovery = Files.createTempDirectory("ae-tuner-idempotent-init");
        System.setProperty("ae.tuner.recovery.dir", recovery.toString());
        AeTunerPlugin plugin = new AeTunerPlugin();
        try {
            plugin.initialize(null);
            require(plugin.lifecycleActiveForTest(),
                    "first initialize did not activate the plugin lifecycle");
            require(plugin.initializeActivationCountForTest() == 1,
                    "first initialize did not register exactly one lifecycle activation");
            require(!((EvidenceRecoveryManager) field(plugin, "recoveryManager")).isRunningForTest(),
                    "hidden initialize started the periodic recovery worker before first display");
            boolean suspended = plugin.presentationSuspendedForTest();
            boolean guidedPrepared = plugin.guidedControllerPreparedForTest();

            plugin.initialize(null);
            require(plugin.initializeActivationCountForTest() == 1,
                    "same ControllerAccess initialize performed a second lifecycle activation");
            require(plugin.presentationSuspendedForTest() == suspended,
                    "idempotent initialize reset presentation state");
            require(plugin.guidedControllerPreparedForTest() == guidedPrepared,
                    "idempotent initialize reset Guided controller ownership");
        } finally {
            plugin.close();
            System.clearProperty("ae.tuner.recovery.dir");
        }
    }

    private static void hiddenHostAttachmentCannotDestroyInitializedLifecycle()
            throws Exception {
        Path recovery = Files.createTempDirectory("ae-tuner-hidden-attach");
        System.setProperty("ae.tuner.recovery.dir", recovery.toString());
        AeTunerPlugin plugin = new AeTunerPlugin();
        try {
            plugin.initialize(null);
            JPanel host = (JPanel) field(plugin, "panel");
            require(!host.isShowing(),
                    "headless/unit host fixture unexpectedly reports the direct host showing");
            require(plugin.presentationSuspendedForTest(),
                    "pre-first-show presentation must remain intentionally suspended");
            require(!plugin.guidedControllerPreparedForTest(),
                    "Guided controller must not be prepared before first visible activation");

            fire(host, HierarchyEvent.PARENT_CHANGED);
            require(plugin.lifecycleActiveForTest(),
                    "non-visibility hierarchy event destroyed active lifecycle");
            require(plugin.presentationSuspendedForTest(),
                    "hidden attachment unexpectedly activated presentation work");
            require(!plugin.guidedControllerPreparedForTest(),
                    "hidden attachment unexpectedly activated Guided controller work");

            fire(host, HierarchyEvent.SHOWING_CHANGED);
            require(plugin.lifecycleActiveForTest(),
                    "pre-first-show hidden transition destroyed active lifecycle");
            require(plugin.presentationSuspendedForTest(),
                    "pre-first-show hidden transition activated presentation work");
            require(!plugin.shownOnceForTest(),
                    "hidden attachment incorrectly counted as first display");
        } finally {
            plugin.close();
            System.clearProperty("ae.tuner.recovery.dir");
        }
    }

    private static void hiddenPresentationStopsGuidedRuntimeResources()
            throws Exception {
        Path recovery = Files.createTempDirectory("ae-tuner-hidden-runtime");
        System.setProperty("ae.tuner.recovery.dir", recovery.toString());
        AeTunerPlugin plugin = new AeTunerPlugin();
        try {
            plugin.initialize(null);
            setBoolean(plugin, "presentationSuspended", false);
            setBoolean(plugin, "shownOnce", true);

            EvidenceRecoveryManager manager =
                    (EvidenceRecoveryManager) field(plugin, "recoveryManager");
            GuidedCapturePanel guided =
                    (GuidedCapturePanel) field(plugin, "guidedPanel");
            GuidedLiveSampleSource liveSource =
                    (GuidedLiveSampleSource) field(plugin, "liveSource");

            manager.resume();
            guided.resumePanel();
            require(manager.isRunningForTest(),
                    "test fixture failed to start Guided recovery worker");
            require(guided.guidedSampleDispatcher().runtimeStats().accepting,
                    "test fixture failed to activate the bounded Guided dispatcher");

            Method suspend = AeTunerPlugin.class.getDeclaredMethod("suspendForHide");
            suspend.setAccessible(true);
            suspend.invoke(plugin);

            require(plugin.presentationSuspendedForTest(),
                    "hide did not mark presentation suspended");
            require(!guided.guidedSampleDispatcher().runtimeStats().accepting,
                    "hide left the Guided sample dispatcher accepting work");
            require(!liveSource.runtimeStats().enabled,
                    "hide left the Guided live-source callback path enabled");
            require(!manager.isRunningForTest(),
                    "hide left automatic Guided recovery scheduled");
            require(GuidedDialogLifecycle.liveCount() == 0,
                    "hide left owned Guided dialogs alive");

            Method resume = AeTunerPlugin.class.getDeclaredMethod("resumeAfterShow");
            resume.setAccessible(true);
            resume.invoke(plugin);
            require(!plugin.presentationSuspendedForTest(),
                    "reopen did not restore presentation state");
            require(manager.isRunningForTest(),
                    "reopen did not restore automatic Guided recovery worker");
            require(!liveSource.runtimeStats().enabled,
                    "null-controller fixture unexpectedly enabled live callbacks on reopen");
            require(!plugin.guidedControllerPreparedForTest(),
                    "null-controller fixture unexpectedly prepared Guided controller ownership");
        } finally {
            plugin.close();
            System.clearProperty("ae.tuner.recovery.dir");
        }
    }

    private static void edtCloseCannotWaitOnDeferredRecoveryCheckpoint()
            throws Exception {
        Path root = Files.createTempDirectory("ae-tuner-edt-close");
        GuidedCapturePanel guided = new GuidedCapturePanel();
        EvidenceRecoveryManager manager = new EvidenceRecoveryManager(guided, root);
        manager.resume();
        manager.requestCheckpoint();

        final AtomicLong elapsedMillis = new AtomicLong(Long.MAX_VALUE);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                long started = System.nanoTime();
                try {
                    manager.flushAndClose();
                } catch (Throwable ex) {
                    failure.set(ex);
                } finally {
                    elapsedMillis.set((System.nanoTime() - started) / 1000000L);
                }
            }
        });

        try {
            if (failure.get() != null) {
                throw new AssertionError("EDT close failed", failure.get());
            }
            require(elapsedMillis.get() < 1500L,
                    "EDT recovery close blocked for " + elapsedMillis.get()
                            + " ms; recovery close must remain non-blocking on Swing EDT");
            require(!manager.isRunningForTest(),
                    "Guided recovery manager remained logically active after close");
        } finally {
            guided.disposePanel();
        }
    }

    private static void retiredValidationLabCannotOwnLifecycle() throws Exception {
        Path guidedRoot = Paths.get("src/main/java/se/anders/tunerstudio/aetuner/guided");
        require(!Files.exists(guidedRoot.resolve("AeApplyRestoreValidationLabPanel.java"))
                        && !Files.exists(guidedRoot.resolve("AeApplyRestoreValidationLabWindow.java")),
                "retired Validation Lab Swing UI source returned");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.AeApplyRestoreValidationLabPanel");
        requireClassMissing("se.anders.tunerstudio.aetuner.guided.AeApplyRestoreValidationLabWindow");

        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java")), "UTF-8");
        require(!source.contains("ValidationLab")
                        && !source.contains("Apply/Restore Validation Lab (TEMP)"),
                "retired physical Validation Lab still owns host runtime/lifecycle state");
        require(!source.contains("EngagementDeltaWindowLifecycleGuard")
                        && !source.contains("EngagementDeltaWindowSweepRuntime"),
                "retired controlled-sweep lifecycle ownership reappeared in the plugin shell");
        require(!source.contains("AeTunerPanel")
                        && !source.contains("rootTabs")
                        && !source.contains("passiveAnalysisWindow"),
                "retired Passive/top-level-tab runtime ownership reappeared in the plugin shell");
        require(!source.contains("GuidedVehicleTestOverridePanel")
                        && !source.contains("soundCues = new JCheckBox")
                        && !source.contains("openGuidedFocus = new JButton")
                        && !source.contains("vehicleTestStatus = new JLabel"),
                "retired host-only control construction reappeared in the plugin shell");
        require(source.contains("new GuidedLiveSampleSource")
                        && source.contains("new EvidenceRecoveryManager(guidedPanel)"),
                "current Guided-only live/recovery ownership is missing from the plugin shell");

        String engine = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/host/AeApplyRestoreValidationEngine.java")), "UTF-8");
        require(engine.contains("new ProposalApplyCoordinator(access)")
                        && engine.contains("ProposalApplyCoordinator.ApplyResult apply = coordinator.apply(activePlan)")
                        && engine.contains("ProposalApplyCoordinator.ApplyResult restore = coordinator.restorePreviousApply()")
                        && engine.contains("Automatic safety restore PASS")
                        && engine.contains("Independent original-value readback PASS"),
                "retained validation engine lost coordinator/readback/Restore safety authority");

        int initialize = source.indexOf("public synchronized void initialize(ControllerAccess access)");
        int ensureGuided = source.indexOf("private synchronized void ensureGuidedControllerActive()", initialize);
        require(initialize >= 0 && ensureGuided > initialize,
                "plugin initialize/Guided activation lifecycle methods are missing");
        String initializeBlock = source.substring(initialize, ensureGuided);
        require(!initializeBlock.contains("guidedPanel.connectController(access)")
                        && !initializeBlock.contains("liveSource.connectController(access)")
                        && !initializeBlock.contains("recoveryManager.resume()"),
                "hidden initialize reintroduced live subscription or periodic recovery work");

        int hide = source.indexOf("private synchronized void suspendForHide()");
        int hideSuspend = source.indexOf("guidedPanel.suspendPanel()", hide);
        int hideDisconnect = source.indexOf("liveSource.disconnectController()", hide);
        int hideDialogs = source.indexOf("guidedProductionWorkspace.disposeOwnedDialogs()", hide);
        int hideRuntime = source.indexOf("runtimeTimer.stop()", hide);
        int hideRecoveryStop = source.indexOf("recoveryManager.flushAndClose()", hide);
        require(hide >= 0 && hideSuspend > hide
                        && hideDisconnect > hideSuspend
                        && hideDialogs > hideDisconnect
                        && hideRuntime > hideDialogs
                        && hideRecoveryStop > hideRuntime,
                "plugin hide lost Guided/live/dialog/runtime/recovery suspension ordering");

        int resume = source.indexOf("private synchronized void resumeAfterShow()");
        int recoveryResume = source.indexOf("recoveryManager.resume()", resume);
        int guidedResume = source.indexOf("ensureGuidedControllerActive()", resume);
        int liveResume = source.indexOf("liveSource.connectController(controllerAccess)", resume);
        int runtimeResume = source.indexOf("runtimeTimer.start()", resume);
        require(resume >= 0 && recoveryResume > resume
                        && guidedResume > recoveryResume
                        && liveResume > guidedResume
                        && runtimeResume > liveResume,
                "plugin reopen lost recovery/Guided/live/runtime resume ordering");

        int close = source.indexOf("private synchronized boolean beginFinalClose()");
        int closeSuspend = source.indexOf("guidedPanel.suspendPanel()", close);
        int closeDisconnect = source.indexOf("liveSource.disconnectController()", close);
        int closeTerminate = source.indexOf("guidedPanel.terminateForClose()", close);
        int closeDialogs = source.indexOf("guidedProductionWorkspace.disposeOwnedDialogs()", close);
        int closeRuntime = source.indexOf("runtimeTimer.stop()", close);
        require(close >= 0 && closeSuspend > close
                        && closeDisconnect > closeSuspend
                        && closeTerminate > closeDisconnect
                        && closeDialogs > closeTerminate
                        && closeRuntime > closeDialogs,
                "plugin close lost the current Guided/live/dialog teardown ordering");
    }

    private static void requireClassMissing(String name) throws Exception {
        try {
            Class.forName(name);
            throw new AssertionError("retired Validation Lab UI type is still loadable: " + name);
        } catch (ClassNotFoundException expected) {
            // Expected permanent architecture state.
        }
    }

    private static void fire(Component component, long flags) {
        HierarchyEvent event = new HierarchyEvent(component,
                HierarchyEvent.HIERARCHY_CHANGED, component, null, flags);
        for (HierarchyListener listener : component.getHierarchyListeners()) {
            listener.hierarchyChanged(event);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setBoolean(Object target, String name, boolean value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
