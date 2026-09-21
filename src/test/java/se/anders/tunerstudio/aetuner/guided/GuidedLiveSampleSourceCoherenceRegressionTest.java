package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Permanent coherent-callback contract for the Guided live source.
 *
 * The assembler deliberately remains private production implementation detail;
 * this regression reaches it reflectively so testability cannot widen the live
 * runtime API or re-introduce the retired Passive assembler.
 */
public final class GuidedLiveSampleSourceCoherenceRegressionTest {
    private GuidedLiveSampleSourceCoherenceRegressionTest() { }

    public static void main(String[] args) throws Exception {
        doesNotEmitMidBurstTornValues();
        repeatedRoleDefinesFrameBoundary();
        incompleteCriticalBatchIsDropped();
        System.out.println("GuidedLiveSampleSourceCoherenceRegressionTest passed");
    }

    private static void doesNotEmitMidBurstTornValues() throws Exception {
        Assembler assembler = configured();
        long t = 1000000000L;
        require(assembler.accept(ChannelRole.RPM, 1500.0, t) == null, "frame emitted on RPM");
        require(assembler.accept(ChannelRole.TPS, 20.0, t + 100000L) == null, "frame emitted on TPS");
        require(assembler.accept(ChannelRole.MAP, 60.0, t + 200000L) == null, "frame emitted on MAP");
        require(assembler.accept(ChannelRole.FALLBACK_MAP, 79.1, t + 300000L) == null,
                "frame emitted on fallback");
        require(assembler.accept(ChannelRole.EFFECTIVE_MAP, 79.1, t + 400000L) == null,
                "frame emitted on effective");
        require(assembler.accept(ChannelRole.MAP_PRED_ACTIVE, 1.0, t + 500000L) == null,
                "frame emitted on prediction");
        require(assembler.accept(ChannelRole.SMOOTHED_DELTA_TPS, 0.60, t + 600000L) == null,
                "frame emitted on delta");
        require(assembler.accept(ChannelRole.ACCEL_THRESHOLD, 0.45, t + 700000L) == null,
                "frame emitted on threshold");
        require(assembler.accept(ChannelRole.GEAR, 2.0, t + 800000L) == null, "frame emitted on gear");
        require(assembler.accept(ChannelRole.VSS, 26.0, t + 900000L) == null, "frame emitted on VSS");

        // Use a role not already present in the callback batch so this is a
        // genuine quiet-gap boundary rather than a duplicate-role boundary.
        Object frame = assembler.accept(ChannelRole.FUEL_CUT, 0.0, t + 10000000L);
        require(frame != null, "completed coherent frame was not emitted at quiet-gap boundary");
        EnumMap<ChannelRole, Double> values = values(frame);
        close(values.get(ChannelRole.FALLBACK_MAP), 79.1, 0.0001,
                "previous-frame fallback was replaced by next-frame data");
        close(values.get(ChannelRole.EFFECTIVE_MAP), 79.1, 0.0001,
                "quiet-gap trigger changed the completed coherent frame");
        require(assembler.quietGapBoundaries() == 1L,
                "quiet-gap boundary counter changed");
        require(assembler.duplicateBoundaries() == 0L,
                "quiet-gap regression accidentally exercised duplicate-role precedence");
    }

    private static void repeatedRoleDefinesFrameBoundary() throws Exception {
        Assembler assembler = configured();
        long t = 2000000000L;
        feedRequired(assembler, t, 1500.0, 20.0, 60.0, 80.0, 80.0, 2.0, 26.0);
        Object frame = assembler.accept(ChannelRole.RPM, 1510.0, t + 1500000L);
        require(frame != null, "repeated role did not close the previous callback batch");
        close(values(frame).get(ChannelRole.EFFECTIVE_MAP), 80.0, 0.0001,
                "repeated-role boundary returned mixed data");
        require(assembler.duplicateBoundaries() == 1L,
                "duplicate boundary counter changed");
    }

    private static void incompleteCriticalBatchIsDropped() throws Exception {
        Assembler assembler = configured();
        long t = 3000000000L;
        assembler.accept(ChannelRole.RPM, 1500.0, t);
        assembler.accept(ChannelRole.TPS, 20.0, t + 100000L);
        assembler.accept(ChannelRole.MAP, 60.0, t + 200000L);
        Object frame = assembler.accept(ChannelRole.RPM, 1510.0, t + 300000L);
        require(frame == null, "incomplete critical batch was emitted");
        require(assembler.incompleteFrames() == 1L,
                "incomplete-frame counter changed");
    }

    private static Assembler configured() throws Exception {
        Assembler assembler = new Assembler();
        assembler.configure(EnumSet.of(
                ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                ChannelRole.FALLBACK_MAP, ChannelRole.EFFECTIVE_MAP,
                ChannelRole.MAP_PRED_ACTIVE, ChannelRole.SMOOTHED_DELTA_TPS,
                ChannelRole.ACCEL_THRESHOLD, ChannelRole.GEAR, ChannelRole.VSS));
        return assembler;
    }

    private static void feedRequired(Assembler assembler,
                                     long t, double rpm, double tps, double map,
                                     double fallback, double effective,
                                     double gear, double vss) throws Exception {
        assembler.accept(ChannelRole.RPM, rpm, t);
        assembler.accept(ChannelRole.TPS, tps, t + 100000L);
        assembler.accept(ChannelRole.MAP, map, t + 200000L);
        assembler.accept(ChannelRole.FALLBACK_MAP, fallback, t + 300000L);
        assembler.accept(ChannelRole.EFFECTIVE_MAP, effective, t + 400000L);
        assembler.accept(ChannelRole.MAP_PRED_ACTIVE, 1.0, t + 500000L);
        assembler.accept(ChannelRole.SMOOTHED_DELTA_TPS, 0.60, t + 600000L);
        assembler.accept(ChannelRole.ACCEL_THRESHOLD, 0.45, t + 700000L);
        assembler.accept(ChannelRole.GEAR, gear, t + 800000L);
        assembler.accept(ChannelRole.VSS, vss, t + 900000L);
    }

    @SuppressWarnings("unchecked")
    private static EnumMap<ChannelRole, Double> values(Object frame) throws Exception {
        Field values = frame.getClass().getDeclaredField("values");
        values.setAccessible(true);
        return (EnumMap<ChannelRole, Double>) values.get(frame);
    }

    private static final class Assembler {
        private final Object target;
        private final Method configure;
        private final Method accept;
        private final Method incompleteFrames;
        private final Method duplicateBoundaries;
        private final Method quietGapBoundaries;

        Assembler() throws Exception {
            Class<?> type = Class.forName(
                    "se.anders.tunerstudio.aetuner.guided.GuidedLiveSampleSource$CoherentAssembler");
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            target = constructor.newInstance();
            configure = type.getDeclaredMethod("configureResolvedRoles", Set.class);
            accept = type.getDeclaredMethod("accept", ChannelRole.class, double.class, long.class);
            incompleteFrames = type.getDeclaredMethod("incompleteFrames");
            duplicateBoundaries = type.getDeclaredMethod("duplicateBoundaries");
            quietGapBoundaries = type.getDeclaredMethod("quietGapBoundaries");
            configure.setAccessible(true);
            accept.setAccessible(true);
            incompleteFrames.setAccessible(true);
            duplicateBoundaries.setAccessible(true);
            quietGapBoundaries.setAccessible(true);
        }

        void configure(Set<ChannelRole> resolved) throws Exception {
            configure.invoke(target, resolved);
        }

        Object accept(ChannelRole role, double value, long nano) throws Exception {
            return accept.invoke(target, role, Double.valueOf(value), Long.valueOf(nano));
        }

        long incompleteFrames() throws Exception {
            return ((Number) incompleteFrames.invoke(target)).longValue();
        }

        long duplicateBoundaries() throws Exception {
            return ((Number) duplicateBoundaries.invoke(target)).longValue();
        }

        long quietGapBoundaries() throws Exception {
            return ((Number) quietGapBoundaries.invoke(target)).longValue();
        }
    }

    private static void close(Double actual, double expected,
                              double tolerance, String message) {
        if (actual == null || !Double.isFinite(actual.doubleValue())
                || Math.abs(actual.doubleValue() - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " vs " + expected);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
