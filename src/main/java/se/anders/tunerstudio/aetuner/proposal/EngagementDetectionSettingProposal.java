package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit operator-selected working-tune proposals for shared AE detector settings. */
public final class EngagementDetectionSettingProposal {
    private static final double EPSILON = 0.000001;

    private EngagementDetectionSettingProposal() {
    }

    /** Build a manual Delta Window plan, or null when unchanged. No ECU write occurs here. */
    public static ProposalWritePlan deltaWindow(AeProjectSnapshot snapshot,
                                                double requestedMs) {
        requireSnapshot(snapshot);
        ProposalWritePlan.Change change = deltaWindowChange(snapshot, requestedMs);
        if (change == null) return null;
        return new ProposalWritePlan(
                "engagement-detection-delta-window",
                "AE Foundation — Delta Window",
                snapshot.getConfigurationName(),
                "Explicit operator-selected detector setting. This plan uses the guarded working-tune write/readback/Restore path; the requested value is not an automatic tuning recommendation.",
                Collections.singletonList(change));
    }

    /** Build a manual Engagement Model enum plan, or null when unchanged. */
    public static ProposalWritePlan engagementModel(AeProjectSnapshot snapshot,
                                                    EngagementModelOption requested) {
        requireSnapshot(snapshot);
        ProposalWritePlan.Change change = engagementModelChange(snapshot, requested);
        if (change == null) return null;
        EngagementModelOption current = EngagementModelOption.fromControllerText(
                snapshot.getEngagementModel());
        return new ProposalWritePlan(
                "engagement-detection-model",
                "AE Foundation — Engagement Model",
                snapshot.getConfigurationName(),
                "Explicit operator-selected Engagement Model: "
                        + current.displayName() + " (" + current.controllerValue() + ") -> "
                        + requested.displayName() + " (" + requested.controllerValue() + "). "
                        + "This is a controller-representation/write qualification, not an automatic tuning recommendation.",
                Collections.singletonList(change));
    }

    /** Build a manual Sample Length scalar plan, or null when unchanged. */
    public static ProposalWritePlan sampleLength(AeProjectSnapshot snapshot,
                                                 double requestedSeconds) {
        requireSnapshot(snapshot);
        ProposalWritePlan.Change change = sampleLengthChange(snapshot, requestedSeconds);
        if (change == null) return null;
        return new ProposalWritePlan(
                "engagement-detection-sample-length",
                "AE Foundation — Sample Length",
                snapshot.getConfigurationName(),
                "Explicit operator-selected Sample Length. This is an ordinary scalar working-tune change using the already guarded scalar Apply/readback/Restore path; it is not an automatic tuning recommendation.",
                Collections.singletonList(change));
    }

    /** Build a manual Fast Callback bit-selection plan, or null when unchanged. */
    public static ProposalWritePlan fastCallback(AeProjectSnapshot snapshot,
                                                 boolean requestedEnabled) {
        requireSnapshot(snapshot);
        ProposalWritePlan.Change change = fastCallbackChange(snapshot, requestedEnabled);
        if (change == null) return null;
        return new ProposalWritePlan(
                "engagement-detection-fast-callback",
                "AE Foundation — Fast Callback",
                snapshot.getConfigurationName(),
                "Explicit operator-selected Fast Callback: "
                        + (snapshot.isEngagementFastCallback() ? "ON" : "OFF")
                        + " -> " + (requestedEnabled ? "ON" : "OFF")
                        + ". The proposal layer records logical 0/1 while the Apply coordinator uses TunerStudio's live bit-selection option contract; no containing-word manipulation is performed by AE Tuner.",
                Collections.singletonList(change));
    }

    /** Backward-compatible detector plan overload retained for existing callers/tests. */
    public static ProposalWritePlan detectorSettings(AeProjectSnapshot snapshot,
                                                     EngagementModelOption requestedModel,
                                                     double requestedDeltaWindowMs) {
        return detectorSettings(snapshot, requestedModel, requestedDeltaWindowMs,
                Double.NaN, null);
    }

    /** Build all deliberately selected Detector Model / Timing changes in one exact plan. */
    public static ProposalWritePlan detectorSettings(AeProjectSnapshot snapshot,
                                                     EngagementModelOption requestedModel,
                                                     double requestedDeltaWindowMs,
                                                     double requestedSampleLengthSeconds,
                                                     Boolean requestedFastCallback) {
        requireSnapshot(snapshot);
        List<ProposalWritePlan.Change> changes = new ArrayList<ProposalWritePlan.Change>();
        List<String> descriptions = new ArrayList<String>();

        if (requestedModel != null) {
            EngagementModelOption current = EngagementModelOption.fromControllerText(
                    snapshot.getEngagementModel());
            ProposalWritePlan.Change model = engagementModelChange(snapshot, requestedModel);
            if (model != null) {
                changes.add(model);
                descriptions.add("Engagement Model " + current.displayName()
                        + " (" + current.controllerValue() + ") -> "
                        + requestedModel.displayName() + " ("
                        + requestedModel.controllerValue() + ")");
            }
        }

        if (Double.isFinite(requestedDeltaWindowMs)) {
            ProposalWritePlan.Change delta = deltaWindowChange(snapshot, requestedDeltaWindowMs);
            if (delta != null) {
                changes.add(delta);
                descriptions.add("Delta Window " + format(delta.expectedValue)
                        + " -> " + format(delta.proposedValue) + " ms");
            }
        }

        if (Double.isFinite(requestedSampleLengthSeconds)) {
            ProposalWritePlan.Change sample = sampleLengthChange(
                    snapshot, requestedSampleLengthSeconds);
            if (sample != null) {
                changes.add(sample);
                descriptions.add("Sample Length " + format(sample.expectedValue)
                        + " -> " + format(sample.proposedValue) + " s");
            }
        }

        if (requestedFastCallback != null) {
            ProposalWritePlan.Change fast = fastCallbackChange(
                    snapshot, requestedFastCallback.booleanValue());
            if (fast != null) {
                changes.add(fast);
                descriptions.add("Fast Callback "
                        + (fast.expectedValue > 0.5 ? "ON" : "OFF")
                        + " -> " + (fast.proposedValue > 0.5 ? "ON" : "OFF"));
            }
        }

        if (changes.isEmpty()) return null;
        StringBuilder context = new StringBuilder(
                "Explicit operator-selected detector setting change(s): ");
        for (int i = 0; i < descriptions.size(); i++) {
            if (i > 0) context.append("; ");
            context.append(descriptions.get(i));
        }
        context.append(". Guarded working-tune Apply/readback/Restore only; no automatic recommendation and no burn.");
        return new ProposalWritePlan(
                "engagement-detection-settings",
                "AE Foundation — Detector Model / Timing",
                snapshot.getConfigurationName(),
                context.toString(),
                changes);
    }

    private static ProposalWritePlan.Change engagementModelChange(
            AeProjectSnapshot snapshot, EngagementModelOption requested) {
        if (requested == null) {
            throw new IllegalArgumentException("requested Engagement Model is required");
        }
        EngagementModelOption current = EngagementModelOption.fromControllerText(
                snapshot.getEngagementModel());
        if (current == null) {
            throw new IllegalArgumentException(
                    "working tune does not expose a recognized Engagement Model baseline: "
                            + snapshot.getEngagementModel());
        }
        if (current == requested) return null;
        return ProposalWritePlan.Change.scalar(
                AeParameterNames.TPS_AE_DETECT_MODE,
                current.controllerValue(), requested.controllerValue(),
                "Engagement Model", "");
    }

    private static ProposalWritePlan.Change deltaWindowChange(
            AeProjectSnapshot snapshot, double requestedMs) {
        if (!snapshot.hasEngagementDeltaWindow()) {
            throw new IllegalArgumentException(
                    "working tune does not expose a finite AE Delta Window baseline");
        }
        if (!Double.isFinite(requestedMs) || requestedMs <= 0.0) {
            throw new IllegalArgumentException(
                    "requested AE Delta Window must be a finite positive value");
        }
        double current = snapshot.getEngagementDeltaWindowMs();
        if (Math.abs(current - requestedMs) <= EPSILON) return null;
        return ProposalWritePlan.Change.scalar(
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                current, requestedMs, "Delta Window", "ms");
    }

    private static ProposalWritePlan.Change sampleLengthChange(
            AeProjectSnapshot snapshot, double requestedSeconds) {
        if (!snapshot.hasEngagementSampleLength()) {
            throw new IllegalArgumentException(
                    "working tune does not expose a finite AE Sample Length baseline");
        }
        if (!Double.isFinite(requestedSeconds) || requestedSeconds <= 0.0) {
            throw new IllegalArgumentException(
                    "requested AE Sample Length must be a finite positive value");
        }
        double current = snapshot.getEngagementSampleLengthSeconds();
        if (Math.abs(current - requestedSeconds) <= EPSILON) return null;
        return ProposalWritePlan.Change.scalar(
                AeParameterNames.TPS_ACCEL_LOOKBACK,
                current, requestedSeconds, "Sample Length", "s");
    }

    private static ProposalWritePlan.Change fastCallbackChange(
            AeProjectSnapshot snapshot, boolean requestedEnabled) {
        boolean current = snapshot.isEngagementFastCallback();
        if (current == requestedEnabled) return null;
        return ProposalWritePlan.Change.scalar(
                AeParameterNames.TPS_AE_FAST_CALLBACK,
                current ? 1.0 : 0.0, requestedEnabled ? 1.0 : 0.0,
                "Fast Callback", "");
    }

    private static void requireSnapshot(AeProjectSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("working-tune snapshot is required");
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
