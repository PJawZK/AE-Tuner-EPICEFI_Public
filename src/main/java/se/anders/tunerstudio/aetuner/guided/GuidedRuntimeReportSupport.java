package se.anders.tunerstudio.aetuner.guided;

/**
 * Persistence-boundary decorator for bounded runtime diagnostics.
 *
 * Runtime performance is deliberately appended after evidence/report builders
 * have completed. It is diagnostic provenance only and cannot affect capture,
 * recommendation, ProposalWritePlan, Apply/Restore, or Burn behavior.
 */
public final class GuidedRuntimeReportSupport {
    public static final String HEADING = "RUNTIME PERFORMANCE DIAGNOSTICS";

    private GuidedRuntimeReportSupport() { }

    public static String append(String report) {
        String base = report == null ? "" : report;
        if (base.contains(HEADING)) return base;
        StringBuilder out = new StringBuilder(base.length() + 2048);
        out.append(base);
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
        out.append('\n').append(HEADING).append('\n')
                .append("===============================\n")
                .append(RuntimePerformanceHub.reportText()).append('\n');
        return out.toString();
    }

    public static boolean isFinalGuidedReportName(String name) {
        return "guided-report.txt".equals(name)
                || "guided-method-report.txt".equals(name);
    }
}
