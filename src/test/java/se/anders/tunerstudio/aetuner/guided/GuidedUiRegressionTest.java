package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import javax.swing.ScrollPaneConstants;
import javax.swing.text.DefaultCaret;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.EnumMap;

public final class GuidedUiRegressionTest {
    private GuidedUiRegressionTest() { }

    public static void main(String[] args) {
        checksCaretNeverFollowsLiveText();
        liveAdaptiveGuidanceIsVisibleWithoutTheDashboard();
        adaptiveTpsGuideWaitsForFrozenBaseline();
        adaptiveRpmMarkersAndRoadBandsFollowInputs();
        guidedWorkspaceUsesVerticalOnlyOuterScroll();
        preferredWindowSizeIsCappedToTheScreen();
        System.out.println("GuidedUiRegressionTest passed");
    }

    private static void checksCaretNeverFollowsLiveText() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            assertEquals(DefaultCaret.NEVER_UPDATE,
                    panel.checksCaretPolicyForTest(),
                    "live checks caret must not drag the scrollbar during refresh");
        } finally {
            panel.disposePanel();
        }
    }

    private static void liveAdaptiveGuidanceIsVisibleWithoutTheDashboard() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.showLiveSampleForTest(sample(12.0, 1987.0, 42.34));
            String tps = panel.liveTpsTextForTest();
            assertContains(tps, "TPS 42.3%",
                    "guided panel must show current TPS above its bar");
            assertContains(tps, "guide +20.0",
                    "guided panel must show the configured relative TPS-step guide");
            assertContains(tps, "not an absolute acceptance band",
                    "guided panel must make clear that the TPS marker is advisory");
            assertContains(tps, "target marker waits for frozen opening baseline",
                    "without a frozen opening baseline the driver target must remain hidden");

            String rpm = panel.liveRpmTextForTest();
            assertContains(rpm, "RPM 1987",
                    "guided panel must show current RPM above its bar");
            assertContains(rpm, "road region 2000 ±300",
                    "guided panel must show the adaptive RPM acquisition region");
            assertContains(rpm, "READY retain ±450",
                    "guided panel must show the broader READY retention region");
        } finally {
            panel.disposePanel();
        }
    }

    private static void adaptiveTpsGuideWaitsForFrozenBaseline() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.showLiveSampleForTest(sample(12.0, 1987.0, 42.34));
            assertNaN(panel.liveTpsTargetForTest(),
                    "without a frozen opening baseline the TPS marker must not chase current TPS");
            assertNaN(panel.liveTpsBandLowForTest(),
                    "without a frozen opening baseline the TPS guide band must be hidden");
            assertNaN(panel.liveTpsBandHighForTest(),
                    "without a frozen opening baseline the TPS guide band must be hidden");
        } finally {
            panel.disposePanel();
        }

        GuidedTargetGauge gauge = new GuidedTargetGauge(GuidedTargetGauge.Mode.TPS);
        gauge.setTpsAdaptive(31.0, 11.0, 20.0);
        assertDouble(31.0, gauge.targetForTest(),
                "a frozen 11-percent baseline with a +20 guide must produce a fixed 31-percent marker");
        assertDouble(26.0, gauge.innerLowForTest(),
                "frozen-baseline guide low edge must be five points below the marker");
        assertDouble(36.0, gauge.innerHighForTest(),
                "frozen-baseline guide high edge must be five points above the marker");
        assertContains(gauge.labelTextForTest(), "frozen baseline 11.0%",
                "visible TPS guidance must say when the displayed baseline is frozen");
    }

    private static void adaptiveRpmMarkersAndRoadBandsFollowInputs() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.showLiveSampleForTest(sample(12.0, 1987.0, 42.34));
            assertDouble(2000.0, panel.liveRpmTargetForTest(),
                    "RPM marker must use the selected RPM point");
            assertDouble(1700.0, panel.liveRpmAcquireLowForTest(),
                    "adaptive road RPM region low edge must follow selected RPM");
            assertDouble(2300.0, panel.liveRpmAcquireHighForTest(),
                    "adaptive road RPM region high edge must follow selected RPM");
            assertDouble(1550.0, panel.liveRpmRetainLowForTest(),
                    "adaptive READY RPM low edge must follow selected RPM");
            assertDouble(2450.0, panel.liveRpmRetainHighForTest(),
                    "adaptive READY RPM high edge must follow selected RPM");
        } finally {
            panel.disposePanel();
        }
    }

    private static void guidedWorkspaceUsesVerticalOnlyOuterScroll() {
        AeTunerPlugin plugin = new AeTunerPlugin();
        try {
            assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    plugin.guidedWorkspaceVerticalScrollPolicyForTest(),
                    "Guided Capture must expose a vertical scrollbar when content is taller than the window");
            assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                    plugin.guidedWorkspaceHorizontalScrollPolicyForTest(),
                    "Guided Capture must not add a horizontal scrollbar");
            assertTrue(plugin.guidedWorkspaceTracksViewportWidthForTest(),
                    "Guided Capture content must follow the available viewport width");
            assertTrue(!plugin.guidedWorkspaceTracksViewportHeightForTest(),
                    "Guided Capture must retain natural height so vertical scrolling can occur");
            assertTrue(plugin.guidedWorkspaceScrollUnitForTest() >= 16,
                    "Guided Capture mouse-wheel/arrow scrolling must use a practical increment");
        } finally {
            plugin.close();
        }
    }

    private static void preferredWindowSizeIsCappedToTheScreen() {
        Dimension capped = AeTunerPlugin.screenRelativePreferredSize(
                new Dimension(1800, 1200),
                new Rectangle(0, 0, 1366, 768));
        assertEquals(1174, capped.width,
                "1366-pixel screen width must use the 86-percent cap");
        assertEquals(645, capped.height,
                "768-pixel screen height must use the 84-percent cap");

        Dimension normal = AeTunerPlugin.screenRelativePreferredSize(
                new Dimension(700, 400),
                new Rectangle(0, 0, 1920, 1080));
        assertEquals(900, normal.width,
                "large screens must preserve a usable minimum width");
        assertEquals(620, normal.height,
                "large screens must preserve a usable minimum height");
    }

    private static LiveSample sample(double seconds, double rpm, double tps) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, 55.0);
        values.put(ChannelRole.FALLBACK_MAP, 55.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static void assertContains(String actual, String expected,
                                       String message) {
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError(message + ": expected to find `"
                    + expected + "` in `" + actual + "`");
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertDouble(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertNaN(double actual, String message) {
        if (!Double.isNaN(actual)) {
            throw new AssertionError(message + ": expected NaN but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
