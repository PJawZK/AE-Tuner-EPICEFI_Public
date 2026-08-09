package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;

public final class AeControllerBridge {
    private static final String PARAM_CYCLE_BINS = "tpsAeCycleCycleBins";
    private static final String PARAM_TPS_TO_BINS = "tpsAeCycleTpsToBins";
    private static final String PARAM_CYCLE_VALUES = "tpsAeCycleValues";
    private static final String PARAM_THRESHOLD_RPM_BINS = "tpsAeThresholdRpmBins";
    private static final String PARAM_THRESHOLD_VALUES = "tpsAeThresholdValue";
    private static final String PARAM_EXTRA_SHOT_MULT = "tpsExtraShotMult";
    private static final String PARAM_EXTRA_SHOT_TIMER = "tpsExtraShotTimer";
    private static final String PARAM_CLT_CORR_BINS = "aeCltCorrBins";
    private static final String PARAM_CLT_CORR = "aeCltCorr";
    private static final String PARAM_TPS_AE_ENABLED = "tpsAccelAeEnabled";
    private static final String PARAM_WALL_WETTING_ENABLED = "wallWettingAeEnabled";
    private static final String PARAM_WALL_MODEL = "complexWallModel";
    private static final String PARAM_EXTRA_SHOT_ENABLED = "tpsAccelExtraShot";
    private static final String PARAM_MAP_ESTIMATE_ENABLED = "useMapEstimateDuringTransient";
    private static final String PARAM_DYNAMIC_THRESHOLD = "tpsAeUseDynamicThreshold";
    private static final String PARAM_DYNAMIC_AVERAGE_STATIC = "tpsAeDynamicTresholdAverageStaticCurve";
    private static final String PARAM_WALL_TAU_TABLE = "wwTauMapTable";
    private static final String PARAM_WALL_BETA_TABLE = "wwBetaMapTable";
    private static final String PARAM_MAP_ESTIMATE_RPM_BINS = "mapEstimateRpmBins";
    private static final String PARAM_MAP_ESTIMATE_TPS_BINS = "mapEstimateTpsBins";
    private static final String PARAM_MAP_ESTIMATE_TABLE = "mapEstimateTable";
    private static final String PARAM_BLEND_DURATION_RPM_BINS = "predictiveMapBlendDurationBins";
    private static final String PARAM_BLEND_DURATION_VALUES = "predictiveMapBlendDurationValues";

    private final ControllerAccess controllerAccess;

    public AeControllerBridge(ControllerAccess controllerAccess) {
        if (controllerAccess == null) {
            throw new IllegalArgumentException("Controller access is required");
        }
        this.controllerAccess = controllerAccess;
    }

    public AeProjectSnapshot readSnapshot() throws ControllerException {
        ControllerParameterServer server = controllerAccess.getControllerParameterServer();
        if (server == null) {
            throw new ControllerException("TunerStudio did not provide a controller-parameter server");
        }

        String configurationName = findEpicEfiConfiguration(server);
        return new AeProjectSnapshot(
                configurationName,
                readAxis(server, configurationName, PARAM_CYCLE_BINS),
                readAxis(server, configurationName, PARAM_TPS_TO_BINS),
                readTable(server, configurationName, PARAM_CYCLE_VALUES),
                readAxis(server, configurationName, PARAM_THRESHOLD_RPM_BINS),
                readAxis(server, configurationName, PARAM_THRESHOLD_VALUES),
                readScalar(server, configurationName, PARAM_EXTRA_SHOT_MULT),
                readScalar(server, configurationName, PARAM_EXTRA_SHOT_TIMER),
                readAxis(server, configurationName, PARAM_CLT_CORR_BINS),
                readAxis(server, configurationName, PARAM_CLT_CORR),
                readBooleanOptional(server, configurationName, PARAM_TPS_AE_ENABLED, true),
                readBooleanOptional(server, configurationName, PARAM_WALL_WETTING_ENABLED, false),
                readStringOptional(server, configurationName, PARAM_WALL_MODEL, "unknown"),
                readBooleanOptional(server, configurationName, PARAM_EXTRA_SHOT_ENABLED, false),
                readBooleanOptional(server, configurationName, PARAM_MAP_ESTIMATE_ENABLED, false),
                readBooleanOptional(server, configurationName, PARAM_DYNAMIC_THRESHOLD, false),
                readBooleanOptional(server, configurationName, PARAM_DYNAMIC_AVERAGE_STATIC, false),
                readTableOptional(server, configurationName, PARAM_WALL_TAU_TABLE),
                readTableOptional(server, configurationName, PARAM_WALL_BETA_TABLE),
                readAxisOptional(server, configurationName, PARAM_MAP_ESTIMATE_RPM_BINS),
                readAxisOptional(server, configurationName, PARAM_MAP_ESTIMATE_TPS_BINS),
                readTableOptional(server, configurationName, PARAM_MAP_ESTIMATE_TABLE),
                readAxisOptional(server, configurationName, PARAM_BLEND_DURATION_RPM_BINS),
                readAxisOptional(server, configurationName, PARAM_BLEND_DURATION_VALUES));
    }

    private String findEpicEfiConfiguration(ControllerParameterServer server)
            throws ControllerException {
        String[] configurationNames = controllerAccess.getEcuConfigurationNames();
        if (configurationNames == null || configurationNames.length == 0) {
            configurationNames = new String[]{"Main Controller", "mainController", "MainController", "ECU", "Controller"};
        }

        ControllerException lastError = null;
        for (String configurationName : configurationNames) {
            if (configurationName == null || configurationName.trim().isEmpty()) {
                continue;
            }
            try {
                server.getControllerParameter(configurationName, PARAM_CYCLE_BINS);
                server.getControllerParameter(configurationName, PARAM_THRESHOLD_VALUES);
                return configurationName;
            } catch (ControllerException ex) {
                lastError = ex;
            }
        }

        String message = "Could not find the active EpicEFI configuration containing TPS AE parameters";
        if (lastError != null && lastError.getMessage() != null) {
            message += ": " + lastError.getMessage();
        }
        throw new ControllerException(message);
    }

    private static double readScalar(ControllerParameterServer server,
                                     String configurationName,
                                     String parameterName)
            throws ControllerException {
        ControllerParameter parameter = server.getControllerParameter(configurationName, parameterName);
        if (parameter == null) {
            return Double.NaN;
        }
        return parameter.getScalarValue();
    }

    private static boolean readBooleanOptional(ControllerParameterServer server,
                                               String configurationName,
                                               String parameterName,
                                               boolean fallback) {
        try {
            ControllerParameter parameter = server.getControllerParameter(configurationName, parameterName);
            if (parameter == null) {
                return fallback;
            }

            String rawValue = parameter.getStringValue();
            if (rawValue != null && rawValue.trim().length() > 0) {
                String value = rawValue.trim().replace("\"", "").toLowerCase(java.util.Locale.ROOT);

                // Bit fields are returned differently by different TunerStudio/API builds.
                // Prefer the displayed description, including numeric descriptions, instead
                // of treating the containing bit-field word as a boolean scalar.
                if ("true".equals(value) || "on".equals(value) || "enabled".equals(value)
                        || "yes".equals(value) || value.startsWith("true ")
                        || value.startsWith("on ") || value.startsWith("enabled ")) {
                    return true;
                }
                if ("false".equals(value) || "off".equals(value) || "disabled".equals(value)
                        || "no".equals(value) || value.startsWith("false ")
                        || value.startsWith("off ") || value.startsWith("disabled ")) {
                    return false;
                }

                try {
                    double numericDescription = Double.parseDouble(value);
                    if (Math.abs(numericDescription) < 1.0e-9) {
                        return false;
                    }
                    if (Math.abs(numericDescription - 1.0) < 1.0e-9) {
                        return true;
                    }
                    return fallback;
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }

            // Only accept an unambiguous standalone 0/1 scalar. Some API builds
            // expose the whole containing bit-field word here, which must not be
            // interpreted as true merely because another bit is set.
            double scalar = parameter.getScalarValue();
            if (Math.abs(scalar) < 1.0e-9) {
                return false;
            }
            if (Math.abs(scalar - 1.0) < 1.0e-9) {
                return true;
            }
            return fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String readStringOptional(ControllerParameterServer server,
                                             String configurationName,
                                             String parameterName,
                                             String fallback) {
        try {
            ControllerParameter parameter = server.getControllerParameter(configurationName, parameterName);
            if (parameter == null || parameter.getStringValue() == null) {
                return fallback;
            }
            String value = parameter.getStringValue().trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            return value.length() == 0 ? fallback : value;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static double[] readAxis(ControllerParameterServer server,
                                     String configurationName,
                                     String parameterName)
            throws ControllerException {
        return flatten(server.getControllerParameter(configurationName, parameterName));
    }

    private static double[][] readTable(ControllerParameterServer server,
                                        String configurationName,
                                        String parameterName)
            throws ControllerException {
        ControllerParameter parameter = server.getControllerParameter(configurationName, parameterName);
        if (parameter == null || parameter.getArrayValues() == null) {
            return new double[0][0];
        }
        return cloneTable(parameter.getArrayValues());
    }


    private static double[] readAxisOptional(ControllerParameterServer server,
                                             String configurationName,
                                             String parameterName) {
        try {
            return readAxis(server, configurationName, parameterName);
        } catch (Exception ex) {
            return new double[0];
        }
    }

    private static double[][] readTableOptional(ControllerParameterServer server,
                                                String configurationName,
                                                String parameterName) {
        try {
            return readTable(server, configurationName, parameterName);
        } catch (Exception ex) {
            return new double[0][0];
        }
    }

    private static double[][] cloneTable(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int row = 0; row < values.length; row++) {
            copy[row] = values[row] == null ? new double[0] : values[row].clone();
        }
        return copy;
    }

    private static double[] flatten(ControllerParameter parameter) {
        if (parameter == null || parameter.getArrayValues() == null) {
            return new double[0];
        }
        double[][] array = parameter.getArrayValues();
        int count = 0;
        for (double[] row : array) {
            if (row != null) {
                count += row.length;
            }
        }
        double[] result = new double[count];
        int index = 0;
        for (double[] row : array) {
            if (row != null) {
                for (double value : row) {
                    result[index++] = value;
                }
            }
        }
        return result;
    }
}
