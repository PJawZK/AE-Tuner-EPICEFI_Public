package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import se.anders.tunerstudio.aetuner.ui.AeUtilityWorkspacePanel;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/** Xvfb evidence for the real production Task Settings editor. */
public final class GuidedTaskSettingsUiSyntheticTest {
    private GuidedTaskSettingsUiSyntheticTest() { }

    public static void main(String[] args) throws Exception {
        final AeUiTheme.Side oldTheme = AeUiTheme.side();
        final File out = outputDirectory();
        if (!out.isDirectory() && !out.mkdirs()) {
            throw new IllegalStateException("Could not create " + out);
        }

        final GuidedTaskSettingsDraft draft = GuidedTaskSettingsDraft.capture(
                new GuidedTaskSettingsDraft.ValueReader() {
                    @Override public double read(String configurationName,
                                                 se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationTargets.Target target) {
                        if (AeParameterNames.WALL_TAU.equals(target.getControllerName())) return 1.00;
                        if (AeParameterNames.WALL_BETA.equals(target.getControllerName())) return 0.30;
                        if (target.getDefinition().getKind()
                                == AeControllerDefinitionCatalog.Kind.BITS) {
                            String[] labels = target.getDefinition().getOptionLabels();
                            for (int i = 0; i < labels.length; i++) {
                                String label = labels[i] == null ? "" : labels[i].trim();
                                if (label.length() > 0
                                        && !"INVALID".equalsIgnoreCase(label)) return i;
                            }
                            return Math.rint(target.getDefinition().getMinimum());
                        }
                        double minimum = target.getDefinition().getMinimum();
                        double maximum = target.getDefinition().getMaximum();
                        if (Double.isFinite(minimum) && Double.isFinite(maximum)) {
                            return minimum + (maximum - minimum) * 0.25;
                        }
                        return 0.0;
                    }
                }, "Synthetic Task Settings", GuidedTuningRecipe.WALL_WETTING);

        final AtomicReference<GuidedTaskSettingsDialog> dialogRef =
                new AtomicReference<GuidedTaskSettingsDialog>();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);
                    GuidedTaskSettingsDialog dialog =
                            new GuidedTaskSettingsDialog(null, draft);
                    dialog.setSize(new Dimension(1060, 720));
                    dialog.doLayout();
                    dialog.getContentPane().doLayout();
                    dialogRef.set(dialog);
                }
            });
            GuidedTaskSettingsDialog dialog = dialogRef.get();
            require(dialog != null, "Task Settings dialog was not constructed");
            require(dialog.parameterSectionCountForTest() == draft.controllerNames().size(),
                    "Task Settings section navigator did not expose every captured controller parameter");
            require(dialog.parameterSectionCountForTest() >= 2,
                    "Wall Wetting did not provide the expected multi-parameter Task Settings coverage");
            require(dialog.editorBindingCountForTest() == draft.getEntries().size(),
                    "Task Settings did not retain every captured editable target");
            require(!contains(dialog.getContentPane(), JTabbedPane.class),
                    "legacy JTabbedPane remains in the real Task Settings component tree");
            require(containsText(dialog.getContentPane(), "DRAFT ONLY")
                            && containsText(dialog.getContentPane(), "NO ECU WRITE")
                            && containsText(dialog.getContentPane(), "NO BURN"),
                    "Task Settings safety boundary is not visible");
            require(containsText(dialog.getContentPane(), "WORKING TUNE")
                            && containsText(dialog.getContentPane(), "PROPOSED"),
                    "Task Settings no longer distinguishes Working Tune and Proposed values");
            require("NO DRAFT EDITS".equals(dialog.editStateForTest()),
                    "fresh Task Settings editor incorrectly reports a pending edit");

            render((JComponent) dialog.getContentPane(),
                    new File(out, "workspace-guided-v019-task-settings-light-1060.png"));

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    GuidedTaskSettingsDialog dialog = dialogRef.get();
                    JSpinner spinner = findSpinner(dialog.getContentPane());
                    require(spinner != null, "Task Settings did not expose a numeric production editor");
                    Number value = (Number) spinner.getValue();
                    Number step = (Number) ((javax.swing.SpinnerNumberModel) spinner.getModel()).getStepSize();
                    spinner.setValue(Double.valueOf(value.doubleValue() + step.doubleValue()));
                }
            });
            require(dialog.editStateForTest().contains("DRAFT EDIT"),
                    "Task Settings did not surface a changed editor as a draft edit");

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    GuidedTaskSettingsDialog dialog = dialogRef.get();
                    dialog.selectParameterSectionForTest("parameter-1");
                    require("parameter-1".equals(dialog.selectedParameterSectionForTest()),
                            "Task Settings parameter navigator did not change the focused parameter");
                    AeUiTheme.set(AeUiTheme.Side.DARK_SIDE);
                    AeUtilityWorkspacePanel workspace = findWorkspace(dialog.getContentPane());
                    require(workspace != null, "shared utility workspace disappeared from Task Settings");
                    workspace.applyTheme();
                    dialog.getContentPane().doLayout();
                }
            });
            render((JComponent) dialog.getContentPane(),
                    new File(out, "workspace-guided-v019-task-settings-dark-1060.png"));

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    AbstractButton reset = findButton(dialogRef.get().getContentPane(),
                            "Reset to Working Tune");
                    require(reset != null, "Task Settings reset action is not reachable");
                    reset.doClick();
                }
            });
            require("NO DRAFT EDITS".equals(dialog.editStateForTest()),
                    "Task Settings reset did not return editors to the captured Working Tune");

            System.out.println("GuidedTaskSettingsUiSyntheticTest passed");
        } finally {
            final GuidedTaskSettingsDialog dialog = dialogRef.get();
            if (dialog != null) {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override public void run() { dialog.dispose(); }
                });
            }
            AeUiTheme.set(oldTheme);
        }
    }

    private static AeUtilityWorkspacePanel findWorkspace(Component root) {
        if (root instanceof AeUtilityWorkspacePanel) return (AeUtilityWorkspacePanel) root;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                AeUtilityWorkspacePanel found = findWorkspace(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JSpinner findSpinner(Component root) {
        if (root instanceof JSpinner) return (JSpinner) root;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JSpinner found = findSpinner(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static AbstractButton findButton(Component root, String text) {
        if (root instanceof AbstractButton && text.equals(((AbstractButton) root).getText())) {
            return (AbstractButton) root;
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                AbstractButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean contains(Component root, Class<?> type) {
        if (type.isInstance(root)) return true;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                if (contains(child, type)) return true;
            }
        }
        return false;
    }

    private static boolean containsText(Component root, String needle) {
        if (root instanceof JLabel && ((JLabel) root).getText() != null
                && ((JLabel) root).getText().contains(needle)) return true;
        if (root instanceof AbstractButton && ((AbstractButton) root).getText() != null
                && ((AbstractButton) root).getText().contains(needle)) return true;
        if (root instanceof JTextArea && ((JTextArea) root).getText() != null
                && ((JTextArea) root).getText().contains(needle)) return true;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                if (containsText(child, needle)) return true;
            }
        }
        return false;
    }

    private static void render(final JComponent component, final File file) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try {
                    component.setSize(1060, 680);
                    component.doLayout();
                    layoutTree(component);
                    BufferedImage image = new BufferedImage(
                            Math.max(1, component.getWidth()), Math.max(1, component.getHeight()),
                            BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        component.printAll(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    ImageIO.write(image, "png", file);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        require(file.isFile() && file.length() > 0L,
                "Task Settings screenshot was not created: " + file);
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) layoutTree(child);
        }
    }

    private static File outputDirectory() {
        String configured = System.getenv("SYNTHETIC_INTEGRATION_OUT");
        return new File(configured == null || configured.length() == 0
                ? "target/synthetic-plugin-integration" : configured);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
