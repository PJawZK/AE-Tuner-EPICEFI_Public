package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Foundation 2 event-level threshold evidence/recommendation policy. */
final class FoundationThresholdRecommendation {
    static final int QUIET_CALIBRATION_TARGET = 80;
    static final double QUIET_CALIBRATION_MIN_SECONDS = 1.50;
    static final int MIN_GLOBAL_QUIET_SAMPLES = QUIET_CALIBRATION_TARGET;
    static final int MIN_INCIDENTAL_EVENTS_PER_BIN = 3;
    static final int MIN_INTENT_EVENTS_PER_BIN = 3;
    static final String CLASS_NORMAL_CORRECTION = "NORMAL_CORRECTION";
    static final String CLASS_ACCELERATION_OPENING = "ACCELERATION_OPENING";
    static final String CLASS_LOCKED = "LOCKED";

    private static final double EVENT_GAP = 0.18, QUIET_GAP = 0.20;
    private static final double MIN_MOVE_RATE = 2.0, MIN_ACCEL_RATE = 6.0;
    private static final double INTENT_QUIET_MULT = 5.0, MOVE_QUIET_MULT = 0.75;
    private static final double NOISE_GUARD = 1.25, INTENT_GUARD = 0.80;
    private static final double MIN_SEP = 0.02, MAX_MOVE_FRAC = 0.25, MIN_MAX_MOVE = 0.05;
    private static final double NORMAL_MAX_EXCURSION = 1.25, NORMAL_RATE_FRAC = 0.80;
    private static final double ACCEL_MIN_EXCURSION = 0.50, ACCEL_TINY_RATIO = 0.20;

    static final class EventObservation {
        final String eventClass;
        final double peakDelta, peakRpm, peakThreshold, peakRate, excursion;
        EventObservation(String c, double d, double r, double t, double rate, double ex) {
            eventClass=c==null?"":c; peakDelta=d; peakRpm=r; peakThreshold=t; peakRate=rate; excursion=ex;
        }
        double ratio() { return finite(peakDelta)&&finite(peakThreshold)&&peakThreshold>1e-6 ? peakDelta/peakThreshold : Double.NaN; }
    }

    static final class BinDecision {
        final double rpm, current;
        final String regionLabel;
        final int incidentalEvents, intentEvents, rejectedEvents;
        final double incidentalP95, intentP25, incidentalRatioP95, intentRatioP25;
        final int ordinaryCrossingEvents, deliberateBelowThresholdEvents, nearThresholdDeliberateEvents;
        final double effectiveLow, effectiveHigh, separationGap, requiredSeparation, low, high;
        final boolean effectiveValidated, eligible, changed;
        final double proposed;
        final String requestedClass, status, lastRejectedReason;
        final double lastRejectedRate, lastRejectedExcursion, lastRejectedDelta, lastRejectedRatio;
        final List<EventObservation> normalCorrectionEvents, accelerationOpeningEvents;
        BinDecision(double rpm,String region,double current,int n,int a,int rej,double np95,double ap25,
                    double nr95,double ar25,int crossings,int misses,int near,double el,double eh,
                    double gap,double required,double low,double high,boolean locked,boolean eligible,
                    double proposed,boolean changed,String requested,String status,String rejectReason,
                    double rejectRate,double rejectExc,double rejectDelta,double rejectRatio,List<EventObservation> normals,List<EventObservation> accels) {
            this.rpm=rpm; regionLabel=nn(region); this.current=current; incidentalEvents=n; intentEvents=a;
            rejectedEvents=rej; incidentalP95=np95; intentP25=ap25; incidentalRatioP95=nr95; intentRatioP25=ar25;
            ordinaryCrossingEvents=crossings; deliberateBelowThresholdEvents=misses; nearThresholdDeliberateEvents=near;
            effectiveLow=el; effectiveHigh=eh; separationGap=gap; requiredSeparation=required; this.low=low; this.high=high;
            effectiveValidated=locked; this.eligible=eligible; this.proposed=proposed; this.changed=changed;
            requestedClass=nn(requested); this.status=nn(status); lastRejectedReason=nn(rejectReason);
            lastRejectedRate=rejectRate; lastRejectedExcursion=rejectExc; lastRejectedDelta=rejectDelta; lastRejectedRatio=rejectRatio;
            normalCorrectionEvents=immutable(normals); accelerationOpeningEvents=immutable(accels);
        }
    }

    static final class EvidenceSummary {
        final int validSamples, calibrationQuietTarget, quietSamples;
        final boolean calibrationFrozen;
        final double quietDurationSeconds, quietP95, quietP99, quietRatioP99, movementRateFloor, intentRateFloor;
        final int rawCrossingSamples, falseTriggerCandidateEvents, missedIntentCandidateEvents,
                nearThresholdIntentEvents, movementEvents, semanticRejectedEvents, effectiveValidatedBins;
        final double peakRatio;
        final boolean staticCurveDirect;
        final List<BinDecision> bins;
        EvidenceSummary(int valid,boolean frozen,int target,int quiet,double duration,double p95,double p99,double rp99,
                        double moveFloor,double intentFloor,int raw,int falseEvents,int misses,int near,int moves,int rejects,
                        int locked,double peak,boolean direct,List<BinDecision> bins) {
            validSamples=valid; calibrationFrozen=frozen; calibrationQuietTarget=target; quietSamples=quiet;
            quietDurationSeconds=duration; quietP95=p95; quietP99=p99; quietRatioP99=rp99;
            movementRateFloor=moveFloor; intentRateFloor=intentFloor; rawCrossingSamples=raw;
            falseTriggerCandidateEvents=falseEvents; missedIntentCandidateEvents=misses; nearThresholdIntentEvents=near;
            movementEvents=moves; semanticRejectedEvents=rejects; effectiveValidatedBins=locked; peakRatio=peak;
            staticCurveDirect=direct; this.bins=Collections.unmodifiableList(new ArrayList<BinDecision>(bins));
        }
        static EvidenceSummary empty() { return new EvidenceSummary(0,false,QUIET_CALIBRATION_TARGET,0,0,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0,0,0,0,0,0,0,Double.NaN,false,
                Collections.<BinDecision>emptyList()); }
    }

    static final class Result {
        final String reviewText; final ProposalWritePlan plan; final int eligibleBins, changedBins; final EvidenceSummary summary;
        Result(String text,ProposalWritePlan plan,int eligible,int changed,EvidenceSummary summary) {
            reviewText=nn(text); this.plan=plan; eligibleBins=eligible; changedBins=changed; this.summary=summary==null?EvidenceSummary.empty():summary;
        }
    }

    private static final class BinEvidence {
        final List<Double> normalPeaks=new ArrayList<Double>(), accelPeaks=new ArrayList<Double>(),
                normalStatic=new ArrayList<Double>(), accelStatic=new ArrayList<Double>(),
                normalRatios=new ArrayList<Double>(), accelRatios=new ArrayList<Double>();
        final List<EventObservation> normalEvents=new ArrayList<EventObservation>(), accelEvents=new ArrayList<EventObservation>();
        int crossings, misses, near, rejected; boolean locked;
        String lastReject=""; double lastRejectRate=Double.NaN,lastRejectExc=Double.NaN,lastRejectDelta=Double.NaN,lastRejectRatio=Double.NaN;
    }

    private static final class MovementEvent {
        boolean active; double last=Double.NaN,delta=Double.NaN,rpm=Double.NaN,threshold=Double.NaN,rate=Double.NaN,
                staticFloor=Double.NaN,staticCeiling=Double.NaN,startTps=Double.NaN,minTps=Double.NaN,maxTps=Double.NaN;
        void move(LiveSample s,double d,double sf,double sc,double tr,double prev) {
            if(!active) startTps=finite(prev)?prev:s.get(ChannelRole.TPS); active=true; last=s.getSeconds();
            if(!finite(rate)||tr>rate) rate=tr; peak(s,d,sf,sc); tps(s.get(ChannelRole.TPS));
        }
        void tail(LiveSample s,double d,double sf,double sc){ if(active){peak(s,d,sf,sc);tps(s.get(ChannelRole.TPS));}}
        private void peak(LiveSample s,double d,double sf,double sc){ if(finite(d)&&(!finite(delta)||d>delta)){delta=d;rpm=s.get(ChannelRole.RPM);threshold=s.get(ChannelRole.ACCEL_THRESHOLD);staticFloor=sf;staticCeiling=sc;}}
        private void tps(double v){ if(!finite(v))return; if(!finite(minTps)||v<minTps)minTps=v;if(!finite(maxTps)||v>maxTps)maxTps=v;if(finite(startTps)){minTps=Math.min(minTps,startTps);maxTps=Math.max(maxTps,startTps);}}
        double excursion(){return finite(minTps)&&finite(maxTps)?Math.max(0,maxTps-minTps):Double.NaN;}
        void clear(){active=false;last=delta=rpm=threshold=rate=staticFloor=staticCeiling=startTps=minTps=maxTps=Double.NaN;}
    }
    private static final class Counts { int moves,falseEvents,misses,near,rejected; }

    private FoundationThresholdRecommendation() { }

    static Result evaluate(AeProjectSnapshot snapshot,List<LiveSample> evidence) {
        if(snapshot==null)return unavailable("Read Working Tune before threshold recommendation.");
        double[] rpmBins=snapshot.getThresholdRpmBins(), current=snapshot.getThresholdValues();
        if(rpmBins.length==0||rpmBins.length!=current.length)return unavailable("Working-tune threshold curve is unavailable or invalid.");
        if(evidence==null||evidence.isEmpty())return unavailable("No retained Threshold / Sensitivity samples are available.");

        List<Double> quietRates=new ArrayList<Double>(), quietRatios=new ArrayList<Double>();
        boolean frozen=false; double freeze=Double.NaN,qStart=Double.NaN,qLast=Double.NaN,qDuration=0,p95=Double.NaN,p99=Double.NaN,rp99=Double.NaN,peakRatio=Double.NaN;
        int valid=0;
        for(LiveSample s:evidence){
            if(s==null)continue; double d=s.get(ChannelRole.DELTA_TPS),t=s.get(ChannelRole.ACCEL_THRESHOLD),rate=s.getTpsDot(),rpm=s.get(ChannelRole.RPM),sec=s.getSeconds();
            if(!finite(d)||!finite(t)||t<=1e-6||!finite(rate)||!finite(rpm)||!finite(sec))continue; valid++;
            double ratio=d/t;if(!finite(peakRatio)||ratio>peakRatio)peakRatio=ratio;if(frozen)continue;
            boolean quiet=Math.abs(d)<=t*.35;
            if(!quiet){quietRates.clear();quietRatios.clear();qStart=qLast=Double.NaN;continue;}
            if(finite(qLast)&&sec-qLast>QUIET_GAP){quietRates.clear();quietRatios.clear();qStart=Double.NaN;}
            if(!finite(qStart))qStart=sec;qLast=sec;quietRates.add(Math.abs(rate));quietRatios.add(Math.abs(ratio));qDuration=Math.max(0,qLast-qStart);
            if(quietRates.size()>=QUIET_CALIBRATION_TARGET&&qDuration>=QUIET_CALIBRATION_MIN_SECONDS){
                p95=percentile(quietRates,.95);p99=percentile(quietRates,.99);rp99=percentile(quietRatios,.99);frozen=true;freeze=sec;
            }
        }
        if(!frozen){p95=percentile(quietRates,.95);p99=percentile(quietRates,.99);rp99=percentile(quietRatios,.99);qDuration=finite(qStart)&&finite(qLast)?qLast-qStart:0;}
        double intentFloor=Math.max(MIN_ACCEL_RATE,safe(p95)*INTENT_QUIET_MULT), moveFloor=Math.max(MIN_MOVE_RATE,safe(p95)*MOVE_QUIET_MULT);
        AeControllerDefinitionCatalog.Definition def=AeControllerDefinitionCatalog.find(AeParameterNames.TPS_AE_THRESHOLD_VALUES);
        double scale=def==null?.001:def.getScale(),minimum=def==null?0:def.getMinimum(),maximum=def==null?60:def.getMaximum();
        boolean dynamic=snapshot.isDynamicThresholdEnabled(),avg=snapshot.isDynamicThresholdAverageStatic(),averaged=dynamic&&avg,writable=!dynamic||averaged;
        BinEvidence[] bins=new BinEvidence[rpmBins.length];for(int i=0;i<bins.length;i++)bins[i]=new BinEvidence();
        MovementEvent event=new MovementEvent();Counts counts=new Counts();int raw=0;double prevTps=Double.NaN;
        if(frozen)for(LiveSample s:evidence){
            if(s==null)continue;double rpm=s.get(ChannelRole.RPM),tps=s.get(ChannelRole.TPS),d=s.get(ChannelRole.DELTA_TPS),th=s.get(ChannelRole.ACCEL_THRESHOLD),rate=s.getTpsDot(),sec=s.getSeconds();
            if(!finite(rpm)||!finite(d)||!finite(th)||th<=1e-6||!finite(rate)||!finite(sec)){finish(event,bins,rpmBins,intentFloor,scale,counts);prevTps=Double.NaN;continue;}
            if(finite(freeze)&&sec<=freeze){prevTps=tps;continue;} if(d>th)raw++;
            if(event.active&&finite(event.last)&&sec-event.last>=EVENT_GAP)finish(event,bins,rpmBins,intentFloor,scale,counts);
            int idx=binForRpm(rpmBins,rpm);double sf=Double.NaN,sc=Double.NaN;
            if(averaged){sf=requiredStatic(rpm,idx,Math.max(0,d)*NOISE_GUARD+scale,th,rpmBins,current);sc=requiredStatic(rpm,idx,Math.max(0,d)*INTENT_GUARD,th,rpmBins,current);}
            if(rate>moveFloor)event.move(s,d,sf,sc,rate,prevTps);else if(event.active&&finite(event.last)&&sec-event.last<EVENT_GAP)event.tail(s,d,sf,sc);prevTps=tps;
        }
        finish(event,bins,rpmBins,intentFloor,scale,counts);

        List<ProposalWritePlan.Change> changes=new ArrayList<ProposalWritePlan.Change>();List<BinDecision> decisions=new ArrayList<BinDecision>();int eligibleCount=0,lockedCount=0;StringBuilder rows=new StringBuilder();
        for(int i=0;i<bins.length;i++){
            BinEvidence b=bins[i];double np95=percentile(b.normalPeaks,.95),ap25=percentile(b.accelPeaks,.25),nr95=percentile(b.normalRatios,.95),ar25=percentile(b.accelRatios,.25);
            double eLow=finite(np95)?np95*NOISE_GUARD+scale:Double.NaN,eHigh=finite(ap25)?ap25*INTENT_GUARD:Double.NaN;
            double gap=finite(eLow)&&finite(eHigh)?eHigh-eLow:Double.NaN,required=finite(eLow)?Math.max(MIN_SEP,Math.max(0,eLow)*.15):MIN_SEP;
            boolean locked=b.locked;if(locked)lockedCount++;double low=eLow,high=eHigh;boolean staticSep=true;
            if(averaged){low=percentile(b.normalStatic,.95);high=percentile(b.accelStatic,.25);staticSep=separated(low,high);}
            boolean eligible=frozen&&locked&&writable&&(!averaged||staticSep);double proposed=current[i];boolean changed=false;String status;
            if(!frozen)status="calibrating quiet TPS-rate baseline — maneuver evidence is not accepted yet";
            else if(locked&&averaged&&!staticSep)status="effective separation validated and locked; static-cell authority blocked by averaged Dynamic/static inversion — more repetitions are not the next action";
            else if(locked&&!writable)status="effective separation validated and locked; Dynamic Threshold ON with averaging OFF gives the static curve zero runtime authority";
            else if(eligible){eligibleCount++;proposed=boundedProposal(current[i],low,high,scale,minimum,maximum);if(Math.abs(proposed-current[i])<=scale*.5){proposed=current[i];status="effective separation validated and locked; current value already inside the event-backed separation window";}else{changed=true;status="effective separation validated and locked; proposal "+fmt(current[i])+" -> "+fmt(proposed);changes.add(ProposalWritePlan.Change.arrayCell(AeParameterNames.TPS_AE_THRESHOLD_VALUES,i,current[i],proposed,"TPS AE threshold @ "+Math.round(rpmBins[i])+" RPM","#"));}}
            else if(b.normalPeaks.size()<3)status="need "+(3-b.normalPeaks.size())+" more Normal Correction event(s) in this broad RPM region";
            else if(b.accelPeaks.size()<3)status="need "+(3-b.accelPeaks.size())+" more Acceleration Opening event(s) in this broad RPM region";
            else status="Normal Corrections and Acceleration Openings still overlap in effective threshold space — repeat the requested maneuver class";
            String region=regionLabel(rpmBins,i),requested=requestedClass(b);
            decisions.add(new BinDecision(rpmBins[i],region,current[i],b.normalPeaks.size(),b.accelPeaks.size(),b.rejected,np95,ap25,nr95,ar25,b.crossings,b.misses,b.near,eLow,eHigh,gap,required,low,high,locked,eligible,proposed,changed,requested,status,b.lastReject,b.lastRejectRate,b.lastRejectExc,b.lastRejectDelta,b.lastRejectRatio,b.normalEvents,b.accelEvents));
            rows.append("  ").append(region).append(": Normal Corrections ").append(b.normalPeaks.size()).append(" event(s), Acceleration Openings ").append(b.accelPeaks.size()).append(" event(s), rejected ").append(b.rejected).append(", Normal p95 ").append(fmt(np95)).append(", Acceleration p25 ").append(fmt(ap25)).append(", effective gap ").append(fmtSigned(gap)).append(" / required +").append(fmt(required)).append(", effective ").append(locked?"LOCKED":"OPEN").append(", requested ").append(classLabel(requested)).append(" | ").append(status).append('\n');
        }
        ProposalWritePlan plan=changes.isEmpty()?null:new ProposalWritePlan("foundation-threshold-curve","Threshold / Sensitivity — locked guided-event RPM threshold curve",snapshot.getConfigurationName(),"Guided Focus owns Normal Correction / Acceleration Opening semantics; shape rejects obvious physical mismatches. Effective separation and static authority are separate. No automatic Apply or Burn.",changes);
        String boundary=!dynamic?"Dynamic Threshold is OFF: the static RPM threshold curve is directly authoritative.":!avg?"Dynamic Threshold is ON and averaging OFF: static curve has zero runtime authority.":"Dynamic Threshold is ON with averaging ON: firmware uses Effective=(Static+Dynamic)/2; AE Tuner inverts the peak-event live AccelThreshold against the interpolated static curve for conservative static-cell bounds.";
        StringBuilder review=new StringBuilder("THRESHOLD / SENSITIVITY LOCKED GUIDED-EVENT REVIEW\n");
        review.append("Valid retained analysis samples: ").append(valid).append('\n').append("Quiet calibration: ").append(quietRates.size()).append(" / ").append(QUIET_CALIBRATION_TARGET).append(" samples; continuous ").append(fmt(qDuration)).append(" / ").append(fmt(QUIET_CALIBRATION_MIN_SECONDS)).append(" s | ").append(frozen?"LOCKED":"CALIBRATING").append('\n')
                .append("Frozen quiet raw TPS-rate p95 / p99: ").append(fmt(p95)).append(" / ").append(fmt(p99)).append(" %/s\n")
                .append("Shape-check acceleration-rate reference: ").append(fmt(intentFloor)).append(" %/s (quiet p99 is diagnostic only)\n")
                .append("Semantic authority: Guided Focus requested Normal Correction / Acceleration Opening at event close; event shape can reject but never relabel.\n")
                .append("Movement events formed after calibration: ").append(counts.moves).append(" | rejected physical mismatches: ").append(counts.rejected).append('\n')
                .append("Accepted Normal Corrections crossing threshold: ").append(counts.falseEvents).append(" | accepted Acceleration Openings below threshold: ").append(counts.misses).append(" | near threshold: ").append(counts.near).append('\n')
                .append("Peak Fuel: TPS AE change / AccelThreshold: ").append(fmt(peakRatio)).append("\n\nRPM-BIN EVENT DECISIONS\n").append(rows)
                .append("\nEffective-space regions validated and locked: ").append(lockedCount).append(" / ").append(rpmBins.length).append(".\nStatic recommendation eligibility: ").append(eligibleCount).append(" / ").append(rpmBins.length).append(".\nProposed threshold changes: ").append(changes.size()).append(".\n").append(boundary)
                .append("\n\nWHAT TO DO NEXT: ").append(nextAction(decisions)).append("\n\nBoundary: event-count completion is not separation PASS. Only evidence-backed tpsAeThresholdValue cells may be proposed; Dynamic/smoothing controls remain unchanged. No automatic Apply and no burn.");
        EvidenceSummary summary=new EvidenceSummary(valid,frozen,QUIET_CALIBRATION_TARGET,quietRates.size(),qDuration,p95,p99,rp99,moveFloor,intentFloor,raw,counts.falseEvents,counts.misses,counts.near,counts.moves,counts.rejected,lockedCount,peakRatio,!dynamic,decisions);
        return new Result(review.toString(),plan,eligibleCount,changes.size(),summary);
    }

    private static void finish(MovementEvent e,BinEvidence[] bins,double[] rpmBins,double intentFloor,double scale,Counts counts){
        if(e==null||!e.active)return;if(finite(e.delta)&&e.delta>=0&&finite(e.rpm)&&finite(e.rate)){
            counts.moves++;BinEvidence b=bins[binForRpm(rpmBins,e.rpm)];if(!b.locked){String expected=requestedClass(b);double ratio=finite(e.threshold)&&e.threshold>1e-6?e.delta/e.threshold:Double.NaN,ex=e.excursion();String reject="";
                if(CLASS_NORMAL_CORRECTION.equals(expected)&&e.rate>=intentFloor*NORMAL_RATE_FRAC&&finite(ex)&&ex>NORMAL_MAX_EXCURSION)reject="Normal Correction was too large/fast and looked like an acceleration request";
                else if(CLASS_ACCELERATION_OPENING.equals(expected)&&e.rate<intentFloor)reject="Acceleration Opening was too slow; make a clear normal quick opening instead of a slow roll-in";
                else if(CLASS_ACCELERATION_OPENING.equals(expected)&&(!finite(ex)||ex<ACCEL_MIN_EXCURSION)&&(!finite(ratio)||ratio<ACCEL_TINY_RATIO))reject="Acceleration Opening was too small/weak to represent an AE-triggering acceleration request";
                if(reject.length()>0){b.rejected++;counts.rejected++;b.lastReject=reject;b.lastRejectRate=e.rate;b.lastRejectExc=ex;b.lastRejectDelta=e.delta;b.lastRejectRatio=ratio;}
                else if(CLASS_NORMAL_CORRECTION.equals(expected)){b.normalPeaks.add(e.delta);if(finite(ratio))b.normalRatios.add(ratio);if(finite(e.staticFloor))b.normalStatic.add(e.staticFloor);b.normalEvents.add(new EventObservation(expected,e.delta,e.rpm,e.threshold,e.rate,ex));if(finite(ratio)&&ratio>1){b.crossings++;counts.falseEvents++;}}
                else if(CLASS_ACCELERATION_OPENING.equals(expected)){b.accelPeaks.add(e.delta);if(finite(ratio))b.accelRatios.add(ratio);if(finite(e.staticCeiling))b.accelStatic.add(e.staticCeiling);b.accelEvents.add(new EventObservation(expected,e.delta,e.rpm,e.threshold,e.rate,ex));if(finite(ratio)&&ratio<1){b.misses++;counts.misses++;}if(finite(ratio)&&ratio>=.75&&ratio<1){b.near++;counts.near++;}}
                if(!b.locked&&b.normalPeaks.size()>=3&&b.accelPeaks.size()>=3){double n=percentile(b.normalPeaks,.95),a=percentile(b.accelPeaks,.25),lo=finite(n)?n*NOISE_GUARD+scale:Double.NaN,hi=finite(a)?a*INTENT_GUARD:Double.NaN;if(separated(lo,hi))b.locked=true;}
            }}e.clear();
    }

    private static boolean separated(double low,double high){return finite(low)&&finite(high)&&high-low>=Math.max(MIN_SEP,Math.max(0,low)*.15);}
    private static String requestedClass(BinEvidence b){if(b==null||b.locked)return CLASS_LOCKED;if(b.normalPeaks.size()<3)return CLASS_NORMAL_CORRECTION;if(b.accelPeaks.size()<3)return CLASS_ACCELERATION_OPENING;return b.normalPeaks.size()<=b.accelPeaks.size()?CLASS_NORMAL_CORRECTION:CLASS_ACCELERATION_OPENING;}
    private static String classLabel(String c){return CLASS_NORMAL_CORRECTION.equals(c)?"NORMAL CORRECTION":CLASS_ACCELERATION_OPENING.equals(c)?"ACCELERATION OPENING":nn(c).replace('_',' ');}
    private static String nextAction(List<BinDecision> ds){for(BinDecision b:ds){if(b.effectiveValidated||(b.incidentalEvents==0&&b.intentEvents==0))continue;if(CLASS_NORMAL_CORRECTION.equals(b.requestedClass))return "In the "+b.regionLabel+", make a small natural pedal adjustment without trying to accelerate.";if(CLASS_ACCELERATION_OPENING.equals(b.requestedClass))return "In the "+b.regionLabel+", make a clear normal quick throttle opening to accelerate; do not slowly roll into the pedal.";}for(BinDecision b:ds)if(b.effectiveValidated&&b.status.contains("static-cell authority blocked"))return "Effective behavior is validated in the "+b.regionLabel+"; more repetitions are not the next action. Review static/Dynamic authority.";for(BinDecision b:ds)if(b.effectiveValidated)return "At least one broad RPM region has locked separation. Review it or move naturally to another region for more curve coverage.";return "Hold TPS steady until count + continuous-time quiet calibration lock, then follow Normal Correction and Acceleration Opening requests.";}
    private static String regionLabel(double[] bins,int i){if(bins==null||bins.length==0||i<0||i>=bins.length)return"unknown RPM region";if(bins.length==1)return"broad RPM region (curve bin "+Math.round(bins[i])+")";if(i==0)return"≤ "+Math.round(bins[0]+(bins[1]-bins[0])/2)+" RPM low region (curve bin "+Math.round(bins[0])+")";if(i==bins.length-1)return"≥ "+Math.round(bins[i-1]+(bins[i]-bins[i-1])/2)+" RPM high region (curve bin "+Math.round(bins[i])+")";return Math.round(bins[i-1]+(bins[i]-bins[i-1])/2)+"–"+Math.round(bins[i]+(bins[i+1]-bins[i])/2)+" RPM region (curve bin "+Math.round(bins[i])+")";}
    private static int binForRpm(double[] bins,double rpm){for(int i=0;i<bins.length-1;i++)if(rpm<=bins[i]+(bins[i+1]-bins[i])/2)return i;return bins.length-1;}
    private static double requiredStatic(double rpm,int cell,double target,double live,double[] bins,double[] values){if(!finite(rpm)||!finite(target)||!finite(live)||bins.length==0||bins.length!=values.length||cell<0||cell>=values.length)return Double.NaN;double interp=interpolateCurve(rpm,bins,values);if(!finite(interp))return Double.NaN;double desired=2*target-(2*live-interp);if(bins.length==1)return cell==0?desired:Double.NaN;if(rpm<=bins[0])return cell==0?desired:Double.NaN;int last=bins.length-1;if(rpm>=bins[last])return cell==last?desired:Double.NaN;for(int l=0;l<last;l++){int r=l+1;if(rpm>bins[r])continue;double span=bins[r]-bins[l];if(span<=0)return Double.NaN;double wr=Math.max(0,Math.min(1,(rpm-bins[l])/span)),wl=1-wr;if(cell==l&&wl>1e-6)return(desired-wr*values[r])/wl;if(cell==r&&wr>1e-6)return(desired-wl*values[l])/wr;return Double.NaN;}return Double.NaN;}
    static double interpolateCurve(double rpm,double[] bins,double[] values){if(bins.length==0||bins.length!=values.length)return Double.NaN;if(bins.length==1||rpm<=bins[0])return values[0];int last=bins.length-1;if(rpm>=bins[last])return values[last];for(int i=0;i<last;i++){if(rpm>bins[i+1])continue;double span=bins[i+1]-bins[i];if(span<=0)return Double.NaN;double a=Math.max(0,Math.min(1,(rpm-bins[i])/span));return values[i]+(values[i+1]-values[i])*a;}return values[last];}
    private static double boundedProposal(double current,double low,double high,double scale,double min,double max){double target=current;if(current<low)target=low;else if(current>high)target=high;double move=Math.max(MIN_MAX_MOVE,Math.abs(current)*MAX_MOVE_FRAC);target=Math.max(current-move,Math.min(current+move,target));target=Math.max(min,Math.min(max,target));if(target>current)target=Math.ceil((target-1e-7)/scale)*scale;else if(target<current)target=Math.floor((target+1e-7)/scale)*scale;return Math.rint(Math.max(min,Math.min(max,target))/scale)*scale;}
    private static double percentile(List<Double> src,double f){if(src==null||src.isEmpty())return Double.NaN;List<Double> s=new ArrayList<Double>(src);Collections.sort(s);if(s.size()==1)return s.get(0);double p=f*(s.size()-1);int l=(int)Math.floor(p),u=(int)Math.ceil(p);return l==u?s.get(l):s.get(l)*(1-(p-l))+s.get(u)*(p-l);}
    private static <T> List<T> immutable(List<T> s){return Collections.unmodifiableList(new ArrayList<T>(s==null?Collections.<T>emptyList():s));}
    private static boolean finite(double v){return Double.isFinite(v);} private static double safe(double v){return finite(v)?v:0;} private static String nn(String s){return s==null?"":s;}
    private static String fmt(double v){return finite(v)?String.format(Locale.ROOT,"%.3f",v):"n/a";} private static String fmtSigned(double v){return finite(v)?String.format(Locale.ROOT,"%+.3f",v):"n/a";}
    private static Result unavailable(String m){return new Result("Threshold / Sensitivity recommendation unavailable: "+m+" No ProposalWritePlan.",null,0,0,EvidenceSummary.empty());}
}
