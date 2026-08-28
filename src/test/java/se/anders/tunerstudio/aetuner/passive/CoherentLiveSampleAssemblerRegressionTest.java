package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.model.ChannelRole;

import java.util.EnumSet;

public final class CoherentLiveSampleAssemblerRegressionTest {
    private CoherentLiveSampleAssemblerRegressionTest() { }

    public static void main(String[] args) {
        doesNotEmitMidBurstTornValues();
        repeatedRoleDefinesFrameBoundary();
        incompleteCriticalBatchIsDropped();
        System.out.println("CoherentLiveSampleAssemblerRegressionTest passed");
    }

    private static void doesNotEmitMidBurstTornValues() {
        CoherentLiveSampleAssembler assembler = configured();
        long t = 1000000000L;
        require(assembler.accept(ChannelRole.RPM, 1500.0, t) == null, "frame emitted on RPM");
        require(assembler.accept(ChannelRole.TPS, 20.0, t + 100000L) == null, "frame emitted on TPS");
        require(assembler.accept(ChannelRole.MAP, 60.0, t + 200000L) == null, "frame emitted on MAP");
        require(assembler.accept(ChannelRole.FALLBACK_MAP, 79.1, t + 300000L) == null, "frame emitted on fallback");
        require(assembler.accept(ChannelRole.EFFECTIVE_MAP, 79.1, t + 400000L) == null, "frame emitted on effective");
        require(assembler.accept(ChannelRole.MAP_PRED_ACTIVE, 1.0, t + 500000L) == null, "frame emitted on prediction");
        require(assembler.accept(ChannelRole.SMOOTHED_DELTA_TPS, 0.60, t + 600000L) == null, "frame emitted on delta");
        require(assembler.accept(ChannelRole.ACCEL_THRESHOLD, 0.45, t + 700000L) == null, "frame emitted on threshold");
        require(assembler.accept(ChannelRole.GEAR, 2.0, t + 800000L) == null, "frame emitted on gear");
        require(assembler.accept(ChannelRole.VSS, 26.0, t + 900000L) == null, "frame emitted on VSS");

        // A quiet gap starts the next callback burst. The emitted frame must be
        // the completed previous burst, not a latest-value mixture.
        CoherentLiveSampleAssembler.Frame frame = assembler.accept(
                ChannelRole.EFFECTIVE_MAP, 83.3,
                t + CoherentLiveSampleAssembler.DEFAULT_QUIET_GAP_NS + 2000000L);
        require(frame != null, "completed coherent frame was not emitted at burst boundary");
        close(frame.values.get(ChannelRole.FALLBACK_MAP), 79.1, 0.0001,
                "previous-frame fallback was replaced by next-frame data");
        close(frame.values.get(ChannelRole.EFFECTIVE_MAP), 79.1, 0.0001,
                "next-frame Effective MAP leaked into previous coherent frame");
    }

    private static void repeatedRoleDefinesFrameBoundary() {
        CoherentLiveSampleAssembler assembler = configured();
        long t = 2000000000L;
        feedRequired(assembler, t, 1500.0, 20.0, 60.0, 80.0, 80.0, 2.0, 26.0);
        CoherentLiveSampleAssembler.Frame frame = assembler.accept(
                ChannelRole.RPM, 1510.0, t + 1500000L);
        require(frame != null, "repeated role did not close the previous callback batch");
        close(frame.values.get(ChannelRole.EFFECTIVE_MAP), 80.0, 0.0001,
                "repeated-role boundary returned mixed data");
        require(assembler.duplicateBoundaries() == 1,
                "duplicate boundary counter changed");
    }

    private static void incompleteCriticalBatchIsDropped() {
        CoherentLiveSampleAssembler assembler = configured();
        long t = 3000000000L;
        assembler.accept(ChannelRole.RPM, 1500.0, t);
        assembler.accept(ChannelRole.TPS, 20.0, t + 100000L);
        assembler.accept(ChannelRole.MAP, 60.0, t + 200000L);
        // No fallback/effective/prediction/gear/VSS critical callbacks arrived.
        CoherentLiveSampleAssembler.Frame frame = assembler.accept(
                ChannelRole.RPM, 1510.0, t + 300000L);
        require(frame == null, "incomplete critical batch was emitted");
        require(assembler.incompleteFrames() == 1,
                "incomplete-frame counter changed");
    }

    private static CoherentLiveSampleAssembler configured() {
        CoherentLiveSampleAssembler assembler = new CoherentLiveSampleAssembler();
        assembler.configureResolvedRoles(EnumSet.of(
                ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                ChannelRole.FALLBACK_MAP, ChannelRole.EFFECTIVE_MAP,
                ChannelRole.MAP_PRED_ACTIVE, ChannelRole.SMOOTHED_DELTA_TPS,
                ChannelRole.ACCEL_THRESHOLD, ChannelRole.GEAR, ChannelRole.VSS));
        return assembler;
    }

    private static void feedRequired(CoherentLiveSampleAssembler assembler,
                                     long t, double rpm, double tps, double map,
                                     double fallback, double effective,
                                     double gear, double vss) {
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

    private static void close(double actual, double expected,
                              double tolerance, String message) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " vs " + expected);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
