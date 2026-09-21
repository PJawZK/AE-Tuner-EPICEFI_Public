package se.anders.tunerstudio.aetuner.guided;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Explicit ownership registry for short-lived v0.19 Guided dialogs.
 * Every registered dialog is DISPOSE_ON_CLOSE and plugin hide/final close can
 * dispose any still-displayable child window in one operation.
 */
public final class GuidedDialogLifecycle {
    private static final Set<JDialog> OWNED = Collections.newSetFromMap(
            new IdentityHashMap<JDialog, Boolean>());
    private static final Set<Component> ROOTS = Collections.newSetFromMap(
            new WeakHashMap<Component, Boolean>());
    private static boolean listenerInstalled;

    private GuidedDialogLifecycle() { }

    /**
     * Register a production workspace root. This closes the remaining lifecycle
     * gap for older v0.19 factories that still construct JDialog directly: when
     * such a dialog opens under the TunerStudio host window it is adopted into
     * this registry immediately. The root is weakly held so plugin retirement
     * cannot be prevented by the registry itself.
     */
    public static void registerRoot(Component root) {
        if (root == null) return;
        synchronized (ROOTS) { ROOTS.add(root); }
        installWindowListener();
    }

    private static void installWindowListener() {
        synchronized (ROOTS) {
            if (listenerInstalled) return;
            try {
                Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                    @Override public void eventDispatched(AWTEvent event) {
                        if (!(event instanceof WindowEvent)
                                || event.getID() != WindowEvent.WINDOW_OPENED) return;
                        Window opened = ((WindowEvent)event).getWindow();
                        if (!(opened instanceof JDialog)) return;
                        if (belongsToRegisteredRoot(opened)) own((JDialog)opened);
                    }
                }, AWTEvent.WINDOW_EVENT_MASK);
                listenerInstalled = true;
            } catch (HeadlessException | SecurityException ignored) {
                // Synthetic/headless construction still uses explicit own/create paths.
            }
        }
    }

    private static boolean belongsToRegisteredRoot(Window opened) {
        Component[] roots;
        synchronized (ROOTS) { roots = ROOTS.toArray(new Component[0]); }
        for (Component root : roots) {
            if (root == null) continue;
            Window host = SwingUtilities.getWindowAncestor(root);
            if (host != null && isOwnedBy(opened, host)) return true;
        }
        return false;
    }

    private static boolean isOwnedBy(Window child, Window host) {
        Window cursor = child;
        while (cursor != null) {
            if (cursor == host) return true;
            cursor = cursor.getOwner();
        }
        return false;
    }

    public static JDialog create(Window host, String title, boolean modal) {
        JDialog dialog;
        if (host instanceof Dialog) {
            dialog = new JDialog((Dialog)host, title,
                    modal ? Dialog.ModalityType.APPLICATION_MODAL
                            : Dialog.ModalityType.MODELESS);
        } else if (host instanceof Frame) {
            dialog = new JDialog((Frame)host, title, modal);
        } else {
            dialog = new JDialog((Frame)null, title, modal);
        }
        return own(dialog);
    }

    public static JDialog own(final JDialog dialog) {
        if (dialog == null) return null;
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        synchronized (OWNED) {
            if (!OWNED.add(dialog)) return dialog;
        }
        RuntimePerformanceHub.noteDialogCreated();
        dialog.addWindowListener(new WindowAdapter() {
            private boolean released;
            private void release() {
                if (released) return;
                released = true;
                synchronized (OWNED) { OWNED.remove(dialog); }
                RuntimePerformanceHub.noteDialogDisposed();
            }
            @Override public void windowClosed(WindowEvent event) { release(); }
        });
        return dialog;
    }

    public static void disposeAll() {
        JDialog[] snapshot;
        synchronized (OWNED) { snapshot = OWNED.toArray(new JDialog[0]); }
        for (JDialog dialog : snapshot) {
            if (dialog != null) dialog.dispose();
        }
    }

    public static int liveCount() {
        synchronized (OWNED) { return OWNED.size(); }
    }
}
