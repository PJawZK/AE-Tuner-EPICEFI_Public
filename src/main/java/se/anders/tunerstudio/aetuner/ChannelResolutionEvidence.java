package se.anders.tunerstudio.aetuner;

import java.util.Arrays;
import java.util.Map;

/** Formats exact selected channel names and latest raw values for saved evidence. */
final class ChannelResolutionEvidence {
    private static final ChannelRole[] CRITICAL_ROLES = {
            ChannelRole.ENGINE_RUNNING,
            ChannelRole.ENGINE_CRANKING,
            ChannelRole.IGNITION_ON,
            ChannelRole.MAIN_RELAY_HAS_IGN,
            ChannelRole.IGNITION_TIMING,
            ChannelRole.BOOST_TARGET,
            ChannelRole.FUEL_PRESSURE_HIGH,
            ChannelRole.FUEL_PRESSURE_LOW,
            ChannelRole.TRIGGER_ERROR,
            ChannelRole.TRIGGER_ERROR_COUNT,
            ChannelRole.TOTAL_SPARK_CUT,
            ChannelRole.IGN_CUT_CODE,
            ChannelRole.FUEL_CUT,
            ChannelRole.FUEL_CUT_CODE,
            ChannelRole.STOP_ENGINE_CODE,
            ChannelRole.IGN_OVERDWELL,
            ChannelRole.IGN_OVERCHARGE_WARNINGS,
            ChannelRole.IGN_UNDERCHARGE_WARNINGS,
            ChannelRole.IGN_SPARK_OUT_OF_ORDER
    };

    private ChannelResolutionEvidence() { }

    static String build(Map<ChannelRole, String> selectedChannels,
                        Map<ChannelRole, Double> latestValues) {
        StringBuilder text = new StringBuilder();
        for (ChannelRole role : CRITICAL_ROLES) {
            String selected = selectedChannels == null ? null : selectedChannels.get(role);
            Double latest = latestValues == null ? null : latestValues.get(role);
            text.append("- ").append(role.getLabel()).append(": ");
            if (selected == null || selected.length() == 0) {
                text.append("unresolved; tried ")
                        .append(Arrays.toString(role.getCandidates()));
            } else {
                text.append("selected `").append(selected).append("`");
                if (latest != null && Double.isFinite(latest.doubleValue())) {
                    text.append("; latest raw ").append(Double.toString(latest.doubleValue()));
                } else {
                    text.append("; no live value received yet");
                }
            }
            text.append("\n");
        }
        return text.toString();
    }
}
