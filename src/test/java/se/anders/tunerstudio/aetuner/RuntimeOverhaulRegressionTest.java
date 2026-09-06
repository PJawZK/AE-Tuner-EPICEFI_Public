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

/**
 * Regression coverage for the physical Guided/runtime lifecycle failures.
 *
 * These cases deliberately exercise the plugin host shell rather than only
 * testing Guided/audio/recovery components in isolation.
 */
public final class RuntimeOverhaulRegressionTest {
    private RuntimeOverhaulRegressionTest() { }

    public static void main(String[] args) throws Exception {
        hiddenHostAttachmentCannotDestroyInitializedLifecycle();
        edtCloseCannotWaitOnDeferredRecoveryCheckpoint();
        controlledSweepRestorePrecedesHideAndControllerTeardown();
        validationLabIsExplicitAndRestoreGuarded();
        System.out.println("RuntimeOverhaulRegressionTest passed");
    }

    private static void hiddenHostAttachmentCannotDestroyInitializedLifecycle()
            throws Exception {
        Path recovery = Files.createTempDirectory("ae-tuner-hidden-attach");
        System.setProperty("ae.tuner.recovery.dir", recovery.toString());
        AeTunerPlugin plugin = new AeTunerPlugin();
        try {
            require("Apply/Restore Validation Lab (TEMP)".equals(
                            plugin.validationLabButtonTextForTest()),
                    "temporary physical validation Lab is not explicitly visible in Guided header");
            require(plugin.validationLabButtonToolTipForTest().contains("no burn"),
                    "validation Lab launcher does not visibly preserve no-burn safety boundary");
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

    private static void controlledSweepRestorePrecedesHideAndControllerTeardown()
            throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java")), "UTF-8");

        int hide = source.indexOf("private synchronized void suspendForHide()");
        int hideGuard = source.indexOf(
                "EngagementDeltaWindowLifecycleGuard.finishBeforeExternalLifecycleEnd()", hide);
        int hideSuspend = source.indexOf("guidedPanel.suspendPanel()", hide);
        int hideTerminate = source.indexOf("guidedPanel.terminateForClose()", hide);
        require(hide >= 0 && hideGuard > hide
                        && hideSuspend > hideGuard && hideTerminate > hideGuard,
                "presentation hide can suspend/terminate Guided before the controlled-sweep restore guard");

        int close = source.indexOf("private synchronized boolean beginFinalClose()");
        int closeGuard = source.indexOf(
                "EngagementDeltaWindowLifecycleGuard.finishBeforeExternalLifecycleEnd()", close);
        int closeSuspend = source.indexOf("guidedPanel.suspendPanel()", close);
        int disconnect = source.indexOf("panel.disconnectController()", close);
        int closeTerminate = source.indexOf("guidedPanel.terminateForClose()", close);
        require(close >= 0 && closeGuard > close
                        && closeSuspend > closeGuard
                        && disconnect > closeGuard
                        && closeTerminate > closeGuard,
                "plugin close can tear down sample/controller ownership before verified Delta Window restore");

        String guardSource = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/guided/EngagementDeltaWindowLifecycleGuard.java")), "UTF-8");
        require(guardSource.contains("return EngagementDeltaWindowSweepRuntime.finishForLifecycle();"),
                "external lifecycle guard no longer delegates to the verified sweep finish/restore boundary");
    }

    private static void validationLabIsExplicitAndRestoreGuarded() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java")), "UTF-8");
        require(source.contains("new JButton(\"Apply/Restore Validation Lab (TEMP)\")"),
                "physical validation Lab launcher is no longer explicitly visible/temporary");
        require(!source.contains("ae.tuner.validation.lab")
                        && !source.contains("System.getenv(\"AE_TUNER_VALIDATION"),
                "physical validation Lab became hidden behind a property/environment flag");

        int hide = source.indexOf("private synchronized void suspendForHide()");
        int hideValidation = source.indexOf(
                "validationLabWindow.prepareForExternalLifecycleEnd()", hide);
        int hideSweep = source.indexOf(
                "EngagementDeltaWindowLifecycleGuard.finishBeforeExternalLifecycleEnd()", hide);
        int hideSuspend = source.indexOf("guidedPanel.suspendPanel()", hide);
        require(hide >= 0 && hideValidation > hide
                        && hideSweep > hideValidation && hideSuspend > hideSweep,
                "plugin hide does not restore Validation Lab before other teardown guards/resources");

        int close = source.indexOf("private synchronized boolean beginFinalClose()");
        int closeValidation = source.indexOf(
                "validationLabWindow.prepareForExternalLifecycleEnd()", close);
        int closeSweep = source.indexOf(
                "EngagementDeltaWindowLifecycleGuard.finishBeforeExternalLifecycleEnd()", close);
        int disconnect = source.indexOf("panel.disconnectController()", close);
        require(close >= 0 && closeValidation > close
                        && closeSweep > closeValidation && disconnect > closeSweep,
                "plugin close can disconnect controller before Validation Lab exact Restore");
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
