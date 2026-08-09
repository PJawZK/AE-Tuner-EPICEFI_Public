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
import java.awt.FlowLayout;
import java.awt.Insets;

/** Flow layout whose preferred height includes every wrapped row. */
public final class WrapLayout extends FlowLayout {
    public WrapLayout(int alignment, int horizontalGap, int verticalGap) {
        super(alignment, horizontalGap, verticalGap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            if (targetWidth <= 0 && target.getParent() != null) {
                targetWidth = target.getParent().getWidth();
            }
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (getHgap() * 2);
            int maxWidth = targetWidth == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : Math.max(1, targetWidth - horizontalInsetsAndGap);
            Dimension result = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++) {
                Component component = target.getComponent(i);
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = preferred
                        ? component.getPreferredSize() : component.getMinimumSize();
                int nextWidth = rowWidth == 0 ? size.width : rowWidth + getHgap() + size.width;
                if (nextWidth > maxWidth && rowWidth > 0) {
                    addRow(result, rowWidth, rowHeight);
                    rowWidth = size.width;
                    rowHeight = size.height;
                } else {
                    rowWidth = nextWidth;
                    rowHeight = Math.max(rowHeight, size.height);
                }
            }
            addRow(result, rowWidth, rowHeight);

            result.width += horizontalInsetsAndGap;
            result.height += insets.top + insets.bottom + (getVgap() * 2);
            return result;
        }
    }

    private void addRow(Dimension result, int rowWidth, int rowHeight) {
        result.width = Math.max(result.width, rowWidth);
        if (result.height > 0) {
            result.height += getVgap();
        }
        result.height += rowHeight;
    }
}
