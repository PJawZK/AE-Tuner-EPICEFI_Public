package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class DecelHistoricalLogImporterRegressionTest {
    private DecelHistoricalLogImporterRegressionTest() { }

    public static void main(String[] args) {
        try {
            importsAeTunerEvidenceCsvAndKeepsAccelDecelSeparate();
            importsTunerStudioStyleTextWithFirmwareState();
            rejectsPositiveOnlyDeltaAsDecelEvidence();
            importsNativeMlgV2UsingRecordClockAndRetryRecovery();
            importsPublishedMlgV1Layout();
            unsupportedMlgV3IsExplicitRatherThanGuessed();
        } catch (Exception ex) {
            throw new AssertionError("Historical Decel log import regression failed", ex);
        }
        System.out.println("DecelHistoricalLogImporterRegressionTest passed");
    }

    private static void importsAeTunerEvidenceCsvAndKeepsAccelDecelSeparate() throws Exception {
        File file = tempText("ae-tuner", ".csv",
                "sample_index,dt_s,RPM,TPS,Fuel: TPS AE change,Fuel: TPS decel change,Fuel: AE delta newest fall\n"
                + "0,0.000,1500,20.00,0.01,-0.02,-0.02\n"
                + "1,0.050,1500,19.80,-0.03,-0.10,-0.10\n"
                + "2,0.100,1500,19.40,-0.05,-0.35,-0.35\n");
        DecelHistoricalLogImporter.Result result = DecelHistoricalLogImporter.load(file);
        require(result.usable() && result.samples.size() == 2,
                "AE Tuner CSV did not retain rows with reconstructable TPS rate");
        require(Math.abs(result.samples.get(0).getTpsDot() + 4.0) < 0.000001,
                "TPS rate reconstruction did not use adjacent TPS/time rows");
        require(Math.abs(result.samples.get(0).get(ChannelRole.DELTA_TPS) + 0.03) < 0.000001,
                "positive-side Fuel: TPS AE change was not retained separately");
        require(Math.abs(result.samples.get(0).get(ChannelRole.DECEL_DELTA_TPS) + 0.10) < 0.000001,
                "authoritative Fuel: TPS decel change was not retained separately");
        require(result.hasExactDecelSignal && result.hasModeTwinCandidates,
                "CSV did not report direct Decel signal and falling-candidate availability");
    }

    private static void importsTunerStudioStyleTextWithFirmwareState() throws Exception {
        File file = tempText("ts-export", ".txt",
                "TunerStudio text export\n"
                + "Time\tRPM\tTPS\tFuel: TPS AE change\tFuel: TPS decel change\tFuel: AE delta newest fall\tTPSdot\tFuel: TPS AE Active\tFuel: TPS Decel Active\tFuel: TPS decel threshold\tEngine cycles decel duration\tFuel: TPS decel fuel multiplier\n"
                + "s\trpm\t%\t%\t%\t%\t%/s\tbool\tbool\t%\tcycle\tmult\n"
                + "10.000\t2200\t22.0\t0.01\t-0.02\t-0.02\t0.2\t0\t0\t0.600\t8\t1.000\n"
                + "10.050\t2200\t21.2\t-0.05\t-0.80\t-0.80\t-16.0\t0\t1\t0.600\t0\t0.980\n");
        DecelHistoricalLogImporter.Result result = DecelHistoricalLogImporter.load(file);
        require(result.usable() && result.samples.size() == 2,
                "TunerStudio-style text export was not imported");
        LiveSample falling = result.samples.get(1);
        require(result.hasDecelActive && falling.bool(ChannelRole.TPS_DECEL_ACTIVE),
                "Decel Active state was not preserved");
        require(result.hasAccelActive && !falling.bool(ChannelRole.AE_ABOVE_THRESHOLD),
                "Accel Active context was not preserved");
        require(result.hasLoggedDecelThreshold
                        && Math.abs(falling.get(ChannelRole.DECEL_THRESHOLD) - 0.600) < 0.000001,
                "logged Decel threshold was not preserved");
        require(result.hasDecelCycleCount
                        && Math.abs(falling.get(ChannelRole.TPS_DECEL_CYCLE_CNT)) < 0.000001,
                "Decel cycle counter was not preserved");
        require(result.hasDecelFuelMultiplier
                        && Math.abs(falling.get(ChannelRole.DECEL_FUEL_MULT) - 0.980) < 0.000001,
                "Decel fuel multiplier was not preserved");
    }

    private static void rejectsPositiveOnlyDeltaAsDecelEvidence() throws Exception {
        File file = tempText("positive-only", ".csv",
                "Time,RPM,TPS,Fuel: TPS AE change\n"
                + "0.000,2000,20.0,0.01\n"
                + "0.050,2000,19.0,-0.10\n");
        DecelHistoricalLogImporter.Result result = DecelHistoricalLogImporter.load(file);
        require(!result.usable(),
                "positive Fuel: TPS AE change alone was silently accepted as Decel evidence");
        require(result.status.contains("Fuel: TPS AE change alone is not accepted"),
                "positive-only refusal did not explain detector authority");
    }

    private static void importsNativeMlgV2UsingRecordClockAndRetryRecovery() throws Exception {
        File file = writeMlgFixture(2, true, true, true);
        DecelHistoricalLogImporter.Result result = DecelHistoricalLogImporter.load(file);
        require(result.usable(), "native MLG v2 did not import");
        require("TunerStudio MLG v2".equals(result.sourceType),
                "MLG v2 source identity was not retained");
        require(result.inputRows == 6 && result.samples.size() == 3,
                "MLG v2 timing/checksum fixture produced unexpected row counts");
        require(result.status.contains("checksum-rejected: 1 (retry-recovered: 1; unrecovered: 0)"),
                "same-counter/timestamp checksum retry was not recognized as recovered");
        require(result.status.contains("non-advancing MLG timestamp: 1"),
                "duplicate valid MLG record timestamp was not reported separately");
        require(result.status.contains("initial rate: 1"),
                "first reconstructed-rate row was not reported explicitly");
        require(result.status.contains("markers skipped: 1"),
                "MLG marker was not skipped/reported");
        require(result.status.contains("truncated tail"),
                "MLG incomplete final record was not safely reported");
        require(result.status.contains("Embedded ECU Time is present but diagnostic-only"),
                "native MLG record clock did not become timing authority");
        require(result.status.contains("exact Fuel: TPS decel change/decelTps"),
                "native MLG did not report authoritative Decel detector source");

        // Embedded ECU Time intentionally remains 0.0 across these rows. They
        // must still be retained because the native record timestamp advances.
        LiveSample first = result.samples.get(0);
        LiveSample second = result.samples.get(1);
        require(Math.abs(first.get(ChannelRole.TPS) - 21.2) < 0.00001,
                "MLG U16 TPS scale was not applied");
        require(Math.abs(first.getTpsDot() + 80.0) < 0.0001,
                "TPS rate was not reconstructed from the advancing native MLG clock");
        require(Math.abs(second.get(ChannelRole.DELTA_TPS) + 0.08) < 0.00001,
                "MLG positive-side delta scale was not applied separately");
        require(Math.abs(second.get(ChannelRole.DECEL_DELTA_TPS) + 0.80) < 0.00001,
                "MLG authoritative Decel delta scale was not applied");
        require(Math.abs(second.get(ChannelRole.AE_DELTA_NEWEST_FALL) + 0.80) < 0.00001,
                "MLG mode-matched falling candidate was not retained");
        require(Math.abs(second.getTpsDot() + 70.0) < 0.0001,
                "repeated embedded ECU Time incorrectly suppressed native MLG evidence");
        require(first.bool(ChannelRole.TPS_DECEL_ACTIVE),
                "MLG Decel Active value was not preserved");
        require(result.hasAccelActive && result.hasLoggedDecelThreshold
                        && result.hasDecelCycleCount && result.hasDecelFuelMultiplier,
                "native MLG firmware-state channel availability was not retained");
    }

    private static void importsPublishedMlgV1Layout() throws Exception {
        File file = writeMlgFixture(1, false, false, false);
        DecelHistoricalLogImporter.Result result = DecelHistoricalLogImporter.load(file);
        require(result.usable() && result.samples.size() == 3,
                "published MLVLG v1 header/55-byte descriptors were not decoded");
        require("TunerStudio MLG v1".equals(result.sourceType),
                "MLG v1 source identity was not retained");
        require(result.status.contains("checksum-rejected: 0"),
                "clean MLG v1 fixture produced a checksum failure");
    }

    private static void unsupportedMlgV3IsExplicitRatherThanGuessed() throws Exception {
        File file = File.createTempFile("mlg-v3", ".mlg");
        file.deleteOnExit();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[]{'M', 'L', 'V', 'L', 'G', 0});
        out.writeShort(3);
        out.write(new byte[14]);
        out.close();
        Files.write(file.toPath(), bytes.toByteArray());
        try {
            DecelHistoricalLogImporter.load(file);
            throw new AssertionError("unsupported MLG v3 was silently guessed");
        } catch (IOException expected) {
            require(expected.getMessage() != null
                            && expected.getMessage().contains("supports published v1/v2 only"),
                    "MLG v3 refusal did not explain the supported boundary");
        }
    }

    private static File writeMlgFixture(int version, boolean corruptWithRetry,
                                        boolean includeMarker, boolean truncateTail)
            throws Exception {
        FieldSpec[] fields = new FieldSpec[]{
                new FieldSpec(7, "Time", "s", 1.0f, 0.0f),
                new FieldSpec(2, "RPM", "rpm", 1.0f, 0.0f),
                new FieldSpec(2, "TPS", "%", 0.1f, 0.0f),
                new FieldSpec(3, "Fuel: TPS AE change", "%", 0.01f, 0.0f),
                new FieldSpec(3, "Fuel: TPS decel change", "%", 0.01f, 0.0f),
                new FieldSpec(3, "Fuel: AE delta newest fall", "%", 0.01f, 0.0f),
                new FieldSpec(0, "Fuel: TPS AE Active", "", 1.0f, 0.0f),
                new FieldSpec(0, "Fuel: TPS Decel Active", "", 1.0f, 0.0f),
                new FieldSpec(2, "Fuel: TPS decel threshold", "%", 0.001f, 0.0f),
                new FieldSpec(0, "Engine cycles decel duration", "cycle", 1.0f, 0.0f),
                new FieldSpec(2, "Fuel: TPS decel fuel multiplier", "mult", 0.001f, 0.0f)
        };
        int descriptorSize = version == 1 ? 55 : 89;
        int headerSize = version == 1 ? 22 : 24;
        int recordLength = 4 + 2 + 2 + 2 + 2 + 2 + 1 + 1 + 2 + 1 + 2;
        int dataBegin = headerSize + descriptorSize * fields.length;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[]{'M', 'L', 'V', 'L', 'G', 0});
        out.writeShort(version);
        out.writeInt(0);
        if (version == 1) out.writeShort(0); else out.writeInt(0);
        out.writeInt(dataBegin);
        out.writeShort(recordLength);
        out.writeShort(fields.length);
        for (FieldSpec field : fields) writeDescriptor(out, version, field);

        writeDataRecord(out, 0, 1000, payload(0.000f, 2200, 220,
                1, -2, -2, 0, 0, 600, 8, 1000), false);
        if (includeMarker) writeMarker(out, 1, 1500, "synthetic marker");
        if (corruptWithRetry) {
            writeDataRecord(out, 2, 2000, payload(0.000f, 2200, 212,
                    -5, -42, -42, 0, 1, 600, 0, 980), true);
            writeDataRecord(out, 2, 2000, payload(0.000f, 2200, 212,
                    -5, -42, -42, 0, 1, 600, 0, 980), false);
        } else {
            writeDataRecord(out, 2, 2000, payload(0.050f, 2200, 212,
                    -5, -42, -42, 0, 1, 600, 0, 980), false);
        }
        writeDataRecord(out, 3, 3000, payload(corruptWithRetry ? 0.000f : 0.100f,
                2200, 205, -8, -80, -80, 0, 1, 600, 0, 970), false);
        if (corruptWithRetry) {
            // Same valid counter/timestamp again: native timing must reject this
            // duplicate rather than manufacture a zero-duration TPS rate.
            writeDataRecord(out, 3, 3000, payload(0.000f, 2200, 204,
                    -1, -5, -5, 0, 0, 600, 5, 1000), false);
        }
        writeDataRecord(out, 4, 4000, payload(0.150f, 2200, 203,
                -1, -5, -5, 0, 0, 600, 6, 1000), false);
        if (truncateTail) {
            out.writeByte(0);
            out.writeByte(5);
            out.writeShort(5000);
            out.write(new byte[]{0, 0, 0});
        }
        out.close();

        File file = File.createTempFile("native-v" + version + "-", ".mlg");
        file.deleteOnExit();
        Files.write(file.toPath(), bytes.toByteArray());
        return file;
    }

    private static byte[] payload(float time, int rpm, int tpsTenths,
                                  int accelHundredths, int decelHundredths,
                                  int newestFallHundredths, int accelActive,
                                  int decelActive, int thresholdThousandths,
                                  int cycleCount, int fuelMultThousandths) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeFloat(time);
        out.writeShort(rpm);
        out.writeShort(tpsTenths);
        out.writeShort(accelHundredths);
        out.writeShort(decelHundredths);
        out.writeShort(newestFallHundredths);
        out.writeByte(accelActive);
        out.writeByte(decelActive);
        out.writeShort(thresholdThousandths);
        out.writeByte(cycleCount);
        out.writeShort(fuelMultThousandths);
        out.close();
        return bytes.toByteArray();
    }

    private static void writeDataRecord(DataOutputStream out, int counter, int timestamp,
                                        byte[] payload, boolean corruptChecksum) throws Exception {
        out.writeByte(0);
        out.writeByte(counter);
        out.writeShort(timestamp);
        out.write(payload);
        int checksum = 0;
        for (byte value : payload) checksum = (checksum + (value & 0xff)) & 0xff;
        out.writeByte(corruptChecksum ? ((checksum + 1) & 0xff) : checksum);
    }

    private static void writeMarker(DataOutputStream out, int counter, int timestamp,
                                    String message) throws Exception {
        out.writeByte(1);
        out.writeByte(counter);
        out.writeShort(timestamp);
        writeFixed(out, message, 50);
    }

    private static void writeDescriptor(DataOutputStream out, int version, FieldSpec field)
            throws Exception {
        out.writeByte(field.type);
        writeFixed(out, field.name, 34);
        writeFixed(out, field.units, 10);
        out.writeByte(0);
        out.writeFloat(field.scale);
        out.writeFloat(field.transform);
        out.writeByte(3);
        if (version == 2) writeFixed(out, "synthetic", 34);
    }

    private static void writeFixed(DataOutputStream out, String value, int length)
            throws Exception {
        byte[] raw = value == null ? new byte[0] : value.getBytes(StandardCharsets.US_ASCII);
        int count = Math.min(raw.length, length);
        out.write(raw, 0, count);
        for (int i = count; i < length; i++) out.writeByte(0);
    }

    private static File tempText(String prefix, String suffix, String text) throws Exception {
        File file = File.createTempFile(prefix, suffix);
        file.deleteOnExit();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static final class FieldSpec {
        final int type;
        final String name;
        final String units;
        final float scale;
        final float transform;

        FieldSpec(int type, String name, String units, float scale, float transform) {
            this.type = type;
            this.name = name;
            this.units = units;
            this.scale = scale;
            this.transform = transform;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
