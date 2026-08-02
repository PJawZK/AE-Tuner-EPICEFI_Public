package se.anders.tunerstudio.aetuner;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.swing.JFileChooser;

final class AdvisoryExportCoordinator {
    private AdvisoryExportCoordinator() {
    }

    static File chooseCsvTarget(Component parent) {
        return chooseTarget(parent, "ae-tuner-epicefi-events-" + AeTunerPlugin.VERSION + "-"
                + timestamp() + ".csv");
    }

    static File chooseReportTarget(Component parent) {
        return chooseTarget(parent, "ae-tuner-map-predict-report-" + AeTunerPlugin.VERSION + "-"
                + timestamp() + ".txt");
    }

    static void writeCsv(File file, List<EventSummary> events) throws IOException {
        EventCsvWriter.write(file, events);
    }

    static void writeReport(File file, String text) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    static String copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return null;
        } catch (IllegalStateException ex) {
            return ex.getMessage();
        }
    }

    static double elapsedMillis(long startedNano) {
        return Math.max(0L, System.nanoTime() - startedNano) / 1000000.0;
    }

    private static File chooseTarget(Component parent, String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        return chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile() : null;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    }
}
