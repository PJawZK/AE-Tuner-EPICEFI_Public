package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import se.anders.tunerstudio.aetuner.ui.AeUtilityWorkspacePanel;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Headless-safe architecture gate for the production Task Settings presentation. */
public final class GuidedTaskSettingsPresentationRegressionTest {
    private GuidedTaskSettingsPresentationRegressionTest() { }

    public static void main(String[] args) throws Exception {
        productionAuthorityRemainsDraftAndProposalOnly();
        visiblePresentationUsesV019ShellInsteadOfLegacyTabs();
        System.out.println("GuidedTaskSettingsPresentationRegressionTest passed");
    }

    private static void productionAuthorityRemainsDraftAndProposalOnly() throws Exception {
        Field draft = GuidedTaskSettingsDialog.class.getDeclaredField("draft");
        Field staged = GuidedTaskSettingsDialog.class.getDeclaredField("stagedPlan");
        Field workspace = GuidedTaskSettingsDialog.class.getDeclaredField("workspace");
        require(draft.getType() == GuidedTaskSettingsDraft.class,
                "Task Settings stopped using the established GuidedTaskSettingsDraft authority");
        require(staged.getType() == ProposalWritePlan.class,
                "Task Settings no longer stages the established ProposalWritePlan");
        require(workspace.getType() == AeUtilityWorkspacePanel.class,
                "Task Settings no longer uses the shared v0.19 utility workspace shell");
    }

    private static void visiblePresentationUsesV019ShellInsteadOfLegacyTabs() throws Exception {
        Path source = Paths.get("src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedTaskSettingsDialog.java");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        require(text.contains("AeUtilityWorkspacePanel"),
                "Task Settings source lost the shared v0.19 shell");
        require(!text.contains("JTabbedPane"),
                "legacy parameter-tab presentation returned to Task Settings");
        require(!text.contains("JOptionPane"),
                "stock Swing review/status dialogs returned to the normal v0.19 Task Settings path");
        require(text.contains("DRAFT ONLY")
                        && text.contains("NO ECU WRITE")
                        && text.contains("NO BURN"),
                "Task Settings no longer exposes its no-write/no-burn boundary visibly");
        require(text.contains("confirmStagePlan")
                        && text.contains("Stage Reviewed Plan"),
                "Task Settings lost the explicit themed plan-review decision before staging");
        require(text.contains("draft.buildPlan()")
                        && text.contains("stagedPlan = plan"),
                "Task Settings presentation bypassed the existing draft-to-plan staging path");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
