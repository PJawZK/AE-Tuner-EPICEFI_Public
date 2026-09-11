package se.anders.tunerstudio.aetuner.guided;

/**
 * Host-owned secondary tools exposed from the structurally ported v0.19 UI.
 * These actions never participate in tuning/proposal/write authority.
 */
public interface GuidedV019UtilityActions {
    void openPassiveAnalysis();
    void openEvidenceDiagnostics();

    GuidedV019UtilityActions NONE = new GuidedV019UtilityActions() {
        @Override public void openPassiveAnalysis() { }
        @Override public void openEvidenceDiagnostics() { }
    };
}
