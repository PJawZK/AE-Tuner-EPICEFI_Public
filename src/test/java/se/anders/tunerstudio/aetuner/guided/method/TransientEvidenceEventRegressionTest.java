package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Permanent attribution gates for the shared transient event layer. */
public final class TransientEvidenceEventRegressionTest {
    private TransientEvidenceEventRegressionTest() { }

    public static void main(String[] args) {
        steadyInstantPwDoesNotManufactureRepeatedEvents();
        instantCounterEdgesOwnSubsequentEvents();
        lambdaIsReadFromDelayedWindowNotEventOnset();
        fuelCutEventIsExcludedFromAcceptedAuthority();
        wallAndTpsUseTheirOwnAnchors();
        System.out.println("TransientEvidenceEventRegressionTest passed");
    }

    private static void steadyInstantPwDoesNotManufactureRepeatedEvents() {
        List<LiveSample> evidence = new ArrayList<LiveSample>();
        for (int i = 0; i <= 20; i++) {
            double t = i * 0.10;
            evidence.add(sample(t, 10.0, i < 12 ? 0.8 : 0.0,
                    false, false, 0.0, 1.02, false));
        }
        List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.collect(
                TransientEvidenceEvent.Owner.INSTANT, evidence);
        require(events.size() == 1,
                "steady non-zero Instant PW created repeated pulse events");
    }

    private static void instantCounterEdgesOwnSubsequentEvents() {
        List<LiveSample> evidence = new ArrayList<LiveSample>();
        addInstantEvent(evidence, 0.0, 10.0, 0.04);
        addInstantEvent(evidence, 2.5, 11.0, 0.04);
        addInstantEvent(evidence, 5.0, 12.0, 0.04);
        List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.collect(
                TransientEvidenceEvent.Owner.INSTANT, evidence);
        require(events.size() == 3,
                "counter-edge Instant authority did not produce exactly three events");
        require(TransientEvidenceEvent.acceptedForTask(
                TransientEvidenceEvent.Owner.INSTANT, evidence).size() == 3,
                "clean counter-edge Instant events were not accepted");
    }

    private static void lambdaIsReadFromDelayedWindowNotEventOnset() {
        List<LiveSample> evidence = new ArrayList<LiveSample>();
        evidence.add(sample(0.00, 20.0, 0.8, false, false,
                0.0, 0.80, false));
        evidence.add(sample(0.10, 20.0, 0.0, false, false,
                0.0, 0.80, false));
        for (int i = 2; i <= 8; i++) {
            evidence.add(sample(i * 0.10, 20.0, 0.0, false, false,
                    0.0, 1.05, false));
        }
        for (int i = 9; i <= 18; i++) {
            evidence.add(sample(i * 0.10, 20.0, 0.0, false, false,
                    0.0, 1.00, false));
        }
        List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.collect(
                TransientEvidenceEvent.Owner.INSTANT, evidence);
        require(events.size() == 1, "delayed-window fixture lost Instant event");
        require(events.get(0).earlyError > 0.04,
                "event-onset rich lambda contaminated delayed early response attribution");
    }

    private static void fuelCutEventIsExcludedFromAcceptedAuthority() {
        List<LiveSample> evidence = new ArrayList<LiveSample>();
        addInstantEvent(evidence, 0.0, 30.0, 0.05);
        // Mark one response sample as fuel cut; entire owning event must fail.
        evidence.set(3, sample(0.30, 30.0, 0.0, false, false,
                0.0, 1.05, true));
        require(TransientEvidenceEvent.collect(
                TransientEvidenceEvent.Owner.INSTANT, evidence).size() == 1,
                "fuel-cut fixture did not create candidate event");
        require(TransientEvidenceEvent.acceptedForTask(
                TransientEvidenceEvent.Owner.INSTANT, evidence).isEmpty(),
                "fuel-cut event entered automatic Instant authority");
    }

    private static void wallAndTpsUseTheirOwnAnchors() {
        List<LiveSample> evidence = new ArrayList<LiveSample>();
        evidence.add(sample(0.0, Double.NaN, 0.0, true, true,
                15.0, 1.00, false));
        for (int i = 1; i <= 18; i++) {
            evidence.add(sample(i * 0.10, Double.NaN, 0.0,
                    i < 4, false, 0.0, i < 9 ? 1.04 : 1.00, false));
        }
        require(TransientEvidenceEvent.collect(
                TransientEvidenceEvent.Owner.TPS_AE, evidence).size() == 1,
                "TPS AE event did not use AE onset ownership");
        require(TransientEvidenceEvent.collect(
                TransientEvidenceEvent.Owner.WALL, evidence).size() == 1,
                "Wall event did not use Wall-active pedal-opening ownership");
    }

    private static void addInstantEvent(List<LiveSample> out, double t0,
                                        double counter, double earlyError) {
        out.add(sample(t0, counter, 0.8, false, false,
                20.0, 1.00, false));
        for (int i = 1; i <= 9; i++) {
            out.add(sample(t0 + i * 0.10, counter, 0.0,
                    false, false, 0.0, 1.00 + earlyError, false));
        }
        for (int i = 10; i <= 18; i++) {
            out.add(sample(t0 + i * 0.10, counter, 0.0,
                    false, false, 0.0, 1.00, false));
        }
    }

    private static LiveSample sample(double seconds, double counter,
                                     double instantPw, boolean wall,
                                     boolean tpsAe, double tpsDot,
                                     double lambda, boolean fuelCut) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 3000.0);
        values.put(ChannelRole.TPS, 35.0);
        values.put(ChannelRole.MAP, 95.0);
        values.put(ChannelRole.COOLANT, 80.0);
        values.put(ChannelRole.DELTA_TPS, tpsDot > 0.0 ? 20.0 : 0.0);
        values.put(ChannelRole.LAMBDA, lambda);
        values.put(ChannelRole.TARGET_LAMBDA, 1.00);
        values.put(ChannelRole.INSTANT_PULSE_PW, instantPw);
        if (Double.isFinite(counter)) values.put(ChannelRole.INSTANT_PULSE_CNT, counter);
        values.put(ChannelRole.WALL_CORRECTION, wall ? 0.08 : 0.0);
        values.put(ChannelRole.WALL_WETTING_PW, wall ? 0.05 : 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, tpsAe ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ADD_MS, tpsAe ? 0.5 : 0.0);
        values.put(ChannelRole.AE_EVENT_JUST_OCCURRED, tpsAe ? 1.0 : 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, fuelCut ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, tpsDot > 0.0 ? 0.20 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 0.10);
        return new LiveSample(Math.round(seconds * 1.0e9), seconds,
                values, tpsDot, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
