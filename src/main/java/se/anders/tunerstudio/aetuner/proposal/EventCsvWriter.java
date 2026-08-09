package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/** Serializes captured event evidence without owning any Swing state. */
public final class EventCsvWriter {
    private EventCsvWriter() {
    }

    public static void write(File file, List<TransientEvent> events) throws IOException {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("At least one captured event is required");
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        try {
            writer.write(events.get(0).toCsvHeader());
            writer.newLine();
            for (TransientEvent summary : events) {
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
