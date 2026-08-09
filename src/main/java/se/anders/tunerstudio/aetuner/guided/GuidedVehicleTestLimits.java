package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.Locale;

/**
 * Session-only Guided Capture test limits.
 *
 * Pending values are controlled by the vehicle-test UI. A guided session takes
 * one immutable snapshot at start, so changing the pending controls can never
 * alter evidence already being collected. Nothing is persisted and no ECU
 * setting is read, written, burned, or pasted by this class.
 */
final class GuidedVehicleTestLimits {
    static final double DEFAULT_DETECTOR_CONFIRM_SECONDS = 0.55;
    static final double DEFAULT_TARGET_ACQUISITION_SECONDS = 1.00;
    static final double DEFAULT_MAP_CATCHUP_SECONDS = 1.20;
    static final double DEFAULT_TPS_TOLERANCE = 3.00;
    static final double DEFAULT_TPS_BOUNDARY_EPSILON = 0.05;
    static final double DEFAULT_LOCAL_TPS_ONSET_RISE = 2.00;

    private static Snapshot pending = defaults(false);
    private static Snapshot active;

    private GuidedVehicleTestLimits() { }

    static synchronized Snapshot defaults(boolean enabled) {
        return new Snapshot(enabled,
                DEFAULT_DETECTOR_CONFIRM_SECONDS,
                DEFAULT_TARGET_ACQUISITION_SECONDS,
                DEFAULT_MAP_CATCHUP_SECONDS,
                DEFAULT_TPS_TOLERANCE,
                DEFAULT_TPS_BOUNDARY_EPSILON,
                DEFAULT_LOCAL_TPS_ONSET_RISE);
    }

    static synchronized void configurePending(boolean enabled,
                                               double detectorConfirmSeconds,
                                               double targetAcquisitionSeconds,
                                               double mapCatchupSeconds,
                                               double tpsTolerance,
                                               double tpsBoundaryEpsilon,
                                               double localTpsOnsetRise) {
        pending = new Snapshot(enabled,
                detectorConfirmSeconds,
                targetAcquisitionSeconds,
                mapCatchupSeconds,
                tpsTolerance,
                tpsBoundaryEpsilon,
                localTpsOnsetRise);
    }

    static synchronized void restoreCandidateDefaults() {
        pending = defaults(false);
    }

    static synchronized Snapshot pending() {
        return pending;
    }

    static synchronized Snapshot beginSession() {
        active = pending.enabled ? pending : defaults(false);
        return active;
    }

    static synchronized void endSession() {
        active = null;
    }

    static synchronized Snapshot current() {
        if (active != null) {
            return active;
        }
        return pending.enabled ? pending : defaults(false);
    }

    static final class Snapshot {
        final boolean enabled;
        final double detectorConfirmSeconds;
        final double targetAcquisitionSeconds;
        final double mapCatchupSeconds;
        final double tpsTolerance;
        final double tpsBoundaryEpsilon;
        final double localTpsOnsetRise;

        Snapshot(boolean enabled,
                 double detectorConfirmSeconds,
                 double targetAcquisitionSeconds,
                 double mapCatchupSeconds,
                 double tpsTolerance,
                 double tpsBoundaryEpsilon,
                 double localTpsOnsetRise) {
            this.enabled = enabled;
            this.detectorConfirmSeconds = detectorConfirmSeconds;
            this.targetAcquisitionSeconds = targetAcquisitionSeconds;
            this.mapCatchupSeconds = mapCatchupSeconds;
            this.tpsTolerance = tpsTolerance;
            this.tpsBoundaryEpsilon = tpsBoundaryEpsilon;
            this.localTpsOnsetRise = localTpsOnsetRise;
        }

        String summary() {
            return (enabled ? "TEST OVERRIDES ACTIVE — " : "candidate defaults — ")
                    + "detector " + f2(detectorConfirmSeconds) + " s"
                    + " | target acquire " + f2(targetAcquisitionSeconds) + " s"
                    + " | MAP catch-up " + f2(mapCatchupSeconds) + " s"
                    + " | TPS ±" + f2(tpsTolerance) + "%"
                    + " +" + f2(tpsBoundaryEpsilon) + " epsilon"
                    + " | local onset +" + f2(localTpsOnsetRise) + " TPS";
        }

        boolean hasNonDefaultValues() {
            return different(detectorConfirmSeconds,
                            DEFAULT_DETECTOR_CONFIRM_SECONDS)
                    || different(targetAcquisitionSeconds,
                            DEFAULT_TARGET_ACQUISITION_SECONDS)
                    || different(mapCatchupSeconds,
                            DEFAULT_MAP_CATCHUP_SECONDS)
                    || different(tpsTolerance, DEFAULT_TPS_TOLERANCE)
                    || different(tpsBoundaryEpsilon,
                            DEFAULT_TPS_BOUNDARY_EPSILON)
                    || different(localTpsOnsetRise,
                            DEFAULT_LOCAL_TPS_ONSET_RISE);
        }

        private static boolean different(double left, double right) {
            return Math.abs(left - right) > 0.000001;
        }

        private static String f2(double value) {
            return String.format(Locale.US, "%.2f", value);
        }
    }
}
