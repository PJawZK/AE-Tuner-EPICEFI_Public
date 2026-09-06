package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationLabModel;

import javax.swing.JDialog;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Modeless temporary developer window for physical Apply/Restore validation. */
public final class AeApplyRestoreValidationLabWindow {
    private final JDialog dialog;
    private final AeApplyRestoreValidationLabPanel panel;

    public AeApplyRestoreValidationLabWindow(Window owner,
                                             AeApplyRestoreValidationLabModel model) {
        dialog = new JDialog(owner,
                "Apply/Restore Validation Lab — TEMPORARY DEVELOPER TOOL",
                Dialog.ModalityType.MODELESS);
        panel = new AeApplyRestoreValidationLabPanel(model);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setContentPane(panel);
        dialog.setMinimumSize(new Dimension(980, 640));
        dialog.setPreferredSize(new Dimension(1250, 760));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (!panel.canCloseSafely()) {
                    panel.showCloseBlockedMessage();
                    return;
                }
                dialog.setVisible(false);
            }
        });
    }

    public void openWindow() {
        if (!dialog.isVisible()) dialog.setVisible(true);
        dialog.toFront();
    }

    /**
     * Restore any active temporary value before plugin hide/teardown. The host
     * must abort lifecycle teardown if this returns false.
     */
    public boolean prepareForExternalLifecycleEnd() {
        if (!panel.restoreBeforeLifecycleClose()) return false;
        dialog.setVisible(false);
        return true;
    }

    public void disposeWindow() {
        if (!panel.restoreBeforeLifecycleClose()) {
            throw new IllegalStateException(
                    "Validation Lab cannot be disposed while temporary working-tune value remains applied");
        }
        dialog.dispose();
    }

    public boolean isDisplayable() { return dialog.isDisplayable(); }
    public boolean isVisible() { return dialog.isVisible(); }
    public int physicalTargetCountForTest() { return panel.physicalTargetCountForTest(); }
    public String summaryTextForTest() { return panel.summaryTextForTest(); }
}
