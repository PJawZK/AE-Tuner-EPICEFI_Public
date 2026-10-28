package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Regression for the real retained MLG name collision: DECEL_TPS != decelTps. */
public final class DecelHistoricalChannelIdentityRegressionTest {
    private DecelHistoricalChannelIdentityRegressionTest() { }

    public static void main(String[] args) {
        try {
            uppercaseStatusDoesNotStealDirectDecelDeltaIdentity();
        } catch (Exception ex) {
            throw new AssertionError("Historical Decel channel-identity regression failed", ex);
        }
        System.out.println("DecelHistoricalChannelIdentityRegressionTest passed");
    }

    private static void uppercaseStatusDoesNotStealDirectDecelDeltaIdentity() throws Exception {
        File file = File.createTempFile("decel-channel-collision", ".csv");
        file.deleteOnExit();
        String csv = "Time,RPM,TPS,TPSdot,DECEL_TPS,Fuel: TPS decel change,Fuel: AE delta newest fall,running,cranking\n"
                + "0.000,1600,20.0,-10.0,1,-0.80,-0.80,1,0\n"
                + "0.050,1600,19.0,-20.0,0,-1.10,-1.10,1,0\n";
        Files.write(file.toPath(), csv.getBytes(StandardCharsets.UTF_8));

        DecelHistoricalLogImporter.Result result = DecelHistoricalLogImporter.load(file);
        require(result.usable() && result.samples.size() == 2,
                "collision fixture did not import");
        LiveSample first = result.samples.get(0);
        require(Math.abs(first.get(ChannelRole.DECEL_DELTA_TPS) + 0.80) < 0.000001,
                "uppercase DECEL_TPS status stole the direct Fuel: TPS decel change identity");
        require(Math.abs(first.get(ChannelRole.AE_DELTA_NEWEST_FALL) + 0.80) < 0.000001,
                "mode-matched newest-fall twin was not retained");
        require(first.bool(ChannelRole.ENGINE_RUNNING)
                        && !first.bool(ChannelRole.ENGINE_CRANKING),
                "engine running/cranking context was not retained");

        DecelSignalSamples.Agreement agreement = DecelSignalSamples.agreement(
                result.samples, "Dual stride, newest", 0.0005);
        require(agreement.verified() && agreement.comparedSamples == 2
                        && agreement.mismatchedSamples == 0
                        && Math.abs(agreement.maxDifference) < 0.000001,
                "real-log channel collision no longer resolves to verified direct/twin identity");
        require(result.status.contains("cross-check/fallback")
                        && !result.status.contains("Decel detector authority: exact"),
                "importer status still overstates direct-channel authority");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
