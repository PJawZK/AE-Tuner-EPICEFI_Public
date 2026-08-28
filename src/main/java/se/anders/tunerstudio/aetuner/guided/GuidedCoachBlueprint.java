package se.anders.tunerstudio.aetuner.guided;

/**
 * Provisional interaction design for one Guided task.
 *
 * A blueprint is deliberately not tuning authority. It describes how a future
 * coach should obtain trustworthy evidence: what the driver does, what the UI
 * should make visually obvious, which audio transitions help while driving,
 * what is reviewed afterward, and how an A/B or coverage experiment would be
 * compared. Real recommendation math and ProposalWritePlan authority remain in
 * the task's validated implementation.
 */
public final class GuidedCoachBlueprint {
    public enum Archetype {
        DETECTOR("Detector / classifier"),
        COVERAGE("Coverage map"),
        TIMING("Controlled timing experiment"),
        RESPONSE_SHAPE("Response-shape experiment"),
        PAIRED("Paired / bidirectional experiment"),
        CONDITION_MAP("Condition mapping"),
        VALIDATION("Validation suite"),
        OWNERSHIP("Ownership / system review");

        public final String label;
        Archetype(String label) { this.label = label; }
    }

    public final GuidedTuningRecipe recipe;
    public final Archetype archetype;
    public final String question;
    public final String driverCue;
    public final String primaryVisual;
    public final String audio;
    public final String evidence;
    public final String review;
    public final String experiment;
    public final String futureConditions;

    GuidedCoachBlueprint(GuidedTuningRecipe recipe,
                         Archetype archetype,
                         String question,
                         String driverCue,
                         String primaryVisual,
                         String audio,
                         String evidence,
                         String review,
                         String experiment,
                         String futureConditions) {
        this.recipe = recipe;
        this.archetype = archetype;
        this.question = question;
        this.driverCue = driverCue;
        this.primaryVisual = primaryVisual;
        this.audio = audio;
        this.evidence = evidence;
        this.review = review;
        this.experiment = experiment;
        this.futureConditions = futureConditions;
    }
}
