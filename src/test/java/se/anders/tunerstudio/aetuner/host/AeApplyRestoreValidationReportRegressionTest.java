package se.anders.tunerstudio.aetuner.host;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AeApplyRestoreValidationReportRegressionTest {
    private AeApplyRestoreValidationReportRegressionTest() { }

    public static void main(String[] args) throws Exception {
        csvContainsExactlyOneRowPerPhysicalTarget();
        reportCarriesExactMetadataRoundTripAndEscapedNotes();
        summaryCountsPhysicalResults();
        System.out.println("AeApplyRestoreValidationReportRegressionTest passed");
    }

    private static void csvContainsExactlyOneRowPerPhysicalTarget() {
        Fixture fixture = new Fixture();
        String csv = AeApplyRestoreValidationReport.csv(fixture.model);
        int rows = countNewlines(csv);
        require(rows == 817,
                "validation CSV must contain one header plus 816 physical target rows; got " + rows);
        require(countOccurrences(csv, "mapEstimateTable,") == 256,
                "MAP Estimate table must export 256 physical cell rows");
    }

    private static void reportCarriesExactMetadataRoundTripAndEscapedNotes()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);
        fixture.model.select(target);
        fixture.model.setTemporaryValue(60.0);
        require(fixture.model.applyTemporary().success, "setup validation Apply failed");
        require(fixture.model.restoreOriginal().success, "setup validation Restore failed");
        fixture.model.markSelected(
                AeApplyRestoreValidationLabModel.ValidationStatus.PASS,
                "comma, quote \" and newline\nkept");

        String csv = AeApplyRestoreValidationReport.csv(fixture.model);
        require(csv.contains(AeControllerDefinitionCatalog.AUTHORITY_SIGNATURE),
                "validation CSV omitted frozen controller authority signature");
        require(csv.contains("tpsAeDeltaWindowMs"),
                "validation CSV omitted exact controller constant");
        require(csv.contains(",SCALAR,SCALAR,U08,ms,1,5,250,0,,50,60,60,50,PASS,"),
                "validation CSV omitted exact scalar representation/round-trip values");
        require(csv.contains("\"comma, quote \"\" and newline\nkept\""),
                "validation CSV did not RFC-style escape note punctuation/newline");
    }

    private static void summaryCountsPhysicalResults() throws Exception {
        Fixture fixture = new Fixture();
        fixture.io.scalars.put(key("cfg", AeParameterNames.TPS_AE_DELTA_WINDOW_MS),
                Double.valueOf(50.0));
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);
        fixture.model.select(target);
        fixture.model.markSelected(
                AeApplyRestoreValidationLabModel.ValidationStatus.SKIP,
                "bench prerequisite missing");
        String summary = AeApplyRestoreValidationReport.summary(fixture.model);
        require(summary.contains("Physical target count: 816"),
                "validation summary omitted physical target count");
        require(summary.contains("SKIP: 1"),
                "validation summary omitted result count");
        require(summary.contains("NOT TESTED: 815"),
                "validation summary did not count untouched physical targets");
        require(summary.contains("no burn"),
                "validation summary omitted working-tune/no-burn safety boundary");
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }

    private static final class Fixture {
        final FakeIo io = new FakeIo();
        final ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(io);
        final AeApplyRestoreValidationEngine engine =
                new AeApplyRestoreValidationEngine(io, coordinator, "cfg");
        final AeApplyRestoreValidationLabModel model =
                new AeApplyRestoreValidationLabModel(engine);
    }

    private static final class FakeIo
            implements ProposalApplyCoordinator.Backend,
                       AeApplyRestoreValidationEngine.ValueReader {
        final Map<String, Double> scalars = new LinkedHashMap<String, Double>();
        final Map<String, double[][]> arrays = new LinkedHashMap<String, double[][]>();

        @Override
        public double read(String configurationName,
                           AeApplyRestoreValidationTargets.Target target) {
            if (target.isIndexed()) {
                return getFlat(requireArray(configurationName, target.getControllerName()),
                        target.getFlatIndex());
            }
            return requireScalar(configurationName, target.getControllerName());
        }

        @Override
        public double readScalar(String configurationName, String parameterName) {
            return requireScalar(configurationName, parameterName);
        }

        @Override
        public double[][] readArray(String configurationName, String parameterName) {
            return cloneTable(requireArray(configurationName, parameterName));
        }

        @Override
        public void writeScalar(String configurationName, String parameterName, double value) {
            scalars.put(key(configurationName, parameterName), Double.valueOf(value));
        }

        @Override
        public void writeArray(String configurationName, String parameterName,
                               double[][] values) {
            arrays.put(key(configurationName, parameterName), cloneTable(values));
        }

        private double requireScalar(String configurationName, String parameterName) {
            Double value = scalars.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing scalar " + parameterName);
            return value.doubleValue();
        }

        private double[][] requireArray(String configurationName, String parameterName) {
            double[][] value = arrays.get(key(configurationName, parameterName));
            if (value == null) throw new IllegalStateException("missing array " + parameterName);
            return value;
        }
    }

    private static String key(String configurationName, String parameterName) {
        return configurationName + "::" + parameterName;
    }

    private static double getFlat(double[][] values, int targetIndex) {
        int index = 0;
        for (double[] row : values) {
            if (row == null) continue;
            for (double value : row) {
                if (index == targetIndex) return value;
                index++;
            }
        }
        throw new IllegalArgumentException("flat index outside fake array: " + targetIndex);
    }

    private static double[][] cloneTable(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i] == null ? new double[0] : values[i].clone();
        }
        return copy;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
