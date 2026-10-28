package se.anders.tunerstudio.aetuner.guided.method;

import java.io.File;

/** Observational analysis feedback; never evidence or proposal authority. */
public interface DecelHistoricalProgress {
    void update(File file, String stage, long rows);
    DecelHistoricalProgress NONE = (file, stage, rows) -> { };

    static void report(DecelHistoricalProgress listener, File file, String stage, long rows) {
        GuidedAnalysisCancellation.check();
        if (listener != null) listener.update(file, stage, rows);
    }
}
