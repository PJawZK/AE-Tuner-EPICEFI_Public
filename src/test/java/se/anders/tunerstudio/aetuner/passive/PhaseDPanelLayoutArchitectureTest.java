package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PhaseDPanelLayoutArchitectureTest {
    private PhaseDPanelLayoutArchitectureTest() { }

    public static void main(String[] args) throws Exception {
        layoutImplementationLivesOutsideHostPanel();
        layoutCollaboratorIsStateless();
        System.out.println("PhaseDPanelLayoutArchitectureTest passed");
    }

    private static void layoutImplementationLivesOutsideHostPanel() {
        require(hasMethod(AeTunerPanel.class, "buildLayout"),
                "host panel lost its small layout orchestration entry point");
        String[] moved = new String[]{
                "buildStatusPanel", "buildOverviewPanel", "buildCardRow",
                "buildTechnicalStatusPanel", "addTechnicalCard",
                "buildTechnicalSection", "setStatusTabsHeight", "setFixedHeight"
        };
        for (String name : moved) {
            require(!hasMethod(AeTunerPanel.class, name),
                    "AeTunerPanel still owns layout implementation method " + name);
        }
    }

    private static void layoutCollaboratorIsStateless() {
        require(Modifier.isFinal(PassivePanelLayout.class.getModifiers()),
                "PassivePanelLayout should remain a final composition collaborator");
        require(PassivePanelLayout.class.getDeclaredFields().length == 0,
                "PassivePanelLayout acquired runtime state; it should only arrange panel-owned components");
        require(hasMethod(PassivePanelLayout.class, "install"),
                "PassivePanelLayout does not expose its composition entry point");
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) return true;
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
