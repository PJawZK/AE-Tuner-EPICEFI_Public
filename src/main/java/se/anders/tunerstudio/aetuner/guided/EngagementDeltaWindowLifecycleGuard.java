package se.anders.tunerstudio.aetuner.guided;

/**
 * Public host-shell guard for lifecycle exits that must not outlive a temporary
 * controlled Delta Window candidate. The package-private runtime retains write
 * ownership; this class exposes only the verified finish/restore boundary.
 */
public final class EngagementDeltaWindowLifecycleGuard {
    private EngagementDeltaWindowLifecycleGuard() { }

    public static boolean finishBeforeExternalLifecycleEnd() {
        return EngagementDeltaWindowSweepRuntime.finishForLifecycle();
    }

    public static String statusText() {
        return EngagementDeltaWindowSweepRuntime.snapshot().status;
    }
}
