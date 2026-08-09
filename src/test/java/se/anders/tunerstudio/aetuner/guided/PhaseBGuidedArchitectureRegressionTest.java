package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class PhaseBGuidedArchitectureRegressionTest {
    private PhaseBGuidedArchitectureRegressionTest() { }

    public static void main(String[] args) throws Exception {
        coordinatorUsesExtractedCollaborators();
        coordinatorKeepsStandalonePublicBoundary();
        System.out.println("PhaseBGuidedArchitectureRegressionTest passed");
    }

    private static void coordinatorUsesExtractedCollaborators() throws Exception {
        require(fieldType("roadBaseline") == RoadBaselineTracker.class,
                "road baseline logic is no longer delegated to RoadBaselineTracker");
        require(fieldType("mapCatchup") == MapCatchupMeasurement.class,
                "MAP catch-up logic is no longer delegated to MapCatchupMeasurement");
        require(fieldType("openingDetector") == PedalOpeningDetector.class,
                "opening logic is no longer delegated to PedalOpeningDetector");
        require(fieldType("groups") == BlendDurationComparabilityGroups.class,
                "comparability logic is no longer delegated to BlendDurationComparabilityGroups");

        Method plateau = PedalPlateauDetector.class.getDeclaredMethod(
                "evaluate", java.util.List.class, double.class, long.class);
        require(plateau != null,
                "PedalPlateauDetector extraction boundary disappeared");
        Method trace = GuidedAttemptTrace.class.getDeclaredMethod(
                "build", String.class, java.util.List.class,
                BlendDurationCaptureConfig.class,
                GuidedVehicleTestLimits.Snapshot.class,
                double.class, double.class, double.class, double.class,
                LiveSample.class, double.class, LiveSample.class, LiveSample.class);
        require(trace != null,
                "GuidedAttemptTrace extraction boundary disappeared");
    }

    private static void coordinatorKeepsStandalonePublicBoundary() throws Exception {
        Method start = BlendDurationGuidedSession.class.getDeclaredMethod(
                "start", BlendDurationCaptureConfig.class);
        Method snapshot = BlendDurationGuidedSession.class.getDeclaredMethod("snapshot");
        require(start != null,
                "Blend Duration Guided coordinator lost standalone capture config boundary");
        require(snapshot.getReturnType() == GuidedSessionSnapshot.class,
                "Blend Duration Guided coordinator lost standalone snapshot boundary");
        Field state = BlendDurationGuidedSession.class.getDeclaredField("state");
        require(state.getType() == GuidedCaptureState.class,
                "Blend Duration Guided coordinator lost standalone state type");
    }

    private static Class<?> fieldType(String name) throws Exception {
        return BlendDurationGuidedSession.class.getDeclaredField(name).getType();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
