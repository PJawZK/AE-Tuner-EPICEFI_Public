package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultContext;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import java.awt.Component;

/**
 * Decel-specific evidence entry. Historical replay is the normal path; the
 * existing production live capture is retained only as an explicit fallback.
 */
final class GuidedV019DecelEvidenceEntryDialog {
    private GuidedV019DecelEvidenceEntryDialog() { }

    static void choose(Component parent, AeProjectSnapshot snapshot,
                       boolean liveCaptureAvailable, Runnable startLiveCapture) {
        if (snapshot == null || !snapshot.hasDecelDetectionSettings()) return;

        javax.swing.JDialog dialog=createPrompt(parent,liveCaptureAvailable);
        dialog.setVisible(true);
        int choice=(Integer)dialog.getRootPane().getClientProperty("ae.evidence.choice");
        dialog.dispose();
        int cancelChoice=liveCaptureAvailable?2:1;
        if (choice == cancelChoice) {
            GuidedV019WorkflowNavigation.cancelTask();
            return;
        }

        if (choice == 0) {
            DecelHistoricalThresholdProposalContext.clear();
            DecelHistoricalResultContext.clear();
            GuidedV019DecelHistoricalReplayDialog.chooseAndAnalyze(
                    parent, snapshot, startLiveCapture);
        } else if (liveCaptureAvailable && choice == 1 && startLiveCapture != null) {
            DecelHistoricalThresholdProposalContext.clear();
            DecelHistoricalResultContext.clear();
            startLiveCapture.run();
        }
    }
    static javax.swing.JDialog createPrompt(Component parent,boolean liveCaptureAvailable) {
        java.awt.Window host=parent==null?null:javax.swing.SwingUtilities.getWindowAncestor(parent);
        javax.swing.JDialog dialog=GuidedTaskControls.install(
                GuidedDialogLifecycle.create(host,"Decel Detection — Evidence Source",true),false);
        dialog.getRootPane().putClientProperty("ae.evidence.choice",-1);
        javax.swing.JPanel body=new javax.swing.JPanel(new java.awt.BorderLayout(0,12));
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(14,14,14,14));
        javax.swing.JTextArea message = new javax.swing.JTextArea(
                "Use existing evidence first. Prefer the same log/session used for the positive-side Foundation work.\n\n"
                + "The plugin returns KEEP, CHANGE, or the RPM regions that still need evidence. A compatible historical result can launch capture for those missing regions. Starting a new evidence source discards older staged authority. Actual writes still require guarded Apply/readback/Restore.");
        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setColumns(56);
        message.setRows(6);
        message.setOpaque(false);
        message.setFont(javax.swing.UIManager.getFont("Label.font"));
        body.add(message,java.awt.BorderLayout.CENTER);
        javax.swing.JPanel buttons=new javax.swing.JPanel(new GuidedWrapLayout(java.awt.FlowLayout.RIGHT,6,4));
        boolean sharedCancel=GuidedV019WorkflowNavigation.workspace()!=null;
        String[] options=liveCaptureAvailable
                ?(sharedCancel?new String[]{"Analyze Existing Log…","Start Optional Live Capture"}
                    :new String[]{"Analyze Existing Log…","Start Optional Live Capture","Cancel Task"})
                :(sharedCancel?new String[]{"Analyze Existing Log…"}:new String[]{"Analyze Existing Log…","Cancel Task"});
        for(int i=0;i<options.length;i++) {
            final int choice=i;
            javax.swing.JButton button=new javax.swing.JButton(options[i]);
            button.addActionListener(e -> {dialog.getRootPane().putClientProperty("ae.evidence.choice",choice);dialog.dispose();});
            buttons.add(button);
            if(i==0)dialog.getRootPane().setDefaultButton(button);
        }
        body.add(buttons,java.awt.BorderLayout.SOUTH);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE,0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setContentPane(body);dialog.setMinimumSize(new java.awt.Dimension(480,240));
        dialog.setSize(760,240);dialog.setLocationRelativeTo(parent);
        return dialog;
    }

}
