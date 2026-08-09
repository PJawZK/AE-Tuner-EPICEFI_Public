package se.anders.tunerstudio.aetuner.recovery;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable evidence captured on the Swing thread and written off-thread. */
public final class EvidenceRecoverySnapshot {
    final Passive passive;
    final Guided guided;

    EvidenceRecoverySnapshot(Passive passive, Guided guided) {
        this.passive = passive;
        this.guided = guided;
    }

    boolean hasEvidence() {
        return passive != null || guided != null;
    }

    public static final class Passive {
        final String sessionKey;
        final long revision;
        final List<TransientEvent> events;
        final String reportText;

        public Passive(String sessionKey, long revision, List<TransientEvent> events,
                String reportText) {
            this.sessionKey = safeKey(sessionKey, "passive-session");
            this.revision = revision;
            this.events = Collections.unmodifiableList(
                    new ArrayList<TransientEvent>(events == null
                            ? Collections.<TransientEvent>emptyList() : events));
            this.reportText = reportText == null ? "" : reportText;
        }
    }

    public static final class Guided {
        final String sessionKey;
        final int recordCount;
        final String reportText;
        final String csvText;

        public Guided(String sessionKey, int recordCount, String reportText,
               String csvText) {
            this.sessionKey = safeKey(sessionKey, "guided-session");
            this.recordCount = Math.max(0, recordCount);
            this.reportText = reportText == null ? "" : reportText;
            this.csvText = csvText == null ? "" : csvText;
        }
    }

    public static String safeKey(String value, String fallback) {
        String source = value == null || value.trim().length() == 0
                ? fallback : value.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                out.append(c);
            } else {
                out.append('-');
            }
        }
        return out.length() == 0 ? fallback : out.toString();
    }
}
