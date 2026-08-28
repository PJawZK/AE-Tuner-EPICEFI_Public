package se.anders.tunerstudio.aetuner.guided.mapestimate;

/** Output-only MAP Estimate proposal limit. Evidence authority is unchanged. */
public enum MapEstimateProposalLimitPolicy {
    HIGH_TPS_CAP("High-TPS cap"),
    UNRESTRICTED_ELIGIBLE_MAP("Unrestricted eligible MAP");

    private final String label;

    MapEstimateProposalLimitPolicy(String label) {
        this.label = label;
    }

    @Override public String toString() {
        return label;
    }
}
