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
        taskDrivenWorkflowIsVisibleAndSafeByDefault();
        captureRpmIsNotIndependentOfSelectedTablePoint();
        workingTuneRefreshPreservesSelectedTablePoint();
        checksCaretNeverFollowsLiveText();
        liveControlledGuidanceIsVisibleWithoutTheDashboard();
        controlledTpsTargetWaitsForFrozenBaseline();
        fortyPointTargetClampsAcceptanceAtRoadCeiling();
        adaptiveRpmMarkersAndActualBinBandsFollowInputs();
        guidedWorkspaceUsesVerticalOnlyOuterScroll();
        preferredWindowSizeIsCappedToTheScreen();
        System.out.println("GuidedUiRegressionTest passed");
    }

    private static void taskDrivenWorkflowIsVisibleAndSafeByDefault() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            assertEquals(7, panel.tuningAreaCountForTest(),
                    "Guided Tuning must expose the seven product-level tuning areas including Decel / Tip-out");
            assertContains(panel.selectedTuningAreaForTest(), "AE Foundation",
                    "Foundation must be the initial Guided tuning area");
            assertEquals(3, panel.tuningTaskCountForTest(),
                    "Foundation must expose model/timing, threshold/sensitivity and validation tasks");
            assertContains(panel.selectedTuningTaskForTest(), "1. Detector Model / Timing",
                    "Detector Model / Timing must be the first AE Foundation task");
            assertContains(panel.workflowStageTextForTest(), "SETUP",
                    "evidence workflow must expose setup");
            assertContains(panel.workflowStageTextForTest(), "CAPTURE",
                    "evidence workflow must expose optional capture when evidence is needed");
            assertContains(panel.workflowStageTextForTest(), "REVIEW",
                    "evidence workflow must expose review");
            assertContains(panel.workflowStageTextForTest(), "APPLY",
                    "workflow must preserve guarded apply when a change exists");
            assertTrue(!panel.applyCurrentProposalEnabledForTest(),
                    "no-change Foundation startup must not enable Apply");
            assertTrue(!panel.restorePreviousApplyEnabledForTest(),
                    "Restore Previous Apply must start disabled without a successful prior apply");
            assertTrue(panel.lastApplyManifestForTest().length() == 0,
                    "construction alone must not invent an MSQ apply manifest");
        } finally {
            panel.disposePanel();
        }
    }

    private static void captureRpmIsNotIndependentOfSelectedTablePoint() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            assertTrue(!panel.captureRpmEditableForTest(),
                    "writable Blend Duration capture RPM must not be independently editable");
            assertDouble(2000.0, panel.configuredStartRpmForTest(),
                    "pre-project placeholder RPM unexpectedly changed");
        } finally {
            panel.disposePanel();
        }
    }

    private static void workingTuneRefreshPreservesSelectedTablePoint() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.replaceTablePointsForTest(blendPoints());
            panel.selectTablePointForTest(1);
            assertDouble(2600.0, panel.selectedTablePointRpmForTest(),
                    "2600 RPM table point was not selected before refresh");
            panel.replaceTablePointsForTest(blendPoints());
            assertDouble(2600.0, panel.selectedTablePointRpmForTest(),
                    "working-tune refresh reverted the selected 2600 RPM point");
            assertDouble(2600.0, panel.configuredStartRpmForTest(),
                    "capture target did not remain synchronized to 2600 RPM after refresh");
            panel.selectTablePointForTest(2);
            panel.replaceTablePointsForTest(blendPoints());
            assertDouble(3800.0, panel.selectedTablePointRpmForTest(),
                    "working-tune refresh reverted the selected 3800 RPM point");
            panel.selectTablePointForTest(3);
            panel.replaceTablePointsForTest(blendPoints());
            assertDouble(5000.0, panel.selectedTablePointRpmForTest(),
                    "working-tune refresh reverted the selected 5000 RPM point");
        } finally {
            panel.disposePanel();
        }
    }

    private static java.util.List<GuidedBlendProposal.PointChoice> blendPoints() {
        return java.util.Arrays.asList(
                new GuidedBlendProposal.PointChoice(0, 1500.0, 0.30,
                        Double.NEGATIVE_INFINITY, 2050.0),
                new GuidedBlendProposal.PointChoice(1, 2600.0, 0.26,
                        2050.0, 3200.0),
                new GuidedBlendProposal.PointChoice(2, 3800.0, 0.24,
                        3200.0, 4400.0),
                new GuidedBlendProposal.PointChoice(3, 5000.0, 0.18,
                        4400.0, Double.POSITIVE_INFINITY));
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

    private static void liveControlledGuidanceIsVisibleWithoutTheDashboard() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.showLiveSampleForTest(sample(12.0, 1987.0, 42.34));
            String tps = panel.liveTpsTextForTest();
            assertContains(tps, "TPS 42.3%",
                    "guided panel must show current TPS above its bar");
            assertContains(tps, "target step +20.0",
                    "guided panel must show the configured relative TPS-step target");
            assertContains(tps, "accepted +10.0 to +30.0",
                    "guided panel must show the wider controlled TPS-step acceptance window");
            assertContains(tps, "target marker waits for frozen opening baseline",
                    "without a frozen opening baseline the driver target must remain hidden");
            assertTrue(!tps.contains("not an absolute acceptance band"),
                    "retired advisory-only TPS wording is still visible");

            String rpm = panel.liveRpmTextForTest();
            assertContains(rpm, "RPM 1987",
                    "guided panel must show current RPM above its bar");
            assertContains(rpm, "actual table bin 2000",
                    "guided panel must identify the actual RPM-bin target");
            assertContains(rpm, "READY ±200",
                    "guided panel must show the actual-bin READY window");
            assertContains(rpm, "capture ±300",
                    "guided panel must show the active capture drift window");
        } finally {
            panel.disposePanel();
        }
    }

    private static void controlledTpsTargetWaitsForFrozenBaseline() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.showLiveSampleForTest(sample(12.0, 1987.0, 42.34));
            assertNaN(panel.liveTpsTargetForTest(),
                    "without a frozen opening baseline the TPS marker must not chase current TPS");
            assertNaN(panel.liveTpsBandLowForTest(),
                    "without a frozen opening baseline the TPS target band must be hidden");
            assertNaN(panel.liveTpsBandHighForTest(),
                    "without a frozen opening baseline the TPS target band must be hidden");
        } finally {
            panel.disposePanel();
        }

        GuidedTargetGauge gauge = new GuidedTargetGauge(GuidedTargetGauge.Mode.TPS);
        gauge.setTpsAdaptive(31.0, 11.0, 20.0);
        assertDouble(31.0, gauge.targetForTest(),
                "a frozen 11-percent baseline with a +20 target must produce a fixed 31-percent marker");
        assertDouble(21.0, gauge.innerLowForTest(),
                "controlled +20 low edge must be baseline +10");
        assertDouble(41.0, gauge.innerHighForTest(),
                "controlled +20 high edge must be baseline +30");
        assertContains(gauge.labelTextForTest(), "frozen baseline 11.0%",
                "visible TPS guidance must say when the displayed baseline is frozen");
        assertContains(gauge.labelTextForTest(), "accepted +10.0 to +30.0",
                "visible TPS guidance lost the wider controlled relative-step range");
    }

    private static void fortyPointTargetClampsAcceptanceAtRoadCeiling() {
        GuidedTargetGauge gauge = new GuidedTargetGauge(GuidedTargetGauge.Mode.TPS);
        gauge.setTpsAdaptive(50.0, 10.0, 40.0);
        assertDouble(50.0, gauge.targetForTest(),
                "+40 target marker must remain at baseline +40");
        assertDouble(40.0, gauge.innerLowForTest(),
                "+40 target low edge must be baseline +30");
        assertDouble(50.0, gauge.innerHighForTest(),
                "+40 target high edge must clamp to the +40 road-capture ceiling");
        assertContains(gauge.labelTextForTest(), "accepted +30.0 to +40.0",
                "+40 target must visibly communicate its widened clamped acceptance range");
    }

    private static void adaptiveRpmMarkersAndActualBinBandsFollowInputs() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.showLiveSampleForTest(sample(12.0, 1987.0, 42.34));
            assertDouble(2000.0, panel.liveRpmTargetForTest(),
                    "RPM marker must use the selected actual table-bin target");
            assertDouble(1800.0, panel.liveRpmAcquireLowForTest(),
                    "actual-bin READY low edge must follow selected RPM");
            assertDouble(2200.0, panel.liveRpmAcquireHighForTest(),
                    "actual-bin READY high edge must follow selected RPM");
            assertDouble(1700.0, panel.liveRpmRetainLowForTest(),
                    "active capture low edge must follow selected RPM");
            assertDouble(2300.0, panel.liveRpmRetainHighForTest(),
                    "active capture high edge must follow selected RPM");
        } finally {
            panel.disposePanel();
        }
    }

    private static void guidedWorkspaceUsesVerticalOnlyOuterScroll() {
        AeTunerPlugin plugin = new AeTunerPlugin();
        try {
            assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    plugin.guidedWorkspaceVerticalScrollPolicyForTest(),
                    "Guided Tuning must expose a vertical scrollbar when content is taller than the window");
            assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                    plugin.guidedWorkspaceHorizontalScrollPolicyForTest(),
                    "Guided Tuning must not add a horizontal scrollbar");
            assertTrue(plugin.guidedWorkspaceTracksViewportWidthForTest(),
                    "Guided Tuning content must follow the available viewport width");
            assertTrue(!plugin.guidedWorkspaceTracksViewportHeightForTest(),
                    "Guided Tuning must retain natural height so vertical scrolling can occur");
            assertTrue(plugin.guidedWorkspaceScrollUnitForTest() >= 16,
                    "Guided Tuning mouse-wheel/arrow scrolling must use a practical increment");
        } finally {
            plugin.close();
        }
    }

    private static void preferredWindowSizeIsCappedToTheScreen() {
        Dimension capped = AeTunerPlugin.screenRelativePreferredSize(
                new Dimension(1800, 1200), new Rectangle(0, 0, 1366, 768));
        assertEquals(1174, capped.width,
                "1366-pixel screen width must use the 86-percent cap");
        assertEquals(645, capped.height,
                "768-pixel screen height must use the 84-percent cap");
        Dimension normal = AeTunerPlugin.screenRelativePreferredSize(
                new Dimension(700, 400), new Rectangle(0, 0, 1920, 1080));
        assertEquals(900, normal.width,
                "large screens must preserve a usable minimum width");
        assertEquals(620, normal.height,
                "large screens must preserve a usable minimum height");
    }

    private static LiveSample sample(double seconds, double rpm, double tps) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, 55.0);
        values.put(ChannelRole.FALLBACK_MAP, 55.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static void assertContains(String actual, String expected, String message) {
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError(message + ": expected to find `" + expected + "` in `" + actual + "`");
        }
    }
    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + " but was " + actual);
    }
    private static void assertDouble(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) throw new AssertionError(message + ": expected " + expected + " but was " + actual);
    }
    private static void assertNaN(double actual, String message) {
        if (!Double.isNaN(actual)) throw new AssertionError(message + ": expected NaN but was " + actual);
    }
    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}