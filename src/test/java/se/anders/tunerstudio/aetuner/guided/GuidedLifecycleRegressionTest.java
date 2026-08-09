package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GuidedLifecycleRegressionTest {
    private GuidedLifecycleRegressionTest() { }

    public static void main(String[] args) throws Exception {
        guidedPanelIsInertUntilResumedAndFullyDisconnects();
        audioIgnoresWorkflowAfterCloseAndCanReopenCleanly();
        recoveryWorkerStopsAndCanReopenCleanly();
        System.out.println("GuidedLifecycleRegressionTest passed");
    }

    private static void guidedPanelIsInertUntilResumedAndFullyDisconnects() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        GuidedSampleDispatcher dispatcher = panel.sampleDispatcherForPassivePanel();
        require(!panel.isSampleDispatcherActiveForTest(),
                "Guided panel must be inert before lifecycle resume");
        require(!dispatcher.diagnostics().accepting,
                "Guided worker accepted samples before lifecycle resume");

        panel.resumePanel();
        require(panel.isSampleDispatcherActiveForTest(),
                "Lifecycle resume did not activate Guided dispatcher");
        require(dispatcher.diagnostics().accepting,
                "Guided dispatcher did not enter accepting state");

        panel.suspendPanel();
        require(!panel.isSampleDispatcherActiveForTest(),
                "Lifecycle suspend left Guided dispatcher active");
        require(!dispatcher.diagnostics().accepting
                        && dispatcher.diagnostics().queueDepth == 0,
                "Lifecycle suspend did not stop and clear Guided dispatcher");

        panel.resumePanel();
        require(panel.isSampleDispatcherActiveForTest(),
                "Guided dispatcher did not reopen cleanly after suspension");
        panel.disposePanel();
        require(dispatcher.diagnostics().closed,
                "Guided dispose did not close its instance-owned worker");
    }

    private static void audioIgnoresWorkflowAfterCloseAndCanReopenCleanly() {
        RecordingPlayer player = new RecordingPlayer();
        GuidedAudioCueController controller =
                new GuidedAudioCueController(player);
        controller.setEnabled(true);
        controller.onGuidedWorkflowEvent(GuidedWorkflowEvent.READY_ENTERED,
                "ready", 1L);
        require(player.cues.size() == 1,
                "pre-close audio workflow did not reach player");

        controller.close();
        int afterClose = player.cues.size();
        controller.onGuidedWorkflowEvent(GuidedWorkflowEvent.EVENT_EXCLUDED,
                "must stay silent", 2L);
        require(player.cues.size() == afterClose,
                "closed audio controller still processed workflow events");
        require(player.closed,
                "audio close did not terminate the player lifecycle");

        controller.resume();
        controller.setEnabled(true);
        controller.onGuidedWorkflowEvent(GuidedWorkflowEvent.READY_ENTERED,
                "reopened", 3L);
        require(player.resumeCount == 1,
                "audio reopen did not recreate/resume player lifecycle");
        require(player.cues.size() == afterClose + 1,
                "audio did not function after clean reopen");
        controller.close();
    }

    private static void recoveryWorkerStopsAndCanReopenCleanly() throws Exception {
        Path root = Files.createTempDirectory("ae-tuner-lifecycle-recovery");
        AeTunerPanel passive = new AeTunerPanel();
        GuidedCapturePanel guided = new GuidedCapturePanel();
        EvidenceRecoveryManager manager =
                new EvidenceRecoveryManager(passive, guided, root);
        require(!manager.isRunningForTest(),
                "recovery worker must not start in constructor");
        manager.resume();
        require(manager.isRunningForTest(),
                "recovery worker did not start with active lifecycle");
        manager.flushAndClose();
        require(!manager.isRunningForTest(),
                "recovery worker remained alive after close");
        manager.resume();
        require(manager.isRunningForTest(),
                "recovery worker did not recreate after reopen");
        manager.flushAndClose();
        guided.disposePanel();
        passive.disposePanel();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingPlayer
            implements GuidedAudioCueController.CuePlayer {
        final List<GuidedAudioCueController.Cue> cues =
                new ArrayList<GuidedAudioCueController.Cue>();
        boolean closed;
        int resumeCount;

        @Override
        public void play(GuidedAudioCueController.Cue cue,
                         GuidedAudioProfile.Setting setting,
                         GuidedAudioCueController.AuditSink audit) {
            cues.add(cue);
            audit.record("COMPLETED", cue, "test");
        }

        @Override
        public void cancel(GuidedAudioCueController.AuditSink audit) {
            audit.record("CANCELLED", null, "test");
        }

        @Override
        public void close(GuidedAudioCueController.AuditSink audit) {
            closed = true;
            audit.record("CLOSED", null, "test");
        }

        @Override
        public void resume(GuidedAudioCueController.AuditSink audit) {
            closed = false;
            resumeCount++;
            audit.record("PLAYER_RESUMED", null, "test");
        }

        @Override
        public String statusText() {
            return closed ? "closed" : "open";
        }
    }
}
