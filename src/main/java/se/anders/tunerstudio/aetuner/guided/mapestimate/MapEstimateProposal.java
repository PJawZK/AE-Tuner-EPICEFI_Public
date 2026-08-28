package se.anders.tunerstudio.aetuner.guided.mapestimate;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reviewed MAP Estimate table proposal plus exact write mask. */
public final class MapEstimateProposal {
    public static final String PARAMETER = "mapEstimateTable";
    public static final double HIGH_TPS_CAP_START = 33.5;
    /** EpicEFI mainController.ini stores mapEstimateTable as U16 at 0.01 kPa/bit. */
    public static final double TABLE_RESOLUTION_KPA = 0.01;
    private final double[][] proposed;
    private final boolean[][] changed;
    private final ProposalWritePlan plan;
    private final String copyPaste;

    private MapEstimateProposal(double[][] proposed,boolean[][] changed,ProposalWritePlan plan,String copyPaste){this.proposed=proposed;this.changed=changed;this.plan=plan;this.copyPaste=copyPaste;}

    /** Compatibility/default path: preserve the existing high-TPS cap policy. */
    public static MapEstimateProposal build(String configuration,double[] tps,double[] rpm,double[][] current,
            MapEstimateSurface surface,MapEstimateCoverageStrategy strategy,MapEstimateCellScope scope,double capKpa){
        return build(configuration,tps,rpm,current,surface,strategy,scope,capKpa,
                MapEstimateProposalLimitPolicy.HIGH_TPS_CAP);
    }

    public static MapEstimateProposal build(String configuration,double[] tps,double[] rpm,double[][] current,
            MapEstimateSurface surface,MapEstimateCoverageStrategy strategy,MapEstimateCellScope scope,double capKpa,
            MapEstimateProposalLimitPolicy limitPolicy){
        if(current.length!=tps.length||surface.rows()!=tps.length)throw new IllegalArgumentException("row mismatch");
        MapEstimateProposalLimitPolicy policy=limitPolicy==null
                ? MapEstimateProposalLimitPolicy.HIGH_TPS_CAP:limitPolicy;
        double[][] p=cloneTable(current);boolean[][] mask=new boolean[tps.length][rpm.length];List<ProposalWritePlan.Change> changes=new ArrayList<ProposalWritePlan.Change>();
        for(int r=0;r<tps.length;r++){
            if(current[r].length!=rpm.length)throw new IllegalArgumentException("column mismatch row "+r);
            for(int c=0;c<rpm.length;c++){
                if(!scope.contains(r,c))continue;
                MapEstimateSurface.Cell cell=surface.cell(r,c);
                boolean eligible=strategy==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE
                        ? cell.state==MapEstimateSurface.State.DIRECT && cell.proposalEligible()
                        : cell.proposalEligible();
                if(!eligible||!Double.isFinite(cell.valueKpa))continue;
                double next=cell.valueKpa;
                // "Unrestricted" removes only the experimental high-TPS output
                // cap. Evidence quality, scope, conflict/recheck exclusion and
                // bounded interpolation authority remain unchanged.
                if(policy==MapEstimateProposalLimitPolicy.HIGH_TPS_CAP&&tps[r]>=HIGH_TPS_CAP_START)next=Math.min(next,capKpa);
                // Preserve full-precision learned evidence, but make the reviewed
                // write proposal exactly representable by the ECU table before
                // it enters the stale-check/readback/manifest contract.
                next=quantizeForTable(next);
                if(Math.abs(next-quantizeForTable(current[r][c]))<1.0e-9)continue;
                p[r][c]=next;mask[r][c]=true;int flat=r*rpm.length+c;
                changes.add(ProposalWritePlan.Change.arrayCell(PARAMETER,flat,current[r][c],next,String.format(Locale.ROOT,"%.1f%% TPS / %.0f RPM",tps[r],rpm[c]),"kPa"));
            }
        }
        String limitText=policy==MapEstimateProposalLimitPolicy.HIGH_TPS_CAP
                ? String.format(Locale.ROOT,"high-TPS cap %.2f kPa from %.1f%% TPS",capKpa,HIGH_TPS_CAP_START)
                : "unrestricted eligible MAP; evidence safeguards unchanged";
        ProposalWritePlan plan=changes.isEmpty()?null:new ProposalWritePlan("map-estimate-table","MAP Estimate Table",configuration,
                strategy+" | "+scope.size()+" selected cell(s) | "+limitText+"; cells outside scope preserved",changes);
        return new MapEstimateProposal(p,mask,plan,toPaste(p));
    }

    public ProposalWritePlan writePlan(){return plan;} public String copyPasteBlock(){return copyPaste;}
    public boolean changed(int row,int col){return changed[row][col];} public double value(int row,int col){return proposed[row][col];}
    public int changeCount(){return plan==null?0:plan.changeCount();}
    static double quantizeForTable(double value){return Math.round(value/TABLE_RESOLUTION_KPA)*TABLE_RESOLUTION_KPA;}
    private static String toPaste(double[][] p){StringBuilder b=new StringBuilder();for(int r=p.length-1;r>=0;r--){for(int c=0;c<p[r].length;c++){if(c>0)b.append('\t');b.append(String.format(Locale.ROOT,"%.2f",p[r][c]));}if(r>0)b.append('\n');}return b.toString();}
    private static double[][] cloneTable(double[][] a){double[][] b=new double[a.length][];for(int i=0;i<a.length;i++)b[i]=a[i].clone();return b;}
}
