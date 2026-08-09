package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.DefaultCaret;

final class MainContentBuilder {
    private MainContentBuilder() {
    }

    static void configure(JScrollPane mainScroll,
                          JScrollPane channelScroll,
                          JTable channelTable,
                          JTextArea latestEventText,
                          JTextArea recommendationHistoryText,
                          JTabbedPane lowerTabs,
                          EventPlotPanel plotPanel,
                          JComponent statusPanel) {
        configureChannelTable(channelTable);
        channelTable.setFillsViewportHeight(true);
        channelTable.setAutoCreateRowSorter(false);
        channelTable.setFocusable(false);
        channelTable.setPreferredScrollableViewportSize(new Dimension(420, 250));
        channelScroll.setViewportView(channelTable);
        channelScroll.setBorder(BorderFactory.createTitledBorder("Resolved live channels"));
        channelScroll.setPreferredSize(new Dimension(450, 265));
        channelScroll.setMinimumSize(new Dimension(400, 180));
        channelScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        channelScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
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
        eventScroll.setBorder(BorderFactory.createEmptyBorder());
        eventScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        eventScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        eventScroll.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, channelScroll, plotPanel);
        split.setResizeWeight(0.0);
        split.setDividerLocation(455);
        split.setOneTouchExpandable(true);
        split.setContinuousLayout(true);
        split.setBorder(null);

        JPanel liveDataPanel = new JPanel(new BorderLayout());
        liveDataPanel.add(split, BorderLayout.CENTER);

        lowerTabs.addTab("Live channels & event preview", liveDataPanel);
        lowerTabs.addTab("Latest event / session notes", eventScroll);
        JScrollPane guidanceScroll = new JScrollPane(recommendationHistoryText);
        guidanceScroll.setBorder(BorderFactory.createEmptyBorder());
        guidanceScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        guidanceScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        guidanceScroll.getVerticalScrollBar().setUnitIncrement(16);
        lowerTabs.addTab("Session Guidance", guidanceScroll);
        lowerTabs.setToolTipTextAt(0, "Live channel values and the latest captured transient plot.");
        lowerTabs.setToolTipTextAt(1, "Detailed event text, draft reports, CSV status, and session notes.");
        lowerTabs.setToolTipTextAt(2, "Temporary recommendation transitions for this plugin session only.");
        lowerTabs.setFocusable(false);
        lowerTabs.setRequestFocusEnabled(false);
        lowerTabs.setPreferredSize(new Dimension(1000, 330));
        lowerTabs.setMinimumSize(new Dimension(650, 260));
        lowerTabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));

        ViewportWidthPanel scrollContent = new ViewportWidthPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        statusPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        lowerTabs.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        scrollContent.add(statusPanel);
        scrollContent.add(lowerTabs);

        mainScroll.setViewportView(scrollContent);
        mainScroll.setBorder(null);
        mainScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mainScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        mainScroll.getVerticalScrollBar().setUnitIncrement(18);
        mainScroll.getVerticalScrollBar().setBlockIncrement(90);
    }

    private static void configureChannelTable(JTable channelTable) {
        channelTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        channelTable.setRowHeight(19);
        channelTable.getColumnModel().getColumn(0).setPreferredWidth(125);
        channelTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        channelTable.getColumnModel().getColumn(2).setPreferredWidth(65);
        channelTable.getColumnModel().getColumn(3).setPreferredWidth(78);
    }
}
