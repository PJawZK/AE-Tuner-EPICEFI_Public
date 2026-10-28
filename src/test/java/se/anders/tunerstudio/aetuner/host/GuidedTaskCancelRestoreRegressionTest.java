package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedCapturePanel;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import javax.swing.*;
import java.lang.reflect.*;
import java.util.Arrays;

/** Cancel/reset do not write RAM or lose the guarded Restore stack. */
public final class GuidedTaskCancelRestoreRegressionTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GuidedCapturePanel panel = new GuidedCapturePanel();
            try {
                Backend backend = new Backend();
                ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
                ProposalWritePlan plan = new ProposalWritePlan("test", "test", "cfg", "test",
                        Arrays.asList(ProposalWritePlan.Change.scalar("test", 1, 2, "test", "")));
                require(coordinator.apply(plan).success, "fixture Apply failed");
                set(panel, "applyCoordinator", coordinator);
                set(panel, "workingTuneReadRequiredAfterApply", true);
                set(panel, "currentProposalApplied", true);
                invoke(panel, "cancelTask");
                invoke(panel, "startOverTask");
                require(backend.writes == 1 && backend.value == 2 && coordinator.applyDepth() == 1,
                        "cancel/reset wrote RAM or discarded Restore history");
                require((Boolean) get(panel, "workingTuneReadRequiredAfterApply"),
                        "reset removed fresh Working Tune read guard");
                JButton restore = (JButton) get(panel, "restoreProposal");
                require(restore.isEnabled(), "Restore inaccessible after cancel/reset");
                restore.doClick();
                require(backend.writes == 2 && backend.value == 1 && coordinator.applyDepth() == 0,
                        "explicit Restore did not restore the original value");
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { panel.disposePanel(); }
        });
        System.out.println("GuidedTaskCancelRestoreRegressionTest passed");
    }
    private static final class Backend implements ProposalApplyCoordinator.Backend {
        double value=1; int writes;
        public double readScalar(String cfg,String name) { return value; }
        public double[][] readArray(String cfg,String name) { throw new UnsupportedOperationException(); }
        public void writeScalar(String cfg,String name,double v) { value=v; writes++; }
        public void writeArray(String cfg,String name,double[][] v) { throw new UnsupportedOperationException(); }
    }
    private static Object get(Object owner,String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
    }
    private static void invoke(Object owner,String name) throws Exception {
        Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
