package se.anders.tunerstudio.aetuner.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.CardLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared v0.19-style shell for secondary AE Tuner utility workspaces.
 *
 * This class owns presentation only. Section content remains owned by the
 * production subsystem that created it; the shell simply replaces old nested
 * tab chrome with one left navigator and one focused content surface.
 */
public final class AeUtilityWorkspacePanel extends JPanel {
    private static final Font TITLE = new Font("Dialog", Font.BOLD, 22);
    private static final Font SECTION_TITLE = new Font("Dialog", Font.BOLD, 17);
    private static final Font BODY = new Font("Dialog", Font.PLAIN, 12);
    private static final Font SMALL = new Font("Dialog", Font.PLAIN, 10);
    public static final String PRESERVE_BACKGROUND = "ae.theme.preserveBackground";

    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final JLabel sectionTitle = new JLabel();
    private final JLabel sectionSubtitle = new JLabel();
    private final JPanel navigation = new JPanel();
    private final JPanel content = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel toolbarHost = new JPanel(new BorderLayout());
    private final ButtonGroup group = new ButtonGroup();
    private final Map<String, Section> sections = new LinkedHashMap<String, Section>();
    private final List<Runnable> selectionListeners = new ArrayList<Runnable>();
    private final Timer themeTimer;
    private String selectedId;
    private AeUiTheme.Side themedSide;

    public AeUtilityWorkspacePanel(String titleText, String subtitleText) {
        super(new BorderLayout(10, 10));
        title.setText(titleText == null ? "" : titleText);
        subtitle.setText(subtitleText == null ? "" : subtitleText);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(0, 3));
        header.setOpaque(false);
        title.setFont(TITLE);
        subtitle.setFont(BODY);
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        toolbarHost.setOpaque(false);
        toolbarHost.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
        header.add(toolbarHost, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        navigation.setLayout(new javax.swing.BoxLayout(navigation, javax.swing.BoxLayout.Y_AXIS));
        navigation.setPreferredSize(new Dimension(230, 0));
        navigation.setBorder(new CompoundBorder(
                new LineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(8, 7, 8, 7)));
        add(navigation, BorderLayout.WEST);

        content.setLayout(cards);
        add(contentHost(), BorderLayout.CENTER);

        themedSide = AeUiTheme.side();
        applyTheme();
        themeTimer = new Timer(500, event -> {
            if (themedSide != AeUiTheme.side()) applyTheme();
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0L) return;
            if (isShowing()) {
                applyTheme();
                if (!themeTimer.isRunning()) themeTimer.start();
            } else {
                themeTimer.stop();
            }
        });
    }

    private JComponent contentHost() {
        JPanel host = new JPanel(new BorderLayout(0, 8));
        host.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout(0, 2));
        head.setOpaque(false);
        sectionTitle.setFont(SECTION_TITLE);
        sectionSubtitle.setFont(BODY);
        head.add(sectionTitle, BorderLayout.NORTH);
        head.add(sectionSubtitle, BorderLayout.SOUTH);
        host.add(head, BorderLayout.NORTH);
        host.add(content, BorderLayout.CENTER);
        return host;
    }

    public void setToolbar(JComponent toolbar) {
        toolbarHost.removeAll();
        if (toolbar != null) toolbarHost.add(toolbar, BorderLayout.CENTER);
        applyTheme();
        revalidate();
        repaint();
    }

    public void addSection(String id, String titleText, String detailText, JComponent component) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("id");
        if (component == null) throw new IllegalArgumentException("component");
        if (sections.containsKey(id)) throw new IllegalArgumentException("duplicate section " + id);

        final Section section = new Section(id,
                titleText == null ? id : titleText,
                detailText == null ? "" : detailText,
                component);
        sections.put(id, section);
        group.add(section.button);
        navigation.add(section.button);
        navigation.add(javax.swing.Box.createVerticalStrut(5));
        content.add(component, id);
        section.button.addActionListener(event -> selectSection(id));
        if (selectedId == null) selectSection(id);
        applyTheme();
    }

    public void selectSection(String id) {
        Section section = sections.get(id);
        if (section == null) return;
        selectedId = id;
        cards.show(content, id);
        sectionTitle.setText(section.title);
        sectionSubtitle.setText(section.detail);
        section.button.setSelected(true);
        styleButtons();
        for (Runnable listener : new ArrayList<Runnable>(selectionListeners)) {
            if (listener != null) listener.run();
        }
        revalidate();
        repaint();
    }

    public String selectedSectionId() { return selectedId; }

    public int sectionCount() { return sections.size(); }

    public String sectionTitleAt(int index) {
        if (index < 0 || index >= sections.size()) throw new IndexOutOfBoundsException(String.valueOf(index));
        int i = 0;
        for (Section section : sections.values()) {
            if (i++ == index) return section.title;
        }
        throw new IndexOutOfBoundsException(String.valueOf(index));
    }

    public void addSelectionListener(Runnable listener) {
        if (listener != null) selectionListeners.add(listener);
    }

    public void applyTheme() {
        themedSide = AeUiTheme.side();
        setBackground(AeUiTheme.background());
        title.setForeground(AeUiTheme.text());
        subtitle.setForeground(AeUiTheme.muted());
        sectionTitle.setForeground(AeUiTheme.text());
        sectionSubtitle.setForeground(AeUiTheme.muted());
        navigation.setBackground(AeUiTheme.panel());
        navigation.setBorder(new CompoundBorder(
                new LineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(8, 7, 8, 7)));
        content.setBackground(AeUiTheme.background());
        themeTree(toolbarHost);
        for (Section section : sections.values()) themeTree(section.component);
        styleButtons();
        repaint();
    }

    private void styleButtons() {
        for (Section section : sections.values()) {
            boolean selected = section.id.equals(selectedId);
            section.button.setBackground(selected ? AeUiTheme.taskSelectedBg() : AeUiTheme.taskAvailableBg());
            section.button.titleColor = selected ? AeUiTheme.taskSelectedText() : AeUiTheme.taskAvailableText();
            section.button.detailColor = selected ? AeUiTheme.taskSelectedStatusText() : AeUiTheme.taskStatusText();
            section.button.setBorder(new CompoundBorder(
                    new LineBorder(selected ? AeUiTheme.taskSelectedBorder() : AeUiTheme.taskAvailableBorder()),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            section.button.repaint();
        }
    }

    /** Apply v0.19 colors to an existing production-owned component tree. */
    public static void themeTree(Component component) {
        if (component == null) return;
        boolean preserveBackground = component instanceof JComponent
                && Boolean.TRUE.equals(((JComponent) component).getClientProperty(PRESERVE_BACKGROUND));
        if (component instanceof JPanel && !preserveBackground) {
            ((JPanel) component).setBackground(AeUiTheme.card());
        }
        if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            label.setForeground(label.isEnabled() ? AeUiTheme.text() : AeUiTheme.muted());
        }
        if (component instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) component;
            if (!preserveBackground) text.setBackground(AeUiTheme.card());
            text.setForeground(AeUiTheme.text());
            text.setCaretColor(AeUiTheme.text());
            text.setSelectionColor(AeUiTheme.selection());
            text.setSelectedTextColor(Color.WHITE);
        }
        if (component instanceof JTable) {
            JTable table = (JTable) component;
            table.setBackground(AeUiTheme.card());
            table.setForeground(AeUiTheme.text());
            table.setGridColor(AeUiTheme.border());
            table.setSelectionBackground(AeUiTheme.selection());
            table.setSelectionForeground(Color.WHITE);
            if (table.getTableHeader() != null) {
                table.getTableHeader().setBackground(AeUiTheme.tableHeader());
                table.getTableHeader().setForeground(AeUiTheme.text());
            }
        }
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            if (!(button instanceof SectionButton)) {
                button.setOpaque(true);
                button.setBackground(AeUiTheme.button());
                button.setForeground(button.isEnabled() ? AeUiTheme.text() : AeUiTheme.muted());
                button.setBorder(new CompoundBorder(
                        new LineBorder(AeUiTheme.buttonBorder()),
                        BorderFactory.createEmptyBorder(5, 9, 5, 9)));
            }
        }
        if (component instanceof JComboBox) {
            JComboBox<?> combo = (JComboBox<?>) component;
            combo.setBackground(AeUiTheme.neutralSoft());
            combo.setForeground(combo.isEnabled() ? AeUiTheme.text() : AeUiTheme.muted());
        }
        if (component instanceof JTextField) {
            ((JTextField) component).setBackground(AeUiTheme.neutralSoft());
        }
        if (component instanceof JSpinner) {
            JSpinner spinner = (JSpinner) component;
            spinner.setBackground(AeUiTheme.neutralSoft());
            JComponent editor = spinner.getEditor();
            if (editor != null) themeTree(editor);
        }
        if (component instanceof JScrollPane) {
            JScrollPane scroll = (JScrollPane) component;
            scroll.getViewport().setBackground(AeUiTheme.card());
            if (scroll.getBorder() == null || !(scroll.getBorder() instanceof TitledBorder)) {
                scroll.setBorder(new LineBorder(AeUiTheme.border()));
            }
        }
        if (component instanceof JComponent) {
            JComponent jc = (JComponent) component;
            if (jc.getBorder() instanceof TitledBorder) {
                TitledBorder titled = (TitledBorder) jc.getBorder();
                titled.setTitleColor(AeUiTheme.muted());
                titled.setBorder(new LineBorder(AeUiTheme.border()));
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) themeTree(child);
        }
    }

    private static final class Section {
        final String id;
        final String title;
        final String detail;
        final JComponent component;
        final SectionButton button;

        Section(String id, String title, String detail, JComponent component) {
            this.id = id;
            this.title = title;
            this.detail = detail;
            this.component = component;
            this.button = new SectionButton(title, detail);
        }
    }

    private static final class SectionButton extends JToggleButton {
        private final String title;
        private final String detail;
        private Color titleColor = Color.BLACK;
        private Color detailColor = Color.GRAY;

        SectionButton(String title, String detail) {
            this.title = title;
            this.detail = detail;
            setContentAreaFilled(false);
            setOpaque(true);
            setFocusPainted(false);
            setPreferredSize(new Dimension(210, 50));
            setMinimumSize(new Dimension(120, 50));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            setToolTipText(detail);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            Insets in = getInsets();
            int x = in.left;
            int y = in.top;
            g.setFont(new Font("Dialog", Font.BOLD, 12));
            FontMetrics titleMetrics = g.getFontMetrics();
            g.setColor(titleColor);
            g.drawString(ellipsize(title, titleMetrics, Math.max(20, getWidth() - in.left - in.right)),
                    x, y + titleMetrics.getAscent());
            g.setFont(SMALL);
            FontMetrics detailMetrics = g.getFontMetrics();
            g.setColor(detailColor);
            g.drawString(ellipsize(detail, detailMetrics, Math.max(20, getWidth() - in.left - in.right)),
                    x, y + titleMetrics.getHeight() + detailMetrics.getAscent());
            g.dispose();
        }

        private static String ellipsize(String value, FontMetrics fm, int width) {
            if (value == null) return "";
            if (fm.stringWidth(value) <= width) return value;
            String suffix = "…";
            int end = value.length();
            while (end > 0 && fm.stringWidth(value.substring(0, end) + suffix) > width) end--;
            return value.substring(0, Math.max(0, end)) + suffix;
        }
    }
}
