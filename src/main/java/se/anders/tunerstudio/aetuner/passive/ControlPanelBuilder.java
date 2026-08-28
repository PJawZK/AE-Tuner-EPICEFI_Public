package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

final class ControlPanelBuilder {
    private ControlPanelBuilder() {
    }

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

        sessionReviewButton.setText("Export Passive Session");
        sessionReviewButton.setToolTipText(
                "Export all Passive session evidence into one session folder");
        // Retain the legacy button in one component hierarchy for lifecycle/listener
        // compatibility, but keep it invisible so Passive exposes one export action.
        saveCsvButton.setVisible(false);

        JPanel actions = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 3));
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

        // Threshold/noise calibration and Passive analysis parameters now live
        // under Passive Analysis -> Setup / Calibration. Keeping them out of
        // the global action strip makes their scope explicit.
        return panel;
    }
}
