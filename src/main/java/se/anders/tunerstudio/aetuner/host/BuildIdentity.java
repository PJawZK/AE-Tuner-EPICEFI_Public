package se.anders.tunerstudio.aetuner.host;

/**
 * Single small source authority for the visible private development build identity.
 * Private vehicle-test candidates receive distinct visible versions when
 * distributed for testing. Decel retains classification-boundary-v2 and
 * explicitly selected detector-compatible cross-log pooling. Full tune
 * CRC, historical threshold curve/values and Hold remain audit context rather
 * than pooling blockers because they do not define physical event classification.
 * Historical pooling and eligibility remain read-only until explicit Review. The
 * existing ProposalWritePlan -> Apply/readback/Restore path remains the only
 * mutation authority. Zero cells are never auto-enabled and no Burn authority exists.
 */
public final class BuildIdentity {
    public static final String VERSION = "0.4.6";
    public static final String DECEL_DECISION_ALGORITHM = "classification-boundary-v2";
    public static final String DECEL_POOL_POLICY = "detector-compatible-pool-v1";

    private BuildIdentity() { }
}
