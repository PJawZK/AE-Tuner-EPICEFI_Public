package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.util.EnumMap;
import java.util.Locale;

/** Faithful structural port of the validated v0.19 MainPrototypePanel. */
final class GuidedV019WorkspacePanel extends JPanel {
    enum Stage {
        PREPARE("Prepare", "Check readiness"),
        CAPTURE("Capture", "Start session"),
        GUIDED_FOCUS("Guided Focus", "Gather evidence"),
        REVIEW("Review", "See result"),
        APPLY("Apply", "Guarded write"),
        RESULT("Result", "Keep or restore");

        final String title;
        final String subtitle;
        Stage(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private static final Font TITLE = new Font("Dialog", Font.BOLD, 22);
    private static final Font H2 = new Font("Dialog", Font.BOLD, 16);
    private static final Font BODY = new Font("Dialog", Font.PLAIN, 12);
    private static final Font SMALL = new Font("Dialog", Font.PLAIN, 10);
    private static final String FULL_BADGE = "PRODUCTION — GUARDED APPLY/RESTORE • NO BURN";
    private static final String COMPACT_BADGE = "PRODUCTION • NO BURN";

    private final GuidedCapturePanel production;
    private final GuidedV019ProductionBridge bridge;
    private final GuidedV019UtilityActions utilities;
    private final GuidedV019BlendBinSelectorPanel blendBinSelector;
    private final WorkflowStepper stepper = new WorkflowStepper();
    private final TaskSelectorPanel taskSelector;
    private final EnumMap<Stage, JPanel> stageCards = new EnumMap<Stage, JPanel>(Stage.class);
    private final EnumMap<Stage, JButton> stageInfoButtons = new EnumMap<Stage, JButton>(Stage.class);
    private final JLabel taskTitle = new JLabel();
    private final JLabel taskDescription = new JLabel();
    private final JLabel nextStep = new JLabel();
    private final JLabel sessionState = new JLabel();
    private final JLabel productionBadge = new JLabel(FULL_BADGE);
    private final SummaryGrid prepareSummary = new SummaryGrid(4);
    private final SummaryGrid captureSummary = new SummaryGrid(4);
    private final SummaryGrid focusSummary = new SummaryGrid(4);
    private final SummaryGrid reviewSummary = new SummaryGrid(4);
    private final SummaryGrid applySummary = new SummaryGrid(4);
    private final SummaryGrid resultSummary = new SummaryGrid(4);
    private final JButton prepare = primaryButton("Continue");
    private final JButton capture = primaryButton("Start Capture");
    private final JButton focus = primaryButton("Open Guided Focus");
    private final JButton review = primaryButton("Review Results");
    private final JButton apply = primaryButton("Apply Proposal");
    private final JButton result = primaryButton("Open Result");
    private final JButton mapValidation = secondaryButton("Validate MAP Predict system");
    private final JButton exportSession = secondaryButton("Export Current Session…");
    private final JButton diagnostics = secondaryButton("Evidence / Diagnostics…");
    private final JToggleButton lightTheme;
    private final JToggleButton darkTheme;
    private final Timer refreshTimer;

    private volatile AeProjectSnapshot currentTune;
    private GuidedProductionTask task = GuidedProductionTask.TPS_MOVEMENT_TIMING;
    private Stage stage = Stage.PREPARE;
    private boolean mapValidationActive;
    private boolean lastApplyWasPerformed;

    GuidedV019WorkspacePanel(GuidedCapturePanel production) {
        this(production, GuidedV019UtilityActions.NONE);
    }

    GuidedV019WorkspacePanel(GuidedCapturePanel production, GuidedV019UtilityActions utilities) {
        super(new BorderLayout(10, 0));
        if (production == null) throw new IllegalArgumentException("production");
        this.production = production;
        this.bridge = new GuidedV019ProductionBridge(production);
        this.utilities = utilities == null ? GuidedV019UtilityActions.NONE : utilities;
        this.blendBinSelector = new GuidedV019BlendBinSelectorPanel(
                new GuidedV019BlendBinSelectorPanel.Listener() {
                    @Override public void selectionChanged(double[] selectedBins) {
                        bridge.setBlendArmedRpmBins(selectedBins);
                        refreshSummaries();
                        refreshButtons();
                    }
                });
        this.lightTheme = themeChoice("Light Side", AeUiTheme.Side.LIGHT_SIDE);
        this.darkTheme = themeChoice("Dark Side", AeUiTheme.Side.DARK_SIDE);

        setBorder(new EmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(1240, 660));
        taskSelector = new TaskSelectorPanel(new TaskSelectionListener() {
            @Override public void taskSelected(GuidedProductionTask selected) {
                selectTask(selected);
            }
        });
        add(taskSelector, BorderLayout.WEST);
        add(workspace(), BorderLayout.CENTER);
        wireActions();
        refreshAll();

        refreshTimer = new Timer(200, new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { refreshLive(); }
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0L) return;
            if (isShowing()) {
                refreshAll();
                refreshTimer.start();
            } else {
                refreshTimer.stop();
            }
        });
    }

    void setCurrentTune(AeProjectSnapshot snapshot) {
        currentTune = snapshot;
        if (SwingUtilities.isEventDispatchThread()) refreshAll();
        else SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { refreshAll(); }
        });
    }

    private JComponent workspace() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        root.add(header(), BorderLayout.NORTH);
        root.add(cards(), BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent header() {
        JPanel wrap = new JPanel(new BorderLayout(10, 6)) {
            @Override public void doLayout() {
                int width = getWidth();
                if (width < 560) {
                    productionBadge.setVisible(false);
                    taskTitle.setFont(TITLE.deriveFont(18f));
                } else {
                    productionBadge.setVisible(true);
                    productionBadge.setText(width < 820 ? COMPACT_BADGE : FULL_BADGE);
                    taskTitle.setFont(TITLE.deriveFont(width < 760 ? 20f : 22f));
                }
                super.doLayout();
            }
        };
        wrap.setOpaque(false);

        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        taskTitle.setFont(TITLE);
        taskDescription.setFont(BODY);
        title.add(taskTitle, BorderLayout.NORTH);
        title.add(taskDescription, BorderLayout.SOUTH);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        right.setOpaque(false);
        productionBadge.setOpaque(true);
        productionBadge.setFont(new Font("Dialog", Font.BOLD, 10));
        productionBadge.setBorder(new EmptyBorder(5, 8, 5, 8));
        ButtonGroup themes = new ButtonGroup();
        themes.add(lightTheme);
        themes.add(darkTheme);
        right.add(productionBadge);
        right.add(lightTheme);
        right.add(darkTheme);

        wrap.add(title, BorderLayout.CENTER);
        wrap.add(right, BorderLayout.EAST);
        wrap.add(stepper, BorderLayout.SOUTH);
        return wrap;
    }

    private JToggleButton themeChoice(String text, final AeUiTheme.Side side) {
        final JToggleButton button = new JToggleButton(text, AeUiTheme.side() == side);
        button.setFont(new Font("Dialog", Font.BOLD, 10));
        button.setFocusPainted(false);
        button.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!button.isSelected() || AeUiTheme.side() == side) return;
                AeUiTheme.set(side);
                refreshAll();
            }
        });
        return button;
    }

    private JComponent cards() {
        JPanel grid = new JPanel(new GridLayout(2, 3, 8, 8)) {
            @Override public void doLayout() {
                GridLayout layout = (GridLayout)getLayout();
                boolean narrow = getWidth() < 720;
                int rows = narrow ? 3 : 2;
                int columns = narrow ? 2 : 3;
                if (layout.getRows() != rows || layout.getColumns() != columns) {
                    layout.setRows(rows);
                    layout.setColumns(columns);
                }
                super.doLayout();
            }
        };
        grid.setOpaque(false);
        grid.add(stepCard(Stage.PREPARE,
                "Verify task authority, channels and calibration context.", prepareSummary, prepare));
        grid.add(stepCard(Stage.CAPTURE,
                "Create one evidence session with explicit provenance.", captureContent(), capture));
        grid.add(stepCard(Stage.GUIDED_FOCUS,
                "Driver-first view backed by the same session/evidence model.", focusSummary, focus));
        grid.add(stepCard(Stage.REVIEW,
                "Task-specific evidence, finding and exact write/no-write decision.", reviewSummary, review));
        grid.add(stepCard(Stage.APPLY,
                "Exact reviewed plan, stale-baseline preflight, write/readback/restore.", applySummary, apply));
        grid.add(stepCard(Stage.RESULT,
                "Finish a no-write review, or verify an applied proposal then keep/restore.", resultSummary, result));
        return grid;
    }

    private JComponent captureContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        panel.add(captureSummary, BorderLayout.CENTER);
        panel.add(blendBinSelector, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel stepCard(final Stage cardStage,
                            String subtitle,
                            JComponent content,
                            JButton primary) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel head = new JPanel(new BorderLayout(5, 0));
        head.setOpaque(false);
        JLabel title = new JLabel((cardStage.ordinal() + 1) + ". " + cardStage.title);
        title.setFont(H2);
        JButton info = secondaryButton(infoButtonText(cardStage));
        stageInfoButtons.put(cardStage, info);
        info.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { showStepInfo(cardStage); }
        });
        head.add(title, BorderLayout.CENTER);
        head.add(info, BorderLayout.EAST);

        JLabel sub = new JLabel(subtitle);
        sub.setFont(SMALL);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(sub, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        panel.add(primary, BorderLayout.SOUTH);
        stageCards.put(cardStage, panel);
        return panel;
    }

    private static String infoButtonText(Stage stage) {
        switch (stage) {
            case PREPARE: return "Setup / Info…";
            case CAPTURE: return "Capture Info…";
            case GUIDED_FOCUS: return "Focus Info…";
            case REVIEW: return "Review Info…";
            case APPLY: return "Apply Info…";
            default: return "Result Info…";
        }
    }

    private JComponent footer() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        JLabel icon = new JLabel("●");
        icon.setFont(H2);
        nextStep.setFont(BODY);
        sessionState.setFont(SMALL);
        panel.add(icon, BorderLayout.WEST);
        panel.add(nextStep, BorderLayout.CENTER);
        panel.add(sessionState, BorderLayout.EAST);
        panel.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),
                new EmptyBorder(7, 10, 7, 10)));
        return panel;
    }

    private void wireActions() {
        prepare.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { continueFromPrepare(); }
        });
        capture.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                bridge.startCapture();
                GuidedCaptureState state = bridge.captureState(task);
                if (state != GuidedCaptureState.IDLE) stage = Stage.GUIDED_FOCUS;
                refreshAll();
            }
        });
        focus.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { openFocus(); }
        });
        review.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { openReview(); }
        });
        apply.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { openApply(); }
        });
        result.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { openResult(); }
        });
        mapValidation.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { toggleMapValidation(); }
        });
        exportSession.setToolTipText("Export the currently collected Guided report/CSV snapshot. Exporting does not unlock Review or Apply.");
        exportSession.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { bridge.exportCurrentSession(); }
        });
        diagnostics.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { utilities.openEvidenceDiagnostics(); }
        });
    }

    private void continueFromPrepare() {
        if (currentTune == null) {
            AeProjectSnapshot read = bridge.readWorkingTune();
            if (read != null) {
                currentTune = read;
                bridge.select(task);
            }
        }
        GuidedTaskAvailability availability = GuidedTaskAvailabilityAdapter.evaluate(task, currentTune);
        if (currentTune == null || !availability.clickable) {
            refreshAll();
            return;
        }
        bridge.select(task);
        stage = Stage.CAPTURE;
        refreshAll();
        capture.requestFocusInWindow();
    }

    private void selectTask(GuidedProductionTask selected) {
        GuidedTaskAvailability availability = GuidedTaskAvailabilityAdapter.evaluate(selected, currentTune);
        if (!availability.clickable || selected.productionRecipe == null) return;
        mapValidationActive = false;
        task = selected;
        stage = Stage.PREPARE;
        lastApplyWasPerformed = false;
        bridge.select(selected);
        refreshAll();
    }

    private void openFocus() {
        bridge.activateFocusControl();
        final Window host = SwingUtilities.getWindowAncestor(this);
        final JDialog dialog = createDialog(host, task.displayName + " — Guided Focus");
        dialog.setContentPane(GuidedV019FocusViews.create(task, dialog, new Runnable() {
            @Override public void run() {
                dialog.dispose();
                GuidedCaptureState captureState = bridge.captureState(task);
                if (bridge.evidenceReady(task)) stage = Stage.REVIEW;
                else if (captureState == GuidedCaptureState.COMPLETE) stage = Stage.CAPTURE;
                refreshAll();
            }
        }));
        dialog.setMinimumSize(new Dimension(950, 560));
        dialog.setSize(1260, 680);
        dialog.setLocationRelativeTo(host);
        dialog.setVisible(true);
    }

    private void openReview() {
        final Window host = SwingUtilities.getWindowAncestor(this);
        final JDialog dialog = createDialog(host, task.displayName + " — Review Results");
        dialog.setContentPane(GuidedV019ReviewViews.create(task, currentTune, bridge,
                new GuidedV019ReviewViews.Listener() {
                    @Override public void action(GuidedV019ReviewViews.Action action) {
                        if (action == GuidedV019ReviewViews.Action.APPLY) {
                            dialog.dispose();
                            stage = Stage.APPLY;
                        } else if (action == GuidedV019ReviewViews.Action.FINISH) {
                            dialog.dispose();
                            lastApplyWasPerformed = false;
                            stage = Stage.RESULT;
                        } else if (action == GuidedV019ReviewViews.Action.RECAPTURE) {
                            dialog.dispose();
                            stage = Stage.CAPTURE;
                        } else if (action == GuidedV019ReviewViews.Action.CLOSE) {
                            dialog.dispose();
                        }
                        refreshAll();
                    }
                }));
        dialog.setMinimumSize(new Dimension(1000, 600));
        dialog.setSize(1260, 700);
        dialog.setLocationRelativeTo(host);
        dialog.setVisible(true);
    }

    private void openApply() {
        final Window host = SwingUtilities.getWindowAncestor(this);
        final JDialog dialog = createDialog(host, task.displayName + " — Guided Apply");
        dialog.setContentPane(GuidedV019ApplyResultViews.createApply(task, currentTune, bridge,
                new GuidedV019ApplyResultViews.ApplyListener() {
                    @Override public void action(GuidedV019ApplyResultViews.ApplyAction action) {
                        if (action == GuidedV019ApplyResultViews.ApplyAction.BACK) {
                            dialog.dispose();
                            stage = Stage.REVIEW;
                        } else if (action == GuidedV019ApplyResultViews.ApplyAction.PERFORM) {
                            if (bridge.applyEnabled()) {
                                bridge.applyReviewedProposal();
                                lastApplyWasPerformed = bridge.restoreEnabled();
                                stage = lastApplyWasPerformed ? Stage.RESULT : Stage.REVIEW;
                            }
                            dialog.dispose();
                        } else if (action == GuidedV019ApplyResultViews.ApplyAction.CLOSE) {
                            dialog.dispose();
                        }
                        refreshAll();
                    }
                }));
        dialog.setMinimumSize(new Dimension(1000, 600));
        dialog.setSize(1260, 700);
        dialog.setLocationRelativeTo(host);
        dialog.setVisible(true);
    }

    private void openResult() {
        final Window host = SwingUtilities.getWindowAncestor(this);
        final JDialog dialog = createDialog(host, task.displayName + " — Result");
        dialog.setContentPane(GuidedV019ApplyResultViews.createResult(task, currentTune, bridge,
                lastApplyWasPerformed, new GuidedV019ApplyResultViews.ResultListener() {
                    @Override public void action(GuidedV019ApplyResultViews.ResultAction action) {
                        if (action == GuidedV019ApplyResultViews.ResultAction.RESTORE) {
                            if (bridge.restoreEnabled()) bridge.restorePreviousApply();
                            lastApplyWasPerformed = false;
                            dialog.dispose();
                            stage = Stage.PREPARE;
                        } else if (action == GuidedV019ApplyResultViews.ResultAction.KEEP) {
                            if (lastApplyWasPerformed) bridge.acceptValidationKeep();
                            bridge.resetSession();
                            lastApplyWasPerformed = false;
                            dialog.dispose();
                            stage = Stage.PREPARE;
                        } else if (action == GuidedV019ApplyResultViews.ResultAction.CLOSE) {
                            dialog.dispose();
                        }
                        refreshAll();
                    }
                }));
        dialog.setMinimumSize(new Dimension(980, 590));
        dialog.setSize(1180, 680);
        dialog.setLocationRelativeTo(host);
        dialog.setVisible(true);
    }

    private void showStepInfo(Stage selectedStage) {
        Window host = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = createDialog(host, task.displayName + " — " + selectedStage.title);
        dialog.setContentPane(GuidedV019TaskInfoViews.create(task, selectedStage,
                currentTune, bridge));
        dialog.setMinimumSize(new Dimension(760, 500));
        dialog.setSize(900, 590);
        dialog.setLocationRelativeTo(host);
        dialog.setVisible(true);
    }

    private void toggleMapValidation() {
        if (currentTune == null || !currentTune.isMapEstimateEnabled()
                || task.group != GuidedProductionTask.Group.MAP_PREDICT) return;
        mapValidationActive = !mapValidationActive;
        if (mapValidationActive) production.selectTuningTaskForTest(GuidedTuningRecipe.MAP_PREDICT);
        else bridge.select(task);
        refreshAll();
    }

    private void refreshLive() {
        refreshSummaries();
        refreshButtons();
        stepper.setStage(stage.ordinal(),
                stage == Stage.RESULT && !lastApplyWasPerformed && !bridge.applyEnabled());
        sessionState.setText(trim(bridge.connectionText(), 92));
        applyStagePresentation();
        repaint();
    }

    private void refreshAll() {
        taskTitle.setText(task.displayName);
        taskDescription.setText(task.group.displayName + " — " + task.description);
        taskSelector.setCurrentTune(currentTune);
        taskSelector.setSelectedTask(task);
        applyTheme();
        blendBinSelector.applyTheme();
        refreshSummaries();
        refreshButtons();
        stepper.setStage(stage.ordinal(),
                stage == Stage.RESULT && !lastApplyWasPerformed && !bridge.applyEnabled());
        sessionState.setText(trim(bridge.connectionText(), 92));
        applyStagePresentation();
        revalidate();
        repaint();
    }

    private void refreshSummaries() {
        GuidedTaskAvailability availability = GuidedTaskAvailabilityAdapter.evaluate(task, currentTune);
        GuidedCaptureState captureState = bridge.captureState(task);
        refreshBlendBinSelector(captureState);
        GuidedFocusHub.State focusState = bridge.focusState(task);
        boolean ready = bridge.evidenceReady(task);
        prepareSummary.set(new String[][]{
                {"Working Tune", currentTune == null ? "not read" : "loaded"},
                {"Availability", availability.status},
                {"Controller state", GuidedTaskAvailabilityAdapter.groupState(task.group, currentTune)},
                {"Safety", "capture writes NONE"}
        });
        captureSummary.set(new String[][]{
                {"Session", String.valueOf(captureState)},
                {"Primary action", bridge.startText()},
                {"Evidence", evidenceSummary(focusState)},
                {task == GuidedProductionTask.BLEND_DURATION ? "Armed RPM bins" : "Provenance",
                 task == GuidedProductionTask.BLEND_DURATION ? blendBinSelector.summaryText()
                    : currentTune == null ? "waiting" : currentTune.getConfigurationName()}
        });
        focusSummary.set(new String[][]{
                {"Driver state", focusStatus(focusState)},
                {"Live", liveSummary(focusState)},
                {"Progress", progressSummary(focusState)},
                {"Detail", "task-specific Focus + Diagnostics"}
        });
        reviewSummary.set(new String[][]{
                {"Evidence", ready ? "ready" : captureState == GuidedCaptureState.COMPLETE ? "incomplete" : String.valueOf(captureState)},
                {"Decision", bridge.applyEnabled() ? "Proposal ready" : reviewLabel(captureState)},
                {"Write", bridge.applyEnabled() ? "real plan available" : "none"},
                {"Authority", "production model"}
        });
        applySummary.set(new String[][]{
                {"Reviewed plan", bridge.applyEnabled() ? "READY" : "not available"},
                {"Stale baseline", "coordinator preflight"},
                {"Readback", "exact targets required"},
                {"Burn", "NEVER"}
        });
        resultSummary.set(new String[][]{
                {"Apply", lastApplyWasPerformed ? "performed / verify" : "none"},
                {"Restore", bridge.restoreEnabled() ? "AVAILABLE" : "not required"},
                {"Evidence", ready ? "retained" : captureState == GuidedCaptureState.COMPLETE ? "incomplete" : "session active"},
                {"Decision", lastApplyWasPerformed ? "KEEP / RESTORE" : ready ? "finish no-write result" : "continue capture"}
        });
    }

    /**
     * Compute the desired active-button state first, then apply it once. The old
     * disable-all/re-enable-active sequence could fire between mouse-down and
     * mouse-up every 200 ms and cancel an otherwise valid Swing button click.
     */
    private void refreshButtons() {
        JButton[] all = {prepare, capture, focus, review, apply, result};
        for (JButton button : all) resetButton(button);

        JButton active;
        String text;
        boolean enabled;
        switch (stage) {
            case PREPARE:
                active = prepare;
                if (currentTune == null) {
                    text = "Read Working Tune";
                    enabled = bridge.readEnabled();
                } else {
                    text = "Continue";
                    enabled = GuidedTaskAvailabilityAdapter.evaluate(task, currentTune).clickable;
                }
                break;
            case CAPTURE:
                active = capture;
                text = bridge.captureState(task) == GuidedCaptureState.COMPLETE
                        && !bridge.evidenceReady(task) ? "Continue Capture" : bridge.startText();
                enabled = bridge.startEnabled()
                        && (task != GuidedProductionTask.BLEND_DURATION
                            || blendBinSelector.hasValidSelection());
                if (task == GuidedProductionTask.BLEND_DURATION
                        && !blendBinSelector.hasValidSelection()) {
                    text = "Select 1–4 RPM Bins";
                }
                break;
            case GUIDED_FOCUS:
                active = focus;
                text = "Open Guided Focus";
                enabled = true;
                break;
            case REVIEW:
                active = review;
                text = "Review Results";
                enabled = bridge.evidenceReady(task);
                break;
            case APPLY:
                active = apply;
                enabled = bridge.applyEnabled();
                text = enabled ? "Apply Proposal" : "No Proposal to Apply";
                break;
            default:
                active = result;
                text = "Open Result";
                enabled = bridge.evidenceReady(task) || lastApplyWasPerformed;
                break;
        }

        for (JButton button : all) {
            boolean shouldEnable = button == active && enabled;
            if (button.isEnabled() != shouldEnable) button.setEnabled(shouldEnable);
        }
        if (!text.equals(active.getText())) active.setText(text);
        highlight(active);
        nextStep.setText("Next: " + active.getText());
        mapValidation.setVisible(task.group == GuidedProductionTask.Group.MAP_PREDICT
                && currentTune != null && currentTune.isMapEstimateEnabled());
        mapValidation.setText(mapValidationActive
                ? "Return to " + task.displayName : "Validate MAP Predict system");
        exportSession.setEnabled(bridge.currentSessionExportAvailable());
    }

    private void refreshBlendBinSelector(GuidedCaptureState captureState) {
        boolean blend = task == GuidedProductionTask.BLEND_DURATION;
        blendBinSelector.setVisible(blend);
        if (!blend) return;
        double[] available = currentTune == null
                ? new double[0] : currentTune.getBlendDurationRpmBins();
        blendBinSelector.setAvailableBins(available, bridge.blendArmedRpmBins());
        boolean active = captureState != GuidedCaptureState.IDLE
                && captureState != GuidedCaptureState.COMPLETE;
        blendBinSelector.setLocked(active);
    }

    private boolean evidenceReady(GuidedCaptureState state) {
        return bridge.evidenceReady(task);
    }

    private String reviewLabel(GuidedCaptureState state) {
        if (state == GuidedCaptureState.COMPLETE && !bridge.evidenceReady(task))
            return "Capture ended — more evidence needed";
        if (task == GuidedProductionTask.BLEND_DURATION && evidenceReady(state))
            return "Evidence accepted — proposal withheld";
        return evidenceReady(state) ? "No write proposal" : "More evidence needed";
    }

    private String evidenceSummary(GuidedFocusHub.State state) {
        if (state == null) return "waiting";
        if (state.engagement != null)
            return state.engagement.activityEvents + " activity / " + state.engagement.observedSamples + " samples";
        if (state.foundationThreshold != null)
            return state.foundationThreshold.movementEvents + " movement events";
        if (state.mapEstimate != null)
            return state.mapEstimate.evidenceSamplesUsed + " MAP samples";
        if (state.blendDuration != null)
            return state.blendDuration.allValidEvents + " valid event(s)";
        return String.valueOf(state.captureState);
    }

    private String focusStatus(GuidedFocusHub.State state) {
        if (state == null) return "waiting for production Focus";
        if (state.engagement != null) return trim(state.engagement.detectorStatusText(), 36);
        if (state.foundationThreshold != null) return trim(state.foundationThreshold.driverInstruction(), 36);
        if (state.mapEstimate != null) return trim(state.mapEstimate.targetReason, 36);
        if (state.blendDuration != null) return trim(state.blendDuration.instruction, 36);
        return String.valueOf(state.captureState);
    }

    private String liveSummary(GuidedFocusHub.State state) {
        if (state == null) return "n/a";
        if (state.engagement != null)
            return fmt0(state.engagement.rpm) + " RPM / " + fmt1(state.engagement.tps) + "% TPS";
        if (state.foundationThreshold != null)
            return fmt0(state.foundationThreshold.liveRpm) + " RPM / Δ " + fmt3(state.foundationThreshold.liveDelta);
        if (state.mapEstimate != null) {
            int row = state.mapEstimate.liveRow;
            int column = state.mapEstimate.liveCol;
            if (row >= 0 && column >= 0 && row < state.mapEstimate.tpsAxis.length
                    && column < state.mapEstimate.rpmAxis.length) {
                return "cell " + fmt0(state.mapEstimate.rpmAxis[column]) + " RPM / "
                        + fmt1(state.mapEstimate.tpsAxis[row]) + "% TPS";
            }
            return trim(state.mapEstimate.liveEligibility, 36);
        }
        if (state.blendDuration != null)
            return fmt0(state.blendDuration.liveRpm) + " RPM / MAP " + fmt1(state.blendDuration.liveMap);
        return "cached production model";
    }

    private String progressSummary(GuidedFocusHub.State state) {
        if (state == null) return "0";
        if (state.engagement != null)
            return state.engagement.activityEvents + " / " + state.engagement.targetEvents;
        if (state.foundationThreshold != null)
            return state.foundationThreshold.effectiveValidatedBins + " validated region(s)";
        if (state.mapEstimate != null)
            return state.mapEstimate.confirmedCount + " confirmed / "
                    + state.mapEstimate.proposalChangeCount + " change(s)";
        if (state.blendDuration != null)
            return state.blendDuration.matchingEvents + " / " + state.blendDuration.targetEvents;
        return String.valueOf(state.captureState);
    }

    private void applyTheme() {
        setBackground(AeUiTheme.background());
        taskTitle.setForeground(AeUiTheme.text());
        taskDescription.setForeground(AeUiTheme.muted());
        nextStep.setForeground(AeUiTheme.text());
        sessionState.setForeground(AeUiTheme.muted());
        productionBadge.setBackground(AeUiTheme.softBlue());
        productionBadge.setForeground(AeUiTheme.blue());
        taskSelector.applyTheme();
        stepper.repaint();
        styleThemeChoice(lightTheme, AeUiTheme.side() == AeUiTheme.Side.LIGHT_SIDE);
        styleThemeChoice(darkTheme, AeUiTheme.side() == AeUiTheme.Side.DARK_SIDE);
        for (JButton info : stageInfoButtons.values()) resetButton(info);
        resetButton(mapValidation);
        resetButton(exportSession);
        resetButton(diagnostics);
        for (SummaryGrid grid : new SummaryGrid[]{prepareSummary, captureSummary, focusSummary,
                reviewSummary, applySummary, resultSummary}) grid.applyTheme();
        repaint();
    }

    private void applyStagePresentation() {
        for (Stage candidate : Stage.values()) {
            JPanel card = stageCards.get(candidate);
            if (card == null) continue;
            Color border;
            int thickness = 1;
            if (candidate == stage) {
                border = AeUiTheme.blue();
                thickness = 2;
            } else if (candidate.ordinal() < stage.ordinal()) {
                border = AeUiTheme.green();
            } else {
                border = AeUiTheme.border();
            }
            card.setBackground(AeUiTheme.card());
            card.setBorder(new CompoundBorder(new LineBorder(border, thickness),
                    new EmptyBorder(8, 10, 8, 10)));
            themeLabels(card);
        }
    }

    private static void styleThemeChoice(AbstractButton button, boolean selected) {
        button.setOpaque(true);
        button.setBackground(selected ? AeUiTheme.taskSelectedBg() : AeUiTheme.button());
        button.setForeground(selected ? AeUiTheme.taskSelectedText() : AeUiTheme.text());
        button.setBorder(new CompoundBorder(new LineBorder(selected
                ? AeUiTheme.taskSelectedBorder() : AeUiTheme.buttonBorder()),
                new EmptyBorder(5, 9, 5, 9)));
    }

    private static JDialog createDialog(Window host, String title) {
        if (host instanceof Dialog)
            return new JDialog((Dialog)host, title, Dialog.ModalityType.APPLICATION_MODAL);
        if (host instanceof Frame) return new JDialog((Frame)host, title, true);
        return new JDialog((Frame)null, title, true);
    }

    private static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Dialog", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(180, 34));
        return button;
    }

    private static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(SMALL);
        button.setFocusPainted(false);
        resetButton(button);
        return button;
    }

    private static void resetButton(AbstractButton button) {
        button.setOpaque(true);
        button.setBackground(AeUiTheme.button());
        button.setForeground(AeUiTheme.text());
        button.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.buttonBorder()),
                new EmptyBorder(5, 9, 5, 9)));
    }

    private static void highlight(JButton button) {
        if (!button.isEnabled()) {
            resetButton(button);
            return;
        }
        button.setOpaque(true);
        button.setBackground(AeUiTheme.amber());
        button.setForeground(new Color(42, 31, 4));
        button.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.amberDark(), 2),
                new EmptyBorder(4, 8, 4, 8)));
    }

    private static void themeLabels(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel) {
                JLabel label = (JLabel)child;
                label.setForeground(label.getFont().isBold() ? AeUiTheme.text() : AeUiTheme.muted());
            }
            if (child instanceof Container) themeLabels((Container)child);
        }
    }

    private interface TaskSelectionListener { void taskSelected(GuidedProductionTask task); }

    private final class TaskSelectorPanel extends JPanel {
        private final TaskSelectionListener listener;
        private final EnumMap<GuidedProductionTask, TaskToggleButton> buttons =
                new EnumMap<GuidedProductionTask, TaskToggleButton>(GuidedProductionTask.class);
        private final EnumMap<GuidedProductionTask.Group, JLabel> groupStates =
                new EnumMap<GuidedProductionTask.Group, JLabel>(GuidedProductionTask.Group.class);
        private final EnumMap<GuidedProductionTask.Group, JLabel> groupNames =
                new EnumMap<GuidedProductionTask.Group, JLabel>(GuidedProductionTask.Group.class);
        private final ButtonGroup group = new ButtonGroup();
        private final JLabel selectorTitle = new JLabel("AE tuning tasks");
        private final JLabel selectorSub = new JLabel("Firmware-backed task ownership");
        private final JLabel detail = new JLabel();
        private final JLabel tuneTitle = new JLabel("WORKING TUNE");
        private final JLabel tuneState = new JLabel();
        private final JScrollPane taskScroll;

        TaskSelectorPanel(TaskSelectionListener listener) {
            this.listener = listener;
            setLayout(new BorderLayout(0, 8));
            setPreferredSize(new Dimension(312, 0));

            JPanel north = new JPanel();
            north.setOpaque(false);
            north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            selectorTitle.setFont(H2);
            selectorSub.setFont(SMALL);
            head.add(selectorTitle, BorderLayout.NORTH);
            head.add(selectorSub, BorderLayout.SOUTH);
            north.add(head);
            north.add(Box.createVerticalStrut(7));

            JPanel tune = new JPanel(new BorderLayout(4, 2));
            tune.setOpaque(false);
            tune.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            tuneTitle.setFont(new Font("Dialog", Font.BOLD, 9));
            tuneState.setFont(new Font("Dialog", Font.PLAIN, 9));
            tune.add(tuneTitle, BorderLayout.NORTH);
            tune.add(tuneState, BorderLayout.CENTER);
            north.add(tune);
            add(north, BorderLayout.NORTH);

            JPanel list = new JPanel();
            list.setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            for (GuidedProductionTask.Group taskGroup : GuidedProductionTask.Group.values()) {
                list.add(groupHeader(taskGroup));
                list.add(Box.createVerticalStrut(3));
                for (final GuidedProductionTask item : GuidedProductionTask.values()) {
                    if (item.group != taskGroup) continue;
                    final TaskToggleButton button = new TaskToggleButton();
                    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 39));
                    button.setPreferredSize(new Dimension(100, 39));
                    button.setFont(BODY);
                    button.setFocusPainted(false);
                    buttons.put(item, button);
                    group.add(button);
                    list.add(button);
                    list.add(Box.createVerticalStrut(3));
                    button.addActionListener(new ActionListener() {
                        @Override public void actionPerformed(ActionEvent e) {
                            if (GuidedTaskAvailabilityAdapter.evaluate(item, currentTune).clickable)
                                listener.taskSelected(item);
                        }
                    });
                }
                list.add(Box.createVerticalStrut(5));
            }
            taskScroll = new JScrollPane(list);
            taskScroll.setBorder(null);
            taskScroll.setOpaque(false);
            taskScroll.getViewport().setOpaque(false);
            taskScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            taskScroll.getVerticalScrollBar().setUnitIncrement(18);
            taskScroll.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
            add(taskScroll, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(4, 6));
            bottom.setOpaque(false);
            detail.setFont(new Font("Dialog", Font.PLAIN, 9));
            bottom.add(detail, BorderLayout.NORTH);
            JButton info = secondaryButton("Task Details…");
            info.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) { showTaskInfo(); }
            });
            JPanel actions = new JPanel(new GridLayout(0, 1, 0, 4));
            actions.setOpaque(false);
            actions.add(mapValidation);
            actions.add(info);
            actions.add(exportSession);
            actions.add(diagnostics);
            bottom.add(actions, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);
        }

        private JComponent groupHeader(GuidedProductionTask.Group taskGroup) {
            JPanel panel = new JPanel(new BorderLayout(5, 0));
            panel.setOpaque(false);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            JLabel name = new JLabel(taskGroup.displayName.toUpperCase());
            name.setFont(new Font("Dialog", Font.BOLD, 10));
            JLabel state = new JLabel("READ TUNE");
            state.setFont(new Font("Dialog", Font.BOLD, 8));
            groupNames.put(taskGroup, name);
            groupStates.put(taskGroup, state);
            panel.add(name, BorderLayout.WEST);
            panel.add(state, BorderLayout.EAST);
            return panel;
        }

        void setCurrentTune(AeProjectSnapshot tune) {
            tuneState.setText(tune == null
                    ? "Read Working Tune to bind feature state."
                    : "<html>TPS AE " + onOff(tune.isTpsAeEnabled()) + " • MAP "
                    + onOff(tune.isMapEstimateEnabled()) + "<br>Wall "
                    + onOff(tune.isWallWettingEnabled()) + " • Instant "
                    + onOff(tune.isExtraShotEnabled()) + "</html>");
            for (GuidedProductionTask.Group item : GuidedProductionTask.Group.values()) {
                String state = GuidedTaskAvailabilityAdapter.groupState(item, tune);
                JLabel label = groupStates.get(item);
                label.setText(state);
                label.setForeground("ON".equals(state) ? AeUiTheme.green()
                        : "SHARED".equals(state) ? AeUiTheme.blue() : AeUiTheme.taskGroupOff());
            }
            for (GuidedProductionTask item : GuidedProductionTask.values()) styleTask(item);
            updateDetail();
        }

        void setSelectedTask(GuidedProductionTask selected) {
            for (GuidedProductionTask item : GuidedProductionTask.values()) {
                TaskToggleButton button = buttons.get(item);
                if (button != null) {
                    button.setSelected(item == selected);
                    styleTask(item);
                }
            }
            updateDetail();
        }

        void applyTheme() {
            setBackground(AeUiTheme.panel());
            setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),
                    new EmptyBorder(9, 8, 9, 8)));
            selectorTitle.setForeground(AeUiTheme.text());
            selectorSub.setForeground(AeUiTheme.muted());
            tuneTitle.setForeground(AeUiTheme.blue());
            tuneState.setForeground(AeUiTheme.muted());
            detail.setForeground(AeUiTheme.muted());
            for (GuidedProductionTask.Group item : GuidedProductionTask.Group.values()) {
                JLabel name = groupNames.get(item);
                if (name != null) name.setForeground(AeUiTheme.text());
            }
            for (GuidedProductionTask item : GuidedProductionTask.values()) styleTask(item);
            taskScroll.getViewport().setBackground(AeUiTheme.panel());
            repaint();
        }

        private void styleTask(GuidedProductionTask item) {
            TaskToggleButton button = buttons.get(item);
            if (button == null) return;
            GuidedTaskAvailability availability = GuidedTaskAvailabilityAdapter.evaluate(item, currentTune);
            boolean selected = availability.clickable && button.isSelected();
            Color background;
            Color title;
            Color status;
            Color border;
            if (!availability.clickable) {
                background = AeUiTheme.taskDisabledBg();
                title = AeUiTheme.taskDisabledText();
                status = AeUiTheme.taskDisabledStatusText();
                border = AeUiTheme.taskDisabledBorder();
            } else if (selected) {
                background = AeUiTheme.taskSelectedBg();
                title = AeUiTheme.taskSelectedText();
                status = AeUiTheme.taskSelectedStatusText();
                border = AeUiTheme.taskSelectedBorder();
            } else {
                background = AeUiTheme.taskAvailableBg();
                title = AeUiTheme.taskAvailableText();
                status = AeUiTheme.taskStatusText();
                border = AeUiTheme.taskAvailableBorder();
            }
            button.setEnabled(availability.clickable);
            button.setBackground(background);
            button.setBorder(new CompoundBorder(new LineBorder(border),
                    new EmptyBorder(4, 7, 4, 7)));
            button.setRowPresentation(item.displayName, availability.status, title, status);
            button.setToolTipText(availability.reason);
        }

        private void updateDetail() {
            GuidedTaskAvailability availability = GuidedTaskAvailabilityAdapter.evaluate(task, currentTune);
            detail.setText("<html><b>Selected:</b> " + html(task.displayName)
                    + "<br>" + html(task.group.displayName) + " • "
                    + html(availability.status) + "</html>");
        }

        private void showTaskInfo() {
            Window host = SwingUtilities.getWindowAncestor(this);
            JDialog dialog = createDialog(host, task.displayName + " — Task Details");
            dialog.setContentPane(GuidedV019TaskInfoViews.create(task, Stage.PREPARE,
                    currentTune, bridge));
            dialog.setMinimumSize(new Dimension(760, 500));
            dialog.setSize(900, 590);
            dialog.setLocationRelativeTo(host);
            dialog.setVisible(true);
        }
    }

    private static final class TaskToggleButton extends JToggleButton {
        private String rowTitle = "";
        private String rowStatus = "";
        private Color rowTitleColor = Color.WHITE;
        private Color rowStatusColor = Color.GRAY;

        TaskToggleButton() {
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(false);
        }

        void setRowPresentation(String title, String status,
                                Color titleColor, Color statusColor) {
            rowTitle = title == null ? "" : title;
            rowStatus = status == null ? "" : status;
            rowTitleColor = titleColor;
            rowStatusColor = statusColor;
            setText(rowTitle + " — " + rowStatus);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            Insets insets = getInsets();
            g2.setFont(getFont().deriveFont(Font.BOLD));
            FontMetrics metrics = g2.getFontMetrics();
            int y = insets.top + metrics.getAscent();
            g2.setColor(rowTitleColor);
            drawClipped(g2, rowTitle, insets.left, y,
                    Math.max(20, getWidth() - insets.left - insets.right));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 9f));
            g2.setColor(rowStatusColor);
            drawClipped(g2, rowStatus, insets.left,
                    y + g2.getFontMetrics().getHeight(),
                    Math.max(20, getWidth() - insets.left - insets.right));
            g2.dispose();
        }

        private static void drawClipped(Graphics2D g2, String text, int x, int y, int width) {
            FontMetrics metrics = g2.getFontMetrics();
            if (metrics.stringWidth(text) <= width) {
                g2.drawString(text, x, y);
                return;
            }
            String ellipsis = "…";
            int limit = Math.max(0, width - metrics.stringWidth(ellipsis));
            int end = text.length();
            while (end > 0 && metrics.stringWidth(text.substring(0, end)) > limit) end--;
            g2.drawString(text.substring(0, end) + ellipsis, x, y);
        }
    }

    private static final class WorkflowStepper extends JPanel {
        private int stage;
        private boolean skippedApply;

        WorkflowStepper() {
            setOpaque(false);
            setPreferredSize(new Dimension(760, 72));
        }

        void setStage(int stage, boolean skippedApply) {
            this.stage = stage;
            this.skippedApply = skippedApply;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int count = Stage.values().length;
            int margin = 42;
            int y = 24;
            int available = Math.max(100, getWidth() - margin * 2);
            int step = available / (count - 1);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(AeUiTheme.border());
            g2.drawLine(margin, y, margin + step * (count - 1), y);
            for (int i = 0; i < count; i++) {
                int x = margin + step * i;
                boolean skipped = skippedApply && i == Stage.APPLY.ordinal();
                boolean complete = i < stage && !skipped;
                boolean active = i == stage;
                g2.setColor(skipped ? AeUiTheme.inactiveStep()
                        : complete ? AeUiTheme.green()
                        : active ? AeUiTheme.blue() : AeUiTheme.inactiveStep());
                g2.fillOval(x - 13, y - 13, 26, 26);
                String number = skipped ? "—" : complete ? "✓" : String.valueOf(i + 1);
                g2.setColor(complete || active ? Color.WHITE : AeUiTheme.muted());
                g2.setFont(new Font("Dialog", Font.BOLD, 11));
                FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(number, x - metrics.stringWidth(number) / 2, y + 4);
                Stage item = Stage.values()[i];
                g2.setColor(skipped ? AeUiTheme.muted() : AeUiTheme.text());
                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                metrics = g2.getFontMetrics();
                g2.drawString(item.title, x - metrics.stringWidth(item.title) / 2, y + 27);
                String sub = skipped ? "not required" : item.subtitle;
                g2.setColor(AeUiTheme.muted());
                g2.setFont(new Font("Dialog", Font.PLAIN, 8));
                metrics = g2.getFontMetrics();
                g2.drawString(sub, x - metrics.stringWidth(sub) / 2, y + 39);
            }
            g2.dispose();
        }
    }

    private static final class SummaryGrid extends JPanel {
        private final JLabel[] keys;
        private final JLabel[] values;

        SummaryGrid(int rows) {
            super(new GridBagLayout());
            setOpaque(false);
            keys = new JLabel[rows];
            values = new JLabel[rows];
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = 0;
            constraints.anchor = GridBagConstraints.WEST;
            constraints.insets = new Insets(2, 0, 2, 5);
            for (int i = 0; i < rows; i++) {
                keys[i] = new JLabel();
                values[i] = new JLabel();
                keys[i].setFont(BODY);
                values[i].setFont(new Font("Dialog", Font.BOLD, 12));
                constraints.gridx = 0;
                constraints.weightx = 0;
                add(keys[i], constraints);
                constraints.gridx = 1;
                constraints.weightx = 1;
                add(values[i], constraints);
                constraints.gridy++;
            }
            applyTheme();
        }

        void set(String[][] rows) {
            for (int i = 0; i < keys.length; i++) {
                String[] row = i < rows.length ? rows[i] : new String[]{"", ""};
                keys[i].setText(row[0]);
                values[i].setText(row.length > 1 ? row[1] : "");
            }
        }

        void applyTheme() {
            for (JLabel key : keys) key.setForeground(AeUiTheme.muted());
            for (JLabel value : values) value.setForeground(AeUiTheme.text());
        }
    }

    int taskCountForTest() { return GuidedProductionTask.values().length; }
    String taskNameForTest(int index) { return GuidedProductionTask.values()[index].displayName; }
    boolean taskAvailableForTest(GuidedProductionTask item) {
        return GuidedTaskAvailabilityAdapter.evaluate(item, currentTune).clickable;
    }
    String taskStatusForTest(GuidedProductionTask item) {
        return GuidedTaskAvailabilityAdapter.evaluate(item, currentTune).status;
    }
    String groupStateForTest(GuidedProductionTask.Group group) {
        return GuidedTaskAvailabilityAdapter.groupState(group, currentTune);
    }
    String visibleStageForTest() { return stage.title; }
    void selectTaskForTest(GuidedProductionTask selected) { selectTask(selected); }
    int stageCardCountForTest() { return stageCards.size(); }
    boolean mainUsesTwoByThreeCardsForTest() { return stageCards.size() == 6; }
    JButton mapValidationToggleForTest() { return mapValidation; }
    boolean mapValidationActiveForTest() { return mapValidationActive; }
    JButton prepareButtonForTest() { return prepare; }
    JButton captureButtonForTest() { return capture; }
    JButton reviewButtonForTest() { return review; }
    JButton resultButtonForTest() { return result; }
    JButton exportSessionButtonForTest() { return exportSession; }
    JButton diagnosticsButtonForTest() { return diagnostics; }
    JComponent stepInfoViewForTest(Stage selectedStage) {
        return GuidedV019TaskInfoViews.create(task, selectedStage, currentTune, bridge);
    }
    JComponent reviewViewForTest() {
        return GuidedV019ReviewViews.create(task, currentTune, bridge, null);
    }
    JComponent applyViewForTest() {
        return GuidedV019ApplyResultViews.createApply(task, currentTune, bridge, null);
    }
    JComponent resultViewForTest(boolean applied) {
        return GuidedV019ApplyResultViews.createResult(task, currentTune, bridge, applied, null);
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private static String fmt0(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0f", value) : "n/a";
    }
    private static String fmt1(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "n/a";
    }
    private static String fmt3(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
    private static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
