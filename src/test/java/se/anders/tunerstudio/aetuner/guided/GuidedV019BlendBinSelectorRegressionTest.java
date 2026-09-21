package se.anders.tunerstudio.aetuner.guided;

import javax.swing.JToggleButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

public final class GuidedV019BlendBinSelectorRegressionTest {
    private GuidedV019BlendBinSelectorRegressionTest() { }

    public static void main(String[] args) {
        selectorIsExplicitAndLimitedToFourActualBins();
        System.out.println("GuidedV019BlendBinSelectorRegressionTest passed");
    }

    private static void selectorIsExplicitAndLimitedToFourActualBins() {
        final double[][] callback = new double[1][];
        GuidedV019BlendBinSelectorPanel panel = new GuidedV019BlendBinSelectorPanel(
                selected -> callback[0] = selected);
        panel.setAvailableBins(new double[]{1000,1500,2000,2600,3200}, new double[0]);
        List<JToggleButton> buttons = toggles(panel);
        require(buttons.size() == 5, "actual Working Tune bins were not rendered as direct controls");
        for (int i = 0; i < 4; i++) buttons.get(i).doClick();
        require(panel.selectedCount() == 4, "could not arm four bins");
        buttons.get(4).doClick();
        require(panel.selectedCount() == 4 && !buttons.get(4).isSelected(),
                "selector allowed more than four bins");
        require(callback[0] != null && callback[0].length == 4,
                "selection did not publish explicit armed bins");
    }

    private static List<JToggleButton> toggles(Container root) {
        List<JToggleButton> out = new ArrayList<JToggleButton>();
        for (Component c : root.getComponents()) {
            if (c instanceof JToggleButton) out.add((JToggleButton)c);
            if (c instanceof Container) out.addAll(toggles((Container)c));
        }
        return out;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
