package se.anders.tunerstudio.aetuner;

import com.efiAnalytics.plugin.ApplicationPlugin;
import com.efiAnalytics.plugin.ecu.ControllerAccess;

import javax.swing.JComponent;

/**
 * TunerStudio shell for AE Tuner (EPICEFI).
 */
public final class AeTunerPlugin implements ApplicationPlugin {
    public static final String VERSION = "0.3.19";

    private final AeTunerPanel panel = new AeTunerPanel();
    private ControllerAccess controllerAccess;

    @Override
    public String getIdName() {
        return "aeTunerEpicefi";
    }

    @Override
    public int getPluginType() {
        return PERSISTENT_DIALOG_PANEL;
    }

    @Override
    public String getDisplayName() {
        return "AE Tuner (EPICEFI)";
    }

    @Override
    public String getDescription() {
        return "Read-only EPICEFI transient-fueling tuner for MAP Predict, Wall Wetting, Instant Fuel, and legacy TPS cycle AE diagnostics.";
    }

    @Override
    public void initialize(ControllerAccess controllerAccess) {
        this.controllerAccess = controllerAccess;
        panel.connectController(controllerAccess);
    }

    @Override
    public boolean displayPlugin(String controllerSignature) {
        return true;
    }

    @Override
    public boolean isMenuEnabled() {
        return true;
    }

    @Override
    public String getAuthor() {
        return "Anders Wedin";
    }

    @Override
    public JComponent getPluginPanel() {
        return panel;
    }

    @Override
    public void close() {
        controllerAccess = null;
        panel.disposePanel();
    }

    @Override
    public String getHelpUrl() {
        return "";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public double getRequiredPluginSpec() {
        return 1.0;
    }
}
