package se.anders.tunerstudio.aetuner.guided;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PhaseCSampleDispatchArchitectureTest {
    private PhaseCSampleDispatchArchitectureTest() { }

    public static void main(String[] args) throws Exception {
        legacyGlobalStreamClassIsGone();
        retiredPassivePanelClassIsGone();
        guidedLiveSourceOwnsOnlyInjectedEndpoint();
        guidedPanelOwnsDispatcherLifecycle();
        dispatcherQueueAndWorkerAreInstanceLocal();
        System.out.println("PhaseCSampleDispatchArchitectureTest passed");
    }

    private static void legacyGlobalStreamClassIsGone() {
        try {
            Class.forName("se.anders.tunerstudio.aetuner.LiveSampleStream");
            throw new AssertionError("legacy process-global LiveSampleStream is still compiled");
        } catch (ClassNotFoundException expected) {
            // Desired architecture.
        }
    }

    private static void retiredPassivePanelClassIsGone() {
        try {
            Class.forName("se.anders.tunerstudio.aetuner.AeTunerPanel");
            throw new AssertionError("retired Passive AeTunerPanel is still compiled");
        } catch (ClassNotFoundException expected) {
            // Guided-only production runtime must not restore the old panel.
        }
    }

    private static void guidedLiveSourceOwnsOnlyInjectedEndpoint() throws Exception {
        Field field = GuidedLiveSampleSource.class.getDeclaredField("dispatcher");
        require(field.getType() == GuidedSampleDispatcher.class,
                "Guided live source is not wired directly to the bounded dispatcher type");
        require(!Modifier.isStatic(field.getModifiers()),
                "Guided live source dispatcher endpoint became static/global");
        Constructor<GuidedLiveSampleSource> constructor =
                GuidedLiveSampleSource.class.getDeclaredConstructor(GuidedSampleDispatcher.class);
        require(constructor != null,
                "Guided live source lacks explicit dispatcher injection boundary");
    }

    private static void guidedPanelOwnsDispatcherLifecycle() throws Exception {
        Field field = GuidedCapturePanel.class.getDeclaredField("sampleDispatcher");
        require(field.getType() == GuidedSampleDispatcher.class,
                "Guided panel does not own its dispatcher");
        require(!Modifier.isStatic(field.getModifiers()),
                "Guided panel dispatcher became static/global");
        Method accessor = GuidedCapturePanel.class.getDeclaredMethod(
                "guidedSampleDispatcher");
        require(accessor.getReturnType() == GuidedSampleDispatcher.class,
                "Guided panel does not expose explicit producer endpoint");
    }

    private static void dispatcherQueueAndWorkerAreInstanceLocal() throws Exception {
        Field queue = GuidedSampleDispatcher.class.getDeclaredField("queue");
        Field worker = GuidedSampleDispatcher.class.getDeclaredField("worker");
        require(!Modifier.isStatic(queue.getModifiers())
                        && !Modifier.isStatic(worker.getModifiers()),
                "Guided queue/worker became process-global state");
        require(GuidedSampleDispatcher.CAPACITY == 96,
                "bounded dispatcher capacity changed unexpectedly");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
