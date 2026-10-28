package se.anders.tunerstudio.aetuner.guided.method;

public final class MlgEmbeddedTuneMetadataRegressionTest {
    private MlgEmbeddedTuneMetadataRegressionTest() { }

    public static void main(String[] args) {
        parsesCapturedDecelCalibrationWithoutInventingMissingValues();
        supportsValueAttributeConstants();
        incompleteHistoricalCurveFailsClosed();
        System.out.println("MlgEmbeddedTuneMetadataRegressionTest passed");
    }

    private static void parsesCapturedDecelCalibrationWithoutInventingMissingValues() {
        String msq = "<?xml version=\"1.0\"?>\n"
                + "<msq firmwareSignature=\"rusEFI master jz\">\n"
                + "<constant name=\"tuneCrcPcVariable\">123456</constant>\n"
                + "<constant name=\"tpsAeDetectMode\">Dual stride, newest</constant>\n"
                + "<constant name=\"tpsAeDeltaWindowMs\">40</constant>\n"
                + "<constant name=\"tpsAccelLookback\">0.06</constant>\n"
                + "<constant name=\"tpsAeFastCallback\">true</constant>\n"
                + "<constant name=\"tpsDecelThresholdRpmBins\">800 1600 2400 3200</constant>\n"
                + "<constant name=\"tpsDecelThresholdValue\">0.570 0.597 0.831 0.867</constant>\n"
                + "<constant name=\"tpsDecelHoldCycles\">4</constant>\n"
                + "</msq>";
        MlgEmbeddedTuneMetadata metadata = MlgEmbeddedTuneMetadata.parse(msq);
        require(metadata.available, "embedded tune was not recognized");
        require("rusEFI master jz".equals(metadata.firmwareSignature),
                "firmware signature was not preserved");
        require("Dual stride, newest".equals(metadata.detectorModeRaw),
                "detector mode text was not preserved");
        require(Math.abs(metadata.deltaWindowMs - 40.0) < 0.000001,
                "Delta Window was not parsed");
        require(Math.abs(metadata.sampleLengthSeconds - 0.06) < 0.000001,
                "TPS lookback was not parsed");
        require(Boolean.TRUE.equals(metadata.fastCallback),
                "Fast Callback was not parsed");
        require(metadata.hasDecelThresholdCurve()
                        && metadata.decelThresholdValues.length == 4,
                "Decel threshold curve was not parsed");
        require(Math.abs(metadata.decelThresholdForRpm(2000.0) - 0.714) < 0.000001,
                "historical threshold interpolation was incorrect");
        require(Math.abs(metadata.decelHoldCycles - 4.0) < 0.000001,
                "historical Hold was not parsed");
        require(metadata.fingerprint.length() == 64,
                "calibration fingerprint was not generated");
    }

    private static void supportsValueAttributeConstants() {
        String msq = "<msq>"
                + "<constant name=\"tpsAeDetectMode\" value=\"3\"/>"
                + "<constant name=\"tpsDecelHoldCycles\" value=\"7\"/>"
                + "</msq>";
        MlgEmbeddedTuneMetadata metadata = MlgEmbeddedTuneMetadata.parse(msq);
        require("3".equals(metadata.detectorModeRaw),
                "numeric/raw detector enum was not retained without guessing its display meaning");
        require(Math.abs(metadata.decelHoldCycles - 7.0) < 0.000001,
                "value-attribute Hold was not parsed");
    }

    private static void incompleteHistoricalCurveFailsClosed() {
        String msq = "<msq>"
                + "<constant name=\"tpsDecelThresholdRpmBins\">1000 2000 3000</constant>"
                + "<constant name=\"tpsDecelThresholdValue\">0.5 0.7</constant>"
                + "</msq>";
        MlgEmbeddedTuneMetadata metadata = MlgEmbeddedTuneMetadata.parse(msq);
        require(!metadata.hasDecelThresholdCurve(),
                "mismatched historical RPM/value arrays were treated as authoritative");
        require(!Double.isFinite(metadata.decelThresholdForRpm(2000.0)),
                "invalid historical curve manufactured an interpolated threshold");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
