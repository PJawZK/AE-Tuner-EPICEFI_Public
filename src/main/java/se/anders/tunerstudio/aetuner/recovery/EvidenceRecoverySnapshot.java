package se.anders.tunerstudio.aetuner.recovery;

/** Immutable Guided evidence captured from model state and written off-thread. */
public final class EvidenceRecoverySnapshot {
    final Guided guided;

    EvidenceRecoverySnapshot(Guided guided) {
        this.guided = guided;
    }

    boolean hasEvidence() { return guided != null; }

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
