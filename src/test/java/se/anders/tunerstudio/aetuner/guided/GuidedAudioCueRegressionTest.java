package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.ArrayList;
import java.util.List;

public final class GuidedAudioCueRegressionTest {
    public static void main(String[] args) {
        RecordingPlayer player = new RecordingPlayer();
        GuidedAudioCueController controller =
                new GuidedAudioCueController(player);
        controller.setEnabled(true);

        controller.onGuidedWorkflowEvent(
                GuidedWorkflowEvent.SESSION_STARTED, "start", 1L);
        assertCue(player, 0, GuidedAudioCueController.Cue.SESSION_STARTED,
                "session start must dispatch directly");
        controller.onGuidedWorkflowEvent(
                GuidedWorkflowEvent.READY_ENTERED, "ready", 2L);
        assertCue(player, 1, GuidedAudioCueController.Cue.READY,
                "READY must dispatch directly");
        controller.onGuidedWorkflowEvent(
                GuidedWorkflowEvent.TARGET_ACQUIRED, "target", 3L);
        assertCue(player, 2, GuidedAudioCueController.Cue.TARGET_ACQUIRED,
                "brief target state must not depend on Swing polling");
        controller.onGuidedWorkflowEvent(
                GuidedWorkflowEvent.EVENT_ACCEPTED, "accepted", 4L);
        assertCue(player, 3, GuidedAudioCueController.Cue.ACCEPTED,
                "accepted event must request backoff cue");
        controller.onGuidedWorkflowEvent(
                GuidedWorkflowEvent.EVENT_EXCLUDED, "excluded", 5L);
        assertCue(player, 4, GuidedAudioCueController.Cue.EXCLUDED,
                "excluded event must request backoff cue");
        controller.onGuidedWorkflowEvent(
                GuidedWorkflowEvent.RETURN_TO_BASELINE, "return", 6L);
        assertCue(player, 5, GuidedAudioCueController.Cue.RETURN_TO_BASELINE,
                "partial opening must have a distinct return cue");
        controller.onGuidedWorkflowEvent(
                GuidedWorkflowEvent.PAUSED, "pause", 7L);
        require(player.cancelCount == 1,
                "pause must cancel current and queued audio");
        require(controller.auditText().contains("REQUESTED"),
                "audio audit must retain cue requests");
        require(controller.auditText().contains("COMPLETED"),
                "audio audit must retain backend completion");

        GuidedAudioProfile.Setting activeReady = player.settings.get(1);
        GuidedAudioProfile.Setting changed =
                new GuidedAudioProfile.Setting(true,
                        GuidedAudioProfile.Pattern.SINGLE,
                        400, 400, 250, 1, 60, 0.30);
        require(!controller.updatePendingSetting(
                        GuidedAudioCueController.Cue.READY, changed),
                "audio profile must be immutable during a Guided Session");
        require(activeReady.startHz != 400.0,
                "active session profile was modified unexpectedly");

        controller.onGuidedWorkflowEvent(
                GuidedWorkflowEvent.SESSION_ENDED, "end", 8L);
        require(controller.updatePendingSetting(
                        GuidedAudioCueController.Cue.READY, changed),
                "audio profile should be editable after the session");
        controller.testReady();
        GuidedAudioProfile.Setting preview =
                player.settings.get(player.settings.size() - 1);
        require(Math.abs(preview.startHz - 400.0) < 0.001,
                "stationary preview did not use edited pending profile");
        controller.close();
        require(player.closed, "close must close the audio player");
        System.out.println("GuidedAudioCueRegressionTest passed");
    }

    private static void assertCue(RecordingPlayer player, int index,
                                  GuidedAudioCueController.Cue expected,
                                  String message) {
        require(player.cues.size() > index, message + " (missing cue)");
        require(player.cues.get(index) == expected,
                message + ": expected " + expected
                        + " but was " + player.cues.get(index));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingPlayer
            implements GuidedAudioCueController.CuePlayer {
        final List<GuidedAudioCueController.Cue> cues =
                new ArrayList<GuidedAudioCueController.Cue>();
        final List<GuidedAudioProfile.Setting> settings =
                new ArrayList<GuidedAudioProfile.Setting>();
        int cancelCount;
        boolean closed;
        String status = "On — test player";

        @Override
        public void play(GuidedAudioCueController.Cue cue,
                         GuidedAudioProfile.Setting setting,
                         GuidedAudioCueController.AuditSink audit) {
            cues.add(cue);
            settings.add(setting.copy());
            audit.record("QUEUED", cue, setting.summary());
            audit.record("COMPLETED", cue, setting.summary());
            status = "Audio OK — last cue: " + cue.name();
        }

        @Override
        public void cancel(GuidedAudioCueController.AuditSink audit) {
            cancelCount++;
            audit.record("CANCELLED", null, "test cancel");
        }

        @Override
        public void close(GuidedAudioCueController.AuditSink audit) {
            closed = true;
            audit.record("CLOSED", null, "test close");
        }

        @Override
        public void resume(GuidedAudioCueController.AuditSink audit) {
            closed = false;
            audit.record("PLAYER_RESUMED", null, "test resume");
        }

        @Override
        public String statusText() {
            return status;
        }
    }
}
