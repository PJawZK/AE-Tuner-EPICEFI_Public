package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.BuildIdentity;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DecelHistoricalPoolRegressionTest {
    private static final double[] RPM =
            new double[]{800, 1600, 2400, 3200, 4100, 4900, 5700, 6500};

    private DecelHistoricalPoolRegressionTest() { }

    public static void main(String[] args) throws Exception {
        differentThresholdTuneStateDoesNotBlockCompatiblePooling();
        detectorOrFirmwareMismatchFailsClosed();
        independentEventsPoolBeforeClassificationBoundaryDecision();
        duplicateFileSelectionIsRejected();
        require(BuildIdentity.DECEL_POOL_POLICY.equals(DecelHistoricalPoolCompatibility.POLICY_ID),
                "pool policy identity drifted away from BuildIdentity");
        System.out.println("DecelHistoricalPoolRegressionTest passed");
    }

    private static void differentThresholdTuneStateDoesNotBlockCompatiblePooling() {
        AeProjectSnapshot tune = snapshot(0.40, "Dual stride, newest", 40.0, 0.06);
        MlgEmbeddedTuneMetadata a = metadata("rusEFI-test-fw", "crc-a", 4.0,
                new double[]{0.57,0.60,0.83,0.87,0.90,0.94,0.97,1.01});
        MlgEmbeddedTuneMetadata b = metadata("rusEFI-test-fw", "crc-b", 9.0,
                new double[]{0.20,0.30,0.25,0.20,0.70,0.74,0.77,0.81});

        require(!a.fingerprint.equals(b.fingerprint),
                "fixture full-tune fingerprints unexpectedly match");
        DecelHistoricalPoolCompatibility.Result compatibility =
                DecelHistoricalPoolCompatibility.evaluate(tune, Arrays.asList(a, b));
        require(compatibility.compatible,
                "threshold/Hold/tune-CRC changes incorrectly blocked physical-event pooling: "
                        + compatibility.status);
        require(compatibility.detectorFingerprint.length() == 64,
                "compatible detector evidence did not produce a SHA-256 identity");
        require(compatibility.status.contains("threshold curve/values and Hold are intentionally excluded"),
                "pool compatibility report did not explain the threshold/Hold exclusion");

        MlgEmbeddedTuneMetadata authority = MlgEmbeddedTuneMetadata.pooledAuthority(
                a, compatibility.detectorFingerprint);
        require(authority.fingerprint.equals(compatibility.detectorFingerprint),
                "pooled authority did not replace single-session full-tune fingerprint");
        require(authority.detectorModeRaw.equals(a.detectorModeRaw)
                        && near(authority.deltaWindowMs, a.deltaWindowMs),
                "pooled authority changed detector-defining calibration");
    }

    private static void detectorOrFirmwareMismatchFailsClosed() {
        AeProjectSnapshot tune = snapshot(0.40, "Dual stride, newest", 40.0, 0.06);
        MlgEmbeddedTuneMetadata baseline = metadata("rusEFI-test-fw", "crc-a", 4.0,
                new double[]{0.40,0.40,0.40,0.40,0.40,0.40,0.40,0.40});
        MlgEmbeddedTuneMetadata wrongFirmware = metadata("other-fw", "crc-b", 4.0,
                new double[]{0.20,0.20,0.20,0.20,0.20,0.20,0.20,0.20});
        DecelHistoricalPoolCompatibility.Result firmware =
                DecelHistoricalPoolCompatibility.evaluate(tune,
                        Arrays.asList(baseline, wrongFirmware));
        require(!firmware.compatible && firmware.status.contains("Firmware signature differs"),
                "firmware mismatch retained cross-log pooling authority");

        MlgEmbeddedTuneMetadata wrongWindow = metadata("rusEFI-test-fw", "crc-c", 4.0,
                new double[]{0.20,0.20,0.20,0.20,0.20,0.20,0.20,0.20}, 50.0, 0.06);
        DecelHistoricalPoolCompatibility.Result window =
                DecelHistoricalPoolCompatibility.evaluate(tune,
                        Arrays.asList(baseline, wrongWindow));
        require(!window.compatible && window.status.contains("current Working Tune detector configuration"),
                "detector timing mismatch retained cross-log pooling authority");
    }

    private static void independentEventsPoolBeforeClassificationBoundaryDecision() {
        AeProjectSnapshot tune = snapshot(0.40, "Dual stride, newest", 40.0, 0.06);
        DecelHistoricalEvidenceAnalyzer.Result one = analysis(
                new double[]{0.05,0.06,0.07,0.08,0.10},
                new double[]{0.50,0.55,0.60,0.65,0.70});
        DecelHistoricalEvidenceAnalyzer.Result two = analysis(
                new double[]{0.05,0.06,0.07,0.09,0.11},
                new double[]{0.45,0.48,0.55,0.60,0.65});

        DecelHistoricalEvidenceAnalyzer.Result pooled =
                DecelHistoricalEvidencePool.combine(tune, Arrays.asList(one, two));
        require(pooled.quietCalibrationLocked,
                "two independently locked sessions lost pooled quiet qualification");
        require(pooled.normalCorrections == 10 && pooled.decelReleases == 10,
                "pooled event counts were not the sum of independent physical events");
        DecelHistoricalEvidenceAnalyzer.BinSummary bin = pooled.bins.get(1);
        require(bin.normalCorrections == 10 && bin.decelReleases == 10 && bin.separated,
                "1600-RPM pooled evidence did not become a separated 10+10 population");

        MlgEmbeddedTuneMetadata authority = metadata("rusEFI-test-fw", "crc-a", 4.0,
                new double[]{0.40,0.40,0.40,0.40,0.40,0.40,0.40,0.40});
        DecelHistoricalThresholdDecision.Result decision =
                DecelHistoricalThresholdDecision.evaluate(tune, pooled, authority);
        DecelHistoricalThresholdDecision.BinDecision target = decision.bins.get(1);
        require(target.outcome == DecelHistoricalThresholdDecision.Outcome.PROPOSE,
                "pooled separated evidence did not produce a classification-boundary proposal");
        require(near(target.preview, 0.300),
                "pooled independent events should produce 1600-RPM target 0.300, got "
                        + target.preview);
        require(DecelHistoricalEvidenceStrength.assess(target).level
                        == DecelHistoricalEvidenceStrength.Level.STRONG,
                "10+10 pooled independent events were not labelled STRONG");
        require(pooled.reportText.contains("Raw samples were not concatenated before classification"),
                "pooled report lost per-session classification boundary");
    }

    private static void duplicateFileSelectionIsRejected() throws Exception {
        File temp = File.createTempFile("ae-tuner-pool-duplicate", ".mlg");
        try {
            boolean rejected = false;
            try {
                DecelHistoricalPoolService.analyze(new File[]{temp, temp},
                        snapshot(0.40, "Dual stride, newest", 40.0, 0.06));
            } catch (IOException expected) {
                rejected = expected.getMessage() != null
                        && expected.getMessage().contains("selected more than once");
            }
            require(rejected, "duplicate source selection could double-weight evidence");
        } finally {
            temp.delete();
        }
    }

    private static DecelHistoricalEvidenceAnalyzer.Result analysis(
            double[] normals, double[] releases) {
        List<DecelHistoricalEvidenceAnalyzer.BinSummary> bins =
                new ArrayList<DecelHistoricalEvidenceAnalyzer.BinSummary>();
        for (int i = 0; i < RPM.length; i++) {
            if (i != 1) {
                bins.add(new DecelHistoricalEvidenceAnalyzer.BinSummary(
                        RPM[i], 0, 0, 0, 0, 0,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        false, Collections.<DecelHistoricalEvidenceAnalyzer.Event>emptyList()));
                continue;
            }
            List<DecelHistoricalEvidenceAnalyzer.Event> events =
                    new ArrayList<DecelHistoricalEvidenceAnalyzer.Event>();
            for (double peak : normals) events.add(event(
                    DecelHistoricalEvidenceAnalyzer.CLASS_NORMAL_CORRECTION, peak));
            for (double peak : releases) events.add(event(
                    DecelHistoricalEvidenceAnalyzer.CLASS_DECEL_RELEASE, peak));
            bins.add(new DecelHistoricalEvidenceAnalyzer.BinSummary(
                    RPM[i], normals.length, releases.length, 0, 0, 0,
                    0.10, 0.50, 0.125, 0.400, true, events));
        }
        return new DecelHistoricalEvidenceAnalyzer.Result(
                "synthetic qualified session", 1000, true, 80, 2.0,
                0.2, 0.3, 2.0, 6.0,
                normals.length + releases.length,
                normals.length, releases.length, 0, 1, bins);
    }

    private static DecelHistoricalEvidenceAnalyzer.Event event(String eventClass, double peak) {
        return new DecelHistoricalEvidenceAnalyzer.Event(
                eventClass, 0.0, 0.1, 1600.0, peak, 12.0,
                1.0, 0.40, peak > 0.40, false);
    }

    private static MlgEmbeddedTuneMetadata metadata(String firmware, String crc,
                                                     double hold, double[] thresholds) {
        return metadata(firmware, crc, hold, thresholds, 40.0, 0.06);
    }

    private static MlgEmbeddedTuneMetadata metadata(String firmware, String crc,
                                                     double hold, double[] thresholds,
                                                     double window, double lookback) {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < thresholds.length; i++) {
            if (i > 0) values.append(',');
            values.append(thresholds[i]);
        }
        String xml = "<msq signature=\"" + firmware + "\"><page>"
                + "<constant name=\"tuneCrcPcVariable\">" + crc + "</constant>"
                + "<constant name=\"tpsAeDetectMode\">\"Dual stride, newest\"</constant>"
                + "<constant name=\"tpsAeDeltaWindowMs\">" + window + "</constant>"
                + "<constant name=\"tpsAccelLookback\">" + lookback + "</constant>"
                + "<constant name=\"tpsDecelThresholdRpmBins\">800,1600,2400,3200,4100,4900,5700,6500</constant>"
                + "<constant name=\"tpsDecelThresholdValue\">" + values + "</constant>"
                + "<constant name=\"tpsDecelHoldCycles\">" + hold + "</constant>"
                + "</page></msq>";
        return MlgEmbeddedTuneMetadata.parse(xml);
    }

    private static AeProjectSnapshot snapshot(double threshold, String mode,
                                              double windowMs, double lookbackSeconds) {
        double[] thresholds = new double[RPM.length];
        for (int i = 0; i < thresholds.length; i++) thresholds[i] = threshold;
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                mode, windowMs, lookbackSeconds,
                false, true, 0.35,
                RPM, thresholds, 12.0);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.000001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
