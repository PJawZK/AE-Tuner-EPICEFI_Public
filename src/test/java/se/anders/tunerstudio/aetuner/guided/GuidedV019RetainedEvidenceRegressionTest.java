package se.anders.tunerstudio.aetuner.guided;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Prevents the presentation bridge from duplicating serialized evidence payloads. */
public final class GuidedV019RetainedEvidenceRegressionTest {
    private GuidedV019RetainedEvidenceRegressionTest() { }

    public static void main(String[] args) throws Exception {
        completedProbeRetentionIsMetadataOnly();
        System.out.println("GuidedV019RetainedEvidenceRegressionTest passed");
    }

    private static void completedProbeRetentionIsMetadataOnly() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019ProductionBridge.java")),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private void retainCompletedProbe(");
        int end = source.indexOf("private GuidedMethodProbeSession probeSession()", start);
        require(start >= 0 && end > start,
                "could not isolate v0.19 completed-probe retention path");
        String method = source.substring(start, end);

        require(method.contains("retainedProbeEvidence.put")
                        && method.contains("probe.sampleCount()"),
                "completed probe marker lost its recipe/sample-count metadata");
        require(!method.contains("reportText(")
                        && !method.contains("csvText(")
                        && !method.contains("copyPasteBlock("),
                "v0.19 bridge again eagerly serializes duplicate completed evidence");
        require(!source.contains("class RetainedProbeEvidence"),
                "v0.19 bridge again owns a duplicate serialized evidence payload object");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
