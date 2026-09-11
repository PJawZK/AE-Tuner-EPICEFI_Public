package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import se.anders.tunerstudio.aetuner.ui.AeUtilityWorkspacePanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

enum CardState { GOOD, ACTIVE, INFO, OFF, WAITING, WARNING, ERROR }

final class StatusCard extends JPanel {
    private final JLabel title = new JLabel();
    private final JLabel value = new JLabel();
    private String lastText;
    private CardState lastState;
    private AeUiTheme.Side lastTheme;

    StatusCard(String titleText, int width, int height) {
        super(new BorderLayout(4, 3));
        Dimension fixedSize = new Dimension(width, height);
        setPreferredSize(fixedSize);
        setMinimumSize(fixedSize);
        setMaximumSize(fixedSize);
        putClientProperty(AeUtilityWorkspacePanel.PRESERVE_BACKGROUND, Boolean.TRUE);
        title.setText(titleText);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        value.setFocusable(false);
        value.setVerticalAlignment(JLabel.TOP);
        value.setHorizontalAlignment(JLabel.LEFT);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 12f));
        add(title, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
        setValue("Waiting", CardState.WAITING);
    }

    void setValueFontSize(float size) {
        value.setFont(value.getFont().deriveFont(Font.BOLD, size));
    }

    void setValue(String text, CardState state) {
        String normalized = text == null ? "" : text.replace("  •  ", "\n");
        AeUiTheme.Side theme = AeUiTheme.side();
        if (normalized.equals(lastText) && state == lastState && theme == lastTheme) return;
        lastText = normalized;
        lastState = state;
        lastTheme = theme;
        value.setText(toHtml(normalized));

        Color background;
        Color foreground = AeUiTheme.text();
        switch (state) {
            case GOOD:
                background = AeUiTheme.softGreen();
                break;
            case ACTIVE:
                background = AeUiTheme.softBlue();
                break;
            case INFO:
                background = AeUiTheme.neutralSoft();
                break;
            case OFF:
                background = AeUiTheme.disabled();
                foreground = AeUiTheme.muted();
                break;
            case WARNING:
                background = AeUiTheme.softAmber();
                break;
            case ERROR:
                background = AeUiTheme.softRed();
                break;
            default:
                background = AeUiTheme.neutralSoft();
                foreground = AeUiTheme.muted();
                break;
        }
        setBackground(background);
        title.setForeground(AeUiTheme.muted());
        value.setForeground(foreground);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        setOpaque(true);
        repaint();
    }

    private static String toHtml(String text) {
        String escaped = text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html>" + escaped + "</html>";
    }
}
