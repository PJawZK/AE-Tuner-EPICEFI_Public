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

public final class PhaseA2AdaptiveTypeMigrationTest {
    public static void main(String[] args) throws Exception {
        adaptiveSessionUsesStandaloneTypes();
        roadGroupingUsesStandaloneAttempt();
        adaptiveEvidenceBoundaryUsesStandaloneSnapshot();
        System.out.println("PhaseA2AdaptiveTypeMigrationTest passed");
    }

    private static void adaptiveSessionUsesStandaloneTypes() throws Exception {
        Method start = BlendDurationGuidedSession.class.getDeclaredMethod(
                "start", BlendDurationCaptureConfig.class);
        require(start != null, "adaptive start no longer accepts standalone capture config");
        Method snapshot = BlendDurationGuidedSession.class.getDeclaredMethod("snapshot");
        require(snapshot.getReturnType() == GuidedSessionSnapshot.class,
                "adaptive snapshot still exposes legacy snapshot type");
        Field state = BlendDurationGuidedSession.class.getDeclaredField("state");
        require(state.getType() == GuidedCaptureState.class,
                "adaptive state still uses legacy state type");
    }

    private static void roadGroupingUsesStandaloneAttempt() throws Exception {
        Method assign = BlendDurationComparabilityGroups.class.getDeclaredMethod(
                "assign", BlendDurationAttempt.class);
        require(assign != null,
                "Blend Duration comparability groups do not accept standalone attempts");
    }

    private static void adaptiveEvidenceBoundaryUsesStandaloneSnapshot()
            throws Exception {
        Method record = GuidedEvidenceRecorder.class.getDeclaredMethod(
                "record", GuidedOutcome.class, GuidedSessionSnapshot.class);
        Method finish = GuidedEvidenceRecorder.class.getDeclaredMethod(
                "finish", GuidedSessionSnapshot.class);
        Method report = GuidedEvidenceRecorder.class.getDeclaredMethod(
                "reportText", String.class, GuidedSessionSnapshot.class);
        require(record != null && finish != null && report != null,
                "adaptive evidence boundary still exposes legacy snapshot types");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
