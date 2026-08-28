package se.anders.tunerstudio.aetuner.guided;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;

/** Large driver-facing MAP Estimate coverage heat map. */
public final class MapEstimateGuidedFocusPanel extends JPanel {
    /**
     * Compatibility bridge for the frozen dev15 same-package class name.
     * Dev16 configuration authority lives on the new mapestimate Focus panel.
     */
    public interface ConfigurationListener extends
            se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel.ConfigurationListener { }

    private static final Color PARTIAL = new Color(232, 181, 66);
    private static final Color COMPLETE = new Color(65, 145, 87);
    private static final Color NOISY = new Color(190, 91, 76);
    private static final Color TARGET = new Color(55, 118, 205);

    private final JLabel instruction = new JLabel("MAP Estimate Guided Focus", SwingConstants.LEFT);
    private final JLabel live = new JLabel("Live cell: n/a");
    private final JLabel coverage = new JLabel("Coverage: 0/0");
    private MapEstimateFocusSnapshot snapshot = MapEstimateFocusSnapshot.empty(20);
    private final CoverageTableModel model = new CoverageTableModel();
    private final JTable table = new JTable(model);
    private final JLabel legendEmpty = legend("— empty", null);
    private final JLabel legendPartial = legend("≈ minimum seconds left", PARTIAL);
    private final JLabel legendComplete = legend("✓ clean", COMPLETE);
    private final JLabel legendNoisy = legend("! / × quality issue", NOISY);
    private final JLabel legendTarget = legend("T suggested target", TARGET);

    public MapEstimateGuidedFocusPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        instruction.setFont(instruction.getFont().deriveFont(Font.BOLD, 19f));
        JPanel status = new JPanel(new GridLayout(3, 1, 0, 3));
        status.add(instruction);
        status.add(live);
        status.add(coverage);
        add(status, BorderLayout.NORTH);

        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(42);
        table.setDefaultRenderer(Object.class, new HeatMapRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.getHorizontalScrollBar().setUnitIncrement(28);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        JPanel legend = new JPanel(new GridLayout(1, 5, 6, 0));
        legend.add(legendEmpty);
        legend.add(legendPartial);
        legend.add(legendComplete);
        legend.add(legendNoisy);
        legend.add(legendTarget);
        add(legend, BorderLayout.SOUTH);
    }

    public void updateSnapshot(MapEstimateFocusSnapshot next, GuidedCaptureState state) {
        snapshot = next == null ? MapEstimateFocusSnapshot.empty(20) : next;
        instruction.setText(snapshot.instructionText(state));
        live.setText(snapshot.liveCellText() + "   |   Live status: "
                + snapshot.eligibility.getDisplayText());
        int total = snapshot.rowCount() * snapshot.columnCount();
        coverage.setText("Coverage: " + snapshot.completeCellCount() + "/" + total
                + " clean   |   " + snapshot.partialCellCount() + " partial   |   "
                + snapshot.noisyCellCount() + " noisy/rejected   |   Suggested target: "
                + snapshot.targetText());
        model.fireTableStructureChanged();
        configureWidths();
        table.repaint();
    }

    MapEstimateFocusSnapshot snapshotForTest() { return snapshot; }
    JTable tableForTest() { return table; }

    private void configureWidths() {
        if (table.getColumnModel().getColumnCount() == 0) return;
        table.getColumnModel().getColumn(0).setPreferredWidth(86);
        for (int col = 1; col < table.getColumnModel().getColumnCount(); col++) {
            table.getColumnModel().getColumn(col).setPreferredWidth(82);
        }
    }

    private final class CoverageTableModel extends AbstractTableModel {
        @Override public int getRowCount() { return snapshot.rowCount(); }
        @Override public int getColumnCount() { return snapshot.columnCount() + 1; }

        @Override public String getColumnName(int column) {
            if (column == 0) return "TPS \\ RPM";
            if (column - 1 >= snapshot.rpmBins.length) return "";
            return Math.round(snapshot.rpmBins[column - 1]) + "";
        }

        @Override public Object getValueAt(int row, int column) {
            if (column == 0) {
                return row >= 0 && row < snapshot.tpsBins.length
                        ? String.format(Locale.US, "%.1f%%", snapshot.tpsBins[row]) : "";
            }
            int col = column - 1;
            long count = snapshot.countAt(row, col);
            String prefix = "";
            if (snapshot.isLiveCell(row, col)) prefix += "▶";
            if (snapshot.isTargetCell(row, col)) prefix += "T ";
            if (count <= 0) return prefix + "—";
            double seconds = snapshot.acceptedSecondsAt(row, col);
            if (snapshot.isRangeRejected(row, col)) return prefix + "× " + one(seconds) + "s";
            if (snapshot.isNoisy(row, col)) return prefix + "! " + one(seconds) + "s";
            if (snapshot.isComplete(row, col)) return prefix + "✓ " + one(seconds) + "s";
            return prefix + "≈" + one(snapshot.minimumSecondsRemaining(row, col)) + "s min";
        }
    }

    private final class HeatMapRenderer extends DefaultTableCellRenderer implements TableCellRenderer {
        private final Border normal = BorderFactory.createEmptyBorder(2, 2, 2, 2);
        private final Border target = BorderFactory.createLineBorder(TARGET, 3);
        private final Border liveBorder = BorderFactory.createLineBorder(
                UIManager.getColor("Table.foreground") == null
                        ? Color.BLACK : UIManager.getColor("Table.foreground"), 2);

        HeatMapRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override public Component getTableCellRendererComponent(JTable table,
                                                                  Object value,
                                                                  boolean isSelected,
                                                                  boolean hasFocus,
                                                                  int row,
                                                                  int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setOpaque(true);
            setFont(table.getFont().deriveFont(column == 0 ? Font.BOLD : Font.PLAIN));
            if (column == 0) {
                Color header = UIManager.getColor("TableHeader.background");
                setBackground(header == null ? table.getBackground() : header);
                setForeground(UIManager.getColor("TableHeader.foreground") == null
                        ? table.getForeground() : UIManager.getColor("TableHeader.foreground"));
                setBorder(normal);
                return this;
            }

            int col = column - 1;
            Color background = table.getBackground();
            if (snapshot.isNoisy(row, col) || snapshot.isRangeRejected(row, col)) background = NOISY;
            else if (snapshot.isComplete(row, col)) background = COMPLETE;
            else if (snapshot.countAt(row, col) > 0) background = PARTIAL;
            setBackground(background);
            setForeground(contrast(background, table.getForeground()));

            boolean liveCell = snapshot.isLiveCell(row, col);
            boolean targetCell = snapshot.isTargetCell(row, col);
            if (liveCell && targetCell) setBorder(BorderFactory.createCompoundBorder(target, liveBorder));
            else if (targetCell) setBorder(target);
            else if (liveCell) setBorder(liveBorder);
            else setBorder(normal);
            setToolTipText(toolTip(row, col));
            return this;
        }
    }

    private String toolTip(int row, int col) {
        if (row < 0 || row >= snapshot.tpsBins.length || col < 0 || col >= snapshot.rpmBins.length) return null;
        return String.format(Locale.US,
                "TPS %.1f%% / %.0f RPM — %d sample(s), %.2f accepted s, SD %s kPa, range %s kPa, min remaining ~%.2f s",
                snapshot.tpsBins[row], snapshot.rpmBins[col], snapshot.countAt(row, col),
                snapshot.acceptedSecondsAt(row, col), metric(snapshot.standardDeviationAt(row, col)),
                metric(snapshot.rangeAt(row, col)), snapshot.minimumSecondsRemaining(row, col));
    }

    private static JLabel legend(String text, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        if (color != null) {
            label.setBackground(color);
            label.setForeground(contrast(color, label.getForeground()));
        }
        label.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 4));
        label.setPreferredSize(new Dimension(120, 28));
        return label;
    }

    private static Color contrast(Color background, Color fallback) {
        if (background == null) return fallback == null ? Color.BLACK : fallback;
        double luminance = 0.2126 * background.getRed()
                + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue();
        return luminance < 135.0 ? Color.WHITE : Color.BLACK;
    }

    private static String one(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.1f", value) : "n/a";
    }

    private static String metric(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "n/a";
    }
}
