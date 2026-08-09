package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

public final class GuidedAudioCueLabRegressionTest {
    public static void main(String[] args) {
        GuidedAudioCueController controller =
                new GuidedAudioCueController(new SilentPlayer());
        controller.setEnabled(true);
        GuidedAudioCueLabPanel lab = new GuidedAudioCueLabPanel(controller);
        try {
            require(lab.cueRowCountForTest()
                            == GuidedAudioCueController.Cue.values().length,
                    "Audio Cue Lab must expose every assignable event");
            require(lab.triggerDescriptionForTest(
                            GuidedAudioCueController.Cue.TARGET_ACQUIRED)
                            .contains("natural pedal plateau"),
                    "cue demo must explain when adaptive pedal-hold acquisition triggers");
            require(controller.pendingSetting(
                            GuidedAudioCueController.Cue.EXCLUDED)
                            .estimatedDurationMs() >= 300,
                    "excluded cue should be long enough for road-noise testing");
        } finally {
            lab.disposePanel();
            controller.close();
        }
        System.out.println("GuidedAudioCueLabRegressionTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class SilentPlayer
            implements GuidedAudioCueController.CuePlayer {
        @Override
        public void play(GuidedAudioCueController.Cue cue,
                         GuidedAudioProfile.Setting setting,
                         GuidedAudioCueController.AuditSink audit) {
            audit.record("COMPLETED", cue, "silent test backend");
        }

        @Override
        public void cancel(GuidedAudioCueController.AuditSink audit) { }

        @Override
        public void close(GuidedAudioCueController.AuditSink audit) { }

        @Override
        public void resume(GuidedAudioCueController.AuditSink audit) { }

        @Override
        public String statusText() {
            return "silent test backend";
        }
    }
}
