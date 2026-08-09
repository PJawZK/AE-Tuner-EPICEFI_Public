package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class PhaseDAdvisoryActionsArchitectureTest {
    private PhaseDAdvisoryActionsArchitectureTest() { }

    public static void main(String[] args) throws Exception {
        panelKeepsOnlyThinActionBoundaries();
        collaboratorOwnsReportAndSuggestionAssembly();
        System.out.println("PhaseDAdvisoryActionsArchitectureTest passed");
    }

    private static void panelKeepsOnlyThinActionBoundaries() throws Exception {
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/passive/AeTunerPanel.java");
        require(source.contains("PassiveAdvisoryActions advisoryActions"),
                "AeTunerPanel does not own the advisory-actions collaborator");
        String[] forbidden = new String[]{
                "AdvisoryExportCoordinator.chooseCsvTarget",
                "AdvisoryExportCoordinator.chooseReportTarget",
                "AeTableSuggestion.build(",
                "MapEstimateSuggestion.build(",
                "MapBlendSuggestion.build(",
                "MapPredictReportBuilder.build("
        };
        for (String token : forbidden) {
            require(!source.contains(token),
                    "AeTunerPanel still contains advisory implementation token " + token);
        }
        require(source.contains("private void saveCsv()")
                        && source.contains("private void saveMapPredictReport()"),
                "existing action-listener method boundaries were removed");
    }

    private static void collaboratorOwnsReportAndSuggestionAssembly() throws Exception {
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/passive/PassiveAdvisoryActions.java");
        require(source.contains("AdvisoryExportCoordinator.chooseCsvTarget")
                        && source.contains("AdvisoryExportCoordinator.chooseReportTarget"),
                "advisory collaborator does not own file chooser actions");
        require(source.contains("AeTableSuggestion.build(")
                        && source.contains("MapEstimateSuggestion.build(")
                        && source.contains("MapBlendSuggestion.build(")
                        && source.contains("MapPredictReportBuilder.build("),
                "advisory collaborator does not own proposal/report assembly");
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
