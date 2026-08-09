package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import javax.swing.table.DefaultTableModel;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

/** Deterministic checks for exact EPICEFI output-channel aliases and evidence text. */
public final class OutputChannelResolutionRegressionTest {
    private OutputChannelResolutionRegressionTest() { }

    public static void main(String[] args) {
        exactGeneratedNamesMustResolve();
        evidenceMustExposeSelectedNamesAndRawValues();
        technicalDetailsMustDistinguishUnavailableMapFromReceivedZero();
        technicalGuidanceRenderingMustPreserveEvidenceAndReadOnlyText();
        liveChannelTableMustDistinguishUnavailableFromReceivedZero();
        overviewTextMustDistinguishUnavailableFromReceivedZero();
        System.out.println("OutputChannelResolutionRegressionTest passed");
    }

    private static void exactGeneratedNamesMustResolve() {
        Set<String> available = new HashSet<String>(Arrays.asList(
                "ready",
                "crank",
                "ignitionOn",
                "Main relay: Has IGN voltage",
                "sparkCutReason",
                "fuelCutReason",
                "overDwellNotScheduledCounter",
                "dwellOverChargeCounter",
                "dwellUnderChargeCounter",
                "sparkOutOfOrderCounter"));

        requireEquals("ready", OutputChannelResolver.resolve(ChannelRole.ENGINE_RUNNING, available));
        requireEquals("crank", OutputChannelResolver.resolve(ChannelRole.ENGINE_CRANKING, available));
        requireEquals("sparkCutReason", OutputChannelResolver.resolve(ChannelRole.IGN_CUT_CODE, available));
        requireEquals("fuelCutReason", OutputChannelResolver.resolve(ChannelRole.FUEL_CUT_CODE, available));
        requireEquals("overDwellNotScheduledCounter",
                OutputChannelResolver.resolve(ChannelRole.IGN_OVERDWELL, available));
        requireEquals("dwellOverChargeCounter",
                OutputChannelResolver.resolve(ChannelRole.IGN_OVERCHARGE_WARNINGS, available));
        requireEquals("dwellUnderChargeCounter",
                OutputChannelResolver.resolve(ChannelRole.IGN_UNDERCHARGE_WARNINGS, available));
        requireEquals("sparkOutOfOrderCounter",
                OutputChannelResolver.resolve(ChannelRole.IGN_SPARK_OUT_OF_ORDER, available));
    }

    private static void evidenceMustExposeSelectedNamesAndRawValues() {
        EnumMap<ChannelRole, String> selected = new EnumMap<ChannelRole, String>(ChannelRole.class);
        selected.put(ChannelRole.ENGINE_RUNNING, "ready");
        selected.put(ChannelRole.ENGINE_CRANKING, "crank");
        selected.put(ChannelRole.IGN_CUT_CODE, "sparkCutReason");

        EnumMap<ChannelRole, Double> latest = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        latest.put(ChannelRole.ENGINE_RUNNING, 1.0);
        latest.put(ChannelRole.ENGINE_CRANKING, 0.0);
        latest.put(ChannelRole.IGN_CUT_CODE, 14.0);

        String evidence = ChannelResolutionEvidence.build(selected, latest);
        require(evidence.contains("running: selected `ready`; latest raw 1.0"),
                "Running evidence must expose selected internal name and raw value");
        require(evidence.contains("cranking: selected `crank`; latest raw 0.0"),
                "Cranking evidence must preserve a received zero value");
        require(evidence.contains("Ign: Cut Code: selected `sparkCutReason`; latest raw 14.0"),
                "Cut-code evidence must expose selected internal name and raw value");
        require(evidence.contains("Ignition: overcharge warnings: unresolved; tried"),
                "Unresolved evidence must list attempted candidates");
    }

    private static void technicalDetailsMustDistinguishUnavailableMapFromReceivedZero() {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        String unavailable = AeTunerPanel.buildFuelPathStatusText(true, values);
        require(unavailable.contains(" | MAP n/a |"),
                "Technical details must render never-received MAP as n/a: " + unavailable);

        values.put(ChannelRole.MAP, 0.0);
        String receivedZero = AeTunerPanel.buildFuelPathStatusText(true, values);
        require(receivedZero.contains(" | MAP 0.00 |"),
                "Technical details must preserve a genuinely received MAP zero: " + receivedZero);

        values.clear();
        values.put(ChannelRole.AE_ADD_MS, 0.125);
        String unavailableCompanion = AeTunerPanel.buildFuelPathStatusText(false, values);
        require(unavailableCompanion.contains("Fuel: TPS AE add fuel ms 0.125"),
                "Technical details must preserve the received TPS fuel value: " + unavailableCompanion);
        require(unavailableCompanion.contains("Fuel: TPS extraFuel n/a"),
                "Technical details must keep an unavailable companion TPS fuel value as n/a: "
                        + unavailableCompanion);
    }

    private static void technicalGuidanceRenderingMustPreserveEvidenceAndReadOnlyText() {
        String mapGuidance = TechnicalDetailsRenderer.mapPredictGuidance(
                3, 1, 2, 1, "MAP Estimate collection: 24 stable samples.", "Review next.");
        require(mapGuidance.equals("MAP Predict guidance: 3 captured prediction event(s), "
                        + "1 event(s) with repeated timer resets, 2 reset-counter discontinuity event(s), "
                        + "1 event(s) with visible Wall Wetting contribution. "
                        + "MAP Estimate collection: 24 stable samples. Review next."),
                "Technical guidance must preserve all supplied evidence and selected next step: " + mapGuidance);

        String emptyTpsGuidance = TechnicalDetailsRenderer.tpsCycleGuidance(0, 0, 0);
        require(emptyTpsGuidance.contains("The plugin is read-only and will not change the ECU."),
                "Empty TPS guidance must retain the read-only statement: " + emptyTpsGuidance);
    }

    private static void liveChannelTableMustDistinguishUnavailableFromReceivedZero() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Role", "Channel", "Value", "Status"}, 0);
        EnumMap<ChannelRole, String> names = new EnumMap<ChannelRole, String>(ChannelRole.class);
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);

        names.put(ChannelRole.RPM, "RPMValue");
        values.put(ChannelRole.RPM, 0.0);
        LiveChannelTableRenderer.update(model, names, values);

        int rpmRow = ChannelRole.RPM.ordinal();
        int mapRow = ChannelRole.MAP.ordinal();
        requireEquals("0.00", String.valueOf(model.getValueAt(rpmRow, 2)));
        requireEquals("subscribed", String.valueOf(model.getValueAt(rpmRow, 3)));
        requireEquals("", String.valueOf(model.getValueAt(mapRow, 2)));
        requireEquals("missing", String.valueOf(model.getValueAt(mapRow, 3)));
    }

    private static void overviewTextMustDistinguishUnavailableFromReceivedZero() {
        String unavailableMap = OverviewTextRenderer.mapValues(Double.NaN, Double.NaN, Double.NaN);
        require(unavailableMap.contains("Real: n/a kPa"),
                "Overview must preserve unavailable MAP: " + unavailableMap);
        require(unavailableMap.contains("Gap: n/a"),
                "Overview must not derive a gap from unavailable MAP: " + unavailableMap);

        String receivedZero = OverviewTextRenderer.mapValues(0.0, 0.0, 0.0);
        require(receivedZero.contains("Real: 0.0 kPa"),
                "Overview must preserve received MAP zero: " + receivedZero);
        require(receivedZero.contains("Gap: 0.0 kPa"),
                "Overview must calculate a valid received-zero gap: " + receivedZero);

        requireEquals("Wall: n/a ms\nInstant: 0.000 ms",
                OverviewTextRenderer.transientFuel(Double.NaN, 0.0));
    }

    private static void requireEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
