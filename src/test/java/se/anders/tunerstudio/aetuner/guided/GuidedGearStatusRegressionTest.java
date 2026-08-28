package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.EnumMap;

/** Regression coverage for the user-visible one-way Guided session gear latch. */
public final class GuidedGearStatusRegressionTest {
    private GuidedGearStatusRegressionTest() { }

    public static void main(String[] args) {
        automaticStatusShowsAcquisitionThenPersistentLatch();
        manualAndIgnoreStatusesRemainAuthoritative();
        System.out.println("GuidedGearStatusRegressionTest passed");
    }

    private static void automaticStatusShowsAcquisitionThenPersistentLatch() {
        BlendDurationGuidedSession session = new BlendDurationGuidedSession();
        session.start(new BlendDurationCaptureConfig(2600.0, 20.0, 5, 0, true));

        for (int i = 0; i < 22; i++) {
            double t = i * 0.05;
            session.accept(sample(t, 2600.0, 50.0, 8.0,
                    5.0, 55.0, false));
        }

        GuidedSessionSnapshot ready = session.snapshot();
        require(ready.state == GuidedCaptureState.READY,
                "stable automatic-gear setup did not reach READY; state=" + ready.state);
        String readyStatus = session.gearStatusForDisplay();
        requireContains(readyStatus, "Gear: automatic | ACQUIRING",
                "READY status does not expose automatic gear acquisition state");
        requireContains(readyStatus, "candidate 5",
                "READY status does not expose the dominant gear candidate");
        requireContains(readyStatus, "100%",
                "READY status does not expose candidate confidence");
        requireContains(ready.checks, readyStatus,
                "user-visible Guided checks do not contain the live gear status");

        session.accept(sample(1.10, 2600.0, 66.0, 28.0,
                5.0, 55.0, true));
        GuidedSessionSnapshot latched = session.snapshot();
        String latchedStatus = session.gearStatusForDisplay();
        requireContains(latchedStatus, "SESSION LATCHED",
                "first controlled opening did not expose the session latch transition");
        requireContains(latchedStatus, "session gear 5 latched",
                "automatic session did not visibly latch 5th gear");
        requireContains(latched.checks, latchedStatus,
                "latched gear status is not visible in Guided checks");

        session.accept(sample(1.15, 2600.0, 67.0, 28.0,
                0.0, 579.0, true));
        session.accept(sample(1.20, 2600.0, 68.0, 28.0,
                2.0, 55.0, true));
        String afterNoise = session.gearStatusForDisplay();
        requireContains(afterNoise, "session gear 5 latched",
                "VSS spike/contradictory later gear replaced the session latch");
        require(!afterNoise.contains("session gear 2 latched"),
                "later contradictory gear was allowed to replace the one-way latch; status=" + afterNoise);

        session.reset();
        GuidedSessionSnapshot reset = session.snapshot();
        requireContains(reset.checks, "Gear: no active Guided session",
                "reset did not clear the visible session gear latch state");
        require(!reset.checks.contains("session gear 5 latched"),
                "reset retained stale automatic gear status; checks=" + reset.checks);
    }

    private static void manualAndIgnoreStatusesRemainAuthoritative() {
        BlendDurationGuidedSession session = new BlendDurationGuidedSession();
        session.start(new BlendDurationCaptureConfig(2600.0, 20.0, 5, 3, false));
        requireContains(session.snapshot().checks,
                "Gear: manual 3 — operator authoritative",
                "manual gear status must show operator authority");
        session.reset();

        session.start(new BlendDurationCaptureConfig(2600.0, 20.0, 5, 0, false));
        requireContains(session.snapshot().checks,
                "Gear: ignored for this Guided session",
                "Ignore mode unexpectedly depends on detected gear");
        session.reset();
    }

    private static LiveSample sample(double seconds, double rpm,
                                     double map, double tps,
                                     double gear, double vss,
                                     boolean detector) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, detector ? map + 15.0 : map);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, detector ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, detector ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        values.put(ChannelRole.GEAR, gear);
        values.put(ChannelRole.VSS, vss);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values,
                detector ? 60.0 : 0.0, 0.0);
    }

    private static void requireContains(String actual, String expected, String message) {
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError(message + ": expected `" + expected
                    + "` in `" + actual + "`");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
