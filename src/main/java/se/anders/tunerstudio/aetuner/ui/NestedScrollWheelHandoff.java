package se.anders.tunerstudio.aetuner.ui;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

/** Keeps wheel navigation continuous across a nested vertical scroll pane. */
public final class NestedScrollWheelHandoff {
    private NestedScrollWheelHandoff() {
    }

    public static void install(final JScrollPane inner, final JScrollPane outer) {
        inner.setWheelScrollingEnabled(false);
        inner.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                int direction = event.getWheelRotation() < 0 ? -1
                        : event.getWheelRotation() > 0 ? 1 : 0;
                if (direction == 0) {
                    return;
                }

                JScrollBar innerBar = inner.getVerticalScrollBar();
                JScrollBar target = canMove(innerBar, direction)
                        ? innerBar : outer.getVerticalScrollBar();
                move(target, event, direction);
                // This listener is the sole handler for the nested pane. It
                // moves at most one scrollbar and never redispatches an event.
                event.consume();
            }
        });
    }

    private static boolean canMove(JScrollBar bar, int direction) {
        int minimum = bar.getMinimum();
        int maximum = bar.getMaximum() - bar.getVisibleAmount();
        return direction < 0 ? bar.getValue() > minimum : bar.getValue() < maximum;
    }

    private static void move(JScrollBar bar, MouseWheelEvent event, int direction) {
        int delta;
        if (event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
            delta = bar.getBlockIncrement(direction) * event.getWheelRotation();
        } else {
            delta = bar.getUnitIncrement(direction) * event.getUnitsToScroll();
        }
        int minimum = bar.getMinimum();
        int maximum = Math.max(minimum, bar.getMaximum() - bar.getVisibleAmount());
        long requested = (long) bar.getValue() + delta;
        bar.setValue((int) Math.max(minimum, Math.min(maximum, requested)));
    }
}
