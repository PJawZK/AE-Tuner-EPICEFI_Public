package se.anders.tunerstudio.aetuner;

import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.host.AeControllerBridge;
import se.anders.tunerstudio.aetuner.host.BuildIdentity;
import se.anders.tunerstudio.aetuner.guided.GuidedWorkingTuneSurfaceCache;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.recovery.EvidenceRecoveryManager;
import se.anders.tunerstudio.aetuner.ui.EvidenceDiagnosticsPanel;

import com.efiAnalytics.plugin.ApplicationPlugin;
import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Supplier;

/**
 * Thin TunerStudio host for the current Guided AE Tuner system.
 *
 * The retired Passive analysis runtime is intentionally not constructed here.
 * TunerStudio live callbacks are owned only by GuidedLiveSampleSource and feed
 * the existing bounded GuidedSampleDispatcher. The guarded production write
 * path remains owned by GuidedCapturePanel; this shell adds no writer and no Burn.
 */
public final class AeTunerPlugin implements ApplicationPlugin {
    public static final String VERSION = BuildIdentity.VERSION;
    public static final String PUBLIC_REPOSITORY_URL =
            "https://github.com/PJawZK/AE-Tuner-EPICEFI_Public";
    static final String VEHICLE_TEST_BANNER =
            "AE TUNER " + VERSION
                    + " — PUBLIC RELEASE; guarded Apply/Restore only; NO BURN";

    private final JPanel panel = new JPanel(new BorderLayout());
    private final GuidedCapturePanel guidedPanel = new GuidedCapturePanel();
    private final GuidedLiveSampleSource liveSource =
            new GuidedLiveSampleSource(guidedPanel.guidedSampleDispatcher());
    private final GuidedAudioCueController guidedAudio = new GuidedAudioCueController();
    private final GuidedAudioCueLabPanel audioCueLab = new GuidedAudioCueLabPanel(guidedAudio);
    private final EvidenceRecoveryManager recoveryManager = new EvidenceRecoveryManager(guidedPanel);
    private final GuidedProductionWorkspacePanel guidedProductionWorkspace;
    private final EvidenceDiagnosticsPanel evidenceDiagnostics;
    private final Timer runtimeTimer;

    private ControllerAccess controllerAccess;
    private JDialog evidenceDiagnosticsWindow;
    private volatile AeProjectSnapshot overviewSnapshot;
    private volatile String overviewReadStatus = "Working tune not read yet.";
    private volatile boolean lifecycleActive;
    private volatile boolean closingLifecycle;
    private volatile boolean shownOnce;
    private volatile boolean presentationSuspended = true;
    private volatile boolean guidedControllerPrepared;
    private volatile long initializeDurationMillis = -1L;
    private volatile int initializeActivationCount;

    public AeTunerPlugin() {
        evidenceDiagnostics = new EvidenceDiagnosticsPanel(
                audioCueLab,
                new Supplier<String>() {
                    @Override public String get() { return evidenceOverviewText(); }
                },
                new Supplier<String>() {
                    @Override public String get() {
                        return liveSource.runtimeDiagnosticsText() + "\n\n"
                                + RuntimePerformanceHub.reportText();
                    }
                },
                new Supplier<String>() {
                    @Override public String get() { return recoveryAuditText(); }
                });

        guidedPanel.setRecoveryDirtyAction(new Runnable() {
            @Override public void run() { recoveryManager.requestCheckpoint(); }
        });
        guidedPanel.setWorkflowEventListener(guidedAudio);
        guidedPanel.setAudioAuditSuppliers(new Supplier<String>() {
            @Override public String get() { return guidedAudio.auditText(); }
        }, new Supplier<String>() {
            @Override public String get() { return guidedAudio.auditCsv(); }
        });
        guidedPanel.setPauseAudioAction(new Runnable() {
            @Override public void run() { guidedAudio.pauseNow(); }
        });

        guidedAudio.setEnabled(true);

        guidedProductionWorkspace = new GuidedProductionWorkspacePanel(
                guidedPanel, new GuidedV019UtilityActions() {
            @Override public void openEvidenceDiagnostics() { openEvidenceDiagnosticsWindow(); }
        });
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        panel.add(guidedProductionWorkspace, BorderLayout.CENTER);

        runtimeTimer = new Timer(250, event -> {
            RuntimePerformanceHub.edtHeartbeat();
            RuntimePerformanceHub.noteDispatcher(guidedPanel.guidedSampleDispatcher().runtimeStats());
            RuntimePerformanceHub.noteLiveSource(liveSource.runtimeStats());
        });

        applyScreenRelativePreferredSize();
        panel.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0L) return;
            if (panel.isShowing()) {
                shownOnce = true;
                applyScreenRelativePreferredSize();
                resumeAfterShow();
            } else if (shownOnce) {
                suspendForHide();
            }
        });
    }

    private void openEvidenceDiagnosticsWindow() {
        if (evidenceDiagnosticsWindow == null || !evidenceDiagnosticsWindow.isDisplayable()) {
            evidenceDiagnosticsWindow = createModelessUtilityDialog(
                    "AE Tuner — Evidence / Diagnostics");
            evidenceDiagnosticsWindow.setContentPane(evidenceDiagnostics);
            evidenceDiagnosticsWindow.setMinimumSize(new Dimension(880, 580));
            evidenceDiagnosticsWindow.setSize(1120, 720);
            evidenceDiagnosticsWindow.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) {
                    evidenceDiagnostics.disposePanel();
                    RuntimePerformanceHub.noteDialogDisposed();
                    evidenceDiagnosticsWindow = null;
                }
            });
            RuntimePerformanceHub.noteDialogCreated();
        }
        refreshOverviewSnapshot();
        evidenceDiagnostics.resumePanel();
        evidenceDiagnosticsWindow.setLocationRelativeTo(SwingUtilities.getWindowAncestor(panel));
        evidenceDiagnosticsWindow.setVisible(true);
        evidenceDiagnosticsWindow.toFront();
    }

    private JDialog createModelessUtilityDialog(String title) {
        Window host = SwingUtilities.getWindowAncestor(panel);
        JDialog dialog;
        if (host instanceof Dialog) dialog = new JDialog((Dialog)host, title, false);
        else if (host instanceof Frame) dialog = new JDialog((Frame)host, title, false);
        else dialog = new JDialog((Frame)null, title, false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        return dialog;
    }

    private void disposeUtilityWindows() {
        if (evidenceDiagnosticsWindow != null) {
            JDialog dialog = evidenceDiagnosticsWindow;
            evidenceDiagnosticsWindow = null;
            dialog.dispose();
        }
        evidenceDiagnostics.disposePanel();
    }

    private void refreshOverviewSnapshot() {
        if (controllerAccess == null) {
            overviewSnapshot = null;
            overviewReadStatus = "Working tune unavailable: controller not connected.";
            guidedProductionWorkspace.setCurrentTune(null);
            return;
        }
        try {
            overviewSnapshot = new AeControllerBridge(controllerAccess).readSnapshot();
            overviewReadStatus = "Working tune read: " + overviewSnapshot.getConfigurationName();
            guidedProductionWorkspace.setCurrentTune(overviewSnapshot);
            if (GuidedFocusHub.snapshot().isIdle()) {
                GuidedFocusHub.publishMapEstimateSetup(
                        overviewSnapshot, 20, 115.0,
                        "MAP Estimate Guided Focus is ready. Start MAP Estimate capture to turn accepted stable evidence into live per-cell progress.");
            }
        } catch (ControllerException ex) {
            overviewReadStatus = "Working tune read failed: " + ex.getMessage();
            guidedProductionWorkspace.setCurrentTune(null);
        }
    }

    private String evidenceOverviewText() {
        return "EVIDENCE / DIAGNOSTICS\n======================\n"
                + "Plugin: " + VERSION + "\n"
                + "Working tune: " + overviewReadStatus + "\n"
                + "Lifecycle: " + (lifecycleActive ? "ACTIVE" : "INACTIVE") + "\n"
                + "Presentation: " + (presentationSuspended ? "SUSPENDED" : "ACTIVE") + "\n"
                + "Guided controller prepared: " + (guidedControllerPrepared ? "YES" : "NO") + "\n"
                + "Audio: " + guidedAudio.statusText() + "\n"
                + "Recovery: " + recoveryManager.statusText() + "\n\n"
                + "Passive Analysis has been retired. Live channel ownership, capture, evidence, diagnostics and recovery now belong to the current Guided system only.";
    }

    private String recoveryAuditText() {
        return "RECOVERY\n========\n" + recoveryManager.statusText() + "\n"
                + "Active recovery folder: "
                + recoveryManager.activeRecoveryDirectory().toAbsolutePath() + "\n\n"
                + "GUIDED APPLY / RESTORE AUDIT\n============================\n"
                + guidedPanel.applyAuditStatusForDiagnostics() + "\n\n"
                + RuntimePerformanceHub.reportText();
    }

    private void applyScreenRelativePreferredSize() {
        Rectangle usable = usableScreenBounds();
        if (usable == null || usable.width <= 0 || usable.height <= 0) return;
        panel.setPreferredSize(screenRelativePreferredSize(panel.getPreferredSize(), usable));
    }

    private Rectangle usableScreenBounds() {
        try {
            GraphicsConfiguration configuration = panel.getGraphicsConfiguration();
            if (configuration != null) return configuration.getBounds();
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        } catch (HeadlessException ex) {
            return null;
        }
    }

    public static Dimension screenRelativePreferredSize(Dimension natural, Rectangle usable) {
        int naturalWidth = natural == null ? 1000 : Math.max(1, natural.width);
        int naturalHeight = natural == null ? 700 : Math.max(1, natural.height);
        int widthCap = Math.max(1, (int)Math.floor(usable.width * 0.86));
        int heightCap = Math.max(1, (int)Math.floor(usable.height * 0.84));
        int desiredWidth = Math.max(900, naturalWidth);
        int desiredHeight = Math.max(620, naturalHeight);
        return new Dimension(Math.min(desiredWidth, widthCap), Math.min(desiredHeight, heightCap));
    }

    @Override public String getIdName() { return "aeTunerEpicefi"; }
    @Override public int getPluginType() { return PERSISTENT_DIALOG_PANEL; }
    @Override public String getDisplayName() { return "AE Tuner (EPICEFI)"; }
    @Override public String getDescription() {
        return "EPICEFI transient-fueling tuner " + VERSION
                + " with Guided evidence, reviewed exact-target proposals and guarded working-tune Apply/Restore; no burns.";
    }

    @Override
    public synchronized void initialize(ControllerAccess access) {
        if (closingLifecycle) return;
        if (lifecycleActive && controllerAccess == access) {
            if (panel.isShowing()) {
                shownOnce = true;
                resumeAfterShow();
            }
            return;
        }
        long started = System.nanoTime();
        controllerAccess = access;
        closingLifecycle = false;
        presentationSuspended = true;
        guidedControllerPrepared = false;
        refreshOverviewSnapshot();
        lifecycleActive = true;
        initializeActivationCount++;
        if (panel.isShowing()) {
            shownOnce = true;
            resumeAfterShow();
        }
        initializeDurationMillis = Math.max(0L,
                (System.nanoTime() - started) / 1000000L);
        System.err.println("AE Tuner " + VERSION + " initialized in "
                + initializeDurationMillis + " ms");
    }

    private synchronized void ensureGuidedControllerActive() {
        if (!guidedControllerPrepared && controllerAccess != null) {
            guidedPanel.connectController(controllerAccess);
            guidedControllerPrepared = true;
        } else if (guidedControllerPrepared) {
            guidedPanel.resumePanel();
        }
    }

    /** Hiding the plugin suspends every AE Tuner live/background resource. */
    private synchronized void suspendForHide() {
        if (!lifecycleActive || closingLifecycle || presentationSuspended) return;
        presentationSuspended = true;
        guidedAudio.stopNow();
        guidedPanel.suspendPanel();
        liveSource.disconnectController();
        guidedProductionWorkspace.disposeOwnedDialogs();
        disposeUtilityWindows();
        runtimeTimer.stop();
        recoveryManager.flushAndClose();
    }

    private synchronized void resumeAfterShow() {
        if (!lifecycleActive || closingLifecycle || !presentationSuspended) return;
        recoveryManager.resume();
        ensureGuidedControllerActive();
        if (controllerAccess != null) liveSource.connectController(controllerAccess);
        guidedAudio.resume();
        guidedAudio.setEnabled(true);
        presentationSuspended = false;
        runtimeTimer.start();
    }

    private synchronized boolean beginFinalClose() {
        if (!lifecycleActive || closingLifecycle) return false;
        closingLifecycle = true;
        lifecycleActive = false;
        presentationSuspended = true;
        guidedAudio.stopNow();
        guidedPanel.suspendPanel();
        liveSource.disconnectController();
        guidedPanel.terminateForClose();
        guidedProductionWorkspace.disposeOwnedDialogs();
        disposeUtilityWindows();
        runtimeTimer.stop();
        guidedAudio.close();
        GuidedFocusHub.dispose();
        return true;
    }

    private void closeLifecycle() {
        boolean closing = beginFinalClose();
        if (!closing) return;
        try {
            recoveryManager.flushAndClose();
        } finally {
            guidedPanel.releaseAfterClose();
            GuidedWorkingTuneSurfaceCache.clear();
            AeControllerBridge.clearLatestControllerAccess();
            controllerAccess = null;
            overviewSnapshot = null;
            guidedControllerPrepared = false;
            RuntimePerformanceHub.resetForPluginRetirement();
            synchronized (this) { closingLifecycle = false; }
        }
    }

    @Override public boolean displayPlugin(String controllerSignature) { return true; }
    @Override public boolean isMenuEnabled() { return true; }
    @Override public String getAuthor() { return "Anders Wedin"; }
    @Override public JComponent getPluginPanel() { return panel; }
    @Override public void close() { closeLifecycle(); }
    @Override public String getHelpUrl() { return PUBLIC_REPOSITORY_URL; }
    @Override public String getVersion() { return VERSION; }
    @Override public double getRequiredPluginSpec() { return 1.0; }

    boolean areGuidedSoundCuesEnabledForTest() { return guidedAudio.isEnabled(); }
    String guidedAudioStatusForTest() { return guidedAudio.statusText(); }
    int audioCueLabRowCountForTest() { return audioCueLab.cueRowCountForTest(); }
    int evidenceDiagnosticsTabCountForTest() { return evidenceDiagnostics.tabCountForTest(); }
    String evidenceDiagnosticsTabTitleForTest(int index) { return evidenceDiagnostics.tabTitleForTest(index); }
    Dimension preferredPanelSizeForTest() { return panel.getPreferredSize(); }
    boolean lifecycleActiveForTest() { return lifecycleActive; }
    boolean presentationSuspendedForTest() { return presentationSuspended; }
    boolean shownOnceForTest() { return shownOnce; }
    boolean guidedControllerPreparedForTest() { return guidedControllerPrepared; }
    long initializeDurationMillisForTest() { return initializeDurationMillis; }
    int initializeActivationCountForTest() { return initializeActivationCount; }
    boolean structuralWorkspaceDirectHostForTest() {
        return guidedProductionWorkspace != null && guidedProductionWorkspace.getParent() == panel;
    }
    boolean legacyTabsInstalledForTest() { return false; }
    boolean passivePopoutAvailableForTest() { return false; }
    boolean diagnosticsPopoutAvailableForTest() { return true; }
}
