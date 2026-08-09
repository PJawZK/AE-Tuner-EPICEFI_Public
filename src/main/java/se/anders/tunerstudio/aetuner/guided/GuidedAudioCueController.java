package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.Component;
import java.awt.Toolkit;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Direct, auditable generated-tone guidance for Guided Capture. */
public final class GuidedAudioCueController implements GuidedWorkflowEvent.Listener {
    public enum Cue {
        SESSION_STARTED("Session started",
                GuidedWorkflowEvent.SESSION_STARTED.triggerDescription),
        READY("Ready",
                GuidedWorkflowEvent.READY_ENTERED.triggerDescription),
        OPENING_PENDING("Opening pending",
                GuidedWorkflowEvent.OPENING_PENDING.triggerDescription),
        TARGET_ACQUIRED("Target acquired",
                GuidedWorkflowEvent.TARGET_ACQUIRED.triggerDescription),
        ACCEPTED("Accepted — back off",
                GuidedWorkflowEvent.EVENT_ACCEPTED.triggerDescription),
        EXCLUDED("Excluded — back off",
                GuidedWorkflowEvent.EVENT_EXCLUDED.triggerDescription),
        RETURN_TO_BASELINE("Return to baseline",
                GuidedWorkflowEvent.RETURN_TO_BASELINE.triggerDescription),
        COMPLETE("Series complete",
                GuidedWorkflowEvent.SERIES_COMPLETE.triggerDescription);

        final String label;
        final String triggerDescription;

        Cue(String label, String triggerDescription) {
            this.label = label;
            this.triggerDescription = triggerDescription;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    interface AuditSink {
        void record(String stage, Cue cue, String detail);
    }

    interface CuePlayer {
        void play(Cue cue, GuidedAudioProfile.Setting setting,
                  AuditSink audit);
        void cancel(AuditSink audit);
        public void close(AuditSink audit);
        public void resume(AuditSink audit);
        public String statusText();
    }

    private static final int MAX_AUDIT_ENTRIES = 40;

    private final CuePlayer player;
    private GuidedAudioProfile pendingProfile = GuidedAudioProfile.defaults();
    private GuidedAudioProfile activeProfile;
    private final ArrayDeque<AuditEntry> audit = new ArrayDeque<AuditEntry>();
    private final AuditSink auditSink = new AuditSink() {
        @Override
        public void record(String stage, Cue cue, String detail) {
            appendAudit(stage, cue, detail);
        }
    };
    private boolean enabled;
    private boolean sessionActive;
    private boolean suspended;
    private long auditSequence;

    public GuidedAudioCueController() {
        this(new ToneCuePlayer());
    }

    /** Compatibility constructor; the component is intentionally ignored. */
    GuidedAudioCueController(Component ignored) {
        this(new ToneCuePlayer());
    }

    GuidedAudioCueController(CuePlayer player) {
        this.player = player == null ? new ToneCuePlayer() : player;
    }

    @Override
    public synchronized void onGuidedWorkflowEvent(
            GuidedWorkflowEvent event, String detail, long nanoTime) {
        if (event == null || suspended) return;
        appendAudit("WORKFLOW", cueFor(event),
                event.name() + (detail == null || detail.length() == 0
                        ? "" : " — " + detail));
        if (event == GuidedWorkflowEvent.SESSION_STARTED) {
            sessionActive = true;
            activeProfile = pendingProfile.copy();
            requestCue(Cue.SESSION_STARTED, "workflow");
            return;
        }
        if (event == GuidedWorkflowEvent.PAUSED) {
            player.cancel(auditSink);
            return;
        }
        if (event == GuidedWorkflowEvent.SESSION_ENDED) {
            player.cancel(auditSink);
            sessionActive = false;
            activeProfile = null;
            return;
        }
        Cue cue = cueFor(event);
        if (cue != null) {
            requestCue(cue, "workflow");
        }
    }

    public synchronized void setEnabled(boolean next) {
        enabled = next;
        appendAudit(next ? "ENABLED" : "DISABLED", null,
                next ? "Guided audio enabled" : "Guided audio disabled");
        if (!next) {
            player.cancel(auditSink);
        }
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    synchronized boolean isSessionActive() {
        return sessionActive;
    }

    public synchronized void testReady() {
        previewCue(Cue.READY);
    }

    synchronized boolean previewCue(Cue cue) {
        if (sessionActive) {
            appendAudit("PREVIEW_BLOCKED", cue,
                    "Audio Cue Lab is stationary-only and disabled during a Guided Session");
            return false;
        }
        return requestCue(cue, "stationary preview");
    }

    public synchronized void stopNow() {
        player.cancel(auditSink);
    }

    public synchronized void pauseNow() {
        player.cancel(auditSink);
    }

    synchronized GuidedAudioProfile.Setting pendingSetting(Cue cue) {
        return pendingProfile.get(cue).copy();
    }

    synchronized boolean updatePendingSetting(
            Cue cue, GuidedAudioProfile.Setting setting) {
        if (sessionActive || cue == null || setting == null) {
            return false;
        }
        pendingProfile.put(cue, setting);
        appendAudit("PROFILE_EDITED", cue, setting.summary());
        return true;
    }

    synchronized boolean restoreDefaults() {
        if (sessionActive) return false;
        pendingProfile = GuidedAudioProfile.defaults();
        appendAudit("PROFILE_RESTORED", null,
                "Candidate audio defaults restored");
        return true;
    }

    synchronized String profileSummary() {
        GuidedAudioProfile profile = sessionActive && activeProfile != null
                ? activeProfile : pendingProfile;
        return (sessionActive ? "ACTIVE SESSION PROFILE: "
                : "PENDING SESSION PROFILE: ") + profile.summary();
    }

    public synchronized String statusText() {
        if (suspended) return "Off — plugin lifecycle suspended";
        return enabled ? player.statusText()
                : "Off — all Guided Capture cues remain silent";
    }

    public synchronized void resume() {
        player.resume(auditSink);
        suspended = false;
        appendAudit("RESUMED", null, "Audio lifecycle resumed");
    }

    public synchronized String auditText() {
        StringBuilder out = new StringBuilder();
        out.append(profileSummary()).append('\n');
        if (audit.isEmpty()) {
            out.append("No audio events recorded.\n");
        }
        for (AuditEntry entry : audit) {
            out.append(entry.sequence).append(" | ")
                    .append(entry.timestamp).append(" | ")
                    .append(entry.stage).append(" | ")
                    .append(entry.cue).append(" | ")
                    .append(entry.detail).append('\n');
        }
        return out.toString();
    }

    public synchronized String auditCsv() {
        StringBuilder out = new StringBuilder();
        out.append("audio_sequence,audio_timestamp,audio_stage,audio_cue,audio_detail\n");
        for (AuditEntry entry : audit) {
            out.append(entry.sequence).append(',')
                    .append(csv(entry.timestamp)).append(',')
                    .append(csv(entry.stage)).append(',')
                    .append(csv(entry.cue)).append(',')
                    .append(csv(entry.detail)).append('\n');
        }
        return out.toString();
    }

    synchronized void clearAudit() {
        audit.clear();
        auditSequence = 0L;
    }

    public synchronized void close() {
        suspended = true;
        enabled = false;
        sessionActive = false;
        activeProfile = null;
        player.close(auditSink);
    }

    private boolean requestCue(Cue cue, String origin) {
        if (cue == null || suspended) return false;
        GuidedAudioProfile profile = sessionActive && activeProfile != null
                ? activeProfile : pendingProfile;
        GuidedAudioProfile.Setting setting = profile.get(cue).copy();
        if (!enabled) {
            appendAudit("SKIPPED_DISABLED", cue, origin);
            return false;
        }
        if (!setting.enabled || setting.pattern == GuidedAudioProfile.Pattern.SILENT) {
            appendAudit("SKIPPED_PROFILE", cue,
                    origin + " — " + setting.summary());
            return false;
        }
        appendAudit("REQUESTED", cue,
                origin + " — " + setting.summary());
        player.play(cue, setting, auditSink);
        return true;
    }

    private static Cue cueFor(GuidedWorkflowEvent event) {
        switch (event) {
            case SESSION_STARTED: return Cue.SESSION_STARTED;
            case READY_ENTERED: return Cue.READY;
            case OPENING_PENDING: return Cue.OPENING_PENDING;
            case TARGET_ACQUIRED: return Cue.TARGET_ACQUIRED;
            case EVENT_ACCEPTED: return Cue.ACCEPTED;
            case EVENT_EXCLUDED: return Cue.EXCLUDED;
            case RETURN_TO_BASELINE: return Cue.RETURN_TO_BASELINE;
            case SERIES_COMPLETE: return Cue.COMPLETE;
            default: return null;
        }
    }

    private synchronized void appendAudit(
            String stage, Cue cue, String detail) {
        while (audit.size() >= MAX_AUDIT_ENTRIES) {
            audit.removeFirst();
        }
        audit.addLast(new AuditEntry(++auditSequence, nowIso(),
                stage == null ? "" : stage,
                cue == null ? "-" : cue.name(),
                detail == null ? "" : detail));
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String nowIso() {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }

    private static final class AuditEntry {
        final long sequence;
        final String timestamp;
        final String stage;
        final String cue;
        final String detail;

        AuditEntry(long sequence, String timestamp, String stage,
                   String cue, String detail) {
            this.sequence = sequence;
            this.timestamp = timestamp;
            this.stage = stage;
            this.cue = cue;
            this.detail = detail;
        }
    }

    /** Standard-library generated PCM; no bundled samples or decoder. */
    private static final class ToneCuePlayer implements CuePlayer {
        private static final float SAMPLE_RATE = 16000.0f;
        private static final AudioFormat FORMAT =
                new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

        private volatile ExecutorService executor = newExecutor();
        private final AtomicLong generation = new AtomicLong();
        private volatile boolean closed;
        private volatile SourceDataLine activeLine;
        private volatile String status =
                "On — audio not yet confirmed; use Audio Cue Lab while stationary";

        @Override
        public void play(final Cue cue,
                         final GuidedAudioProfile.Setting setting,
                         final AuditSink audit) {
            if (closed || cue == null || setting == null) return;
            final long token = generation.get();
            status = "Audio cue queued: " + cue.name();
            audit.record("QUEUED", cue, setting.summary());
            try {
                ExecutorService activeExecutor = executor;
                if (activeExecutor == null || activeExecutor.isShutdown()) return;
                activeExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (token == generation.get() && !closed) {
                            playNow(cue, setting, token, audit);
                        }
                    }
                });
            } catch (RuntimeException ex) {
                status = "Audio queue unavailable: " + failureText(ex);
                audit.record("QUEUE_FAILED", cue, failureText(ex));
            }
        }

        @Override
        public void cancel(AuditSink audit) {
            generation.incrementAndGet();
            SourceDataLine line = activeLine;
            if (line != null) {
                try {
                    line.stop();
                    line.flush();
                } catch (RuntimeException ignored) {
                    // Advisory audio cancellation must not affect capture.
                }
            }
            if (!closed) {
                status = "On — audio queue cleared";
            }
            if (audit != null) {
                audit.record("CANCELLED", null,
                        "Current and queued cues cleared");
            }
        }

        @Override
        public String statusText() {
            return status;
        }

        private void playNow(Cue cue,
                             GuidedAudioProfile.Setting setting,
                             long token, AuditSink audit) {
            try {
                byte[] pcm = render(setting);
                if (pcm.length == 0 || cancelled(token)) {
                    audit.record("SKIPPED_EMPTY", cue,
                            "Profile produced no PCM data");
                    return;
                }
                SourceDataLine line = ensureLine();
                audit.record("PCM_OPENED", cue,
                        FORMAT.toString());
                line.stop();
                line.flush();
                if (cancelled(token)) return;
                line.start();
                long started = System.nanoTime();
                long deadline = started + TimeUnit.MILLISECONDS.toNanos(
                        Math.max(1500, setting.estimatedDurationMs() + 800));
                int offset = 0;
                while (offset < pcm.length && !cancelled(token)) {
                    if (System.nanoTime() > deadline) {
                        throw new IllegalStateException(
                                "audio playback deadline exceeded");
                    }
                    int length = Math.min(1024, pcm.length - offset);
                    int written = line.write(pcm, offset, length);
                    if (written <= 0) {
                        throw new IllegalStateException(
                                "audio backend accepted zero bytes");
                    }
                    offset += written;
                }
                long finishAt = started + TimeUnit.MILLISECONDS.toNanos(
                        setting.estimatedDurationMs() + 60L);
                while (!cancelled(token) && System.nanoTime() < finishAt) {
                    Thread.sleep(8L);
                }
                if (cancelled(token)) {
                    audit.record("PLAYBACK_CANCELLED", cue,
                            "Cue cancelled before completion");
                    return;
                }
                line.stop();
                line.flush();
                status = "Audio OK — last cue: " + cue.name();
                audit.record("COMPLETED", cue,
                        setting.summary());
            } catch (Throwable failure) {
                boolean intentionalCancellation = cancelled(token);
                closeLine();
                if (intentionalCancellation) {
                    status = closed ? "Audio closed" : "On — audio queue cleared";
                    audit.record("PLAYBACK_CANCELLED", cue,
                            "Intentional lifecycle/cue cancellation; fallback beep suppressed");
                    return;
                }
                boolean fallback = systemBeep();
                status = "PCM audio failed: " + failureText(failure)
                        + (fallback
                        ? " — system-beep fallback requested"
                        : " — system-beep fallback also failed");
                audit.record("PCM_FAILED", cue,
                        failureText(failure) + " | fallback=" + fallback);
            }
        }

        private SourceDataLine ensureLine() throws Exception {
            SourceDataLine line = activeLine;
            if (line != null && line.isOpen()) {
                return line;
            }
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, 8192);
            activeLine = line;
            return line;
        }

        private static byte[] render(GuidedAudioProfile.Setting setting) {
            if (!setting.enabled
                    || setting.pattern == GuidedAudioProfile.Pattern.SILENT) {
                return new byte[0];
            }
            int pulses = setting.pattern == GuidedAudioProfile.Pattern.THREE_ASCENDING
                    ? 3 : setting.pattern == GuidedAudioProfile.Pattern.DOUBLE
                    ? Math.max(2, setting.repeats) : setting.repeats;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int pulse = 0; pulse < pulses; pulse++) {
                int sampleCount = Math.max(1, Math.round(
                        SAMPLE_RATE * setting.durationMs / 1000.0f));
                for (int i = 0; i < sampleCount; i++) {
                    double fraction = sampleCount <= 1
                            ? 0.0 : i / (double) (sampleCount - 1);
                    double frequency;
                    if (setting.pattern == GuidedAudioProfile.Pattern.RISING_CHIRP
                            || setting.pattern == GuidedAudioProfile.Pattern.FALLING_CHIRP) {
                        frequency = setting.startHz
                                + (setting.endHz - setting.startHz) * fraction;
                    } else if (setting.pattern
                            == GuidedAudioProfile.Pattern.THREE_ASCENDING) {
                        double pulseFraction = pulses <= 1
                                ? 0.0 : pulse / (double) (pulses - 1);
                        frequency = setting.startHz
                                + (setting.endHz - setting.startHz) * pulseFraction;
                    } else {
                        frequency = setting.startHz;
                    }
                    double envelope = envelope(i, sampleCount);
                    double wave = Math.sin(2.0 * Math.PI
                            * frequency * i / SAMPLE_RATE);
                    short value = (short) Math.round(Short.MAX_VALUE
                            * setting.volume * envelope * wave);
                    out.write(value & 0xff);
                    out.write((value >>> 8) & 0xff);
                }
                if (pulse + 1 < pulses) {
                    int silenceSamples = Math.max(1, Math.round(
                            SAMPLE_RATE * setting.gapMs / 1000.0f));
                    for (int i = 0; i < silenceSamples * 2; i++) {
                        out.write(0);
                    }
                }
            }
            return out.toByteArray();
        }

        private static double envelope(int index, int total) {
            int ramp = Math.max(1, Math.min(total / 4,
                    Math.round(SAMPLE_RATE * 0.012f)));
            if (index < ramp) {
                return index / (double) ramp;
            }
            if (index >= total - ramp) {
                return Math.max(0.0,
                        (total - index - 1) / (double) ramp);
            }
            return 1.0;
        }

        private boolean cancelled(long token) {
            return closed || token != generation.get();
        }

        private void closeLine() {
            SourceDataLine line = activeLine;
            activeLine = null;
            if (line != null) {
                try { line.stop(); } catch (RuntimeException ignored) { }
                try { line.flush(); } catch (RuntimeException ignored) { }
                try { line.close(); } catch (RuntimeException ignored) { }
            }
        }

        private static boolean systemBeep() {
            try {
                Toolkit.getDefaultToolkit().beep();
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static String failureText(Throwable failure) {
            if (failure == null) return "unknown failure";
            String name = failure.getClass().getSimpleName();
            String message = failure.getMessage();
            return message == null || message.trim().length() == 0
                    ? name : name + ": " + message.trim();
        }

        private static ExecutorService newExecutor() {
            return Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "ae-tuner-guided-audio");
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }

        @Override
        public synchronized void resume(AuditSink audit) {
            if (!closed && executor != null && !executor.isShutdown()) return;
            executor = newExecutor();
            closed = false;
            status = "On — audio not yet confirmed; use Audio Cue Lab while stationary";
            if (audit != null) audit.record("PLAYER_RESUMED", null,
                    "Audio worker recreated after plugin reopen");
        }

        @Override
        public synchronized void close(AuditSink audit) {
            closed = true;
            generation.incrementAndGet();
            closeLine();
            status = "Audio closed";
            ExecutorService activeExecutor = executor;
            if (activeExecutor != null) {
                activeExecutor.shutdownNow();
                try {
                    activeExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            if (audit != null) audit.record("CLOSED", null,
                    "Audio worker terminated");
        }
    }
}
