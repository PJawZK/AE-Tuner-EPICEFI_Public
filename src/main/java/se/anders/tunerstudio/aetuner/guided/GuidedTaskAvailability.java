package se.anders.tunerstudio.aetuner.guided;

/** Immutable presentation-only availability state for one v0.19 sidebar task. */
public final class GuidedTaskAvailability {
    public final boolean clickable;
    public final String status;
    public final String reason;

    GuidedTaskAvailability(boolean clickable, String status, String reason) {
        this.clickable = clickable;
        this.status = status == null ? "" : status;
        this.reason = reason == null ? "" : reason;
    }
}
