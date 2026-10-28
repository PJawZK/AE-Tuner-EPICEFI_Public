package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalProgress;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded, observational progress: the worker replaces one pending update, EDT renders it. */
final class GuidedDecelAnalysisProgress extends JPanel implements DecelHistoricalProgress {
    private static final class Update {
        final File file; final String stage; final long rows;
        Update(File file, String stage, long rows) { this.file=file; this.stage=stage; this.rows=rows; }
    }
    private final File[] files;
    private final AtomicReference<Update> pending = new AtomicReference<>();
    private final JLabel headline = new JLabel("Analyzing Decel evidence…");
    private final JLabel file = new JLabel();
    private final JLabel stage = new JLabel("Starting background analysis");
    private final JLabel elapsed = new JLabel("Elapsed: 0 s");
    private final JProgressBar activity = new JProgressBar();
    private final JTextArea explanation = new JTextArea();
    private final long started = System.nanoTime();
    private final Timer timer;
    private volatile boolean active = true;

    GuidedDecelAnalysisProgress(File[] files) {
        super(new BorderLayout(0, 12));
        this.files = files.clone();
        setBackground(AeUiTheme.card());
        setBorder(new EmptyBorder(18,18,18,18));
        JPanel status = new JPanel(); status.setOpaque(false);
        status.setLayout(new BoxLayout(status,BoxLayout.Y_AXIS));
        headline.setFont(new Font("Dialog",Font.BOLD,18));
        headline.setForeground(AeUiTheme.text());
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);
        for(JLabel label:new JLabel[]{file,stage,elapsed}) {
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setForeground(AeUiTheme.text()); label.setFont(new Font("Dialog",Font.PLAIN,12));
        }
        status.add(headline); status.add(Box.createVerticalStrut(12));
        status.add(file);status.add(Box.createVerticalStrut(6));status.add(stage);
        status.add(Box.createVerticalStrut(10));
        activity.setAlignmentX(Component.LEFT_ALIGNMENT);
        activity.setIndeterminate(true);activity.setForeground(AeUiTheme.blue());
        activity.setMaximumSize(new Dimension(Integer.MAX_VALUE,22));
        status.add(activity);status.add(Box.createVerticalStrut(6));status.add(elapsed);
        explanation.setEditable(false);explanation.setLineWrap(true);explanation.setWrapStyleWord(true);
        explanation.setBackground(AeUiTheme.card());explanation.setForeground(AeUiTheme.text());
        explanation.setFont(new Font("Dialog",Font.PLAIN,12));
        explanation.setText("Analysis is running in the background. Large logs can take time. The activity bar shows work in progress, not an estimated percentage.\n\nEach source log is classified independently before compatible events are pooled.\n\nCancel Analysis stops this analysis without changing the Working Tune. Cancel Task and Start Over are also available below.");
        add(status,BorderLayout.NORTH);add(explanation,BorderLayout.CENTER);
        update(files.length == 0 ? null : files[0],"Starting background analysis",0);
        render();
        timer = new Timer(200,e -> render());
        addHierarchyListener(e -> {
            if((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED)==0) return;
            if(isShowing() && active) timer.start();else timer.stop();
        });
    }
    @Override public void update(File source,String phase,long rows) {
        if(active) pending.set(new Update(source,phase,rows));
    }
    void render() {
        if(!active) return;
        Update update=pending.getAndSet(null);
        if(update!=null) {
            int index=-1;
            for(int i=0;i<files.length;i++) if(files[i].equals(update.file)) { index=i;break; }
            file.setText(index>=0 ? "File "+(index+1)+" of "+files.length+": "+update.file.getName()
                    : files.length+" selected log(s)");
            stage.setText(update.stage+(update.rows>=0 ? " — decoded rows: "+update.rows : ""));
        }
        elapsed.setText("Elapsed: "+((System.nanoTime()-started)/1000000000L)+" s");
    }
    void cancelled() {
        terminal("Analysis cancelled",warningColor(),false);
        explanation.setText("Analysis stopped. No result or proposal was published and no Working Tune value changed.\n\nThe selected files are unchanged. Close this window to choose another log from Capture, or use Start Over for a fresh task.");
    }
    void failed(Throwable failure) {
        terminal("Analysis failed",warningColor(),false);
        explanation.setText("The selected log(s) could not be analyzed.\n\n"
                +failure.getClass().getSimpleName()+": "+(failure.getMessage()==null?"No additional details":failure.getMessage())
                +"\n\nNo result or proposal was published and no Working Tune value changed. Close this window and try another log, or use Start Over.");
    }
    JLabel completed(boolean supported) {
        String text=supported ? "Analysis complete" : "Analysis finished — no tuning result; see the evidence conditions below";
        terminal(text,supported?successColor():warningColor(),true);
        JLabel label=new JLabel(text+" — "+files.length+" source log(s)");
        label.setForeground(supported?successColor():warningColor());
        label.setBorder(new EmptyBorder(6,6,6,6));return label;
    }
    private static Color warningColor() { return AeUiTheme.isDark()?AeUiTheme.amber():new Color(122,73,0); }
    private static Color successColor() { return AeUiTheme.isDark()?AeUiTheme.green():new Color(30,110,60); }

    private void terminal(String text,Color color,boolean complete) {
        active=false;pending.set(null);timer.stop();
        headline.setText(text);headline.setForeground(color);
        stage.setText(complete?"Background analysis finished":"No result published");
        activity.setIndeterminate(false);activity.setStringPainted(true);
        activity.setValue(complete?100:0);activity.setString(text);
    }
    void close() { active=false;pending.set(null);timer.stop(); }
    boolean active() { return active; }
    String statusText() { return headline.getText()+"\n"+file.getText()+"\n"+stage.getText()+"\n"+explanation.getText(); }
}
