package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

/**
 * Tiny presentation-only handoff for historical replay surfaces.
 *
 * The production bridge publishes the exact Working Tune snapshot it already
 * owns. Historical analyzers may read it, but this context has no writer,
 * ProposalWritePlan or controller authority.
 */
final class GuidedV019HistoricalReplayContext {
    private static volatile AeProjectSnapshot workingTune;

    private GuidedV019HistoricalReplayContext() { }

    static void publish(AeProjectSnapshot snapshot) {
        workingTune = snapshot;
    }

    static AeProjectSnapshot workingTune() {
        return workingTune;
    }

    static void clear() {
        workingTune = null;
    }
}
