package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

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
        fortyPointSuggestionDoesNotMoveUsabilityBounds();
        adaptiveRpmMarkersAndActualBinBandsFollowInputs();
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
                    "Foundation must expose movement/timing, threshold/sensitivity and validation tasks");
            assertContains(panel.selectedTuningTaskForTest(), "1. TPS Movement / Timing",
                    "TPS Movement / Timing must be the first AE Foundation task");
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
            assertContains(tps, "suggested step +20.0 (guide only)",
                    "guided panel must identify the configured TPS step as coaching only");
            assertContains(tps, "usable +10.0 to +40.0",
                    "guided panel must show the broad physical TPS-step usability range");
            assertContains(tps, "suggested marker waits for frozen opening baseline",
                    "without a frozen opening baseline the coaching marker must remain hidden");
            assertTrue(!tps.contains("accepted +"),
                    "retired fixed TPS acceptance-window wording is still visible");

            String rpm = panel.liveRpmTextForTest();
            assertContains(rpm, "RPM 1987",
                    "guided panel must show current RPM above its bar");
            assertContains(rpm, "actual table bin 2000",
                    "guided panel must identify the actual RPM-bin target");
            assertContains(rpm, "entry/READY ±300",
                    "guided panel must show the actual Blend entry/READY window");
            assertContains(rpm, "no post-start RPM ceiling",
                    "guided panel must not imply a post-start RPM rejection window");
            assertTrue(!rpm.contains("READY ±200") && !rpm.contains("capture ±300"),
                    "legacy conflicting Blend RPM guidance is still visible");
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
                "a frozen 11-percent baseline with a +20 suggestion must produce a fixed 31-percent coaching marker");
        assertDouble(21.0, gauge.innerLowForTest(),
                "broad usable low edge must be baseline +10");
        assertDouble(51.0, gauge.innerHighForTest(),
                "broad usable high edge must be baseline +40");
        assertContains(gauge.labelTextForTest(), "frozen baseline 11.0%",
                "visible TPS guidance must say when the displayed baseline is frozen");
        assertContains(gauge.labelTextForTest(), "suggested step +20.0 (guide only)",
                "visible TPS guidance lost the coaching-only marker identity");
        assertContains(gauge.labelTextForTest(), "usable +10.0 to +40.0",
                "visible TPS guidance lost the broad physical usability range");
    }

    private static void fortyPointSuggestionDoesNotMoveUsabilityBounds() {
        GuidedTargetGauge gauge = new GuidedTargetGauge(GuidedTargetGauge.Mode.TPS);
        gauge.setTpsAdaptive(50.0, 10.0, 40.0);
        assertDouble(50.0, gauge.targetForTest(),
                "+40 coaching marker must remain at baseline +40");
        assertDouble(20.0, gauge.innerLowForTest(),
                "changing the suggestion moved the broad +10 usable low edge");
        assertDouble(50.0, gauge.innerHighForTest(),
                "broad +40 usable high edge did not remain at baseline +40");
        assertContains(gauge.labelTextForTest(), "suggested step +40.0 (guide only)",
                "+40 coaching marker is not visibly non-authoritative");
        assertContains(gauge.labelTextForTest(), "usable +10.0 to +40.0",
                "changing the suggestion altered visible physical usability bounds");
    }

    private static void adaptiveRpmMarkersAndActualBinBandsFollowInputs() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.showLiveSampleForTest(sample(12.0, 1987.0, 42.34));
            assertDouble(2000.0, panel.liveRpmTargetForTest(),
                    "RPM marker must use the selected actual table-bin target");
            assertDouble(1700.0, panel.liveRpmAcquireLowForTest(),
                    "Blend entry/READY low edge must follow selected RPM and ±300 tolerance");
            assertDouble(2300.0, panel.liveRpmAcquireHighForTest(),
                    "Blend entry/READY high edge must follow selected RPM and ±300 tolerance");
            assertDouble(1700.0, panel.liveRpmRetainLowForTest(),
                    "legacy outer band must not imply a different Blend RPM authority");
            assertDouble(2300.0, panel.liveRpmRetainHighForTest(),
                    "legacy outer band must match the Blend entry window");
        } finally {
            panel.disposePanel();
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
