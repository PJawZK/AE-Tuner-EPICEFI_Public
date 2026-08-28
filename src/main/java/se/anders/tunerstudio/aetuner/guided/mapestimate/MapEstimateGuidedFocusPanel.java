package se.anders.tunerstudio.aetuner.guided.mapestimate;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

/** Driver-facing MAP Estimate Table surface, scope, evidence maturity and proposal view. */
public final class MapEstimateGuidedFocusPanel extends JPanel {
    public enum ScopeMode {
        WHOLE_TABLE("Whole table"),
        SELECTED_CELLS("Selected cells");
        private final String label;
        ScopeMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public interface ConfigurationListener {
        void onStrategyRequested(MapEstimateCoverageStrategy strategy);
        void onScopeRequested(MapEstimateCellScope scope);
        default void onEvidenceBasisRequested(MapEstimateEvidenceBasis basis) { }
        default void onProposalLimitPolicyRequested(MapEstimateProposalLimitPolicy policy) { }
    }

    private static final Color DIRECT = new Color(69, 145, 89);
    private static final Color INTERPOLATED = new Color(99, 155, 190);
    private static final Color WEAK = new Color(218, 181, 78);
    private static final Color CONFLICT = new Color(190, 91, 76);
    private static final Color TARGET = new Color(55, 118, 205);
    private static final Color PROPOSAL = new Color(137, 85, 190);
    private static final Color CURRENT_RUN = new Color(89, 180, 176);

    private final JComboBox<MapEstimateCoverageStrategy> strategy =
            new JComboBox<MapEstimateCoverageStrategy>(MapEstimateCoverageStrategy.values());
    private final JComboBox<ScopeMode> scopeMode = new JComboBox<ScopeMode>(ScopeMode.values());
    private final JComboBox<MapEstimateEvidenceBasis> evidenceBasis =
            new JComboBox<MapEstimateEvidenceBasis>(MapEstimateEvidenceBasis.values());
    private final JComboBox<MapEstimateProposalLimitPolicy> proposalLimit =
            new JComboBox<MapEstimateProposalLimitPolicy>(MapEstimateProposalLimitPolicy.values());
    private final JButton clearSelection = new JButton("Clear selection");
    private final JLabel instruction = new JLabel("MAP Estimate Table Guided Focus");
    private final JLabel coverage = new JLabel("No MAP Estimate evidence loaded.");
    private final JLabel target = new JLabel("Suggested target: n/a");
    private final CoverageModel tableModel = new CoverageModel();
    private final JTable table = new JTable(tableModel);

    private MapEstimateFocusModel model;
    private MapEstimateCellScope workingScope;
    private ConfigurationListener listener;
    private boolean updatingControls;
    private int dragRow = -1;
    private int dragCol = -1;

    public MapEstimateGuidedFocusPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildUi();
        installActions();
    }

    public void setConfigurationListener(ConfigurationListener listener) {
        this.listener = listener;
    }

    public void updateModel(MapEstimateFocusModel next) {
        model = next;
        if (next != null) workingScope = next.scope;
        updatingControls = true;
        try {
            strategy.setSelectedItem(next == null ? MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE : next.strategy);
            ScopeMode mode = next == null || next.scope.isWholeTable()
                    ? ScopeMode.WHOLE_TABLE : ScopeMode.SELECTED_CELLS;
            scopeMode.setSelectedItem(mode);
            evidenceBasis.setSelectedItem(next == null
                    ? MapEstimateEvidenceBasis.LEARNED_MEMORY : next.evidenceBasis);
            proposalLimit.setSelectedItem(next == null
                    ? MapEstimateProposalLimitPolicy.HIGH_TPS_CAP : next.proposalLimitPolicy);
        } finally {
            updatingControls = false;
        }
        boolean editable = next == null || !next.captureActive;
        strategy.setEnabled(editable);
        scopeMode.setEnabled(editable);
        evidenceBasis.setEnabled(editable);
        proposalLimit.setEnabled(editable);
        clearSelection.setEnabled(editable && scopeMode.getSelectedItem() == ScopeMode.SELECTED_CELLS);
        refreshText();
        tableModel.fireTableStructureChanged();
        configureWidths();
        table.repaint();
    }

    public void setDriverView(boolean driverView) {
        table.setRowHeight(driverView ? 44 : 36);
        float size = driverView ? 14f : 12f;
        table.setFont(table.getFont().deriveFont(size));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(driverView ? 13f : 12f));
        instruction.setFont(instruction.getFont().deriveFont(Font.BOLD, driverView ? 20f : 16f));
    }

    private void buildUi() {
        JPanel primaryControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        primaryControls.add(new JLabel("Coverage strategy"));
        primaryControls.add(strategy);
        primaryControls.add(new JLabel("Capture scope"));
        primaryControls.add(scopeMode);
        primaryControls.add(clearSelection);

        JPanel experimentControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        experimentControls.add(new JLabel("Evidence basis"));
        experimentControls.add(evidenceBasis);
        experimentControls.add(new JLabel("Proposal limit"));
        experimentControls.add(proposalLimit);
        evidenceBasis.setToolTipText("Current capture only excludes all previously stored MAP evidence from this run's surface, target coach and proposal. Finish may still archive the run for later use.");
        proposalLimit.setToolTipText("Unrestricted eligible MAP removes only the experimental high-TPS MAP cap. Scope, evidence quality, Conflict/Recheck exclusion and no-extrapolation safeguards remain active.");

        JPanel controls = new JPanel(new GridLayout(2, 1, 0, 0));
        controls.add(primaryControls);
        controls.add(experimentControls);

        JPanel status = new JPanel(new GridLayout(3, 1, 0, 2));
        instruction.setFont(instruction.getFont().deriveFont(Font.BOLD, 20f));
        status.add(instruction);
        status.add(coverage);
        status.add(target);

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(controls, BorderLayout.NORTH);
        north.add(status, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        table.setRowHeight(44);
        table.setDefaultRenderer(Object.class, new HeatRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.getHorizontalScrollBar().setUnitIncrement(28);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        JPanel legend = new JPanel(new GridLayout(1, 7, 5, 0));
        legend.add(legend("✓D direct", DIRECT));
        legend.add(legend("≈I interpolated", INTERPOLATED));
        legend.add(legend("? weak", WEAK));
        legend.add(legend("! recheck/conflict", CONFLICT));
        legend.add(legend("+ this run", CURRENT_RUN));
        legend.add(legend("T target", TARGET));
        legend.add(legend("Δ proposal", PROPOSAL));
        add(legend, BorderLayout.SOUTH);
    }

    private void installActions() {
        strategy.addActionListener(event -> {
            if (updatingControls || !controlsEditable() || listener == null) return;
            Object selected = strategy.getSelectedItem();
            if (selected instanceof MapEstimateCoverageStrategy) {
                listener.onStrategyRequested((MapEstimateCoverageStrategy) selected);
            }
        });
        evidenceBasis.addActionListener(event -> {
            if (updatingControls || !controlsEditable() || listener == null) return;
            Object selected = evidenceBasis.getSelectedItem();
            if (selected instanceof MapEstimateEvidenceBasis) {
                listener.onEvidenceBasisRequested((MapEstimateEvidenceBasis) selected);
            }
        });
        proposalLimit.addActionListener(event -> {
            if (updatingControls || !controlsEditable() || listener == null) return;
            Object selected = proposalLimit.getSelectedItem();
            if (selected instanceof MapEstimateProposalLimitPolicy) {
                listener.onProposalLimitPolicyRequested((MapEstimateProposalLimitPolicy) selected);
            }
        });
        scopeMode.addActionListener(event -> {
            if (updatingControls || !controlsEditable()) return;
            ScopeMode selected = (ScopeMode) scopeMode.getSelectedItem();
            if (selected == ScopeMode.WHOLE_TABLE) {
                MapEstimateCellScope scope = dimensionsScope(true);
                applyRequestedScope(scope);
            } else {
                MapEstimateCellScope scope = dimensionsScope(false);
                applyRequestedScope(scope);
            }
            clearSelection.setEnabled(selected == ScopeMode.SELECTED_CELLS);
        });
        clearSelection.addActionListener(event -> {
            if (!controlsEditable() || scopeMode.getSelectedItem() != ScopeMode.SELECTED_CELLS) return;
            applyRequestedScope(dimensionsScope(false));
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!selectionEditable()) return;
                int[] cell = cellAt(event.getPoint());
                if (cell == null) return;
                if ((event.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    toggleCell(cell[0], cell[1]);
                    dragRow = dragCol = -1;
                } else {
                    dragRow = cell[0]; dragCol = cell[1];
                }
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (!selectionEditable() || dragRow < 0) return;
                int[] cell = cellAt(event.getPoint());
                if (cell != null) selectRectangle(dragRow, dragCol, cell[0], cell[1]);
                dragRow = dragCol = -1;
            }
        });
    }

    private void refreshText() {
        if (model == null) {
            instruction.setText("Read Working Tune to initialize MAP Estimate Table Guided Focus.");
            coverage.setText("No learned MAP Estimate memory loaded.");
            target.setText("Suggested target: n/a");
            return;
        }
        coverage.setText("Basis " + model.evidenceBasis
                + " | limit " + model.proposalLimitPolicy
                + " | evidence used " + model.evidenceSamplesUsed
                + " | stored " + model.storedSamples
                + " | this run " + model.currentRunSamples
                + " | direct " + model.directCount
                + " | interpolated " + model.interpolatedStrongCount
                + " | confirmed " + model.confirmedCount
                + " | provisional " + model.provisionalCount
                + " | recheck " + model.recheckCount
                + " | conflict " + model.conflictCount
                + " | proposal Δ " + model.proposalChangeCount);
        if (model.targetRow >= 0 && model.targetCol >= 0) {
            String tpsCheck = model.liveTpsInTargetZone ? "TPS ✓" : "TPS →";
            String rpmCheck = model.liveRpmInTargetZone ? "RPM ✓" : "RPM →";
            instruction.setText(String.format(Locale.US,"NOW: %.1f–%.1f%% TPS · %.0f–%.0f RPM",
                    model.targetZone.minTps, model.targetZone.maxTps,
                    model.targetZone.minRpm, model.targetZone.maxRpm));
            target.setText(tpsCheck + "   " + rpmCheck + "  |  " + model.targetReason + "  |  live: " + model.liveEligibility);
        } else {
            String mode = model.strategy == MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE
                    ? "Build useful direct anchors; bounded interpolation fills trustworthy gaps."
                    : "Collect direct evidence only for the selected table cells.";
            instruction.setText(mode + (model.captureActive ? " Capture controls are locked." : ""));
            target.setText("Suggested target: none — " + model.targetReason + " | live: " + model.liveEligibility);
        }
    }

    private boolean controlsEditable() { return model == null || !model.captureActive; }
    private boolean selectionEditable() {
        return controlsEditable() && scopeMode.getSelectedItem() == ScopeMode.SELECTED_CELLS
                && model != null;
    }

    private MapEstimateCellScope dimensionsScope(boolean all) {
        int rows = model == null ? 1 : model.rows();
        int cols = model == null ? 1 : model.cols();
        return all ? MapEstimateCellScope.all(rows, cols) : MapEstimateCellScope.none(rows, cols);
    }

    private void applyRequestedScope(MapEstimateCellScope scope) {
        workingScope = scope;
        table.repaint();
        if (listener != null) listener.onScopeRequested(scope);
    }

    private void toggleCell(int row, int col) {
        if (workingScope == null) workingScope = dimensionsScope(false);
        boolean next = !workingScope.contains(row, col);
        applyRequestedScope(workingScope.withCell(row, col, next));
    }

    private void selectRectangle(int rowA, int colA, int rowB, int colB) {
        MapEstimateCellScope next = dimensionsScope(false)
                .withRectangle(rowA, colA, rowB, colB, true);
        applyRequestedScope(next);
    }

    private int[] cellAt(Point point) {
        int row = table.rowAtPoint(point);
        int column = table.columnAtPoint(point);
        if (row < 0 || column <= 0) return null;
        return new int[]{row, column - 1};
    }

    private void configureWidths() {
        if (table.getColumnModel().getColumnCount() == 0) return;
        table.getColumnModel().getColumn(0).setPreferredWidth(82);
        for (int col = 1; col < table.getColumnModel().getColumnCount(); col++) {
            table.getColumnModel().getColumn(col).setPreferredWidth(86);
        }
    }

    private final class CoverageModel extends AbstractTableModel {
        @Override public int getRowCount() { return model == null ? 0 : model.rows(); }
        @Override public int getColumnCount() { return model == null ? 0 : model.cols() + 1; }
        @Override public String getColumnName(int column) {
            if (column == 0) return "TPS \\ RPM";
            return Math.round(model.rpmAxis[column - 1]) + "";
        }
        @Override public Object getValueAt(int row, int column) {
            if (column == 0) return String.format(Locale.US, "%.1f%%", model.tpsAxis[row]);
            int col = column - 1;
            MapEstimateFocusModel.Cell cell = model.cell(row, col);
            StringBuilder text = new StringBuilder();
            if (model.isLive(row,col)) text.append('▶');
            if (model.isTarget(row,col)) text.append('T');
            if (cell.currentRun) text.append('+');
            if (cell.proposalChange) text.append('Δ');
            if (text.length() > 0) text.append(' ');
            switch (cell.state) {
                case DIRECT:
                    text.append("✓D");
                    if(cell.maturity==MapEstimateSurface.Maturity.CONFIRMED)text.append('✓');
                    else if(cell.maturity==MapEstimateSurface.Maturity.RECHECK)text.append('!');
                    else text.append('·');
                    break;
                case INTERPOLATED_STRONG:
                    text.append("≈I");
                    if(cell.maturity==MapEstimateSurface.Maturity.CONFIRMED)text.append('✓');
                    break;
                case INTERPOLATED_WEAK: text.append('?'); break;
                case CONFLICT: text.append('!'); break;
                default: text.append('—'); break;
            }
            if (Double.isFinite(cell.valueKpa)) text.append(' ').append(Math.round(cell.valueKpa));
            return text.toString();
        }
    }

    private final class HeatRenderer extends DefaultTableCellRenderer {
        private final Border normal = BorderFactory.createEmptyBorder(2,2,2,2);
        private final Border selected = BorderFactory.createLineBorder(TARGET, 2);
        private final Border proposal = BorderFactory.createLineBorder(PROPOSAL, 3);
        private final Border live = BorderFactory.createLineBorder(Color.BLACK, 2);

        HeatRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setOpaque(true);
            if (column == 0) {
                Color header = UIManager.getColor("TableHeader.background");
                setBackground(header == null ? table.getBackground() : header);
                setForeground(UIManager.getColor("TableHeader.foreground") == null
                        ? table.getForeground() : UIManager.getColor("TableHeader.foreground"));
                setFont(table.getFont().deriveFont(Font.BOLD));
                setBorder(normal);
                setToolTipText(null);
                return this;
            }
            int col = column - 1;
            MapEstimateFocusModel.Cell cell = model.cell(row,col);
            Color background = table.getBackground();
            switch (cell.state) {
                case DIRECT: background = cell.maturity==MapEstimateSurface.Maturity.RECHECK ? CONFLICT : DIRECT; break;
                case INTERPOLATED_STRONG: background = INTERPOLATED; break;
                case INTERPOLATED_WEAK: background = WEAK; break;
                case CONFLICT: background = CONFLICT; break;
                default: break;
            }
            if (cell.currentRun && cell.state == MapEstimateSurface.State.UNKNOWN) background = CURRENT_RUN;
            setBackground(background);
            setForeground(contrast(background, table.getForeground()));
            boolean selectedCell = workingScope != null && workingScope.contains(row,col)
                    && scopeMode.getSelectedItem() == ScopeMode.SELECTED_CELLS;
            Border border = normal;
            if (selectedCell) border = selected;
            if (cell.proposalChange) border = BorderFactory.createCompoundBorder(proposal, border);
            if (model.isLive(row,col)) border = BorderFactory.createCompoundBorder(live, border);
            setBorder(border);
            setToolTipText(toolTip(row,col));
            return this;
        }
    }

    private String toolTip(int row, int col) {
        MapEstimateFocusModel.Cell cell = model.cell(row,col);
        String between=Double.isFinite(cell.betweenSessionRangeKpa)
                ? String.format(Locale.US,"%.2f kPa",cell.betweenSessionRangeKpa) : "n/a";
        return String.format(Locale.US,
                "TPS %.1f%% / %.0f RPM — %s / %s; value %s kPa; evidence %d; sessions %d; between-session range %s; confidence %.0f%%; %s%s%s",
                model.tpsAxis[row], model.rpmAxis[col], cell.state, cell.maturity,
                Double.isFinite(cell.valueKpa) ? String.format(Locale.US,"%.2f",cell.valueKpa) : "n/a",
                cell.evidenceSamples, cell.sessionCount, between, cell.confidence * 100.0, cell.reason,
                cell.currentRun ? "; current-run evidence present" : "",
                cell.proposalChange ? "; proposal will change this cell" : "");
    }

    private static JLabel legend(String text, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(color);
        label.setForeground(contrast(color, Color.BLACK));
        label.setBorder(BorderFactory.createEmptyBorder(4,3,4,3));
        label.setPreferredSize(new Dimension(105,26));
        return label;
    }

    private static Color contrast(Color background, Color fallback) {
        if (background == null) return fallback;
        double luminance = 0.2126 * background.getRed()
                + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue();
        return luminance < 135.0 ? Color.WHITE : Color.BLACK;
    }

    // Package-private deterministic UI hooks used by headless/Xvfb regression.
    JTable tableForTest() { return table; }
    JComboBox<MapEstimateCoverageStrategy> strategyForTest() { return strategy; }
    JComboBox<ScopeMode> scopeModeForTest() { return scopeMode; }
    JComboBox<MapEstimateEvidenceBasis> evidenceBasisForTest() { return evidenceBasis; }
    JComboBox<MapEstimateProposalLimitPolicy> proposalLimitForTest() { return proposalLimit; }
    void selectRectangleForTest(int r0,int c0,int r1,int c1) { selectRectangle(r0,c0,r1,c1); }
    void toggleCellForTest(int row,int col) { toggleCell(row,col); }
    MapEstimateCellScope workingScopeForTest() { return workingScope; }
}
