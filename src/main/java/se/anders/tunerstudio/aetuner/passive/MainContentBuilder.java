package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultCaret;

/** Builds the production-owned content used by the v0.19 Passive utility shell. */
final class MainContentBuilder {
    private MainContentBuilder() { }

    static final class Sections {
        final JComponent eventPreview;
        final JComponent notes;
        final JComponent guidance;

        Sections(JComponent eventPreview, JComponent notes, JComponent guidance) {
            this.eventPreview = eventPreview;
            this.notes = notes;
            this.guidance = guidance;
        }
    }

    static Sections build(JScrollPane channelScroll,
                          JTable channelTable,
                          JTextArea latestEventText,
                          JTextArea recommendationHistoryText,
                          EventPlotPanel plotPanel) {
        configureChannelTable(channelTable);
        channelTable.setFillsViewportHeight(true);
        channelTable.setAutoCreateRowSorter(false);
        channelTable.setFocusable(false);
        channelTable.setPreferredScrollableViewportSize(new Dimension(360, 250));
        channelScroll.setViewportView(channelTable);
        channelScroll.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder("Resolved Passive inputs"),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        channelScroll.setPreferredSize(new Dimension(390, 265));
        channelScroll.setMinimumSize(new Dimension(320, 180));
        channelScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        channelScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        channelScroll.getVerticalScrollBar().setUnitIncrement(18);
        channelScroll.getVerticalScrollBar().setBlockIncrement(90);

        latestEventText.setEditable(false);
        latestEventText.setLineWrap(true);
        latestEventText.setWrapStyleWord(true);
        latestEventText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        latestEventText.setFocusable(false);
        DefaultCaret notesCaret = (DefaultCaret) latestEventText.getCaret();
        notesCaret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JScrollPane eventScroll = new JScrollPane(latestEventText);
        eventScroll.setBorder(new LineBorder(AeUiTheme.border()));
        eventScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        eventScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        eventScroll.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, channelScroll, plotPanel);
        split.setResizeWeight(0.32);
        split.setDividerLocation(390);
        split.setOneTouchExpandable(true);
        split.setContinuousLayout(true);
        split.setBorder(null);

        JPanel liveDataPanel = card();
        liveDataPanel.add(split, BorderLayout.CENTER);

        JScrollPane guidanceScroll = new JScrollPane(recommendationHistoryText);
        guidanceScroll.setBorder(new LineBorder(AeUiTheme.border()));
        guidanceScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        guidanceScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        guidanceScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel notesCard = card();
        notesCard.add(eventScroll, BorderLayout.CENTER);
        JPanel guidanceCard = card();
        guidanceCard.add(guidanceScroll, BorderLayout.CENTER);
        return new Sections(liveDataPanel, notesCard, guidanceCard);
    }

    private static JPanel card() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new CompoundBorder(
                new LineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        return panel;
    }

    private static void configureChannelTable(JTable channelTable) {
        channelTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        channelTable.setRowHeight(21);
        channelTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        channelTable.getColumnModel().getColumn(1).setPreferredWidth(145);
        channelTable.getColumnModel().getColumn(2).setPreferredWidth(65);
        channelTable.getColumnModel().getColumn(3).setPreferredWidth(78);
    }
}
