package se.anders.tunerstudio.aetuner.guided;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Prevents the presentation bridge from duplicating serialized evidence payloads. */
public final class GuidedV019RetainedEvidenceRegressionTest {
    private GuidedV019RetainedEvidenceRegressionTest() { }

    public static void main(String[] args) throws Exception {
        completedProbeRetentionIsMetadataOnly();
        blendResultPresentationIsCompactAndLegible();
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

    private static void blendResultPresentationIsCompactAndLegible() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019ApplyResultViews.java")),
                StandardCharsets.UTF_8);
        require(source.contains("evidenceColumn.setPreferredSize(new Dimension(390,0))"),
                "Blend Result evidence column lost its compact in-car width");
        require(source.contains("compactKv(new String[][]"),
                "Blend Result evidence rows regressed to vertically stretched grid layout");
        require(source.contains("resultNote(evidenceReady?\"Evidence retained\""),
                "Blend Result lost its larger retained-evidence presentation");
        require(source.contains("wrapArea(text,13,AeUiTheme.text())"),
                "Blend Result retained-evidence body text is no longer in-car legible");
        require(source.contains("bigText(evidenceReady?\"MEASUREMENT ACCEPTED — PROPOSAL WITHHELD\""),
                "Blend Result outcome headline lost the left-anchored wrapping presentation");
        require(source.contains("if(evidenceReady&&!bridge.applyEnabled())"),
                "Why no Apply must only exist while review-ready Blend evidence still has no Apply authority");
        require(!source.contains("new JPanel(new GridLayout(1,2,8,0))"),
                "Blend Result regressed to the equal-width evidence/outcome layout");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
