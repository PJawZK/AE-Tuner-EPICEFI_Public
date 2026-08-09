package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PhaseCSampleDispatchArchitectureTest {
    private PhaseCSampleDispatchArchitectureTest() { }

    public static void main(String[] args) throws Exception {
        legacyGlobalStreamClassIsGone();
        passivePanelOwnsOnlyInjectedEndpoint();
        guidedPanelOwnsDispatcherLifecycle();
        dispatcherQueueAndWorkerAreInstanceLocal();
        System.out.println("PhaseCSampleDispatchArchitectureTest passed");
    }

    private static void legacyGlobalStreamClassIsGone() {
        try {
            Class.forName("se.anders.tunerstudio.aetuner.LiveSampleStream");
            throw new AssertionError("legacy process-global LiveSampleStream is still compiled");
        } catch (ClassNotFoundException expected) {
            // Desired Phase C architecture.
        }
    }

    private static void passivePanelOwnsOnlyInjectedEndpoint() throws Exception {
        Field field = AeTunerPanel.class.getDeclaredField("guidedSampleDispatcher");
        require(field.getType() == GuidedSampleDispatcher.class,
                "passive panel is not wired to the bounded dispatcher type");
        require(!Modifier.isStatic(field.getModifiers()),
                "passive Guided dispatcher endpoint became static/global");
        Method setter = AeTunerPanel.class.getDeclaredMethod(
                "setGuidedSampleDispatcher", GuidedSampleDispatcher.class);
        require(setter != null, "passive panel lacks explicit dispatcher injection boundary");
    }

    private static void guidedPanelOwnsDispatcherLifecycle() throws Exception {
        Field field = GuidedCapturePanel.class.getDeclaredField("sampleDispatcher");
        require(field.getType() == GuidedSampleDispatcher.class,
                "Guided panel does not own its dispatcher");
        require(!Modifier.isStatic(field.getModifiers()),
                "Guided panel dispatcher became static/global");
        Method accessor = GuidedCapturePanel.class.getDeclaredMethod(
                "sampleDispatcherForPassivePanel");
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
