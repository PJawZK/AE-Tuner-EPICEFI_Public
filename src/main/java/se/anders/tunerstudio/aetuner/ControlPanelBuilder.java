package se.anders.tunerstudio.aetuner;

import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
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

        JPanel rowOne = wrappingRow();
        rowOne.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        rowOne.add(reconnectButton);
        rowOne.add(readProjectButton);
        rowOne.add(saveCsvButton);
        rowOne.add(suggestTableButton);
        rowOne.add(suggestMapEstimateButton);
        rowOne.add(suggestBlendButton);
        rowOne.add(sessionReviewButton);
        rowOne.add(resetButton);

        JPanel rowTwo = wrappingRow();
        rowTwo.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        rowTwo.add(settingGroup("Manual TPSdot threshold %/s:", thresholdField));
        rowTwo.add(settingGroup("Calibration seconds:", calibrationSeconds));
        rowTwo.add(calibrateButton);
        rowTwo.add(applyCalibrationButton);

        JPanel rowThree = wrappingRow();
        rowThree.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        rowThree.add(settingGroup("MAP draft minimum samples/cell:", mapMinimumSamples));
        rowThree.add(settingGroup("Turbo MAP cap kPa (TPS >=33.5%):", mapCapField));

        panel.add(rowOne);
        panel.add(rowTwo);
        panel.add(rowThree);
        return panel;
    }

    private static JPanel wrappingRow() {
        return new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 3));
    }

    private static JPanel settingGroup(String label, Component editor) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        group.add(new JLabel(label));
        group.add(editor);
        return group;
    }
}
