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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression coverage for the two physical vehicle-test.9 runtime failures.
 *
 * These cases deliberately exercise the plugin host shell rather than only
 * testing Guided/audio/recovery components in isolation.
 */
public final class RuntimeOverhaulRegressionTest {
    private RuntimeOverhaulRegressionTest() { }

    public static void main(String[] args) throws Exception {
        hiddenHostAttachmentCannotDestroyInitializedLifecycle();
        edtCloseCannotWaitOnDeferredRecoveryCheckpoint();
        System.out.println("RuntimeOverhaulRegressionTest passed");
    }

    private static void hiddenHostAttachmentCannotDestroyInitializedLifecycle()
            throws Exception {
        Path recovery = Files.createTempDirectory("ae-tuner-hidden-attach");
        System.setProperty("ae.tuner.recovery.dir", recovery.toString());
        AeTunerPlugin plugin = new AeTunerPlugin();
        try {
            // Model the point immediately after ApplicationPlugin.initialize()
            // without requiring a TunerStudio ControllerAccess mock. The .9 bug
            // lived in the Swing hierarchy listener after lifecycle activation.
            setBoolean(plugin, "lifecycleActive", true);

            JTabbedPane tabs = (JTabbedPane) field(plugin, "rootTabs");
            require(!tabs.isShowing(),
                    "headless/unit host fixture unexpectedly reports tabs showing");
            require(plugin.presentationSuspendedForTest(),
                    "pre-first-show presentation must remain intentionally suspended");
            require(!plugin.guidedControllerPreparedForTest(),
                    "Guided controller must not be prepared before Guided is selected");

            // Ordinary parent/displayability hierarchy churn while hidden is
            // normal during TunerStudio persistent-dialog attachment. .9 treated
            // this as a close and destroyed the controller subscription.
            fire(tabs, HierarchyEvent.PARENT_CHANGED);
            require(plugin.lifecycleActiveForTest(),
                    "non-visibility hierarchy event destroyed active lifecycle");
            require(plugin.presentationSuspendedForTest(),
                    "hidden attachment unexpectedly activated presentation work");
            require(!plugin.guidedControllerPreparedForTest(),
                    "hidden attachment unexpectedly activated Guided controller work");

            // Even a hidden SHOWING_CHANGED before the first real display must
            // not be interpreted as the user closing the plugin.
            fire(tabs, HierarchyEvent.SHOWING_CHANGED);
            require(plugin.lifecycleActiveForTest(),
                    "pre-first-show hidden transition destroyed active lifecycle");
            require(plugin.presentationSuspendedForTest(),
                    "pre-first-show hidden transition activated presentation work");
            require(!plugin.shownOnceForTest(),
                    "hidden attachment incorrectly counted as first display");

            // After a real display has occurred, hide is reversible: Guided and
            // audio presentation work remain suspended, but the host lifecycle
            // and passive controller ownership stay active.
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

        // Reproduce the .9 ordering: Guided close marked recovery dirty, then
        // final recovery shutdown began before the two-second task fired.
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
