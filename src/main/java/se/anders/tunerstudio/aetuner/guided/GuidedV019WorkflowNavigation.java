package se.anders.tunerstudio.aetuner.guided;

import javax.swing.SwingUtilities;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Small presentation-only bridge for modal result surfaces that complete a v0.19
 * workflow step outside the main card. It owns no evidence, tuning or write logic.
 * Reflection is intentionally contained here while GuidedV019WorkspacePanel still
 * keeps its stage controller private, matching the existing production bridge's
 * temporary adapter approach.
 */
final class GuidedV019WorkflowNavigation {
    private static WeakReference<GuidedV019WorkspacePanel> workspace =
            new WeakReference<GuidedV019WorkspacePanel>(null);

    private GuidedV019WorkflowNavigation() { }

    static synchronized void register(GuidedV019WorkspacePanel panel) {
        workspace = new WeakReference<GuidedV019WorkspacePanel>(panel);
    }

    static synchronized GuidedV019WorkspacePanel workspace() { return workspace.get(); }
    static void cancelTask() {
        GuidedV019WorkspacePanel panel = workspace();
        if (panel != null) panel.cancelTask();
    }
    static void startOverTask() {
        GuidedV019WorkspacePanel panel = workspace();
        if (panel != null) panel.startOverTask();
    }
    static void restoreTask() {
        GuidedV019WorkspacePanel panel = workspace();
        if (panel != null) panel.restoreTask();
    }

    static void decelHistoricalResult() { move(GuidedV019WorkspacePanel.Stage.RESULT); }
    static void decelHistoricalProposalStaged() { move(GuidedV019WorkspacePanel.Stage.APPLY); }
    static void decelTargetedCaptureStarted() { move(GuidedV019WorkspacePanel.Stage.GUIDED_FOCUS); }

    private static void move(final GuidedV019WorkspacePanel.Stage target) {
        final GuidedV019WorkspacePanel panel;
        synchronized (GuidedV019WorkflowNavigation.class) { panel = workspace.get(); }
        if (panel == null || target == null) return;
        Runnable action = new Runnable() {
            @Override public void run() {
                if (panel.taskCancelled()) return;
                try {
                    Field stage = GuidedV019WorkspacePanel.class.getDeclaredField("stage");
                    stage.setAccessible(true);
                    stage.set(panel, target);
                    Method refresh = GuidedV019WorkspacePanel.class.getDeclaredMethod("refreshAll");
                    refresh.setAccessible(true);
                    refresh.invoke(panel);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Could not hand Decel result back to v0.19 workflow", ex);
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
