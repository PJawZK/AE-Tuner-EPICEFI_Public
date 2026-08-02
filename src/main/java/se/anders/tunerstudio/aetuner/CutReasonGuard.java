package se.anders.tunerstudio.aetuner;

/**
 * Prevents an asynchronously delivered cut-reason code from becoming critical
 * until it remains present through a short, coherent RUNNING interval.
 */
final class CutReasonGuard {
    private static final long MIN_COHERENT_NANOS = 50_000_000L;
    private static final int MIN_COHERENT_SAMPLES = 3;

    private long firstRunningNano = Long.MIN_VALUE;
    private int coherentRunningSamples;

    void reset() {
        firstRunningNano = Long.MIN_VALUE;
        coherentRunningSamples = 0;
    }

    boolean observe(boolean activeReason, boolean coherentRunning, long nanoTime) {
        if (!activeReason || !coherentRunning) {
            reset();
            return false;
        }
        if (firstRunningNano == Long.MIN_VALUE) {
            firstRunningNano = nanoTime;
            coherentRunningSamples = 1;
            return false;
        }
        coherentRunningSamples++;
        long elapsed = nanoTime >= firstRunningNano ? nanoTime - firstRunningNano : 0L;
        return coherentRunningSamples >= MIN_COHERENT_SAMPLES
                && elapsed >= MIN_COHERENT_NANOS;
    }
}
