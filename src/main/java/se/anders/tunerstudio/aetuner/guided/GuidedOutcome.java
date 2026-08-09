package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/**
 * One completed adaptive Guided Capture outcome.
 *
 * A valid road event is retained even when it belongs to a different
 * comparability group. Proposal code decides which group is sufficiently
 * populated and consistent; capture validity is not conflated with grouping.
 */
final class GuidedOutcome {
    enum Decision {
        VALID,
        VALID_WITH_WARNING,
        EXCLUDED,
        RETURN_TO_BASELINE
    }

    final Decision decision;
    final double sampleSeconds;
    final double durationSeconds;
    final int validCount;
    final String groupId;
    final int groupCount;
    final String details;
    final String trace;

    GuidedOutcome(Decision decision,
                  double sampleSeconds,
                  double durationSeconds,
                  int validCount,
                  String groupId,
                  int groupCount,
                  String details,
                  String trace) {
        this.decision = decision;
        this.sampleSeconds = sampleSeconds;
        this.durationSeconds = durationSeconds;
        this.validCount = validCount;
        this.groupId = groupId == null ? "" : groupId;
        this.groupCount = groupCount;
        this.details = details == null ? "" : details;
        this.trace = trace == null ? "" : trace;
    }

    boolean isValid() {
        return decision == Decision.VALID
                || decision == Decision.VALID_WITH_WARNING;
    }

    String decisionText() {
        switch (decision) {
            case VALID_WITH_WARNING:
                return "VALID_WITH_WARNING";
            case EXCLUDED:
                return "EXCLUDED";
            case RETURN_TO_BASELINE:
                return "RETURN_TO_BASELINE";
            default:
                return "VALID";
        }
    }
}
