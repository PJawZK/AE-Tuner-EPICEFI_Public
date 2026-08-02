package se.anders.tunerstudio.aetuner;

import java.awt.Rectangle;
import javax.swing.JTabbedPane;

/**
 * A tabbed pane embedded in the main scroll page must not ask its parent
 * JScrollPane to scroll the selected tab into view. Swing's default
 * scrollRectToVisible call was the remaining source of the visible jump
 * when clicking Overview/Technical details.
 */
final class StableTabbedPane extends JTabbedPane {
    @Override
    public void scrollRectToVisible(Rectangle aRect) {
        // Intentionally ignored. The user owns the main scrollbar.
    }
}
