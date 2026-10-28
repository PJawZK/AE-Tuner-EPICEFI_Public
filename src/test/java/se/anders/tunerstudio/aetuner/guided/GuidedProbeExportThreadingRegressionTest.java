package se.anders.tunerstudio.aetuner.guided;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Architecture regression: probe report/CSV serialization belongs in SwingWorker background work. */
public final class GuidedProbeExportThreadingRegressionTest {
    private GuidedProbeExportThreadingRegressionTest() { }

    public static void main(String[] args) throws Exception {
        probeSerializationIsInsideBackgroundWorker();
        System.out.println("GuidedProbeExportThreadingRegressionTest passed");
    }

    private static void probeSerializationIsInsideBackgroundWorker() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedCapturePanel.java")),
                StandardCharsets.UTF_8);
        int methodStart = source.indexOf("private void exportProbeSession()");
        int methodEnd = source.indexOf("private void copyReviewedDraft()", methodStart);
        require(methodStart >= 0 && methodEnd > methodStart,
                "could not isolate GuidedCapturePanel.exportProbeSession");
        String method = source.substring(methodStart, methodEnd);

        int worker = method.indexOf("doInBackground() throws Exception");
        int capture = method.indexOf("probeSession.exportSnapshot(AeTunerPlugin.VERSION, request)");
        int csv = method.indexOf("exported.csvText()");
        require(worker >= 0 && capture > worker && csv > capture,
                "probe package capture/CSV serialization must run inside SwingWorker background execution");

        String edtPrefix = method.substring(0, worker);
        require(!edtPrefix.contains("probeSession.exportSnapshot(")
                        && !edtPrefix.contains("probeSession.reportText(")
                        && !edtPrefix.contains("probeSession.csvText()")
                        && !edtPrefix.contains("probeSession.copyPasteBlock()"),
                "heavy probe export serialization leaked back onto the EDT path");
        require(method.contains("SessionExportSupport.writeTextAtomic")
                        && method.contains("guided-method-samples.csv"),
                "probe export no longer writes the expected staged evidence files");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
