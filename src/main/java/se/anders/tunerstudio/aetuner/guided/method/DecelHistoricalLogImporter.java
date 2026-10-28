package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Read-only log import boundary for historical Decel replay.
 *
 * Accepts AE Tuner evidence CSV, ordinary TunerStudio/MegaLogViewer text/CSV
 * exports, and native MLVLG binary .mlg versions 1 and 2. Native MLG decoding is
 * streamed and retains only the channels needed by the Decel evidence engine.
 *
 * Decel signal identity is deliberately preserved. Fuel: TPS AE change is the
 * positive detector output and is never silently substituted for decelTps.
 */
public final class DecelHistoricalLogImporter {
    private static final int MAX_ROWS = 1000000;

    public static final class Result {
        public final List<LiveSample> samples;
        public final String sourceType;
        public final String status;
        public final int inputRows;
        public final int acceptedRows;
        public final int skippedRows;
        public final boolean hasDecelActive;
        public final boolean hasAccelActive;
        public final boolean hasLoggedDecelThreshold;
        public final boolean hasDecelCycleCount;
        public final boolean hasDecelFuelMultiplier;
        public final boolean hasExactDecelSignal;
        public final boolean hasModeTwinCandidates;

        Result(List<LiveSample> samples, String sourceType, String status,
               int inputRows, int acceptedRows, int skippedRows,
               boolean hasDecelActive, boolean hasAccelActive,
               boolean hasLoggedDecelThreshold, boolean hasDecelCycleCount,
               boolean hasDecelFuelMultiplier, boolean hasExactDecelSignal,
               boolean hasModeTwinCandidates) {
            this.samples = Collections.unmodifiableList(new ArrayList<LiveSample>(samples));
            this.sourceType = sourceType == null ? "" : sourceType;
            this.status = status == null ? "" : status;
            this.inputRows = inputRows;
            this.acceptedRows = acceptedRows;
            this.skippedRows = skippedRows;
            this.hasDecelActive = hasDecelActive;
            this.hasAccelActive = hasAccelActive;
            this.hasLoggedDecelThreshold = hasLoggedDecelThreshold;
            this.hasDecelCycleCount = hasDecelCycleCount;
            this.hasDecelFuelMultiplier = hasDecelFuelMultiplier;
            this.hasExactDecelSignal = hasExactDecelSignal;
            this.hasModeTwinCandidates = hasModeTwinCandidates;
        }

        public boolean usable() { return !samples.isEmpty(); }
    }

    private static final class Columns {
        int time = -1;
        int rpm = -1;
        int tps = -1;
        int engineRunning = -1;
        int engineCranking = -1;
        int accelDelta = -1;
        int decelDelta = -1;
        int decelMinStep = -1;
        int decelTimedFall = -1;
        int deltaSpan = -1;
        int decelFall = -1;
        int decelNewestFall = -1;
        int tpsDot = -1;
        int decelActive = -1;
        int accelActive = -1;
        int decelThreshold = -1;
        int decelCycleCnt = -1;
        int decelFuelMult = -1;
        int decelEventCount = -1;

        boolean required() { return time >= 0 && requiredCore(); }
        boolean requiredCore() { return rpm >= 0 && tps >= 0 && hasDecelEvidence(); }
        boolean hasDecelEvidence() {
            return decelDelta >= 0 || decelMinStep >= 0 || decelTimedFall >= 0
                    || deltaSpan >= 0 || decelFall >= 0 || decelNewestFall >= 0;
        }
        boolean hasModeTwinCandidates() {
            return decelMinStep >= 0 || decelTimedFall >= 0 || deltaSpan >= 0
                    || decelFall >= 0 || decelNewestFall >= 0;
        }
        boolean hasEngineState() { return engineRunning >= 0 || engineCranking >= 0; }
    }

    private DecelHistoricalLogImporter() { }

    public static Result load(File file) throws IOException {
        return load(file, DecelHistoricalProgress.NONE);
    }

    public static Result load(File file, DecelHistoricalProgress progress) throws IOException {
        GuidedAnalysisCancellation.check();
        if (file == null) throw new IOException("No historical log selected");
        if (!file.isFile()) throw new IOException("Historical log does not exist: " + file);
        DecelHistoricalProgress.report(progress, file, "Opening log", 0);
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mlg")) return loadMlg(file, progress);
        return loadText(file, lower, progress);
    }

    private static Result loadMlg(final File file, final DecelHistoricalProgress progress) throws IOException {
        final List<LiveSample> samples = new ArrayList<LiveSample>();
        final Columns[] columnsHolder = new Columns[1];
        final double[] previousRecordSeconds = new double[]{Double.NaN};
        final double[] previousTps = new double[]{Double.NaN};
        final int[] invalidRequired = new int[]{0};
        final int[] initialRate = new int[]{0};
        final int[] nonAdvancingRecordTime = new int[]{0};
        final int[] longRecordGap = new int[]{0};

        MlgLogReader.Stats stats = MlgLogReader.read(file, MAX_ROWS,
                new MlgLogReader.RecordHandler() {
                    @Override public boolean onHeader(MlgLogReader.Header header) {
                        Columns columns = mapColumns(header.fieldNames());
                        columnsHolder[0] = columns;
                        return columns.requiredCore();
                    }

                    @Override public boolean onRecord(long index, double recordSeconds,
                                                      MlgLogReader.Record record)
                            throws IOException {
                        GuidedAnalysisCancellation.check();
                        if ((index & 1023L) == 0) DecelHistoricalProgress.report(progress, file, "Decoding native log", index + 1);
                        Columns columns = columnsHolder[0];
                        if (columns == null || !columns.requiredCore()) return false;

                        // Native MLG owns a per-record timestamp. Do not use the ECU's
                        // embedded Time channel as the sampling clock: real TS logs can
                        // contain many consecutive records whose payload changes while
                        // that ECU Time value repeats.
                        double seconds = recordSeconds;
                        double rpm = record.value(columns.rpm);
                        double tps = record.value(columns.tps);
                        if (!finite(seconds) || !finite(rpm) || !finite(tps)) {
                            invalidRequired[0]++;
                            return true;
                        }

                        double tpsDot = columns.tpsDot >= 0
                                ? record.value(columns.tpsDot) : Double.NaN;
                        if (!finite(tpsDot)) {
                            if (finite(previousRecordSeconds[0]) && finite(previousTps[0])) {
                                double dt = seconds - previousRecordSeconds[0];
                                if (dt > 0.000001 && dt <= 2.0) {
                                    tpsDot = (tps - previousTps[0]) / dt;
                                } else if (dt <= 0.000001) {
                                    nonAdvancingRecordTime[0]++;
                                } else {
                                    longRecordGap[0]++;
                                }
                            } else {
                                initialRate[0]++;
                            }
                        }
                        previousRecordSeconds[0] = seconds;
                        previousTps[0] = tps;
                        if (!finite(tpsDot)) return true;

                        EnumMap<ChannelRole, Double> values =
                                new EnumMap<ChannelRole, Double>(ChannelRole.class);
                        values.put(ChannelRole.RPM, rpm);
                        values.put(ChannelRole.TPS, tps);
                        put(values, ChannelRole.ENGINE_RUNNING, record, columns.engineRunning);
                        put(values, ChannelRole.ENGINE_CRANKING, record, columns.engineCranking);
                        put(values, ChannelRole.DELTA_TPS, record, columns.accelDelta);
                        put(values, ChannelRole.DECEL_DELTA_TPS, record, columns.decelDelta);
                        put(values, ChannelRole.AE_DELTA_MIN_STEP, record, columns.decelMinStep);
                        put(values, ChannelRole.AE_DELTA_TIMED_FALL, record, columns.decelTimedFall);
                        put(values, ChannelRole.AE_DELTA_SPAN, record, columns.deltaSpan);
                        put(values, ChannelRole.AE_DELTA_FALL, record, columns.decelFall);
                        put(values, ChannelRole.AE_DELTA_NEWEST_FALL, record, columns.decelNewestFall);
                        put(values, ChannelRole.TPS_DECEL_ACTIVE, record, columns.decelActive);
                        put(values, ChannelRole.AE_ABOVE_THRESHOLD, record, columns.accelActive);
                        put(values, ChannelRole.DECEL_THRESHOLD, record, columns.decelThreshold);
                        put(values, ChannelRole.TPS_DECEL_CYCLE_CNT, record, columns.decelCycleCnt);
                        put(values, ChannelRole.DECEL_FUEL_MULT, record, columns.decelFuelMult);
                        put(values, ChannelRole.DECEL_EVENT_COUNT, record, columns.decelEventCount);
                        if (!hasResolvedDecelEvidence(values)) {
                            invalidRequired[0]++;
                            return true;
                        }
                        samples.add(new LiveSample(secondsToNano(seconds), seconds,
                                values, tpsDot, Double.NaN));
                        return true;
                    }
                });

        Columns columns = columnsHolder[0];
        String sourceType = "TunerStudio MLG v" + stats.header.version;
        if (columns == null || !columns.requiredCore()) {
            return empty(sourceType,
                    "Native MLG decoded successfully, but authoritative Decel detector evidence was not found. Need RPM, TPS and Fuel: TPS decel change or at least one published falling detector twin. Fuel: TPS AE change alone is not accepted as Decel evidence. Native MLG record timestamps provide the analysis clock.",
                    columns);
        }

        int input = boundedInt(stats.dataRecords);
        int skipped = boundedInt(stats.dataRecords - samples.size());
        int timingRateSkipped = invalidRequired[0] + initialRate[0]
                + nonAdvancingRecordTime[0] + longRecordGap[0];
        StringBuilder status = new StringBuilder();
        status.append("Native MLVLG v").append(stats.header.version)
                .append(" decoded read-only with embedded field metadata and scale/transform values. ")
                .append("Valid records: ").append(stats.deliveredRecords)
                .append("; checksum-rejected: ").append(stats.badChecksumRecords)
                .append(" (retry-recovered: ").append(stats.checksumRetryRecoveredRecords)
                .append("; unrecovered: ").append(stats.checksumUnrecoveredRecords).append(')')
                .append("; markers skipped: ").append(stats.markerRecords).append('.');
        appendSignalAuthority(status, columns);
        if (timingRateSkipped > 0) {
            status.append(" Native timing/rate/channel validation skipped ")
                    .append(timingRateSkipped).append(" valid records")
                    .append(" (initial rate: ").append(initialRate[0])
                    .append("; non-advancing MLG timestamp: ").append(nonAdvancingRecordTime[0])
                    .append("; >2 s MLG gap: ").append(longRecordGap[0])
                    .append("; invalid required channel: ").append(invalidRequired[0]).append(").");
        }
        if (stats.truncatedTail) {
            status.append(" Incomplete final block was ignored as a truncated tail.");
        }
        if (stats.stoppedByHandler && stats.dataRecords >= MAX_ROWS) {
            status.append(" Import stopped at the ").append(MAX_ROWS).append("-record safety limit.");
        }
        if (columns.time >= 0) {
            status.append(" Embedded ECU Time is present but diagnostic-only; native MLG record timestamps are timing authority.");
        } else {
            status.append(" Native MLG record timestamps are timing authority.");
        }
        return result(samples, sourceType, status.toString(), input, samples.size(), skipped, columns);
    }

    private static Result loadText(File file, String lower, DecelHistoricalProgress progress) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String headerLine = null;
            char delimiter = ',';
            Columns columns = null;
            int preambleLines = 0;
            while (preambleLines < 100 && (headerLine = reader.readLine()) != null) {
                preambleLines++;
                if (headerLine.trim().length() == 0) continue;
                char candidate = chooseDelimiter(headerLine);
                List<String> fields = split(headerLine, candidate);
                Columns mapped = mapColumns(fields);
                if (mapped.required()) {
                    delimiter = candidate;
                    columns = mapped;
                    break;
                }
            }
            if (columns == null) {
                return empty("text/CSV",
                        "Required columns were not found. Need time/dt_s, RPM, TPS and Fuel: TPS decel change or a published falling detector twin. Fuel: TPS AE change alone is not accepted as Decel evidence.",
                        null);
            }

            List<LiveSample> samples = new ArrayList<LiveSample>();
            String line;
            int input = 0;
            int skipped = 0;
            double previousTime = Double.NaN;
            double previousTps = Double.NaN;
            while ((line = reader.readLine()) != null) {
                GuidedAnalysisCancellation.check();
                if (input >= MAX_ROWS) break;
                if (line.trim().length() == 0 || line.trim().startsWith("#")) continue;
                input++;
                if ((input & 1023) == 1) DecelHistoricalProgress.report(progress, file, "Reading log rows", input);
                List<String> fields = split(line, delimiter);
                double seconds = number(fields, columns.time);
                double rpm = number(fields, columns.rpm);
                double tps = number(fields, columns.tps);
                if (!finite(seconds) || !finite(rpm) || !finite(tps)) {
                    skipped++;
                    continue;
                }

                double tpsDot = number(fields, columns.tpsDot);
                if (!finite(tpsDot) && finite(previousTime) && finite(previousTps)) {
                    double dt = seconds - previousTime;
                    if (dt > 0.000001 && dt <= 2.0) tpsDot = (tps - previousTps) / dt;
                }
                previousTime = seconds;
                previousTps = tps;
                if (!finite(tpsDot)) {
                    skipped++;
                    continue;
                }

                EnumMap<ChannelRole, Double> values =
                        new EnumMap<ChannelRole, Double>(ChannelRole.class);
                values.put(ChannelRole.RPM, rpm);
                values.put(ChannelRole.TPS, tps);
                put(values, ChannelRole.ENGINE_RUNNING, fields, columns.engineRunning);
                put(values, ChannelRole.ENGINE_CRANKING, fields, columns.engineCranking);
                put(values, ChannelRole.DELTA_TPS, fields, columns.accelDelta);
                put(values, ChannelRole.DECEL_DELTA_TPS, fields, columns.decelDelta);
                put(values, ChannelRole.AE_DELTA_MIN_STEP, fields, columns.decelMinStep);
                put(values, ChannelRole.AE_DELTA_TIMED_FALL, fields, columns.decelTimedFall);
                put(values, ChannelRole.AE_DELTA_SPAN, fields, columns.deltaSpan);
                put(values, ChannelRole.AE_DELTA_FALL, fields, columns.decelFall);
                put(values, ChannelRole.AE_DELTA_NEWEST_FALL, fields, columns.decelNewestFall);
                put(values, ChannelRole.TPS_DECEL_ACTIVE, fields, columns.decelActive);
                put(values, ChannelRole.AE_ABOVE_THRESHOLD, fields, columns.accelActive);
                put(values, ChannelRole.DECEL_THRESHOLD, fields, columns.decelThreshold);
                put(values, ChannelRole.TPS_DECEL_CYCLE_CNT, fields, columns.decelCycleCnt);
                put(values, ChannelRole.DECEL_FUEL_MULT, fields, columns.decelFuelMult);
                put(values, ChannelRole.DECEL_EVENT_COUNT, fields, columns.decelEventCount);
                if (!hasResolvedDecelEvidence(values)) {
                    skipped++;
                    continue;
                }
                samples.add(new LiveSample(secondsToNano(seconds), seconds,
                        values, tpsDot, Double.NaN));
            }

            String type = lower.endsWith(".csv") ? "CSV/text log" : "text log";
            StringBuilder status = new StringBuilder();
            if (samples.isEmpty()) {
                status.append("No usable rows remained after required-channel/rate validation.");
            } else {
                status.append("Imported read-only historical samples. TPS rate was taken from the log when available and otherwise reconstructed from adjacent TPS/time rows.");
                appendSignalAuthority(status, columns);
            }
            return result(samples, type, status.toString(), input, samples.size(), skipped, columns);
        } finally {
            reader.close();
        }
    }

    private static Columns mapColumns(List<String> header) {
        Columns out = new Columns();
        for (int i = 0; i < header.size(); i++) {
            String raw = header.get(i) == null ? "" : header.get(i).trim();
            String n = normalize(raw);
            if (out.time < 0 && any(n, "time", "seconds", "sec", "dts")) out.time = i;
            if (out.rpm < 0 && any(n, "rpm", "rpmvalue", "instantrpm")) out.rpm = i;
            if (out.tps < 0 && any(n, "tps", "tpsvalue", "tps1")) out.tps = i;
            if (out.engineRunning < 0 && any(n, "running", "enginerunning", "isrunning")) out.engineRunning = i;
            if (out.engineCranking < 0 && any(n, "cranking", "enginecranking", "iscranking")) out.engineCranking = i;
            if (out.accelDelta < 0 && any(n, "fueltpsaechange", "deltatps")) out.accelDelta = i;
            // Do not normalize the bare decelTps alias: real MLGs can also publish
            // an unrelated DECEL_TPS status field, which normalizes to the same token.
            // The canonical display name is safe, while the internal alias is accepted
            // only with its exact camel-case spelling.
            if (out.decelDelta < 0 && ("decelTps".equals(raw) || "tpsDecelChange".equals(raw))) {
                out.decelDelta = i;
            }
            if (out.decelDelta < 0 && any(n,
                    "fueltpsdecelchange", "tpsdecelchange", "aefallingdelta")) out.decelDelta = i;
            if (out.decelMinStep < 0 && any(n,
                    "fuelaedeltaminstep", "aedeltaminstep")) out.decelMinStep = i;
            if (out.decelTimedFall < 0 && any(n,
                    "fuelaedeltatimedfall", "aedeltatimedfall")) out.decelTimedFall = i;
            if (out.deltaSpan < 0 && any(n,
                    "fuelaedeltaspan", "aedeltaspan")) out.deltaSpan = i;
            if (out.decelFall < 0 && any(n,
                    "fuelaedeltafall", "aedeltafall")) out.decelFall = i;
            if (out.decelNewestFall < 0 && any(n,
                    "fuelaedeltanewestfall", "aedeltanewestfall")) out.decelNewestFall = i;
            if (out.tpsDot < 0 && any(n,
                    "tpsdot", "tpsrate", "tpsrates", "tpspers", "tpspercentpers")) out.tpsDot = i;
            if (out.decelActive < 0 && any(n,
                    "fueltpsdecelactive", "isbelowdecelthreshold", "tpsdecelactive")) out.decelActive = i;
            if (out.accelActive < 0 && any(n,
                    "fueltpsaeactive", "isaboveaccelthreshold", "tpsaeactive")) out.accelActive = i;
            if (out.decelThreshold < 0 && any(n,
                    "fueltpsdecelthreshold", "decelthreshold", "tpsdecelthreshold")) out.decelThreshold = i;
            if (out.decelCycleCnt < 0 && any(n,
                    "enginecyclesdecelduration", "tpsdecelcnt", "decelcyclecount")) out.decelCycleCnt = i;
            if (out.decelFuelMult < 0 && any(n,
                    "fueltpsdecelfuelmultiplier", "decelfuelmult")) out.decelFuelMult = i;
            if (out.decelEventCount < 0 && any(n,
                    "deceleventtriggeredcnt", "deceleventcount")) out.decelEventCount = i;
        }
        return out;
    }

    private static void put(EnumMap<ChannelRole, Double> values, ChannelRole role,
                            MlgLogReader.Record record, int index) throws IOException {
        if (index < 0) return;
        double value = record.value(index);
        if (finite(value)) values.put(role, value);
    }

    private static void put(EnumMap<ChannelRole, Double> values, ChannelRole role,
                            List<String> fields, int index) {
        double value = number(fields, index);
        if (finite(value)) values.put(role, value);
    }

    private static boolean hasResolvedDecelEvidence(EnumMap<ChannelRole, Double> values) {
        return finite(value(values, ChannelRole.DECEL_DELTA_TPS))
                || finite(value(values, ChannelRole.AE_DELTA_MIN_STEP))
                || finite(value(values, ChannelRole.AE_DELTA_TIMED_FALL))
                || finite(value(values, ChannelRole.AE_DELTA_SPAN))
                || finite(value(values, ChannelRole.AE_DELTA_FALL))
                || finite(value(values, ChannelRole.AE_DELTA_NEWEST_FALL));
    }

    private static double value(EnumMap<ChannelRole, Double> values, ChannelRole role) {
        Double value = values.get(role);
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static void appendSignalAuthority(StringBuilder status, Columns columns) {
        if (columns == null) return;
        if (columns.decelDelta >= 0) {
            status.append(" Direct cross-check/fallback: exact Fuel: TPS decel change/decelTps channel; captured/configured detector mode selects the analysis authority.");
            if (columns.hasModeTwinCandidates()) {
                status.append(" Published falling-mode candidates are retained for mode selection and identity/agreement checks.");
            }
        } else if (columns.hasModeTwinCandidates()) {
            status.append(" Direct decelTps channel is absent; published falling-mode candidates are retained and the captured/configured detector mode must select the matching twin before analysis.");
        }
        if (columns.hasEngineState()) {
            status.append(" Engine running/cranking state is retained for physical-event qualification.");
        }
        if (columns.accelDelta >= 0) {
            status.append(" Fuel: TPS AE change is retained separately as positive-side context and is never substituted for Decel.");
        }
    }

    private static Result result(List<LiveSample> samples, String sourceType, String status,
                                 int inputRows, int acceptedRows, int skippedRows,
                                 Columns columns) {
        return new Result(samples, sourceType, status, inputRows, acceptedRows, skippedRows,
                columns != null && columns.decelActive >= 0,
                columns != null && columns.accelActive >= 0,
                columns != null && columns.decelThreshold >= 0,
                columns != null && columns.decelCycleCnt >= 0,
                columns != null && columns.decelFuelMult >= 0,
                columns != null && columns.decelDelta >= 0,
                columns != null && columns.hasModeTwinCandidates());
    }

    private static Result empty(String sourceType, String status, Columns columns) {
        return result(Collections.<LiveSample>emptyList(), sourceType, status,
                0, 0, 0, columns);
    }

    private static boolean any(String value, String... candidates) {
        for (String candidate : candidates) if (candidate.equals(value)) return true;
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        String lower = value.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) out.append(c);
        }
        return out.toString();
    }

    private static char chooseDelimiter(String line) {
        int commas = count(line, ',');
        int tabs = count(line, '\t');
        int semicolons = count(line, ';');
        if (tabs > commas && tabs >= semicolons) return '\t';
        if (semicolons > commas) return ';';
        return ',';
    }

    private static int count(String text, char needle) {
        int count = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && c == needle) count++;
        }
        return count;
    }

    private static List<String> split(String line, char delimiter) {
        List<String> out = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                out.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        out.add(field.toString().trim());
        return out;
    }

    private static double number(List<String> fields, int index) {
        if (index < 0 || index >= fields.size()) return Double.NaN;
        String value = fields.get(index);
        if (value == null) return Double.NaN;
        value = value.trim();
        if (value.length() == 0) return Double.NaN;
        if (value.indexOf(',') >= 0 && value.indexOf('.') < 0) value = value.replace(',', '.');
        try { return Double.parseDouble(value); }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }

    private static long secondsToNano(double seconds) {
        double value = seconds * 1.0e9;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        if (value <= Long.MIN_VALUE) return Long.MIN_VALUE;
        return (long) value;
    }

    private static int boundedInt(long value) {
        if (value <= 0) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
