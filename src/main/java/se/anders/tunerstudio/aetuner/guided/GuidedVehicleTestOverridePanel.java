package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.FlowLayout;

/**
 * Non-persistent test controls for the next Guided Capture session.
 */
public final class GuidedVehicleTestOverridePanel extends JPanel {
    private final JCheckBox enabled =
            new JCheckBox("Enable vehicle-test overrides", false);
    private final JSpinner detectorConfirm = spinner(0.55, 0.20, 1.50, 0.05);
    private final JSpinner targetAcquisition = spinner(1.00, 0.50, 2.50, 0.05);
    private final JSpinner mapCatchup = spinner(1.20, 0.50, 3.00, 0.05);
    private final JSpinner tpsTolerance = spinner(3.00, 0.50, 10.00, 0.25);
    private final JSpinner boundaryEpsilon = spinner(0.05, 0.00, 0.50, 0.01);
    private final JSpinner localOnset = spinner(2.00, 0.50, 10.00, 0.25);
    private final JButton restore = new JButton("Restore candidate defaults");
    private final JLabel status = new JLabel();

    public GuidedVehicleTestOverridePanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder(
                "Vehicle-test overrides — session-only; next Guided Session"));

        JPanel first = row();
        first.add(enabled);
        first.add(new JLabel("Detector confirm s"));
        first.add(detectorConfirm);
        first.add(new JLabel("Target acquire s"));
        first.add(targetAcquisition);
        first.add(new JLabel("MAP catch-up s"));
        first.add(mapCatchup);
        add(first);

        JPanel second = row();
        second.add(new JLabel("TPS tolerance ±%"));
        second.add(tpsTolerance);
        second.add(new JLabel("Boundary epsilon %"));
        second.add(boundaryEpsilon);
        second.add(new JLabel("Local TPS onset rise"));
        second.add(localOnset);
        second.add(restore);
        add(second);

        JPanel third = row();
        third.add(status);
        add(third);

        enabled.setToolTipText(
                "Applies only to the next Guided Capture session. It never changes ECU settings.");
        detectorConfirm.setToolTipText(
                "Maximum delay between provisional TPS movement and ECU detector confirmation.");
        targetAcquisition.setToolTipText(
                "Maximum time to reach and stabilize inside the TPS target band.");
        mapCatchup.setToolTipText(
                "Maximum measured-MAP catch-up duration after the preserved gap anchor.");
        tpsTolerance.setToolTipText(
                "Accepted target band around the configured TPS target.");
        boundaryEpsilon.setToolTipText(
                "Small numerical allowance used only at the target-band boundary.");
        localOnset.setToolTipText(
                "Minimum baseline-to-current TPS rise that may provisionally start capture before the ECU detector.");

        enabled.addActionListener(event -> apply());
        detectorConfirm.addChangeListener(event -> apply());
        targetAcquisition.addChangeListener(event -> apply());
        mapCatchup.addChangeListener(event -> apply());
        tpsTolerance.addChangeListener(event -> apply());
        boundaryEpsilon.addChangeListener(event -> apply());
        localOnset.addChangeListener(event -> apply());
        restore.addActionListener(event -> restoreDefaults());
        apply();
    }

    private static JPanel row() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 2));
    }

    private static JSpinner spinner(double value, double minimum,
                                    double maximum, double step) {
        return new JSpinner(new SpinnerNumberModel(
                Double.valueOf(value), Double.valueOf(minimum),
                Double.valueOf(maximum), Double.valueOf(step)));
    }

    private void apply() {
        boolean on = enabled.isSelected();
        detectorConfirm.setEnabled(on);
        targetAcquisition.setEnabled(on);
        mapCatchup.setEnabled(on);
        tpsTolerance.setEnabled(on);
        boundaryEpsilon.setEnabled(on);
        localOnset.setEnabled(on);

        GuidedVehicleTestLimits.configurePending(on,
                number(detectorConfirm), number(targetAcquisition),
                number(mapCatchup), number(tpsTolerance),
                number(boundaryEpsilon), number(localOnset));
        GuidedVehicleTestLimits.Snapshot pending =
                GuidedVehicleTestLimits.pending();
        status.setText(on
                ? "TEST OVERRIDES WILL APPLY TO THE NEXT SESSION — "
                    + pending.summary()
                    + ". Changes never write the ECU."
                : "Candidate defaults will apply. Controls are not persisted and never write the ECU.");
    }

    private void restoreDefaults() {
        enabled.setSelected(false);
        detectorConfirm.setValue(
                Double.valueOf(GuidedVehicleTestLimits.DEFAULT_DETECTOR_CONFIRM_SECONDS));
        targetAcquisition.setValue(
                Double.valueOf(GuidedVehicleTestLimits.DEFAULT_TARGET_ACQUISITION_SECONDS));
        mapCatchup.setValue(
                Double.valueOf(GuidedVehicleTestLimits.DEFAULT_MAP_CATCHUP_SECONDS));
        tpsTolerance.setValue(
                Double.valueOf(GuidedVehicleTestLimits.DEFAULT_TPS_TOLERANCE));
        boundaryEpsilon.setValue(
                Double.valueOf(GuidedVehicleTestLimits.DEFAULT_TPS_BOUNDARY_EPSILON));
        localOnset.setValue(
                Double.valueOf(GuidedVehicleTestLimits.DEFAULT_LOCAL_TPS_ONSET_RISE));
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        apply();
    }

    private static double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    public boolean isEnabledForTest() {
        return enabled.isSelected();
    }

    String statusForTest() {
        return status.getText();
    }
}
