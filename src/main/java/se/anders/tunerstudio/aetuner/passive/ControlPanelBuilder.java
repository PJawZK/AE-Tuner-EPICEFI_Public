package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.ui.WrapLayout;
import se.anders.tunerstudio.aetuner.ui.WrappingColumnPanel;

import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/** Presentation-only composition for the Passive utility action bar. */
final class ControlPanelBuilder {
    private ControlPanelBuilder() { }

    static JPanel build(JButton reconnectButton,
                        JButton readProjectButton,
                        JButton saveCsvButton,
                        JButton suggestTableButton,
                        JButton suggestMapEstimateButton,
                        JButton suggestBlendButton,
                        JButton sessionReviewButton,
                        JButton resetButton,
                        JTextField thresholdField,
                        JSpinner calibrationSeconds,
                        JButton calibrateButton,
                        JButton applyCalibrationButton,
                        JSpinner mapMinimumSamples,
                        JTextField mapCapField) {
        JPanel panel = new WrappingColumnPanel();
        panel.setOpaque(false);

        reconnectButton.setText("Reconnect");
        readProjectButton.setText("Read Working Tune");
        suggestTableButton.setText("Copy TPS AE Draft");
        suggestMapEstimateButton.setText("Copy MAP Estimate Draft");
        suggestBlendButton.setText("Blend Duration Info");
        sessionReviewButton.setText("Export Passive Session");
        resetButton.setText("Reset Session");
        sessionReviewButton.setToolTipText(
                "Export all retained Passive session evidence into one session folder");

        // Retain the legacy direct CSV action object for listener/lifecycle
        // compatibility, but the v0.19 utility shell exposes one session export.
        saveCsvButton.setVisible(false);

        JPanel actions = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 3));
        actions.setOpaque(false);
        actions.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        actions.add(reconnectButton);
        actions.add(readProjectButton);
        actions.add(suggestTableButton);
        actions.add(suggestMapEstimateButton);
        actions.add(suggestBlendButton);
        actions.add(sessionReviewButton);
        actions.add(saveCsvButton);
        actions.add(resetButton);
        panel.add(actions);
        return panel;
    }
}
