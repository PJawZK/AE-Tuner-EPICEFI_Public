package se.anders.tunerstudio.aetuner.guided.mapestimate;

/** Driver-selectable MAP Estimate evidence strategy. */
public enum MapEstimateCoverageStrategy {
    INTERPOLATED_COVERAGE("Interpolated Coverage"),
    DIRECT_FINE_TUNE("Direct Fine Tune");

    private final String displayName;
    MapEstimateCoverageStrategy(String displayName) { this.displayName = displayName; }
    @Override public String toString() { return displayName; }
}
