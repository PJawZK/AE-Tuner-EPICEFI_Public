package se.anders.tunerstudio.aetuner.model;

/** Exact valid values of the current EpicEFI tpsAeDetectMode [0:2] enum. */
public enum EngagementModelOption {
    MAX_STEP_LEGACY(0, "Max step (legacy)"),
    MAX_STEP_TIMED(1, "Max step, timed"),
    WINDOW_SPAN(2, "Window span"),
    RISE_FROM_FLOOR(3, "Rise from floor"),
    DUAL_STRIDE_NEWEST(4, "Dual stride, newest");

    private final int controllerValue;
    private final String displayName;

    EngagementModelOption(int controllerValue, String displayName) {
        this.controllerValue = controllerValue;
        this.displayName = displayName;
    }

    public int controllerValue() { return controllerValue; }
    public String displayName() { return displayName; }

    public static EngagementModelOption fromControllerValue(int value) {
        for (EngagementModelOption option : values()) {
            if (option.controllerValue == value) return option;
        }
        return null;
    }

    /** Accept either the TunerStudio enum label or an integral numeric value. */
    public static EngagementModelOption fromControllerText(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1).trim();
        }
        for (EngagementModelOption option : values()) {
            if (option.displayName.equalsIgnoreCase(value)) return option;
        }
        try {
            double numeric = Double.parseDouble(value);
            int integer = (int) Math.rint(numeric);
            if (Math.abs(numeric - integer) <= 0.000001) {
                return fromControllerValue(integer);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    @Override public String toString() { return displayName; }
}
