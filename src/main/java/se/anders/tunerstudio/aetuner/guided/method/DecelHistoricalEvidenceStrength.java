package se.anders.tunerstudio.aetuner.guided.method;

/**
 * Read-only evidence-strength label for historical Decel results.
 *
 * This deliberately uses only independent classified physical-event counts.
 * It does not alter threshold math, proposal eligibility, Apply authority or
 * cross-session pooling.  The labels exist so the operator can distinguish a
 * result backed by broad repeated evidence from one that only clears the
 * minimum 3+3 separation requirement.
 */
public final class DecelHistoricalEvidenceStrength {
    public enum Level {
        STRONG,
        GOOD,
        MINIMUM,
        NONE
    }

    public static final class Assessment {
        public final Level level;
        public final String reason;

        Assessment(Level level, String reason) {
            this.level = level == null ? Level.NONE : level;
            this.reason = reason == null ? "" : reason;
        }
    }

    private DecelHistoricalEvidenceStrength() { }

    public static Assessment assess(DecelHistoricalThresholdDecision.BinDecision decision) {
        if (decision == null
                || decision.outcome == DecelHistoricalThresholdDecision.Outcome.WITHHOLD) {
            return new Assessment(Level.NONE, "no qualified threshold result");
        }

        int normal = decision.normalEvents;
        int release = decision.releaseEvents;
        if (normal >= 10 && release >= 10) {
            return new Assessment(Level.STRONG,
                    normal + " independent Normal + " + release
                            + " independent Release events (10+ in each class)");
        }
        if (normal >= 5 && release >= 5) {
            return new Assessment(Level.GOOD,
                    normal + " independent Normal + " + release
                            + " independent Release events (5+ in each class)");
        }
        if (normal >= 3 && release >= 3) {
            return new Assessment(Level.MINIMUM,
                    normal + " independent Normal + " + release
                            + " independent Release events (minimum 3+3 backing)");
        }
        return new Assessment(Level.NONE,
                normal + " independent Normal + " + release
                        + " independent Release events; below 3+3 result backing");
    }

    public static String display(Level level) {
        if (level == null) return "NONE";
        switch (level) {
            case STRONG: return "STRONG";
            case GOOD: return "GOOD";
            case MINIMUM: return "MINIMUM";
            default: return "NONE";
        }
    }
}
