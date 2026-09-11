package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import javax.swing.JComponent;
import javax.swing.JDialog;

final class GuidedV019UnsupportedFocus extends GuidedV019FocusBase {
    private final GuidedProductionTask task;
    GuidedV019UnsupportedFocus(GuidedProductionTask task, JDialog owner, Runnable done) {
        super(owner, done);
        this.task = task;
        build();
    }
    String taskTitle() { return task == null ? "Guided Focus" : task.displayName; }
    String taskSubtitle() { return "v0.19 task view pending production data binding"; }
    JComponent drivingContent() {
        return notePanel("Presentation binding pending",
                "This task is visible because it exists in the accepted v0.19 ownership tree. No legacy Guided surface is substituted here.",
                AeUiTheme.softAmber(), AeUiTheme.focusAmber());
    }
    JComponent diagnosticsContent() { return drivingContent(); }
    void refreshFromProduction() { review.setEnabled(false); }
}