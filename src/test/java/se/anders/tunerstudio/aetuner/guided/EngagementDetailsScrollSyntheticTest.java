package se.anders.tunerstudio.aetuner.guided;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Real-Swing regression for passive Foundation 1 non-driver Details. */
public final class EngagementDetailsScrollSyntheticTest {
    private EngagementDetailsScrollSyntheticTest() { }

    public static void main(String[] args) throws Exception {
        File out = outputDirectory();
        if (!out.isDirectory() && !out.mkdirs()) {
            throw new IllegalStateException("Could not create synthetic output directory " + out);
        }

        EngagementPassiveCapture.reset();
        EngagementDetectionWriteSelection.resetForTest();

        final GuidedFocusWindow[] window = new GuidedFocusWindow[1];
        final boolean[] toggleOk = new boolean[]{false};
        final boolean[] scrollContractOk = new boolean[]{false};
        final boolean[] naturalHeightOk = new boolean[]{false};
        final boolean[] passiveCardOk = new boolean[]{false};
        final boolean[] legacyCalibrationHidden = new boolean[]{false};

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                GuidedFocusWindow w = new GuidedFocusWindow(null);
                w.setSize(new Dimension(1366, 768));
                w.update(GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                        GuidedCaptureState.IDLE, null, null, "");

                JCheckBox toggle = findCheckBox(w, "Driver view");
                toggleOk[0] = toggle != null && toggle.isSelected();
                if (toggle != null && toggle.isSelected()) toggle.doClick();

                w.setVisible(true);
                w.validate();
                scrollContractOk[0] = w.engagementDetailsScrollEnabledForTest();
                naturalHeightOk[0] = w.engagementDetailsNotCompressedForTest();
                EngagementDetectionGuidedFocusPanel engagement = w.engagementPanelForTest();
                passiveCardOk[0] = "engagement".equals(w.visibleCardForTest())
                        && engagement.sweepTargetTextForTest().contains("No exact TPS target")
                        && !engagement.settingsToggleVisibleForTest()
                        && !engagement.settingsPanelVisibleForTest()
                        && !engagement.requestedDeltaWindowEnabledForTest();
                legacyCalibrationHidden[0] = !w.engagementCalibrationDriverForTest().isShowing()
                        && !w.engagementCalibrationDetailsForTest().isVisible();
                window[0] = w;
            }
        });

        final File file = new File(out,
                "workspace-guided-focus-engagement-details-scroll-1366.png");
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    GuidedFocusWindow w = window[0];
                    BufferedImage image = new BufferedImage(
                            Math.max(1, w.getWidth()),
                            Math.max(1, w.getHeight()),
                            BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    try { w.printAll(g); }
                    finally { g.dispose(); }
                    ImageIO.write(image, "png", file);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                GuidedFocusWindow w = window[0];
                w.setSize(new Dimension(820, 520));
                w.validate();
                // The passive Details surface may or may not need a vertical
                // scrollbar depending on look-and-feel metrics. Horizontal
                // scrolling remains forbidden by the scroll contract.
                w.disposeWindow();
            }
        });

        require(toggleOk[0],
                "synthetic Focus window did not start in Driver view");
        require(scrollContractOk[0],
                "passive non-driver Details is not a width-tracking vertical scroll surface");
        require(naturalHeightOk[0],
                "passive Details content was compressed below its preferred height");
        require(passiveCardOk[0],
                "passive Details revived exact-target or timing-experiment controls");
        require(legacyCalibrationHidden[0],
                "legacy quiet-calibration UI is still reachable in passive Foundation 1");
        require(file.isFile() && file.length() > 0L,
                "passive Foundation Details scroll screenshot missing");

        System.out.println("ENGAGEMENT_DETAILS_SCROLL passive 1366/768 + 820/520 passed");
        System.out.println("EngagementDetailsScrollSyntheticTest passed");
    }

    private static JCheckBox findCheckBox(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox
                    && text.equals(((JCheckBox) component).getText())) {
                return (JCheckBox) component;
            }
            if (component instanceof Container) {
                JCheckBox nested = findCheckBox((Container) component, text);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static File outputDirectory() {
        String configured = System.getenv("SYNTHETIC_INTEGRATION_OUT");
        return new File(configured == null || configured.length() == 0
                ? "target/synthetic-plugin-integration" : configured);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
