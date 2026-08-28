package se.anders.tunerstudio.aetuner;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import com.efiAnalytics.plugin.ApplicationPlugin;
import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Thin TunerStudio host shell for AE Tuner (EPICEFI). */
public final class AeTunerPlugin implements ApplicationPlugin {
    public static final String VERSION = "0.4.2-rc.2";
    public static final String PUBLIC_REPOSITORY_URL =
            "https://github.com/PJawZK/AE-Tuner-EPICEFI_Public";
    static final String VEHICLE_TEST_BANNER =
            "AE TUNER " + VERSION
                    + " — RELEASE CANDIDATE; PUBLIC TEST; guarded Apply/Restore only; NO BURN";
    private static final int GUIDED_SCROLL_UNIT = 24;

    private final AeTunerPanel panel = new AeTunerPanel();
    private final GuidedCapturePanel guidedPanel = new GuidedCapturePanel();
    private final GuidedAudioCueController guidedAudio = new GuidedAudioCueController();
    private final GuidedAudioCueLabPanel audioCueLab = new GuidedAudioCueLabPanel(guidedAudio);
    private final GuidedVehicleTestOverridePanel overridePanel = new GuidedVehicleTestOverridePanel();
    private final EvidenceRecoveryManager recoveryManager = new EvidenceRecoveryManager(panel, guidedPanel);
    private final JLabel vehicleTestStatus = new JLabel(VEHICLE_TEST_BANNER);
    private final JLabel recoveryStatus = new JLabel("Automatic local recovery inactive until plugin initialization.");
    private final JPanel previousRecoveryNotice = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
    private final JCheckBox soundCues = new JCheckBox("Enable one-shot sound cues", true);
    private final JButton testSound = new JButton("Test READY");
    private final JButton openAudioLab = new JButton("Audio Cue Lab");
    private final JButton openGuidedFocus = new JButton("Guided Focus");
    private final JLabel soundCueStatus = new JLabel();
    private final JTextArea overviewPlan = new JTextArea();
    private final JTabbedPane rootTabs = new JTabbedPane();
    private final JScrollPane guidedWorkspaceScroll = new JScrollPane();
    private final EvidenceDiagnosticsPanel evidenceDiagnostics;
    private final Timer audioStatusTimer;

    private ControllerAccess controllerAccess;
    private GuidedFocusWindow guidedFocusWindow;
    private volatile AeProjectSnapshot overviewSnapshot;
    private volatile String overviewReadStatus = "Working tune not read yet.";
    private volatile boolean lifecycleActive;
    private volatile boolean closingLifecycle;
    private volatile boolean shownOnce;
    private volatile boolean presentationSuspended = true;
    private volatile boolean guidedControllerPrepared;
    private volatile long initializeDurationMillis = -1L;

    public AeTunerPlugin() {
        panel.setGuidedSampleDispatcher(guidedPanel.sampleDispatcherForPassivePanel());
        BorderLayout originalLayout = (BorderLayout) panel.getLayout();
        Component passiveContent = originalLayout.getLayoutComponent(BorderLayout.CENTER);
        if (passiveContent == null) {
            throw new IllegalStateException("AE Tuner passive center content was not built");
        }
        panel.remove(passiveContent);

        evidenceDiagnostics = new EvidenceDiagnosticsPanel(
                audioCueLab,
                new Supplier<String>() {
                    @Override public String get() { return evidenceOverviewText(); }
                },
                new Supplier<String>() {
                    @Override public String get() { return panel.runtimeDiagnosticsText(); }
                },
                new Supplier<String>() {
                    @Override public String get() { return recoveryAuditText(); }
                });

        vehicleTestStatus.setFont(vehicleTestStatus.getFont().deriveFont(Font.BOLD));
        vehicleTestStatus.setToolTipText(
                "0.4.2-rc.2 release candidate / public test build. Guided TPS Movement / Timing coaches production detected TPS change against AccelThreshold. Delta Window is the physically qualified A/B setting; detector mode, Sample Length and Fast Callback are read-only context. Capture never writes automatically and no burn exists.");

        JPanel soundBar = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 3));
        soundBar.add(soundCues);
        soundBar.add(testSound);
        soundBar.add(openAudioLab);
        soundBar.add(openGuidedFocus);
        soundBar.add(soundCueStatus);
        soundCues.setToolTipText("Default-on one-shot tones. Pause or hiding the plugin cancels current audio.");
        testSound.setToolTipText("Preview the current READY cue while stationary.");
        openAudioLab.setToolTipText("Open Evidence / Diagnostics -> Audio Cue Lab.");
        openGuidedFocus.setToolTipText(
                "Open the modeless Guided Focus pop-out. MAP Estimate has the learned-surface heat map; TPS Movement / Timing coaches detected TPS movement and offers only the secondary Delta Window A/B control.");

        panel.setRecoveryDirtyAction(new Runnable() {
            @Override public void run() { recoveryManager.requestCheckpoint(); }
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
        soundCueStatus.setText(guidedAudio.statusText());
        soundCues.addActionListener(event -> {
            guidedAudio.setEnabled(soundCues.isSelected());
            testSound.setEnabled(soundCues.isSelected());
            soundCueStatus.setText(guidedAudio.statusText());
        });
        testSound.addActionListener(event -> {
            guidedAudio.testReady();
            soundCueStatus.setText(guidedAudio.statusText());
        });
        openAudioLab.addActionListener(event -> {
            rootTabs.setSelectedIndex(3);
            evidenceDiagnostics.selectAudioCueLab();
        });
        openGuidedFocus.addActionListener(event -> openGuidedFocusWindow());
        audioStatusTimer = new Timer(250, event -> {
            soundCueStatus.setText(guidedAudio.statusText());
            recoveryStatus.setText(recoveryManager.statusText());
            refreshGuidedFocusWindow();
        });

        JPanel guidedHeader = new JPanel(new BorderLayout(0, 2));
        guidedHeader.add(vehicleTestStatus, BorderLayout.NORTH);
        guidedHeader.add(soundBar, BorderLayout.CENTER);
        guidedHeader.add(overridePanel, BorderLayout.SOUTH);

        GuidedWorkspacePanel guidedWorkspace = new GuidedWorkspacePanel();
        guidedWorkspace.add(guidedHeader, BorderLayout.NORTH);
        guidedWorkspace.add(guidedPanel, BorderLayout.CENTER);

        guidedWorkspaceScroll.setViewportView(guidedWorkspace);
        guidedWorkspaceScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        guidedWorkspaceScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        guidedWorkspaceScroll.setBorder(null);
        guidedWorkspaceScroll.getVerticalScrollBar().setUnitIncrement(GUIDED_SCROLL_UNIT);
        guidedWorkspaceScroll.getVerticalScrollBar().setBlockIncrement(120);
        guidedWorkspaceScroll.getViewport().setBackground(guidedWorkspace.getBackground());

        rootTabs.addTab("Overview", buildOverviewPanel());
        rootTabs.addTab("Guided Tuning", guidedWorkspaceScroll);
        rootTabs.addTab("Passive Analysis", passiveContent);
        rootTabs.addTab("Evidence / Diagnostics", evidenceDiagnostics);
        rootTabs.setToolTipTextAt(0, "Current AE method states, general workflow order, combination review and safety boundary.");
        rootTabs.setToolTipTextAt(1, "Choose one isolated AE method, accumulate evidence, review generated output, and explicitly Apply only exact changed targets declared by the reviewed plan. No automatic Apply and no burn.");
        rootTabs.setToolTipTextAt(2, "Passive AE observation, Passive detector calibration, session evidence and drafts.");
        rootTabs.setToolTipTextAt(3, "Runtime/channel diagnostics, Audio Cue Lab, recovery and Apply/Restore audit information.");

        rootTabs.setSelectedIndex(0);
        JPanel tabHost = new JPanel(new BorderLayout(0, 3));
        tabHost.add(buildRecoveryBar(), BorderLayout.NORTH);
        tabHost.add(rootTabs, BorderLayout.CENTER);
        panel.add(tabHost, BorderLayout.CENTER);

        Component controls = originalLayout.getLayoutComponent(BorderLayout.NORTH);
        if (controls != null) {
            controls.setVisible(rootTabs.getSelectedIndex() == 2);
            rootTabs.addChangeListener(event -> {
                controls.setVisible(rootTabs.getSelectedIndex() == 2);
                activateSelectedSurface();
            });
        }

        updateOverviewText();
        applyScreenRelativePreferredSize();
        rootTabs.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0L) return;
            if (rootTabs.isShowing()) {
                shownOnce = true;
                applyScreenRelativePreferredSize();
                resumeAfterShow();
            } else if (shownOnce) {
                suspendForHide();
            }
        });
    }

    private JComponent buildOverviewPanel() {
        JPanel overview = new JPanel(new BorderLayout(12, 12));
        overview.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel title = new JLabel("AE Tuner — transient-fuelling plan");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        overview.add(title, BorderLayout.NORTH);
        overviewPlan.setEditable(false);
        overviewPlan.setLineWrap(true);
        overviewPlan.setWrapStyleWord(true);
        overviewPlan.setFont(overviewPlan.getFont().deriveFont(14f));
        overviewPlan.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        overviewPlan.setFocusable(false);
        JScrollPane scroll = new JScrollPane(overviewPlan);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        overview.add(scroll, BorderLayout.CENTER);
        return overview;
    }

    private void openGuidedFocusWindow() {
        if (guidedFocusWindow == null || !guidedFocusWindow.isDisplayable()) {
            guidedFocusWindow = new GuidedFocusWindow(SwingUtilities.getWindowAncestor(panel));
        }
        GuidedFocusHub.snapshot().refresh(guidedFocusWindow);
        guidedFocusWindow.openWindow();
    }

    private void refreshGuidedFocusWindow() {
        if (guidedFocusWindow == null || !guidedFocusWindow.isDisplayable()
                || !guidedFocusWindow.isVisible()) return;
        GuidedFocusHub.snapshot().refresh(guidedFocusWindow);
    }

    private void refreshOverviewSnapshot() {
        if (controllerAccess == null) {
            overviewSnapshot = null;
            overviewReadStatus = "Working tune unavailable: controller not connected.";
            updateOverviewText();
            return;
        }
        try {
            overviewSnapshot = new AeControllerBridge(controllerAccess).readSnapshot();
            overviewReadStatus = "Working tune read: " + overviewSnapshot.getConfigurationName();
            if (GuidedFocusHub.snapshot().isIdle()) {
                GuidedFocusHub.publishMapEstimateSetup(
                        MapEstimateFocusSnapshot.setup(overviewSnapshot, 20, null),
                        "MAP Estimate Guided Focus is ready. Start MAP Estimate capture to turn accepted stable evidence into live per-cell progress.");
            }
        } catch (ControllerException ex) {
            overviewReadStatus = "Working tune read failed: " + ex.getMessage();
        }
        updateOverviewText();
    }

    private void updateOverviewText() {
        AeProjectSnapshot snapshot = overviewSnapshot;
        String methodStatus = snapshot == null
                ? "TPS cycle AE: UNKNOWN\nPredictive MAP / MAP Estimate: UNKNOWN\nWall Wetting: UNKNOWN\nInstant Fuel Pulse: UNKNOWN"
                : snapshot.methodStatusText();
        String combinations = snapshot == null
                ? "Read the working tune before combination review is available."
                : snapshot.combinationStatusText();
        String detector = snapshot == null
                ? "Engagement / Detection settings: UNKNOWN"
                : snapshot.engagementSettingsText();
        overviewPlan.setText(
                "CURRENT WORKING TUNE\n====================\n" + overviewReadStatus + "\n" + detector + "\n\n" + methodStatus + "\n\n"
                + "COMBINATION REVIEW\n==================\n" + combinations + "\n\n"
                + "GENERAL WORKFLOW\n================\n"
                + "1. Read and verify the current working tune.\n"
                + "2. Choose one AE method in Guided Tuning. The selected method states its operator action, required channels, context channels, accumulation rule and review output.\n"
                + "3. Capture never writes. After Finish/Review, any method that has an actual explicit ProposalWritePlan may use the common stale-check -> Apply -> readback -> Restore gateway. No burn exists.\n"
                + "4. TPS Movement / Timing is upstream of the fuel methods. AE Tuner reads detector mode, Delta Window, Sample Length and Fast Callback for context. Delta Window is physically qualified for guarded A/B changes; detector mode, Sample Length and Fast Callback are read-only in this workflow.\n"
                + "5. MAP Estimate keeps accepted stable MAP at its actual TPS/RPM coordinates in compact learned memory and retains its existing guarded table Apply/Restore path.\n"
                + "6. TPS AE groups coherent bursts into complete transient windows and reuses the existing conservative table generator; MAP Predict, Wall Wetting and Instant Fuel retain isolated evidence/review logic while their numerical tuning rules are expanded.\n"
                + "7. After any successful Apply or Restore, Read Working Tune again before another capture so new evidence cannot silently use a pre-write baseline. Apply/Restore itself does not require the engine to be running.\n\n"
                + "RELEASE CANDIDATE STATUS\n==========================\n"
                + "0.4.2-rc.2 is a release candidate for public testing of the general-AE / coaching foundation. Delta Window is already physically qualified for guarded Apply/readback/Restore and remains the explicit TPS Movement / Timing A/B setting. The temporary Engagement Model editing experiment has been removed; Sample Length and Fast Callback are read-only context.\n\n"
                + "Blend Duration numerical conversion remains withheld until its model is validated. Guarded Apply/Restore infrastructure is shared across Guided methods; there is no Burn button or burn API."
        );
        overviewPlan.setCaretPosition(0);
    }

    private String evidenceOverviewText() {
        return "EVIDENCE / DIAGNOSTICS\n======================\n"
                + "Plugin: " + VERSION + "\n"
                + "Lifecycle: " + (lifecycleActive ? "ACTIVE" : "INACTIVE") + "\n"
                + "Presentation: " + (presentationSuspended ? "SUSPENDED" : "ACTIVE") + "\n"
                + "Guided controller prepared: " + (guidedControllerPrepared ? "YES" : "NO") + "\n"
                + "Audio: " + guidedAudio.statusText() + "\n"
                + "Recovery: " + recoveryManager.statusText() + "\n\n"
                + "Use Channels / Runtime for controller and live-channel diagnostics.\n"
                + "Use Audio Cue Lab for stationary cue testing.\n"
                + "Use Recovery / Audit for recovery state and the latest Guided Apply/Restore verification record.";
    }

    private String recoveryAuditText() {
        return "RECOVERY\n========\n" + recoveryManager.statusText() + "\n"
                + "Active recovery folder: " + recoveryManager.activeRecoveryDirectory().toAbsolutePath() + "\n\n"
                + "GUIDED APPLY / RESTORE AUDIT\n============================\n"
                + guidedPanel.applyAuditStatusForDiagnostics();
    }

    private JComponent buildRecoveryBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        recoveryStatus.setToolTipText("Recovery directory: " + recoveryManager.activeRecoveryDirectory().toAbsolutePath());
        bar.add(recoveryStatus, BorderLayout.CENTER);
        Path previous = recoveryManager.startupRecoveryDirectory();
        if (previous != null) {
            previousRecoveryNotice.setBorder(BorderFactory.createTitledBorder("Recovered evidence from a previous plugin session"));
            JLabel label = new JLabel("A previous plugin session left recoverable local evidence.");
            label.setToolTipText(previous.toAbsolutePath().toString());
            JButton open = new JButton("Open recovery folder");
            JButton dismiss = new JButton("Dismiss notice");
            open.addActionListener(event -> openRecoveryDirectory(previous));
            dismiss.addActionListener(event -> {
                recoveryManager.dismissStartupRecovery();
                previousRecoveryNotice.setVisible(false);
            });
            previousRecoveryNotice.add(label);
            previousRecoveryNotice.add(open);
            previousRecoveryNotice.add(dismiss);
            bar.add(previousRecoveryNotice, BorderLayout.SOUTH);
        } else {
            previousRecoveryNotice.setVisible(false);
        }
        return bar;
    }

    private void openRecoveryDirectory(Path directory) {
        if (directory == null) return;
        if (!Desktop.isDesktopSupported()) {
            recoveryStatus.setText("Recovery folder: " + directory.toAbsolutePath());
            return;
        }
        try {
            Desktop.getDesktop().open(directory.toFile());
        } catch (IOException | UnsupportedOperationException ex) {
            recoveryStatus.setText("Could not open recovery folder; path: " + directory.toAbsolutePath());
        }
    }

    private static final class GuidedWorkspacePanel extends JPanel implements Scrollable {
        GuidedWorkspacePanel() { super(new BorderLayout(0, 4)); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return GUIDED_SCROLL_UNIT; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(GUIDED_SCROLL_UNIT, visibleRect.height - GUIDED_SCROLL_UNIT); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
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
        int widthCap = Math.max(1, (int) Math.floor(usable.width * 0.86));
        int heightCap = Math.max(1, (int) Math.floor(usable.height * 0.84));
        int desiredWidth = Math.max(900, naturalWidth);
        int desiredHeight = Math.max(620, naturalHeight);
        return new Dimension(Math.min(desiredWidth, widthCap), Math.min(desiredHeight, heightCap));
    }

    @Override public String getIdName() { return "aeTunerEpicefi"; }
    @Override public int getPluginType() { return PERSISTENT_DIALOG_PANEL; }
    @Override public String getDisplayName() { return "AE Tuner (EPICEFI)"; }
    @Override public String getDescription() {
        return "EPICEFI transient-fueling tuner " + VERSION
                + " with method-specific Guided evidence, reviewed exact-target proposals and guarded working-tune Apply/Restore; no burns.";
    }

    @Override
    public synchronized void initialize(ControllerAccess access) {
        long started = System.nanoTime();
        controllerAccess = access;
        closingLifecycle = false;
        presentationSuspended = true;
        guidedControllerPrepared = false;
        recoveryManager.resume();
        guidedAudio.resume();
        guidedAudio.setEnabled(soundCues.isSelected());
        panel.connectController(access);
        refreshOverviewSnapshot();
        lifecycleActive = true;
        if (rootTabs.isShowing()) {
            shownOnce = true;
            resumeAfterShow();
        }
        initializeDurationMillis = Math.max(0L, (System.nanoTime() - started) / 1000000L);
        System.err.println("AE Tuner " + VERSION + " initialized in " + initializeDurationMillis + " ms");
    }

    private synchronized void activateSelectedSurface() {
        if (!lifecycleActive || closingLifecycle || presentationSuspended) return;
        int selected = rootTabs.getSelectedIndex();
        if (selected == 1) {
            evidenceDiagnostics.disposePanel();
            if (!guidedControllerPrepared && controllerAccess != null) {
                guidedPanel.connectController(controllerAccess);
                guidedControllerPrepared = true;
            } else if (guidedControllerPrepared) {
                guidedPanel.resumePanel();
            }
        } else {
            guidedPanel.suspendPanel();
            if (selected == 0) {
                refreshOverviewSnapshot();
                evidenceDiagnostics.disposePanel();
            } else if (selected == 3) {
                refreshOverviewSnapshot();
                evidenceDiagnostics.resumePanel();
            } else {
                evidenceDiagnostics.disposePanel();
            }
        }
    }

    private synchronized void suspendForHide() {
        if (!lifecycleActive || closingLifecycle || presentationSuspended) return;
        presentationSuspended = true;
        guidedAudio.stopNow();
        guidedPanel.suspendPanel();
        guidedPanel.terminateForClose();
        evidenceDiagnostics.disposePanel();
        audioStatusTimer.stop();
    }

    private synchronized void resumeAfterShow() {
        if (!lifecycleActive || closingLifecycle || !presentationSuspended) return;
        guidedAudio.resume();
        guidedAudio.setEnabled(soundCues.isSelected());
        presentationSuspended = false;
        audioStatusTimer.start();
        activateSelectedSurface();
    }

    private synchronized boolean beginFinalClose() {
        if (!lifecycleActive || closingLifecycle) return false;
        closingLifecycle = true;
        lifecycleActive = false;
        presentationSuspended = true;
        guidedAudio.stopNow();
        guidedPanel.suspendPanel();
        panel.disconnectController();
        guidedPanel.terminateForClose();
        evidenceDiagnostics.disposePanel();
        audioStatusTimer.stop();
        guidedAudio.close();
        if (guidedFocusWindow != null) {
            guidedFocusWindow.disposeWindow();
            guidedFocusWindow = null;
        }
        GuidedFocusHub.clear();
        return true;
    }

    private void closeLifecycle() {
        boolean closing = beginFinalClose();
        if (!closing) return;
        try {
            recoveryManager.flushAndClose();
        } finally {
            guidedPanel.releaseAfterClose();
            controllerAccess = null;
            overviewSnapshot = null;
            guidedControllerPrepared = false;
            synchronized (this) { closingLifecycle = false; }
        }
    }

    @Override public boolean displayPlugin(String controllerSignature) { return true; }
    @Override public boolean isMenuEnabled() { return true; }
    @Override public String getAuthor() { return "Anders Wedin"; }
    @Override public JComponent getPluginPanel() { return panel; }
    @Override public void close() { closeLifecycle(); }
    boolean areGuidedSoundCuesEnabledForTest() { return guidedAudio.isEnabled(); }
    boolean isGuidedSoundCheckboxSelectedForTest() { return soundCues.isSelected(); }
    String guidedAudioStatusForTest() { return guidedAudio.statusText(); }
    int audioCueLabRowCountForTest() { return audioCueLab.cueRowCountForTest(); }
    int evidenceDiagnosticsTabCountForTest() { return evidenceDiagnostics.tabCountForTest(); }
    String evidenceDiagnosticsTabTitleForTest(int index) { return evidenceDiagnostics.tabTitleForTest(index); }
    boolean areVehicleTestOverridesEnabledForTest() { return overridePanel.isEnabledForTest(); }
    public int guidedWorkspaceVerticalScrollPolicyForTest() { return guidedWorkspaceScroll.getVerticalScrollBarPolicy(); }
    public int guidedWorkspaceHorizontalScrollPolicyForTest() { return guidedWorkspaceScroll.getHorizontalScrollBarPolicy(); }
    public int guidedWorkspaceScrollUnitForTest() { return guidedWorkspaceScroll.getVerticalScrollBar().getUnitIncrement(); }
    public boolean guidedWorkspaceTracksViewportWidthForTest() {
        Component view = guidedWorkspaceScroll.getViewport().getView();
        return view instanceof Scrollable && ((Scrollable) view).getScrollableTracksViewportWidth();
    }
    public boolean guidedWorkspaceTracksViewportHeightForTest() {
        Component view = guidedWorkspaceScroll.getViewport().getView();
        return view instanceof Scrollable && ((Scrollable) view).getScrollableTracksViewportHeight();
    }
    Dimension preferredPanelSizeForTest() { return panel.getPreferredSize(); }
    String getVehicleTestBannerForTest() { return vehicleTestStatus.getText(); }
    boolean lifecycleActiveForTest() { return lifecycleActive; }
    boolean presentationSuspendedForTest() { return presentationSuspended; }
    boolean shownOnceForTest() { return shownOnce; }
    boolean guidedControllerPreparedForTest() { return guidedControllerPrepared; }
    long initializeDurationMillisForTest() { return initializeDurationMillis; }

    @Override public String getHelpUrl() { return PUBLIC_REPOSITORY_URL; }
    @Override public String getVersion() { return VERSION; }
    @Override public double getRequiredPluginSpec() { return 1.0; }
}
