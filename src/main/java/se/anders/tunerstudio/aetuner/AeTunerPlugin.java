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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Thin TunerStudio host shell for AE Tuner (EPICEFI). */
public final class AeTunerPlugin implements ApplicationPlugin {
    public static final String VERSION = BuildIdentity.VERSION;
    public static final String PUBLIC_REPOSITORY_URL =
            "https://github.com/PJawZK/AE-Tuner-EPICEFI_Public";
    static final String VEHICLE_TEST_BANNER =
            "AE TUNER " + VERSION
                    + " — PUBLIC RELEASE; guarded Apply/Restore only; NO BURN";
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
    /** Legacy fallback only. The default v0.19 host does not insert this tabbed pane. */
    private final JTabbedPane rootTabs = new JTabbedPane();
    private final JScrollPane guidedWorkspaceScroll = new JScrollPane();
    private final GuidedProductionWorkspacePanel guidedProductionWorkspace;
    private final EvidenceDiagnosticsPanel evidenceDiagnostics;
    private final Timer audioStatusTimer;
    private final Component passiveContent;
    private final Component passiveControls;
    private final JPanel passivePopoutHost = new JPanel(new BorderLayout(0, 6));

    private ControllerAccess controllerAccess;
    private GuidedFocusWindow guidedFocusWindow;
    private JDialog passiveAnalysisWindow;
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
        panel.setGuidedSampleDispatcher(guidedPanel.sampleDispatcherForPassivePanel());
        BorderLayout originalLayout = (BorderLayout) panel.getLayout();
        passiveContent = originalLayout.getLayoutComponent(BorderLayout.CENTER);
        passiveControls = originalLayout.getLayoutComponent(BorderLayout.NORTH);
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
                "AE Tuner " + VERSION + " public release. Guided v0.19 covers AE Foundation, TPS AE, MAP Predict, Wall Wetting, Instant Fuel and Decel Detection evidence workflows. Evidence-derived Apply remains explicit, readback-verified and reversible; no Burn exists.");

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
                "Open the legacy modeless Guided Focus pop-out. The v0.19 workspace uses its own task-specific Focus views.");

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
            if (GuidedProductionWorkspacePanel.featureEnabled()) {
                openEvidenceDiagnosticsWindow();
                evidenceDiagnostics.selectAudioCueLab();
            } else {
                rootTabs.setSelectedIndex(3);
                evidenceDiagnostics.selectAudioCueLab();
            }
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

        boolean structural = GuidedProductionWorkspacePanel.featureEnabled();
        if (structural) {
            if (passiveControls != null) panel.remove(passiveControls);
            passivePopoutHost.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            if (passiveControls != null) passivePopoutHost.add(passiveControls, BorderLayout.NORTH);
            passivePopoutHost.add(passiveContent, BorderLayout.CENTER);

            guidedProductionWorkspace = new GuidedProductionWorkspacePanel(
                    guidedWorkspaceScroll, guidedPanel, new GuidedV019UtilityActions() {
                @Override public void openPassiveAnalysis() { openPassiveAnalysisWindow(); }
                @Override public void openEvidenceDiagnostics() { openEvidenceDiagnosticsWindow(); }
            });
            panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            panel.add(guidedProductionWorkspace, BorderLayout.CENTER);
        } else {
            guidedProductionWorkspace = null;
            buildLegacyTabbedHost();
        }

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

    private void buildLegacyTabbedHost() {
        rootTabs.addTab("Overview", buildOverviewPanel());
        rootTabs.addTab("Guided Tuning", guidedWorkspaceScroll);
        rootTabs.addTab("Passive Analysis", passiveContent);
        rootTabs.addTab("Evidence / Diagnostics", evidenceDiagnostics);
        rootTabs.setToolTipTextAt(0,
                "Current AE method states, general workflow order, combination review and safety boundary.");
        rootTabs.setToolTipTextAt(1,
                "Choose one isolated AE method, accumulate evidence, review generated output, and explicitly Apply only exact changed targets declared by the reviewed plan. No automatic final Apply and no burn.");
        rootTabs.setToolTipTextAt(2,
                "Passive AE observation, Passive detector calibration, session evidence and drafts.");
        rootTabs.setToolTipTextAt(3,
                "Runtime/channel diagnostics, Audio Cue Lab, recovery and Apply/Restore audit information.");
        rootTabs.setSelectedIndex(0);

        JPanel tabHost = new JPanel(new BorderLayout(0, 3));
        tabHost.add(buildRecoveryBar(), BorderLayout.NORTH);
        tabHost.add(rootTabs, BorderLayout.CENTER);
        panel.add(tabHost, BorderLayout.CENTER);

        if (passiveControls != null) {
            passiveControls.setVisible(rootTabs.getSelectedIndex() == 2);
            rootTabs.addChangeListener(event -> {
                passiveControls.setVisible(rootTabs.getSelectedIndex() == 2);
                activateSelectedSurface();
            });
        }
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
        updateOverviewText();
        return overview;
    }

    private void openPassiveAnalysisWindow() {
        if (passiveAnalysisWindow == null || !passiveAnalysisWindow.isDisplayable()) {
            passiveAnalysisWindow = createModelessUtilityDialog("AE Tuner — Passive Analysis");
            passiveAnalysisWindow.setContentPane(passivePopoutHost);
            passiveAnalysisWindow.setMinimumSize(new Dimension(900, 600));
            passiveAnalysisWindow.setSize(1180, 760);
        }
        passiveAnalysisWindow.setLocationRelativeTo(SwingUtilities.getWindowAncestor(panel));
        passiveAnalysisWindow.setVisible(true);
        passiveAnalysisWindow.toFront();
    }

    private void openEvidenceDiagnosticsWindow() {
        if (evidenceDiagnosticsWindow == null || !evidenceDiagnosticsWindow.isDisplayable()) {
            evidenceDiagnosticsWindow = createModelessUtilityDialog("AE Tuner — Evidence / Diagnostics");
            evidenceDiagnosticsWindow.setContentPane(evidenceDiagnostics);
            evidenceDiagnosticsWindow.setMinimumSize(new Dimension(880, 580));
            evidenceDiagnosticsWindow.setSize(1120, 720);
            evidenceDiagnosticsWindow.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) {
                    evidenceDiagnostics.disposePanel();
                }
                @Override public void windowClosed(WindowEvent event) {
                    evidenceDiagnostics.disposePanel();
                }
            });
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
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        return dialog;
    }

    private void hideUtilityWindows() {
        if (passiveAnalysisWindow != null) passiveAnalysisWindow.setVisible(false);
        if (evidenceDiagnosticsWindow != null) evidenceDiagnosticsWindow.setVisible(false);
        evidenceDiagnostics.disposePanel();
    }

    private void disposeUtilityWindows() {
        if (passiveAnalysisWindow != null) {
            passiveAnalysisWindow.dispose();
            passiveAnalysisWindow = null;
        }
        if (evidenceDiagnosticsWindow != null) {
            evidenceDiagnosticsWindow.dispose();
            evidenceDiagnosticsWindow = null;
        }
        evidenceDiagnostics.disposePanel();
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
            updateGuidedProductionSnapshot(null);
            updateOverviewText();
            return;
        }
        try {
            overviewSnapshot = new AeControllerBridge(controllerAccess).readSnapshot();
            overviewReadStatus = "Working tune read: " + overviewSnapshot.getConfigurationName();
            updateGuidedProductionSnapshot(overviewSnapshot);
            if (GuidedFocusHub.snapshot().isIdle()) {
                GuidedFocusHub.publishMapEstimateSetup(
                        overviewSnapshot, 20, 115.0,
                        "MAP Estimate Guided Focus is ready. Start MAP Estimate capture to turn accepted stable evidence into live per-cell progress.");
            }
        } catch (ControllerException ex) {
            overviewReadStatus = "Working tune read failed: " + ex.getMessage();
            updateGuidedProductionSnapshot(null);
        }
        updateOverviewText();
    }

    private void updateGuidedProductionSnapshot(AeProjectSnapshot snapshot) {
        if (guidedProductionWorkspace != null) guidedProductionWorkspace.setCurrentTune(snapshot);
    }

    private void updateOverviewText() {
        if (!GuidedProductionWorkspacePanel.featureEnabled() && overviewPlan != null) {
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
                    "CURRENT WORKING TUNE\n====================\n" + overviewReadStatus + "\n"
                            + detector + "\n\n" + methodStatus + "\n\n"
                            + "COMBINATION REVIEW\n==================\n" + combinations + "\n\n"
                            + "GENERAL WORKFLOW\n================\n"
                            + "1. Read and verify the current working tune.\n"
                            + "2. Choose one AE method in Guided Tuning.\n"
                            + "3. Capture observes only. Review remains explicit.\n"
                            + "4. Apply uses only a reviewed ProposalWritePlan through the guarded coordinator.\n"
                            + "5. No Burn exists.\n\n"
                            + "PUBLIC RELEASE STATUS\n=====================\n" + VERSION);
            overviewPlan.setCaretPosition(0);
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
        recoveryStatus.setToolTipText("Recovery directory: "
                + recoveryManager.activeRecoveryDirectory().toAbsolutePath());
        bar.add(recoveryStatus, BorderLayout.CENTER);
        Path previous = recoveryManager.startupRecoveryDirectory();
        if (previous != null) {
            previousRecoveryNotice.setBorder(BorderFactory.createTitledBorder(
                    "Recovered evidence from a previous plugin session"));
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
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return GUIDED_SCROLL_UNIT;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(GUIDED_SCROLL_UNIT, visibleRect.height - GUIDED_SCROLL_UNIT);
        }
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
                + " with method-specific Guided evidence, reviewed exact-target proposals and guarded working-tune Apply/Restore; no burns.";
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
        recoveryManager.resume();
        guidedAudio.resume();
        guidedAudio.setEnabled(soundCues.isSelected());
        panel.connectController(access);
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

    private synchronized void activateSelectedSurface() {
        if (!lifecycleActive || closingLifecycle || presentationSuspended) return;
        if (GuidedProductionWorkspacePanel.featureEnabled()) {
            evidenceDiagnostics.disposePanel();
            ensureGuidedControllerActive();
            return;
        }

        int selected = rootTabs.getSelectedIndex();
        if (selected == 1) {
            evidenceDiagnostics.disposePanel();
            ensureGuidedControllerActive();
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

    private void ensureGuidedControllerActive() {
        if (!guidedControllerPrepared && controllerAccess != null) {
            guidedPanel.connectController(controllerAccess);
            guidedControllerPrepared = true;
        } else if (guidedControllerPrepared) {
            guidedPanel.resumePanel();
        }
    }

    /**
     * Presentation hide is a suspend only. It must never complete/terminate an
     * active capture merely because TunerStudio hides the persistent panel.
     */
    private synchronized void suspendForHide() {
        if (!lifecycleActive || closingLifecycle || presentationSuspended) return;
        presentationSuspended = true;
        guidedAudio.stopNow();
        guidedPanel.suspendPanel();
        hideUtilityWindows();
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
        disposeUtilityWindows();
        audioStatusTimer.stop();
        guidedAudio.close();
        if (guidedFocusWindow != null) {
            guidedFocusWindow.disposeWindow();
            guidedFocusWindow = null;
        }
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
        return view instanceof Scrollable && ((Scrollable)view).getScrollableTracksViewportWidth();
    }
    public boolean guidedWorkspaceTracksViewportHeightForTest() {
        Component view = guidedWorkspaceScroll.getViewport().getView();
        return view instanceof Scrollable && ((Scrollable)view).getScrollableTracksViewportHeight();
    }
    Dimension preferredPanelSizeForTest() { return panel.getPreferredSize(); }
    String getVehicleTestBannerForTest() { return vehicleTestStatus.getText(); }
    boolean lifecycleActiveForTest() { return lifecycleActive; }
    boolean presentationSuspendedForTest() { return presentationSuspended; }
    boolean shownOnceForTest() { return shownOnce; }
    boolean guidedControllerPreparedForTest() { return guidedControllerPrepared; }
    long initializeDurationMillisForTest() { return initializeDurationMillis; }
    int initializeActivationCountForTest() { return initializeActivationCount; }
    boolean structuralWorkspaceDirectHostForTest() {
        return guidedProductionWorkspace != null && guidedProductionWorkspace.getParent() == panel;
    }
    boolean legacyTabsInstalledForTest() { return rootTabs.getParent() != null; }
    boolean passivePopoutAvailableForTest() { return guidedProductionWorkspace != null; }
    boolean diagnosticsPopoutAvailableForTest() { return guidedProductionWorkspace != null; }

    @Override public String getHelpUrl() { return PUBLIC_REPOSITORY_URL; }
    @Override public String getVersion() { return VERSION; }
    @Override public double getRequiredPluginSpec() { return 1.0; }
}
