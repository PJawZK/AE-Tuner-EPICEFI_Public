package se.anders.tunerstudio.aetuner.guided.method;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort, read-only extraction of Decel-relevant calibration from the MSQ
 * text embedded in an EFI Analytics MLG information block.
 *
 * Missing or unfamiliar fields fail closed: callers receive NaN/empty arrays and
 * a status string. No value is invented from the current Working Tune here.
 */
public final class MlgEmbeddedTuneMetadata {
    private static final Pattern CONSTANT = Pattern.compile(
            "<constant\\b([^>]*)>(.*?)</constant\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELF_CLOSING_CONSTANT = Pattern.compile(
            "<constant\\b([^>]*)/\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([A-Za-z0-9_:.\\-]+)\\s*=\\s*([\"'])(.*?)\\2",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FIRMWARE_SIGNATURE = Pattern.compile(
            "(?:firmwareSignature|signature)\\s*=\\s*([\"'])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public final boolean available;
    public final String status;
    public final String firmwareSignature;
    public final String tuneCrcPcVariable;
    /** Raw MSQ value. It may be a display string or a firmware enum number. */
    public final String detectorModeRaw;
    public final double deltaWindowMs;
    public final double sampleLengthSeconds;
    public final Boolean fastCallback;
    public final double[] decelThresholdRpmBins;
    public final double[] decelThresholdValues;
    public final double decelHoldCycles;
    public final String fingerprint;

    private MlgEmbeddedTuneMetadata(boolean available, String status,
                                    String firmwareSignature, String tuneCrcPcVariable,
                                    String detectorModeRaw, double deltaWindowMs,
                                    double sampleLengthSeconds, Boolean fastCallback,
                                    double[] decelThresholdRpmBins,
                                    double[] decelThresholdValues,
                                    double decelHoldCycles, String fingerprint) {
        this.available = available;
        this.status = status == null ? "" : status;
        this.firmwareSignature = nn(firmwareSignature);
        this.tuneCrcPcVariable = nn(tuneCrcPcVariable);
        this.detectorModeRaw = nn(detectorModeRaw);
        this.deltaWindowMs = deltaWindowMs;
        this.sampleLengthSeconds = sampleLengthSeconds;
        this.fastCallback = fastCallback;
        this.decelThresholdRpmBins = cloneArray(decelThresholdRpmBins);
        this.decelThresholdValues = cloneArray(decelThresholdValues);
        this.decelHoldCycles = decelHoldCycles;
        this.fingerprint = nn(fingerprint);
    }

    private MlgEmbeddedTuneMetadata() { this(false, "", "", "", "", Double.NaN,
            Double.NaN, null, new double[0], new double[0], Double.NaN, ""); }

    public static MlgEmbeddedTuneMetadata read(File file) throws IOException {
        if (file == null || !file.isFile()) return unavailable("No historical file selected.");
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".mlg")) {
            return unavailable("Text/CSV log has no native MLG information block; as-recorded tune fingerprint is unavailable.");
        }
        String info = MlgLogReader.readInfoText(file);
        if (info == null || info.trim().length() == 0) {
            return unavailable("Native MLG contains no information block / captured tune text.");
        }
        return parse(info);
    }

    static MlgEmbeddedTuneMetadata parse(String info) {
        if (info == null || info.trim().length() == 0) {
            return unavailable("Embedded tune text is empty.");
        }
        String firmware = firstGroup(FIRMWARE_SIGNATURE, info, 2);
        String crc = constant(info, "tuneCrcPcVariable");
        String mode = constant(info, "tpsAeDetectMode");
        double window = scalar(constant(info, "tpsAeDeltaWindowMs"));
        double lookback = scalar(constant(info, "tpsAccelLookback"));
        Boolean fast = bool(constant(info, "tpsAeFastCallback"));
        double[] rpm = numbers(constant(info, "tpsDecelThresholdRpmBins"));
        double[] values = numbers(constant(info, "tpsDecelThresholdValue"));
        double hold = scalar(constant(info, "tpsDecelHoldCycles"));

        int found = 0;
        if (firmware.length() > 0) found++;
        if (crc.length() > 0) found++;
        if (mode.length() > 0) found++;
        if (Double.isFinite(window)) found++;
        if (Double.isFinite(lookback)) found++;
        if (fast != null) found++;
        if (rpm.length > 0) found++;
        if (values.length > 0) found++;
        if (Double.isFinite(hold)) found++;

        String canonical = "firmware=" + firmware
                + "|crc=" + crc
                + "|mode=" + mode
                + "|window=" + canonical(window)
                + "|lookback=" + canonical(lookback)
                + "|fast=" + (fast == null ? "?" : fast.toString())
                + "|rpm=" + canonical(rpm)
                + "|threshold=" + canonical(values)
                + "|hold=" + canonical(hold);
        String fingerprint = found == 0 ? "" : sha256(canonical);
        boolean curveValid = rpm.length > 0 && rpm.length == values.length;
        StringBuilder status = new StringBuilder();
        status.append("Embedded MLG tune metadata: ").append(found)
                .append(" Decel/detector field group(s) recovered.");
        if (!curveValid) {
            status.append(" Historical Decel threshold curve is unavailable or incomplete.");
        } else {
            status.append(" Historical Decel threshold curve has ").append(rpm.length)
                    .append(" matched RPM/value cells.");
        }
        if (!Double.isFinite(hold)) status.append(" Historical Hold is unavailable.");
        if (mode.length() == 0) status.append(" Historical detector mode is unavailable.");
        if (found == 0) status.append(" No recognized calibration field was found; no values were guessed.");

        return new MlgEmbeddedTuneMetadata(found > 0, status.toString(), firmware, crc,
                mode, window, lookback, fast, rpm, values, hold, fingerprint);
    }

    /**
     * Cross-log pooling retains the first session's detector fields as the
     * compatibility authority while replacing the full-tune fingerprint with
     * the detector-compatibility fingerprint shared by every accepted source.
     * Per-source full fingerprints remain in the pooled technical report.
     */
    static MlgEmbeddedTuneMetadata pooledAuthority(MlgEmbeddedTuneMetadata source,
                                                   String detectorFingerprint) {
        if (source == null) return unavailable("No pooled detector authority is available.");
        return new MlgEmbeddedTuneMetadata(source.available,
                source.status + " Pooled authority identity uses detector-compatible fingerprinting; per-source full tune fingerprints remain audit-only.",
                source.firmwareSignature, source.tuneCrcPcVariable,
                source.detectorModeRaw, source.deltaWindowMs,
                source.sampleLengthSeconds, source.fastCallback,
                source.decelThresholdRpmBins, source.decelThresholdValues,
                source.decelHoldCycles, detectorFingerprint);
    }

    public boolean hasDecelThresholdCurve() {
        return decelThresholdRpmBins.length > 0
                && decelThresholdRpmBins.length == decelThresholdValues.length;
    }

    public boolean hasHoldCycles() { return Double.isFinite(decelHoldCycles); }

    public double decelThresholdForRpm(double rpm) {
        if (!hasDecelThresholdCurve() || !Double.isFinite(rpm)) return Double.NaN;
        if (rpm <= decelThresholdRpmBins[0]) return decelThresholdValues[0];
        int last = decelThresholdRpmBins.length - 1;
        if (rpm >= decelThresholdRpmBins[last]) return decelThresholdValues[last];
        for (int i = 1; i < decelThresholdRpmBins.length; i++) {
            if (rpm <= decelThresholdRpmBins[i]) {
                double x0 = decelThresholdRpmBins[i - 1];
                double x1 = decelThresholdRpmBins[i];
                double y0 = decelThresholdValues[i - 1];
                double y1 = decelThresholdValues[i];
                if (Math.abs(x1 - x0) < 1.0e-9) return y1;
                double fraction = (rpm - x0) / (x1 - x0);
                return y0 + (y1 - y0) * fraction;
            }
        }
        return Double.NaN;
    }

    private static String constant(String text, String name) {
        String value = findConstant(CONSTANT, text, name, false);
        if (value.length() > 0) return value;
        return findConstant(SELF_CLOSING_CONSTANT, text, name, true);
    }

    private static String findConstant(Pattern pattern, String text, String name,
                                       boolean selfClosing) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String attrName = attribute(attrs, "name");
            if (!name.equals(attrName)) continue;
            String attrValue = attribute(attrs, "value");
            if (attrValue.length() > 0) return clean(attrValue);
            if (!selfClosing && matcher.groupCount() >= 2) return clean(matcher.group(2));
            return "";
        }
        return "";
    }

    private static String attribute(String attrs, String name) {
        if (attrs == null) return "";
        Matcher matcher = ATTRIBUTE.matcher(attrs);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) return matcher.group(3).trim();
        }
        return "";
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("<[^>]+>", " ")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
    }

    private static double scalar(String value) {
        double[] values = numbers(value);
        return values.length == 0 ? Double.NaN : values[0];
    }

    private static Boolean bool(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "on".equals(normalized)
                || "yes".equals(normalized) || "enabled".equals(normalized)) return Boolean.TRUE;
        if ("false".equals(normalized) || "off".equals(normalized)
                || "no".equals(normalized) || "disabled".equals(normalized)) return Boolean.FALSE;
        double number = scalar(value);
        if (!Double.isFinite(number)) return null;
        return number >= 0.5 ? Boolean.TRUE : Boolean.FALSE;
    }

    private static double[] numbers(String value) {
        if (value == null || value.trim().length() == 0) return new double[0];
        String cleaned = clean(value).replace('[', ' ').replace(']', ' ')
                .replace('{', ' ').replace('}', ' ').replace(';', ' ');
        String[] tokens = cleaned.split("[,\\s]+");
        List<Double> out = new ArrayList<Double>();
        for (String token : tokens) {
            if (token == null || token.trim().length() == 0) continue;
            try {
                double number = Double.parseDouble(token.trim());
                if (Double.isFinite(number)) out.add(number);
            } catch (NumberFormatException ignored) {
                // Enumerated/textual constants are deliberately not coerced.
            }
        }
        double[] result = new double[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i).doubleValue();
        return result;
    }

    private static String firstGroup(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? clean(matcher.group(group)) : "";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String canonical(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.9f", value) : "?";
    }

    private static String canonical(double[] values) {
        if (values == null || values.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(canonical(values[i]));
        }
        return out.toString();
    }

    private static double[] cloneArray(double[] values) {
        return values == null ? new double[0] : values.clone();
    }

    private static String nn(String value) { return value == null ? "" : value; }

    private static MlgEmbeddedTuneMetadata unavailable(String status) {
        return new MlgEmbeddedTuneMetadata(false, status, "", "", "", Double.NaN,
                Double.NaN, null, new double[0], new double[0], Double.NaN, "");
    }
}
