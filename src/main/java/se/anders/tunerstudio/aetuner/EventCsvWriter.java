package se.anders.tunerstudio.aetuner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/** Serializes captured event evidence without owning any Swing state. */
final class EventCsvWriter {
    private EventCsvWriter() {
    }

    static void write(File file, List<EventSummary> events) throws IOException {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("At least one captured event is required");
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        try {
            writer.write(events.get(0).toCsvHeader());
            writer.newLine();
            for (EventSummary summary : events) {
                for (String row : summary.toCsvRows()) {
                    writer.write(row);
                    writer.newLine();
                }
            }
        } finally {
            writer.close();
        }
    }
}
