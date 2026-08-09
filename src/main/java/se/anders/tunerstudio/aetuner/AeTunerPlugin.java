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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
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

/**
 * Thin TunerStudio host shell for AE Tuner (EPICEFI).
 *
 * Host lifecycle, tab activation and Swing visibility are deliberately
 * separate. TunerStudio may attach a persistent-dialog component to a hidden
 * hierarchy before first display; that transition must never destroy the
 * controller subscription or start heavy Guided processing prematurely.
 */
public final class AeTunerPlugin implements ApplicationPlugin {
    public static final String VERSION = "0.4.0";
    static final String VEHICLE_TEST_BANNER =
            "AE TUNER " + VERSION
                    + " — read-only; physically validated architecture baseline";
    private static final int GUIDED_SCROLL_UNIT = 24;

    private final AeTunerPanel panel = new AeTunerPanel();
    private final GuidedCapturePanel guidedPanel = new GuidedCapturePanel();
    private final GuidedAudioCueController guidedAudio =
            new GuidedAudioCueController();
    private final GuidedAudioCueLabPanel audioCueLab =
            new GuidedAudioCueLabPanel(guidedAudio);
    private final GuidedVehicleTestOverridePanel overridePanel =
            new GuidedVehicleTestOverridePanel();
    private final EvidenceRecoveryManager recoveryManager =
            new EvidenceRecoveryManager(panel, guidedPanel);
    private final JLabel vehicleTestStatus = new JLabel(VEHICLE_TEST_BANNER);
    private final JLabel recoveryStatus = new JLabel(
            "Automatic local recovery inactive until plugin initialization.");
    private final JPanel previousRecoveryNotice = new JPanel(
            new FlowLayout(FlowLayout.LEFT, 8, 3));
    private final JCheckBox soundCues =
            new JCheckBox("Enable one-shot sound cues", true);
    private final JButton testSound = new JButton("Test READY");
    private final JButton openAudioLab = new JButton("Audio Cue Lab");
    private final JLabel soundCueStatus = new JLabel();
    private final JTabbedPane rootTabs = new JTabbedPane();
    private final JScrollPane guidedWorkspaceScroll = new JScrollPane();
    private final Timer audioStatusTimer;

    private ControllerAccess controllerAccess;
    private volatile boolean lifecycleActive;
    private volatile boolean closingLifecycle;
    private volatile boolean shownOnce;
    private volatile boolean presentationSuspended = true;
    private volatile boolean guidedControllerPrepared;
    private volatile long initializeDurationMillis = -1L;

    public AeTunerPlugin() {
        panel.setGuidedSampleDispatcher(
                guidedPanel.sampleDispatcherForPassivePanel());
        /*
         * Keep AeTunerPanel as the host-facing root and preserve its original
         * NORTH control column as panel component 0. Only the original CENTER
         * content moves into the passive tab.
         */
        BorderLayout originalLayout = (BorderLayout) panel.getLayout();
        Component passiveContent = originalLayout.getLayoutComponent(BorderLayout.CENTER);
        if (passiveContent == null) {
            throw new IllegalStateException("AE Tuner passive center content was not built");
        }
        panel.remove(passiveContent);

        vehicleTestStatus.setFont(vehicleTestStatus.getFont().deriveFont(Font.BOLD));
        vehicleTestStatus.setToolTipText(
                "Accepted 0.4.0 baseline. Host lifecycle, Guided target semantics, "
                        + "measurement anchoring and road workflow were physically validated. "
                        + "All tuning output remains read-only advisory guidance.");

        JPanel soundBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        soundBar.add(soundCues);
        soundBar.add(testSound);
        soundBar.add(openAudioLab);
        soundBar.add(soundCueStatus);
        soundCues.setToolTipText(
                "Default-on one-shot tones. Pause or hiding the plugin cancels current audio.");
        testSound.setToolTipText(
                "Preview the current READY cue while stationary.");
        openAudioLab.setToolTipText(
                "Open the stationary Audio Cue Lab and workflow demonstrations.");

        panel.setRecoveryDirtyAction(new Runnable() {
            @Override
            public void run() { recoveryManager.requestCheckpoint(); }
        });
        guidedPanel.setRecoveryDirtyAction(new Runnable() {
            @Override
            public void run() { recoveryManager.requestCheckpoint(); }
        });
        guidedPanel.setWorkflowEventListener(guidedAudio);
        guidedPanel.setAudioAuditSuppliers(new Supplier<String>() {
            @Override
            public String get() { return guidedAudio.auditText(); }
        }, new Supplier<String>() {
            @Override
            public String get() { return guidedAudio.auditCsv(); }
        });
        guidedPanel.setPauseAudioAction(new Runnable() {
            @Override
            public void run() { guidedAudio.pauseNow(); }
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
        openAudioLab.addActionListener(event -> rootTabs.setSelectedIndex(2));
        audioStatusTimer = new Timer(250, event -> {
            soundCueStatus.setText(guidedAudio.statusText());
            recoveryStatus.setText(recoveryManager.statusText());
        });

        JPanel guidedHeader = new JPanel(new BorderLayout(0, 2));
        guidedHeader.add(vehicleTestStatus, BorderLayout.NORTH);
        guidedHeader.add(soundBar, BorderLayout.CENTER);
        guidedHeader.add(overridePanel, BorderLayout.SOUTH);

        GuidedWorkspacePanel guidedWorkspace = new GuidedWorkspacePanel();
        guidedWorkspace.add(guidedHeader, BorderLayout.NORTH);
        guidedWorkspace.add(guidedPanel, BorderLayout.CENTER);

        guidedWorkspaceScroll.setViewportView(guidedWorkspace);
        guidedWorkspaceScroll.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        guidedWorkspaceScroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        guidedWorkspaceScroll.setBorder(null);
        guidedWorkspaceScroll.getVerticalScrollBar().setUnitIncrement(
                GUIDED_SCROLL_UNIT);
        guidedWorkspaceScroll.getVerticalScrollBar().setBlockIncrement(120);
        guidedWorkspaceScroll.getViewport().setBackground(guidedWorkspace.getBackground());

        rootTabs.addTab("Guided Capture", guidedWorkspaceScroll);
        rootTabs.addTab("Passive capture / reports", passiveContent);
        rootTabs.addTab("Audio Cue Lab", audioCueLab);
        rootTabs.setToolTipTextAt(0,
                "Prominent, read-only guided collection for controlled Blend Duration evidence.");
        rootTabs.setToolTipTextAt(1,
                "Existing passive AE Tuner event capture, reports, exports, and diagnostics.");
        rootTabs.setToolTipTextAt(2,
                "Stationary test-only generated-tone editor and workflow demonstration.");

        // Preserve the established passive workspace on first open. Guided is
        // intentionally inert until its tab is actually selected.
        rootTabs.setSelectedIndex(1);
        JPanel tabHost = new JPanel(new BorderLayout(0, 3));
        tabHost.add(buildRecoveryBar(), BorderLayout.NORTH);
        tabHost.add(rootTabs, BorderLayout.CENTER);
        panel.add(tabHost, BorderLayout.CENTER);

        Component controls = originalLayout.getLayoutComponent(BorderLayout.NORTH);
        if (controls != null) {
            controls.setVisible(rootTabs.getSelectedIndex() == 1);
            rootTabs.addChangeListener(event -> {
                controls.setVisible(rootTabs.getSelectedIndex() == 1);
                activateSelectedSurface();
            });
        }

        applyScreenRelativePreferredSize();

        /*
         * Critical runtime rule: only SHOWING_CHANGED is a visibility event.
         * Parent/displayability/layout hierarchy events are normal during host
         * attachment and MUST NOT close or disconnect the plugin.
         *
         * A pre-first-show false transition is ignored. After first display,
         * hiding suspends only reversible presentation/Guided resources. The
         * TunerStudio controller subscription is owned by initialize()/close().
         */
        rootTabs.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0L) {
                return;
            }
            if (rootTabs.isShowing()) {
                shownOnce = true;
                applyScreenRelativePreferredSize();
                resumeAfterShow();
            } else if (shownOnce) {
                suspendForHide();
            }
        });
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
            JLabel label = new JLabel(
                    "A previous plugin session left recoverable local evidence.");
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
            recoveryStatus.setText("Could not open recovery folder; path: "
                    + directory.toAbsolutePath());
        }
    }

    /** Complete Guided page tracks viewport width but keeps natural height. */
    private static final class GuidedWorkspacePanel extends JPanel
            implements Scrollable {
        GuidedWorkspacePanel() {
            super(new BorderLayout(0, 4));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect,
                                              int orientation, int direction) {
            return GUIDED_SCROLL_UNIT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect,
                                               int orientation, int direction) {
            return Math.max(GUIDED_SCROLL_UNIT,
                    visibleRect.height - GUIDED_SCROLL_UNIT);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private void applyScreenRelativePreferredSize() {
        Rectangle usable = usableScreenBounds();
        if (usable == null || usable.width <= 0 || usable.height <= 0) return;
        panel.setPreferredSize(screenRelativePreferredSize(
                panel.getPreferredSize(), usable));
    }

    private Rectangle usableScreenBounds() {
        try {
            GraphicsConfiguration configuration = panel.getGraphicsConfiguration();
            if (configuration != null) return configuration.getBounds();
            return GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds();
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
        return new Dimension(Math.min(desiredWidth, widthCap),
                Math.min(desiredHeight, heightCap));
    }

    @Override
    public String getIdName() {
        return "aeTunerEpicefi";
    }

    @Override
    public int getPluginType() {
        return PERSISTENT_DIALOG_PANEL;
    }

    @Override
    public String getDisplayName() {
        return "AE Tuner (EPICEFI)";
    }

    @Override
    public String getDescription() {
        return "Read-only EPICEFI transient-fueling tuner " + VERSION
                + " with physically validated host lifecycle and Guided evidence.";
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

        // Passive capture owns the one TunerStudio output-channel subscription.
        // Guided project-axis reading and dispatcher activation are deferred
        // until the Guided tab is selected.
        panel.connectController(access);

        lifecycleActive = true;
        if (rootTabs.isShowing()) {
            shownOnce = true;
            resumeAfterShow();
        }
        initializeDurationMillis = Math.max(0L,
                (System.nanoTime() - started) / 1000000L);
        System.err.println("AE Tuner " + VERSION + " initialized in "
                + initializeDurationMillis + " ms");
    }

    /** Activate only the surface the operator is currently using. */
    private synchronized void activateSelectedSurface() {
        if (!lifecycleActive || closingLifecycle || presentationSuspended) return;
        int selected = rootTabs.getSelectedIndex();
        if (selected == 0) {
            audioCueLab.disposePanel();
            if (!guidedControllerPrepared && controllerAccess != null) {
                guidedPanel.connectController(controllerAccess);
                guidedControllerPrepared = true;
            } else if (guidedControllerPrepared) {
                guidedPanel.resumePanel();
            }
        } else {
            guidedPanel.suspendPanel();
            if (selected == 2) {
                audioCueLab.resumePanel();
            } else {
                audioCueLab.disposePanel();
            }
        }
    }

    /**
     * Reversible post-first-show suspension. No controller unsubscribe, no
     * recovery shutdown, and no permanent audio close occurs here.
     */
    private synchronized void suspendForHide() {
        if (!lifecycleActive || closingLifecycle || presentationSuspended) return;
        presentationSuspended = true;
        guidedAudio.stopNow();
        guidedPanel.suspendPanel();
        // End an active Guided attempt/session rather than silently resuming it
        // after a period where samples were intentionally not consumed.
        guidedPanel.terminateForClose();
        audioCueLab.disposePanel();
        audioStatusTimer.stop();
    }

    /** Resume only resources suspended by a real post-first-show hide. */
    private synchronized void resumeAfterShow() {
        if (!lifecycleActive || closingLifecycle || !presentationSuspended) return;
        guidedAudio.resume();
        guidedAudio.setEnabled(soundCues.isSelected());
        presentationSuspended = false;
        audioStatusTimer.start();
        activateSelectedSurface();
    }

    /** Full destructive shutdown is reserved for the host close callback. */
    private synchronized boolean beginFinalClose() {
        if (!lifecycleActive || closingLifecycle) return false;
        closingLifecycle = true;
        lifecycleActive = false;
        presentationSuspended = true;

        guidedAudio.stopNow();
        guidedPanel.suspendPanel();
        panel.disconnectController();
        guidedPanel.terminateForClose();
        audioCueLab.disposePanel();
        audioStatusTimer.stop();
        guidedAudio.close();
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
            guidedControllerPrepared = false;
            synchronized (this) {
                closingLifecycle = false;
            }
        }
    }

    @Override
    public boolean displayPlugin(String controllerSignature) {
        return true;
    }

    @Override
    public boolean isMenuEnabled() {
        return true;
    }

    @Override
    public String getAuthor() {
        return "Anders Wedin";
    }

    @Override
    public JComponent getPluginPanel() {
        return panel;
    }

    @Override
    public void close() {
        closeLifecycle();
    }

    boolean areGuidedSoundCuesEnabledForTest() {
        return guidedAudio.isEnabled();
    }

    boolean isGuidedSoundCheckboxSelectedForTest() {
        return soundCues.isSelected();
    }

    String guidedAudioStatusForTest() {
        return guidedAudio.statusText();
    }

    int audioCueLabRowCountForTest() {
        return audioCueLab.cueRowCountForTest();
    }

    boolean areVehicleTestOverridesEnabledForTest() {
        return overridePanel.isEnabledForTest();
    }

    public int guidedWorkspaceVerticalScrollPolicyForTest() {
        return guidedWorkspaceScroll.getVerticalScrollBarPolicy();
    }

    public int guidedWorkspaceHorizontalScrollPolicyForTest() {
        return guidedWorkspaceScroll.getHorizontalScrollBarPolicy();
    }

    public int guidedWorkspaceScrollUnitForTest() {
        return guidedWorkspaceScroll.getVerticalScrollBar().getUnitIncrement();
    }

    public boolean guidedWorkspaceTracksViewportWidthForTest() {
        Component view = guidedWorkspaceScroll.getViewport().getView();
        return view instanceof Scrollable
                && ((Scrollable) view).getScrollableTracksViewportWidth();
    }

    public boolean guidedWorkspaceTracksViewportHeightForTest() {
        Component view = guidedWorkspaceScroll.getViewport().getView();
        return view instanceof Scrollable
                && ((Scrollable) view).getScrollableTracksViewportHeight();
    }

    Dimension preferredPanelSizeForTest() {
        return panel.getPreferredSize();
    }

    String getVehicleTestBannerForTest() {
        return vehicleTestStatus.getText();
    }

    boolean lifecycleActiveForTest() {
        return lifecycleActive;
    }

    boolean presentationSuspendedForTest() {
        return presentationSuspended;
    }

    boolean shownOnceForTest() {
        return shownOnce;
    }

    boolean guidedControllerPreparedForTest() {
        return guidedControllerPrepared;
    }

    long initializeDurationMillisForTest() {
        return initializeDurationMillis;
    }

    @Override
    public String getHelpUrl() {
        return "";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public double getRequiredPluginSpec() {
        return 1.0;
    }
}