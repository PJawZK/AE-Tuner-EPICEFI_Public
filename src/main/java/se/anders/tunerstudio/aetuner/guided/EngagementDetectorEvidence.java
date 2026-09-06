package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/** Raw detector selection for evidence/review. No coaching/latching transforms. */
public final class EngagementDetectorEvidence {
    private EngagementDetectorEvidence() { }

    public static double rawSelectedOutput(AeProjectSnapshot snapshot, LiveSample sample) {
        if (sample == null) return Double.NaN;
        EngagementModelOption model = snapshot == null ? null
                : EngagementModelOption.fromControllerText(snapshot.getEngagementModel());
        ChannelRole role = selectedRole(model);
        double selected = role == null ? Double.NaN : sample.get(role);
        if (Double.isFinite(selected)) return selected;
        return sample.get(ChannelRole.DELTA_TPS);
    }

    static ChannelRole selectedRole(EngagementModelOption model) {
        if (model == null) return ChannelRole.DELTA_TPS;
        switch (model) {
            case MAX_STEP_LEGACY:
                return ChannelRole.AE_DELTA_MAX_STEP;
            case MAX_STEP_TIMED:
                return ChannelRole.AE_DELTA_TIMED;
            case WINDOW_SPAN:
                return ChannelRole.AE_DELTA_SPAN;
            case RISE_FROM_FLOOR:
                return ChannelRole.AE_DELTA_FLOOR;
            case DUAL_STRIDE_NEWEST:
                return ChannelRole.AE_DELTA_NEWEST_PAIR;
            default:
                return ChannelRole.DELTA_TPS;
        }
    }
}
