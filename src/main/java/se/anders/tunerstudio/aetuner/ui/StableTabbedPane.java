package se.anders.tunerstudio.aetuner.ui;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.awt.Rectangle;
import javax.swing.JTabbedPane;

/**
 * A tabbed pane embedded in the main scroll page must not ask its parent
 * JScrollPane to scroll the selected tab into view. Swing's default
 * scrollRectToVisible call was the remaining source of the visible jump
 * when clicking Overview/Technical details.
 */
public final class StableTabbedPane extends JTabbedPane {
    @Override
    public void scrollRectToVisible(Rectangle aRect) {
        // Intentionally ignored. The user owns the main scrollbar.
    }
}
