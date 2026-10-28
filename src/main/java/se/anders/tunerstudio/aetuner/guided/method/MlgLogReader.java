package se.anders.tunerstudio.aetuner.guided.method;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Streaming reader for EFI Analytics MLVLG binary logs (.mlg), versions 1 and 2.
 *
 * The implementation follows the published EFI Analytics v1/v2 specifications:
 * MLVLG\0 magic, big-endian header/fields, fixed field descriptors, type-0 data
 * records and type-1 markers. Data-record checksum is the unsigned byte sum of
 * the packed logger-field payload only. Unknown versions/block types are never
 * guessed because doing so could silently desynchronise the stream.
 *
 * This class is deliberately read-only and has no ECU/tune mutation dependency.
 */
public final class MlgLogReader {
    private static final byte[] MAGIC = new byte[]{'M', 'L', 'V', 'L', 'G', 0};
    private static final int V1_HEADER_SIZE = 22;
    private static final int V2_HEADER_SIZE = 24;
    private static final int V1_FIELD_SIZE = 55;
    private static final int V2_FIELD_SIZE = 89;
    private static final int MARKER_MESSAGE_SIZE = 50;
    private static final long MAX_INFO_BYTES = 16L * 1024L * 1024L;

    public interface RecordHandler {
        /** Return false to validate the header but skip the data stream. */
        boolean onHeader(Header header) throws IOException;

        /** Return false to stop after the current valid record. */
        boolean onRecord(long index, double recordSeconds, Record record) throws IOException;
    }

    public static final class Field {
        public final int type;
        public final String name;
        public final String units;
        public final double scale;
        public final double transform;
        public final int payloadOffset;
        public final int payloadSize;

        Field(int type, String name, String units, double scale, double transform,
              int payloadOffset, int payloadSize) {
            this.type = type;
            this.name = name == null ? "" : name;
            this.units = units == null ? "" : units;
            this.scale = scale;
            this.transform = transform;
            this.payloadOffset = payloadOffset;
            this.payloadSize = payloadSize;
        }

        boolean scalar() { return type >= 0 && type <= 7; }
    }

    public static final class Header {
        public final int version;
        public final long captureEpochSeconds;
        public final long infoDataStart;
        public final long dataBeginIndex;
        public final int recordLength;
        public final List<Field> fields;

        Header(int version, long captureEpochSeconds, long infoDataStart,
               long dataBeginIndex, int recordLength, List<Field> fields) {
            this.version = version;
            this.captureEpochSeconds = captureEpochSeconds;
            this.infoDataStart = infoDataStart;
            this.dataBeginIndex = dataBeginIndex;
            this.recordLength = recordLength;
            this.fields = Collections.unmodifiableList(new ArrayList<Field>(fields));
        }

        public List<String> fieldNames() {
            List<String> names = new ArrayList<String>(fields.size());
            for (Field field : fields) names.add(field.name);
            return names;
        }
    }

    public static final class Record {
        private final Header header;
        private final byte[] payload;

        Record(Header header, byte[] payload) {
            this.header = header;
            this.payload = payload;
        }

        public double value(int fieldIndex) throws IOException {
            if (fieldIndex < 0 || fieldIndex >= header.fields.size()) return Double.NaN;
            Field field = header.fields.get(fieldIndex);
            if (field.payloadOffset < 0
                    || field.payloadOffset + field.payloadSize > payload.length) {
                throw new IOException("MLG field payload offset is outside record: " + field.name);
            }
            double raw = rawValue(field, payload);
            if (!Double.isFinite(raw)) return Double.NaN;
            return field.scalar() ? (raw + field.transform) * field.scale : raw;
        }
    }

    public static final class Stats {
        public final Header header;
        public final long dataRecords;
        public final long deliveredRecords;
        public final long badChecksumRecords;
        /** Bad-checksum records followed by a valid copy with the same counter/timestamp. */
        public final long checksumRetryRecoveredRecords;
        /** Bad-checksum records for which no valid same-counter/timestamp copy followed. */
        public final long checksumUnrecoveredRecords;
        public final long markerRecords;
        public final boolean truncatedTail;
        public final boolean stoppedByHandler;

        Stats(Header header, long dataRecords, long deliveredRecords,
              long badChecksumRecords, long checksumRetryRecoveredRecords,
              long checksumUnrecoveredRecords, long markerRecords,
              boolean truncatedTail, boolean stoppedByHandler) {
            this.header = header;
            this.dataRecords = dataRecords;
            this.deliveredRecords = deliveredRecords;
            this.badChecksumRecords = badChecksumRecords;
            this.checksumRetryRecoveredRecords = checksumRetryRecoveredRecords;
            this.checksumUnrecoveredRecords = checksumUnrecoveredRecords;
            this.markerRecords = markerRecords;
            this.truncatedTail = truncatedTail;
            this.stoppedByHandler = stoppedByHandler;
        }
    }

    private MlgLogReader() { }

    /**
     * Read the optional EFI Analytics information block located between the
     * field descriptors and the first data record. Real TunerStudio MLG files
     * commonly store the captured MSQ/tune here. The block is returned as text
     * exactly as stored apart from NUL padding removal; no tune values are
     * interpreted in this low-level reader.
     */
    public static String readInfoText(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("MLG file does not exist: " + file);
        RandomAccessFile in = new RandomAccessFile(file, "r");
        try {
            Header header = readHeader(in);
            if (header.infoDataStart <= 0 || header.infoDataStart >= header.dataBeginIndex) return "";
            long length = header.dataBeginIndex - header.infoDataStart;
            if (length <= 0) return "";
            if (length > MAX_INFO_BYTES) {
                throw new IOException("MLG information block exceeds " + MAX_INFO_BYTES
                        + " byte safety limit: " + length);
            }
            byte[] data = new byte[(int) length];
            in.seek(header.infoDataStart);
            in.readFully(data);
            int start = 0;
            while (start < data.length && data[start] == 0) start++;
            int end = data.length;
            while (end > start && data[end - 1] == 0) end--;
            return new String(data, start, end - start, StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    public static Stats read(File file, int maxRecords, RecordHandler handler) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("MLG file does not exist: " + file);
        if (maxRecords <= 0) throw new IOException("MLG maxRecords must be positive");
        if (handler == null) throw new IOException("MLG record handler is required");

        RandomAccessFile in = new RandomAccessFile(file, "r");
        try {
            Header header = readHeader(in);
            boolean continueIntoData = handler.onHeader(header);
            if (!continueIntoData) {
                return new Stats(header, 0, 0, 0, 0, 0, 0, false, true);
            }

            long length = in.length();
            if (header.dataBeginIndex < 0 || header.dataBeginIndex > length) {
                throw new IOException("MLG data-begin offset is outside file: "
                        + header.dataBeginIndex + " / " + length);
            }
            in.seek(header.dataBeginIndex);

            long dataRecords = 0;
            long delivered = 0;
            long badChecksum = 0;
            long retryRecovered = 0;
            long unrecovered = 0;
            long markers = 0;
            boolean truncated = false;
            boolean stopped = false;
            int previousTimestamp = -1;
            long timestampWraps = 0;

            int pendingBadCounter = -1;
            int pendingBadTimestamp = -1;
            long pendingBadCount = 0;

            while (in.getFilePointer() < length && dataRecords < maxRecords) {
                long blockOffset = in.getFilePointer();
                if (length - blockOffset < 4) {
                    truncated = true;
                    break;
                }

                int blockType = in.readUnsignedByte();
                int counter = in.readUnsignedByte();
                int timestamp = in.readUnsignedShort();
                if (previousTimestamp >= 0 && timestamp < previousTimestamp) timestampWraps++;
                previousTimestamp = timestamp;
                double recordSeconds = ((timestampWraps << 16) + (long) timestamp) * 0.00001d;

                if (blockType == 0) {
                    long required = (long) header.recordLength + 1L;
                    if (length - in.getFilePointer() < required) {
                        truncated = true;
                        break;
                    }
                    dataRecords++;
                    byte[] payload = new byte[header.recordLength];
                    in.readFully(payload);
                    int expectedChecksum = in.readUnsignedByte();
                    int actualChecksum = checksum(payload);
                    if (actualChecksum != expectedChecksum) {
                        badChecksum++;
                        if (pendingBadCount > 0
                                && (counter != pendingBadCounter
                                || timestamp != pendingBadTimestamp)) {
                            unrecovered += pendingBadCount;
                            pendingBadCount = 0;
                        }
                        if (pendingBadCount == 0) {
                            pendingBadCounter = counter;
                            pendingBadTimestamp = timestamp;
                        }
                        pendingBadCount++;
                        continue;
                    }

                    if (pendingBadCount > 0) {
                        if (counter == pendingBadCounter && timestamp == pendingBadTimestamp) {
                            retryRecovered += pendingBadCount;
                        } else {
                            unrecovered += pendingBadCount;
                        }
                        pendingBadCount = 0;
                    }

                    delivered++;
                    if (!handler.onRecord(dataRecords - 1, recordSeconds,
                            new Record(header, payload))) {
                        stopped = true;
                        break;
                    }
                } else if (blockType == 1) {
                    if (pendingBadCount > 0) {
                        unrecovered += pendingBadCount;
                        pendingBadCount = 0;
                    }
                    // Marker: type + counter + 2-byte timestamp + 50-byte message.
                    if (length - in.getFilePointer() < MARKER_MESSAGE_SIZE) {
                        truncated = true;
                        break;
                    }
                    in.seek(in.getFilePointer() + MARKER_MESSAGE_SIZE);
                    markers++;
                } else {
                    if (pendingBadCount > 0) {
                        unrecovered += pendingBadCount;
                        pendingBadCount = 0;
                    }
                    throw new IOException("Unsupported MLG block type " + blockType
                            + " at byte " + blockOffset
                            + "; refusing to guess its length and desynchronise the log");
                }
            }

            if (pendingBadCount > 0) unrecovered += pendingBadCount;

            return new Stats(header, dataRecords, delivered, badChecksum,
                    retryRecovered, unrecovered, markers, truncated, stopped);
        } finally {
            in.close();
        }
    }

    private static Header readHeader(RandomAccessFile in) throws IOException {
        if (in.length() < V1_HEADER_SIZE) throw new IOException("MLG file is too short for a header");
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) throw new IOException("Not an MLVLG binary log (magic mismatch)");
        }

        int version = in.readUnsignedShort();
        if (version != 1 && version != 2) {
            throw new IOException("Unsupported MLVLG version " + version
                    + ". This build supports published v1/v2 only; v3 field types are not guessed.");
        }
        long captureEpoch = unsignedInt(in.readInt());
        long infoStart;
        long dataBegin;
        int recordLength;
        int fieldCount;
        int descriptorSize;
        int headerSize;
        if (version == 1) {
            infoStart = in.readUnsignedShort();
            dataBegin = unsignedInt(in.readInt());
            recordLength = in.readUnsignedShort();
            fieldCount = in.readUnsignedShort();
            descriptorSize = V1_FIELD_SIZE;
            headerSize = V1_HEADER_SIZE;
        } else {
            infoStart = unsignedInt(in.readInt());
            dataBegin = unsignedInt(in.readInt());
            recordLength = in.readUnsignedShort();
            fieldCount = in.readUnsignedShort();
            descriptorSize = V2_FIELD_SIZE;
            headerSize = V2_HEADER_SIZE;
        }

        if (recordLength <= 0) throw new IOException("MLG header declares an empty record");
        if (fieldCount <= 0) throw new IOException("MLG header declares no logger fields");
        long descriptorEnd = (long) headerSize + (long) fieldCount * descriptorSize;
        if (descriptorEnd > in.length()) {
            throw new IOException("MLG field descriptors extend beyond the file");
        }
        if (dataBegin < descriptorEnd) {
            throw new IOException("MLG data-begin offset overlaps field descriptors");
        }
        if (infoStart != 0 && (infoStart < descriptorEnd || infoStart > dataBegin)) {
            throw new IOException("MLG info-data offset is inconsistent with header/data offsets");
        }

        List<Field> fields = new ArrayList<Field>(fieldCount);
        int payloadOffset = 0;
        for (int i = 0; i < fieldCount; i++) {
            byte[] descriptor = new byte[descriptorSize];
            in.readFully(descriptor);
            int type = descriptor[0] & 0xff;
            int size = payloadSize(type);
            if (size <= 0) {
                String fieldName = ascii(descriptor, 1, 34);
                throw new IOException("Unsupported MLG field type " + type
                        + " for '" + fieldName + "' in MLVLG v" + version);
            }
            String name = ascii(descriptor, 1, 34);
            String units = ascii(descriptor, 35, 10);
            double scale = 1.0;
            double transform = 0.0;
            if (type <= 7) {
                scale = float32(descriptor, 46);
                transform = float32(descriptor, 50);
                if (!Double.isFinite(scale) || !Double.isFinite(transform)) {
                    throw new IOException("Invalid MLG scale/transform for field '" + name + "'");
                }
            }
            fields.add(new Field(type, name, units, scale, transform,
                    payloadOffset, size));
            payloadOffset += size;
        }

        if (payloadOffset != recordLength) {
            throw new IOException("MLG record length mismatch: header=" + recordLength
                    + " bytes, decoded fields=" + payloadOffset + " bytes");
        }
        return new Header(version, captureEpoch, infoStart, dataBegin, recordLength, fields);
    }

    private static int payloadSize(int type) {
        switch (type) {
            case 0:
            case 1:
            case 10:
                return 1;
            case 2:
            case 3:
            case 11:
                return 2;
            case 4:
            case 5:
            case 7:
            case 12:
                return 4;
            case 6:
                return 8;
            default:
                return -1;
        }
    }

    private static double rawValue(Field field, byte[] payload) throws IOException {
        int p = field.payloadOffset;
        ByteBuffer b = ByteBuffer.wrap(payload, p, field.payloadSize).order(ByteOrder.BIG_ENDIAN);
        switch (field.type) {
            case 0:
            case 10:
                return payload[p] & 0xff;
            case 1:
                return payload[p];
            case 2:
            case 11:
                return b.getShort() & 0xffff;
            case 3:
                return b.getShort();
            case 4:
            case 12:
                return ((long) b.getInt()) & 0xffffffffL;
            case 5:
                return b.getInt();
            case 6:
                return (double) b.getLong();
            case 7:
                return b.getFloat();
            default:
                throw new IOException("Unsupported MLG field type " + field.type);
        }
    }

    private static int checksum(byte[] payload) {
        int sum = 0;
        for (byte value : payload) sum = (sum + (value & 0xff)) & 0xff;
        return sum;
    }

    private static long unsignedInt(int value) { return value & 0xffffffffL; }

    private static double float32(byte[] data, int offset) {
        int bits = ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
        return Float.intBitsToFloat(bits);
    }

    private static String ascii(byte[] data, int offset, int length) {
        int end = offset;
        int limit = Math.min(data.length, offset + length);
        while (end < limit && data[end] != 0) end++;
        return new String(data, offset, Math.max(0, end - offset), StandardCharsets.US_ASCII).trim();
    }
}
