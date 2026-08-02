package se.anders.tunerstudio.aetuner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

enum CardState { GOOD, ACTIVE, INFO, OFF, WAITING, WARNING, ERROR }

final class StatusCard extends JPanel {
    private final JLabel value = new JLabel();
    private String lastText;
    private CardState lastState;

    StatusCard(String title, int width, int height) {
        super(new BorderLayout(4, 3));
        Dimension fixedSize = new Dimension(width, height);
        setPreferredSize(fixedSize);
        setMinimumSize(fixedSize);
        setMaximumSize(fixedSize);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        value.setFocusable(false);
        value.setVerticalAlignment(JLabel.TOP);
        value.setHorizontalAlignment(JLabel.LEFT);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 12f));
        add(titleLabel, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
        setValue("Waiting", CardState.WAITING);
    }

    void setValueFontSize(float size) {
        value.setFont(value.getFont().deriveFont(Font.BOLD, size));
    }

    void setValue(String text, CardState state) {
        String normalized = text == null ? "" : text.replace("  •  ", "\n");
        if (normalized.equals(lastText) && state == lastState) {
            return;
        }
        lastText = normalized;
        lastState = state;
        value.setText(toHtml(normalized));
        Color background;
        Color foreground = Color.BLACK;
        switch (state) {
            case GOOD: background = new Color(220, 242, 220); break;
            case ACTIVE: background = new Color(196, 235, 255); break;
            case INFO: background = new Color(226, 235, 248); break;
            case OFF: background = new Color(236, 236, 236); break;
            case WARNING: background = new Color(255, 238, 190); break;
            case ERROR: background = new Color(255, 210, 210); break;
            default: background = new Color(245, 245, 245); break;
        }
        if (!background.equals(getBackground())) {
            setBackground(background);
        }
        value.setForeground(foreground);
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
