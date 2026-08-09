package se.anders.tunerstudio.aetuner.ui;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/** Vertical column that measures each wrapping child at the available width. */
public final class WrappingColumnPanel extends JPanel implements Scrollable {
    public WrappingColumnPanel() {
        setLayout(null);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);
        if (widthChanged) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    revalidate();
                    if (getParent() != null) {
                        getParent().revalidate();
                    }
                }
            });
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Insets insets = getInsets();
        int width = availableWidth();
        int contentWidth = Math.max(1, width - insets.left - insets.right);
        int height = insets.top + insets.bottom;
        for (Component component : getComponents()) {
            if (!component.isVisible()) {
                continue;
            }
            component.setSize(contentWidth, Math.max(1, component.getHeight()));
            height += component.getPreferredSize().height;
        }
        return new Dimension(width, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public void doLayout() {
        Insets insets = getInsets();
        int contentWidth = Math.max(1, getWidth() - insets.left - insets.right);
        int y = insets.top;
        for (Component component : getComponents()) {
            if (!component.isVisible()) {
                continue;
            }
            component.setSize(contentWidth, Math.max(1, component.getHeight()));
            int height = component.getPreferredSize().height;
            component.setBounds(insets.left, y, contentWidth, height);
            if (component instanceof Container) {
                ((Container) component).doLayout();
            }
            y += height;
        }
    }

    private int availableWidth() {
        if (getParent() != null && getParent().getWidth() > 0) {
            return getParent().getWidth();
        }
        if (getWidth() > 0) {
            return getWidth();
        }
        return 1000;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(90, visibleRect.height - 36);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
