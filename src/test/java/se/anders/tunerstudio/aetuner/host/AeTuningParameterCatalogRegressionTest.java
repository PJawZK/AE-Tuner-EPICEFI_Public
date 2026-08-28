package se.anders.tunerstudio.aetuner.host;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AeTuningParameterCatalogRegressionTest {
    private AeTuningParameterCatalogRegressionTest() { }

    public static void main(String[] args) {
        currentEngagementParametersUseExactControllerNames();
        engagementCatalogPreservesFirstUseRepresentations();
        everyAeSubsystemHasCataloguedParameters();
        detectorSettingsAreUpstreamEvidenceDependencies();
        nonAeParametersStayOutsideTheCatalog();
        controllerNamesAreUnique();
        System.out.println("AeTuningParameterCatalogRegressionTest passed");
    }

    private static void currentEngagementParametersUseExactControllerNames() {
        requireParameter(AeParameterNames.TPS_AE_DETECT_MODE,
                "Engagement model", AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION);
        requireParameter(AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                "Delta window", AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION);
        requireParameter(AeParameterNames.TPS_ACCEL_LOOKBACK,
                "Sample Length", AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION);
        requireParameter(AeParameterNames.TPS_AE_THRESHOLD_VALUES,
                "TPS AE Rate of change vs RPM — threshold",
                AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION);
        require("tpsAeDynamicTresholdAverageStaticCurve".equals(
                        AeParameterNames.TPS_AE_DYNAMIC_THRESHOLD_AVERAGE_STATIC_CURVE),
                "controller's authoritative Treshold spelling must not be silently corrected");
    }

    private static void engagementCatalogPreservesFirstUseRepresentations() {
        requireShape(AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                AeTuningParameterCatalog.Shape.SCALAR);
        requireShape(AeParameterNames.TPS_ACCEL_LOOKBACK,
                AeTuningParameterCatalog.Shape.SCALAR);
        requireShape(AeParameterNames.TPS_AE_DETECT_MODE,
                AeTuningParameterCatalog.Shape.ENUM);
        requireShape(AeParameterNames.TPS_AE_FAST_CALLBACK,
                AeTuningParameterCatalog.Shape.BOOLEAN);
        requireShape(AeParameterNames.TPS_AE_THRESHOLD_VALUES,
                AeTuningParameterCatalog.Shape.CURVE_VALUES);
    }

    private static void everyAeSubsystemHasCataloguedParameters() {
        for (AeTuningParameterCatalog.Subsystem subsystem
                : AeTuningParameterCatalog.Subsystem.values()) {
            List<AeTuningParameterCatalog.Parameter> parameters =
                    AeTuningParameterCatalog.forSubsystem(subsystem);
            require(!parameters.isEmpty(),
                    "general AE catalog omitted subsystem " + subsystem);
        }
    }

    private static void detectorSettingsAreUpstreamEvidenceDependencies() {
        AeTuningParameterCatalog.Parameter model =
                AeTuningParameterCatalog.find(AeParameterNames.TPS_AE_DETECT_MODE);
        AeTuningParameterCatalog.Parameter window =
                AeTuningParameterCatalog.find(AeParameterNames.TPS_AE_DELTA_WINDOW_MS);
        AeTuningParameterCatalog.Parameter threshold =
                AeTuningParameterCatalog.find(AeParameterNames.TPS_AE_THRESHOLD_VALUES);
        AeTuningParameterCatalog.Parameter blend =
                AeTuningParameterCatalog.find(AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES);
        AeTuningParameterCatalog.Parameter tpsFuel =
                AeTuningParameterCatalog.find(AeParameterNames.TPS_AE_CYCLE_VALUES);

        require(AeTuningParameterCatalog.invalidatesEvidence(model, window),
                "changing engagement model must invalidate detector-timing evidence");
        require(AeTuningParameterCatalog.invalidatesEvidence(window, threshold),
                "changing detector timing must invalidate threshold evidence");
        require(AeTuningParameterCatalog.invalidatesEvidence(model, blend),
                "changing engagement model must invalidate downstream MAP Predict evidence");
        require(AeTuningParameterCatalog.invalidatesEvidence(window, tpsFuel),
                "changing Delta Window must invalidate downstream TPS AE fuel evidence");
        require(!AeTuningParameterCatalog.invalidatesEvidence(tpsFuel, model),
                "downstream TPS fuel shape must not invalidate upstream detector evidence");
    }

    private static void nonAeParametersStayOutsideTheCatalog() {
        require(AeTuningParameterCatalog.find("veTable") == null,
                "VE must remain outside AE Tuner tuning scope");
        require(AeTuningParameterCatalog.find("ignitionTable") == null,
                "ignition must remain outside AE Tuner tuning scope");
    }

    private static void controllerNamesAreUnique() {
        Set<String> names = new HashSet<String>();
        for (AeTuningParameterCatalog.Parameter parameter : AeTuningParameterCatalog.all()) {
            require(names.add(parameter.getControllerName()),
                    "duplicate controller parameter in AE catalog: "
                            + parameter.getControllerName());
        }
    }

    private static void requireParameter(String controllerName, String displayName,
                                         AeTuningParameterCatalog.Subsystem subsystem) {
        AeTuningParameterCatalog.Parameter parameter =
                AeTuningParameterCatalog.find(controllerName);
        require(parameter != null, "catalog omitted " + controllerName);
        require(displayName.equals(parameter.getDisplayName()),
                "catalog display name changed for " + controllerName);
        require(parameter.getSubsystem() == subsystem,
                "catalog subsystem changed for " + controllerName);
    }

    private static void requireShape(String controllerName,
                                     AeTuningParameterCatalog.Shape shape) {
        AeTuningParameterCatalog.Parameter parameter =
                AeTuningParameterCatalog.find(controllerName);
        require(parameter != null, "catalog omitted " + controllerName);
        require(parameter.getShape() == shape,
                "catalog representation changed for " + controllerName
                        + ": expected " + shape + " but was " + parameter.getShape());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
