package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Shared session actions for the workspace and explicitly owned task pop-outs. */
final class GuidedTaskControls {
    private static final String INSTALLED = "ae.task.controls";
    private GuidedTaskControls() { }

    static JPanel create() {
        JPanel panel = new JPanel(new GuidedWrapLayout(FlowLayout.RIGHT, 6, 2));
        panel.setOpaque(false);
        JButton cancel = GuidedV019FocusBase.button("Cancel Task");
        JButton reset = GuidedV019FocusBase.button("Start Over");
        JButton restore = GuidedV019FocusBase.button("Restore Previous Apply");
        cancel.setToolTipText("Stop this task and retain evidence for export. Applied values are unchanged.");
        reset.setToolTipText("Clear task evidence and pending proposals. Applied values and Restore history are retained. Wait for any export to finish first.");
        restore.setToolTipText("Explicitly restore the last verified Apply through guarded readback. No Burn.");
        cancel.addActionListener(e -> GuidedV019WorkflowNavigation.cancelTask());
        reset.addActionListener(e -> GuidedV019WorkflowNavigation.startOverTask());
        restore.addActionListener(e -> GuidedV019WorkflowNavigation.restoreTask());
        panel.add(cancel); panel.add(reset); panel.add(restore);
        Runnable update = () -> {
            GuidedV019WorkspacePanel workspace = GuidedV019WorkflowNavigation.workspace();
            cancel.setEnabled(workspace != null && !workspace.taskCancelled());
            reset.setEnabled(workspace != null && workspace.canStartOverTask());
            restore.setEnabled(workspace != null && workspace.canRestoreTask());
            restore.setVisible(workspace != null && workspace.canRestoreTask());
            panel.setBackground(AeUiTheme.panel());
            for (JButton button : new JButton[]{cancel, reset, restore}) {
                button.setForeground(AeUiTheme.focusText());
                button.setBackground(AeUiTheme.button());
            }
        };
        Timer timer = new Timer(200, e -> update.run());
        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
            if (panel.isShowing()) { update.run(); timer.start(); }
            else timer.stop();
        });
        update.run();
        return panel;
    }

    /** Install only on AE-created task windows, never on arbitrary host dialogs. */
    static JDialog install(JDialog dialog) {
        return install(dialog,true);
    }

    static JDialog install(JDialog dialog,boolean scrollBody) {
        GuidedDialogLifecycle.own(dialog);
        dialog.setResizable(true);
        installWindow(dialog,dialog,scrollBody);
        return dialog;
    }

    static void installFrame(JFrame frame,boolean scrollBody) { installWindow(frame,frame,scrollBody); }

    private static void installWindow(Window window,RootPaneContainer root,boolean scrollBody) {
        if (root.getRootPane().getClientProperty(INSTALLED) != null) return;
        root.getRootPane().putClientProperty(INSTALLED, Boolean.TRUE);
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                Container content=root.getContentPane();
                JPanel wrapper=new JPanel(new BorderLayout(0,4));
                wrapper.setBackground(AeUiTheme.panel());
                Component actions=null;
                if((scrollBody || window instanceof JDialog) && content.getLayout() instanceof BorderLayout) {
                    actions=((BorderLayout)content.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
                    if(actions!=null) {
                        content.remove(actions);
                        if(actions instanceof Container && ((Container)actions).getLayout() instanceof FlowLayout)
                            ((Container)actions).setLayout(new GuidedWrapLayout(FlowLayout.RIGHT,6,4));
                    }
                }
                if(scrollBody) {
                    GuidedPopupLayout.prepare(content);
                    JPanel fitted=new WidthFittedBody();
                    fitted.add(content,BorderLayout.CENTER);
                    JScrollPane scroll=new JScrollPane(fitted,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                    scroll.setBorder(null);scroll.getVerticalScrollBar().setUnitIncrement(16);
                    wrapper.add(scroll,BorderLayout.CENTER);
                } else wrapper.add(content,BorderLayout.CENTER);
                JPanel bottom=new JPanel(new BorderLayout(0,2));bottom.setOpaque(false);
                if(actions!=null)bottom.add(actions,BorderLayout.NORTH);
                if(GuidedV019WorkflowNavigation.workspace()!=null)bottom.add(create(),BorderLayout.SOUTH);
                wrapper.add(bottom,BorderLayout.SOUTH);
                // Account for the shared footer before clamping to the monitor.
                window.setSize(window.getWidth(),window.getHeight()+bottom.getPreferredSize().height+4);
                root.setContentPane(wrapper);
                GuidedUiTheme.install(wrapper);
                GuidedWindowTools.fit(window);
                window.validate();
            }
        });
    }

    /** Read-only report columns reflow to the viewport; their own tables retain scrolling. */
    private static final class WidthFittedBody extends JPanel implements Scrollable {
        WidthFittedBody() { super(new BorderLayout()); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction) {
            return Math.max(16,(orientation==SwingConstants.VERTICAL?r.height:r.width)-16);
        }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() {
            return getParent()!=null && getPreferredSize().height<=getParent().getHeight();
        }
    }

    static void closeTaskWindows() {
        for (Window window : Window.getWindows()) {
            if (window instanceof RootPaneContainer && ((RootPaneContainer) window).getRootPane()
                    .getClientProperty(INSTALLED) != null) window.dispose();
        }
    }
}
