package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.BuildIdentity;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Fail-closed compatibility model for pooling threshold-independent historical
 * Decel physical-event evidence across multiple logs.
 *
 * Full tune fingerprints are intentionally not compared: Decel threshold values,
 * threshold RPM curves, Hold and whole-tune CRC may legitimately differ between
 * sessions while the detector/classifier remains physically comparable.
 */
public final class DecelHistoricalPoolCompatibility {
    private static final double WINDOW_TOLERANCE = 0.001;
    private static final double LOOKBACK_TOLERANCE = 0.000001;

    public static final String POLICY_ID = BuildIdentity.DECEL_POOL_POLICY;

    public static final class Result {
        public final boolean compatible;
        public final String detectorFingerprint;
        public final String status;

        Result(boolean compatible, String detectorFingerprint, String status) {
            this.compatible = compatible;
            this.detectorFingerprint = detectorFingerprint == null ? "" : detectorFingerprint;
            this.status = status == null ? "" : status;
        }
    }

    private DecelHistoricalPoolCompatibility() { }

    public static Result evaluate(AeProjectSnapshot snapshot,
                                  List<MlgEmbeddedTuneMetadata> sessions) {
        if (snapshot == null) return withheld("Working Tune is unavailable.");
        if (sessions == null || sessions.size() < 2) {
            return withheld("At least two historical logs are required for cross-log pooling.");
        }

        MlgEmbeddedTuneMetadata first = sessions.get(0);
        String firstProblem = requiredMetadataProblem(first, 1);
        if (firstProblem.length() > 0) return withheld(firstProblem);

        DecelHistoricalThresholdDecision.Compatibility currentCompatibility =
                DecelHistoricalThresholdDecision.compatibility(snapshot, first);
        if (!currentCompatibility.actionable) {
            return withheld("Log 1 does not match the current Working Tune detector configuration: "
                    + currentCompatibility.status);
        }

        ChannelRole firstTwin = DecelSignalSamples.modeTwin(first.detectorModeRaw);
        for (int i = 1; i < sessions.size(); i++) {
            MlgEmbeddedTuneMetadata next = sessions.get(i);
            String problem = requiredMetadataProblem(next, i + 1);
            if (problem.length() > 0) return withheld(problem);

            DecelHistoricalThresholdDecision.Compatibility nextCurrent =
                    DecelHistoricalThresholdDecision.compatibility(snapshot, next);
            if (!nextCurrent.actionable) {
                return withheld("Log " + (i + 1)
                        + " does not match the current Working Tune detector configuration: "
                        + nextCurrent.status);
            }

            ChannelRole nextTwin = DecelSignalSamples.modeTwin(next.detectorModeRaw);
            if (!first.firmwareSignature.equals(next.firmwareSignature)) {
                return withheld("Firmware signature differs between pooled logs.");
            }
            if (firstTwin != nextTwin
                    || !first.detectorModeRaw.trim().equalsIgnoreCase(next.detectorModeRaw.trim())) {
                return withheld("Decel detector mode/twin differs between pooled logs.");
            }
            if (!near(first.deltaWindowMs, next.deltaWindowMs, WINDOW_TOLERANCE)) {
                return withheld("Delta Window differs between pooled logs ("
                        + fmt(first.deltaWindowMs) + " vs " + fmt(next.deltaWindowMs) + " ms).");
            }
            if (!near(first.sampleLengthSeconds, next.sampleLengthSeconds, LOOKBACK_TOLERANCE)) {
                return withheld("TPS lookback differs between pooled logs ("
                        + fmt(first.sampleLengthSeconds) + " vs "
                        + fmt(next.sampleLengthSeconds) + " s).");
            }
            if (first.fastCallback == null ^ next.fastCallback == null) {
                return withheld("Fast Callback availability differs between pooled logs; pooling fails closed rather than guessing.");
            }
            if (first.fastCallback != null
                    && first.fastCallback.booleanValue() != next.fastCallback.booleanValue()) {
                return withheld("Fast Callback differs between pooled logs.");
            }
        }

        String fingerprint = detectorFingerprint(first);
        String status = "COMPATIBLE CROSS-LOG DETECTOR EVIDENCE — " + sessions.size()
                + " logs; firmware=" + first.firmwareSignature
                + ", mode=" + first.detectorModeRaw
                + ", Delta Window=" + fmt(first.deltaWindowMs) + " ms"
                + ", lookback=" + fmt(first.sampleLengthSeconds) + " s"
                + ", Fast Callback=" + (first.fastCallback == null ? "unavailable in all logs"
                        : first.fastCallback.toString())
                + ". Tune CRC, historical Decel threshold curve/values and Hold are intentionally excluded from pooling compatibility because physical-event classification is threshold/Hold independent.";
        return new Result(true, fingerprint, status);
    }

    static String detectorFingerprint(MlgEmbeddedTuneMetadata metadata) {
        if (requiredMetadataProblem(metadata, 1).length() > 0) return "";
        ChannelRole twin = DecelSignalSamples.modeTwin(metadata.detectorModeRaw);
        String canonical = "firmware=" + metadata.firmwareSignature
                + "|mode=" + metadata.detectorModeRaw.trim().toLowerCase(Locale.ROOT)
                + "|twin=" + (twin == null ? "?" : twin.name())
                + "|window=" + canonical(metadata.deltaWindowMs)
                + "|lookback=" + canonical(metadata.sampleLengthSeconds)
                + "|fast=" + (metadata.fastCallback == null ? "?" : metadata.fastCallback.toString());
        return sha256(canonical);
    }

    private static String requiredMetadataProblem(MlgEmbeddedTuneMetadata metadata, int index) {
        if (metadata == null || !metadata.available) {
            return "Log " + index + " has no embedded native MLG detector metadata; cross-log pooling is withheld.";
        }
        if (metadata.firmwareSignature.trim().length() == 0) {
            return "Log " + index + " has no firmware signature; cross-log pooling is withheld.";
        }
        if (metadata.detectorModeRaw.trim().length() == 0
                || DecelSignalSamples.modeTwin(metadata.detectorModeRaw) == null) {
            return "Log " + index + " has no resolvable captured Decel detector mode/twin.";
        }
        if (!Double.isFinite(metadata.deltaWindowMs)) {
            return "Log " + index + " has no captured Delta Window; cross-log pooling is withheld.";
        }
        if (!Double.isFinite(metadata.sampleLengthSeconds)) {
            return "Log " + index + " has no captured TPS lookback; cross-log pooling is withheld.";
        }
        return "";
    }

    private static Result withheld(String reason) {
        return new Result(false, "", "WITHHELD — " + reason);
    }

    private static boolean near(double a, double b, double tolerance) {
        return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= tolerance;
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }

    private static String canonical(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.9f", value) : "?";
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
}
