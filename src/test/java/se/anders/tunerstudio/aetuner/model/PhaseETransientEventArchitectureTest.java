package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PhaseETransientEventArchitectureTest {
    private PhaseETransientEventArchitectureTest() { }

    public static void main(String[] args) {
        compatibilityAliasIsThin();
        transientEventOwnsOnlyModelState();
        compatibilityAliasPreservesObservableContract();
        System.out.println("PhaseETransientEventArchitectureTest passed");
    }

    private static void compatibilityAliasIsThin() {
        require(EventSummary.class.getSuperclass() == TransientEvent.class,
                "EventSummary must be only a compatibility alias over TransientEvent");
        require(EventSummary.class.getDeclaredFields().length == 0,
                "Compatibility alias must not duplicate transient-event state");
        require(EventSummary.class.getDeclaredMethods().length == 0,
                "Compatibility alias must not duplicate transient-event behavior");
    }

    private static void transientEventOwnsOnlyModelState() {
        Field[] fields = TransientEvent.class.getDeclaredFields();
        require(fields.length == 8,
                "TransientEvent should own exactly identity/samples/workflow/analyzer/assessment state; found "
                        + fields.length + " fields");
        Set<String> names = new HashSet<String>();
        for (Field field : fields) names.add(field.getName());
        String[] expected = new String[]{
                "index", "accepted", "eventClass", "reason", "samples",
                "mapPredictWorkflow", "analysis", "assessment"
        };
        for (String name : expected) {
            require(names.contains(name), "TransientEvent missing model field " + name);
        }
    }

    private static void compatibilityAliasPreservesObservableContract() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1000000000L, 1800.0, 10.0, 55.0, false));
        samples.add(sample(1060000000L, 1900.0, 20.0, 65.0, true));
        samples.add(sample(1180000000L, 2000.0, 21.0, 80.0, false));

        TransientEvent event = new TransientEvent(2, true, "MAP Predict event", "architecture",
                samples, true);
        EventSummary alias = new EventSummary(2, true, "MAP Predict event", "architecture",
                samples, true);

        require(event.toDisplayText().equals(alias.toDisplayText()),
                "Compatibility alias changed display output");
        require(event.toCsvHeader().equals(alias.toCsvHeader()),
                "Compatibility alias changed CSV schema");
        require(event.toCsvRows().equals(alias.toCsvRows()),
                "Compatibility alias changed CSV rows");
        require(event.hasMapPrediction() == alias.hasMapPrediction(),
                "Compatibility alias changed prediction evidence");
        require(bits(event.getMaxEffectiveMapGap()) == bits(alias.getMaxEffectiveMapGap()),
                "Compatibility alias changed prediction metrics");
    }

    private static LiveSample sample(long nano, double rpm, double tps, double map,
                                     boolean predictionActive) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        for (ChannelRole role : ChannelRole.values()) values.put(role, Double.valueOf(0.0));
        values.put(ChannelRole.RPM, Double.valueOf(rpm));
        values.put(ChannelRole.TPS, Double.valueOf(tps));
        values.put(ChannelRole.MAP, Double.valueOf(map));
        values.put(ChannelRole.FALLBACK_MAP, Double.valueOf(map + 20.0));
        values.put(ChannelRole.EFFECTIVE_MAP,
                Double.valueOf(map + (predictionActive ? 15.0 : 0.0)));
        values.put(ChannelRole.MAP_PRED_ACTIVE, Double.valueOf(predictionActive ? 1.0 : 0.0));
        values.put(ChannelRole.MAP_PRED_RESET_CNT, Double.valueOf(predictionActive ? 1.0 : 0.0));
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, Double.valueOf(predictionActive ? 2.0 : 0.0));
        values.put(ChannelRole.ACCEL_THRESHOLD, Double.valueOf(1.5));
        values.put(ChannelRole.LAMBDA, Double.valueOf(1.0));
        values.put(ChannelRole.TARGET_LAMBDA, Double.valueOf(1.0));
        values.put(ChannelRole.PW, Double.valueOf(3.0));
        values.put(ChannelRole.ENGINE_RUNNING, Double.valueOf(1.0));
        return new LiveSample(nano, nano / 1000000000.0, values,
                predictionActive ? 20.0 : 0.0, 0.0);
    }

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
