package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
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
        compatibilityAliasIsGone();
        transientEventOwnsOnlyModelState();
        transientEventPreservesObservableContract();
        System.out.println("PhaseETransientEventArchitectureTest passed");
    }

    private static void compatibilityAliasIsGone() {
        try {
            Class.forName("se.anders.tunerstudio.aetuner.model.EventSummary");
            throw new AssertionError("EventSummary compatibility alias returned after TransientEvent migration");
        } catch (ClassNotFoundException expected) {
            // TransientEvent is now the sole event model authority.
        }
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

    private static void transientEventPreservesObservableContract() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1000000000L, 1800.0, 10.0, 55.0, false));
        samples.add(sample(1060000000L, 1900.0, 20.0, 65.0, true));
        samples.add(sample(1180000000L, 2000.0, 21.0, 80.0, false));

        TransientEvent event = new TransientEvent(2, true, "MAP Predict event", "architecture",
                samples, true);

        require(event.toDisplayText().contains("MAP Predict event"),
                "TransientEvent lost display output");
        require(event.toCsvHeader().contains("event_index"),
                "TransientEvent lost CSV schema");
        List<String> rows = event.toCsvRows();
        require(rows.size() == samples.size()
                        && rows.get(0).contains("\"MAP Predict event\"")
                        && rows.get(0).contains("\"architecture\""),
                "TransientEvent lost CSV row output");
        require(event.hasMapPrediction(),
                "TransientEvent lost prediction evidence");
        require(Double.isFinite(event.getMaxEffectiveMapGap()),
                "TransientEvent lost prediction metrics");
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
