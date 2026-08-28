package se.anders.tunerstudio.aetuner.guided.mapestimate;

/** Selects which accepted MAP evidence may influence the live surface/proposal. */
public enum MapEstimateEvidenceBasis {
    LEARNED_MEMORY("Learned memory + this capture"),
    CURRENT_CAPTURE_ONLY("Current capture only");

    private final String label;

    MapEstimateEvidenceBasis(String label) {
        this.label = label;
    }

    @Override public String toString() {
        return label;
    }
}
