package se.anders.tunerstudio.aetuner;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Regression coverage for the physical Guided/runtime lifecycle failures. */
public final class RuntimeOverhaulRegressionTest {
    private RuntimeOverhaulRegressionTest() { }

    public static void main(String[] args) throws Exception {
        sameControllerInitializeIsIdempotent();
        hiddenHostAttachmentCannotDestroyInitializedLifecycle();
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
            setBoolean(plugin, "lifecycleActive", true);

            JTabbedPane tabs = (JTabbedPane) field(plugin, "rootTabs");
            require(!tabs.isShowing(),
                    "headless/unit host fixture unexpectedly reports tabs showing");
            require(plugin.presentationSuspendedForTest(),
                    "pre-first-show presentation must remain intentionally suspended");
            require(!plugin.guidedControllerPreparedForTest(),
                    "Guided controller must not be prepared before Guided is selected");

            fire(tabs, HierarchyEvent.PARENT_CHANGED);
            require(plugin.lifecycleActiveForTest(),
                    "non-visibility hierarchy event destroyed active lifecycle");
            require(plugin.presentationSuspendedForTest(),
                    "hidden attachment unexpectedly activated presentation work");
            require(!plugin.guidedControllerPreparedForTest(),
                    "hidden attachment unexpectedly activated Guided controller work");

            fire(tabs, HierarchyEvent.SHOWING_CHANGED);
            require(plugin.lifecycleActiveForTest(),
                    "pre-first-show hidden transition destroyed active lifecycle");
            require(plugin.presentationSuspendedForTest(),
                    "pre-first-show hidden transition activated presentation work");
            require(!plugin.shownOnceForTest(),
                    "hidden attachment incorrectly counted as first display");

            setBoolean(plugin, "shownOnce", true);
            fire(tabs, HierarchyEvent.SHOWING_CHANGED);
            require(plugin.lifecycleActiveForTest(),
                    "post-show hide destroyed host lifecycle");
            require(plugin.presentationSuspendedForTest(),
                    "post-show hide did not suspend presentation resources");

            Method resume = AeTunerPlugin.class.getDeclaredMethod("resumeAfterShow");
            resume.setAccessible(true);
            resume.invoke(plugin);
            require(plugin.lifecycleActiveForTest(),
                    "reopen lost host lifecycle");
            require(!plugin.presentationSuspendedForTest(),
                    "reopen did not restore presentation resources");
            require(!plugin.guidedControllerPreparedForTest(),
                    "reopen on default Passive tab unexpectedly started Guided engine");
        } finally {
            plugin.close();
            System.clearProperty("ae.tuner.recovery.dir");
        }
    }

    private static void edtCloseCannotWaitOnDeferredRecoveryCheckpoint()
            throws Exception {
        Path root = Files.createTempDirectory("ae-tuner-edt-close");
        AeTunerPanel passive = new AeTunerPanel();
        GuidedCapturePanel guided = new GuidedCapturePanel();
        EvidenceRecoveryManager manager =
                new EvidenceRecoveryManager(passive, guided, root);
        manager.resume();
        manager.requestCheckpoint();

        final AtomicLong elapsedMillis = new AtomicLong(Long.MAX_VALUE);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
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
                            + " ms; vehicle-test.9 could block near its 12 s timeout");
            require(!manager.isRunningForTest(),
                    "recovery manager remained logically active after close");
        } finally {
            guided.disposePanel();
            passive.disposePanel();
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

        String engine = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/host/AeApplyRestoreValidationEngine.java")), "UTF-8");
        require(engine.contains("new ProposalApplyCoordinator(access)")
                        && engine.contains("ProposalApplyCoordinator.ApplyResult apply = coordinator.apply(activePlan)")
                        && engine.contains("ProposalApplyCoordinator.ApplyResult restore = coordinator.restorePreviousApply()")
                        && engine.contains("Automatic safety restore PASS")
                        && engine.contains("Independent original-value readback PASS"),
                "retained validation engine lost coordinator/readback/Restore safety authority");

        int hide = source.indexOf("private synchronized void suspendForHide()");
        int hideSuspend = source.indexOf("guidedPanel.suspendPanel()", hide);
        int hideTerminate = source.indexOf("guidedPanel.terminateForClose()", hide);
        require(hide >= 0 && hideSuspend > hide && hideTerminate > hideSuspend,
                "plugin hide lost the current Guided suspend/termination lifecycle ordering");

        int close = source.indexOf("private synchronized boolean beginFinalClose()");
        int closeSuspend = source.indexOf("guidedPanel.suspendPanel()", close);
        int disconnect = source.indexOf("panel.disconnectController()", close);
        int closeTerminate = source.indexOf("guidedPanel.terminateForClose()", close);
        require(close >= 0 && closeSuspend > close
                        && disconnect > closeSuspend && closeTerminate > disconnect,
                "plugin close lost the current controller/Guided teardown ordering");
    }

    private static void requireClassMissing(String name) throws Exception {
        try {
            Class.forName(name);
            throw new AssertionError("retired Validation Lab UI type is still loadable: " + name);
        } catch (ClassNotFoundException expected) {
            // Expected permanent architecture state.
        }
    }

    private static void fire(JTabbedPane tabs, long flags) {
        HierarchyEvent event = new HierarchyEvent(tabs,
                HierarchyEvent.HIERARCHY_CHANGED, tabs, tabs, flags);
        for (HierarchyListener listener : tabs.getHierarchyListeners()) {
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
