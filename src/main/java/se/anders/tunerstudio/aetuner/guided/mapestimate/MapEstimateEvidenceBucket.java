package se.anders.tunerstudio.aetuner.guided.mapestimate;

/** Mergeable summary of stable measured-MAP evidence in a small continuous-coordinate bucket. */
public final class MapEstimateEvidenceBucket {
    public final int tpsKey;
    public final int rpmKey;
    private long count;
    private double sumTps, sumRpm, sumMap, sumMapSq;
    private double minMap = Double.POSITIVE_INFINITY, maxMap = Double.NEGATIVE_INFINITY;
    private double sumClt, sumMat;
    private long cltCount, matCount;

    // dev17: completed-session provenance. Session means are intentionally
    // weighted equally so a long drive cannot hide disagreement from a short,
    // clean later drive. Sample totals remain separately sample-weighted.
    private long sessionCount;
    private double sumSessionMean, sumSessionMeanSq;
    private double minSessionMean = Double.POSITIVE_INFINITY;
    private double maxSessionMean = Double.NEGATIVE_INFINITY;
    private double sumWithinVarianceWeighted;
    private long withinVarianceWeight;
    private double latestSessionMean = Double.NaN;
    private long latestSessionSamples;

    public MapEstimateEvidenceBucket(int tpsKey, int rpmKey) {
        this.tpsKey = tpsKey;
        this.rpmKey = rpmKey;
    }

    public void add(double tps, double rpm, double map, double clt, double mat) {
        if (!Double.isFinite(tps) || !Double.isFinite(rpm) || !Double.isFinite(map)) return;
        count++;
        sumTps += tps; sumRpm += rpm; sumMap += map; sumMapSq += map * map;
        minMap = Math.min(minMap, map); maxMap = Math.max(maxMap, map);
        if (Double.isFinite(clt)) { sumClt += clt; cltCount++; }
        if (Double.isFinite(mat)) { sumMat += mat; matCount++; }
    }

    /** Exact aggregate merge used for copies/restores; does not invent a new session. */
    public void merge(MapEstimateEvidenceBucket other) {
        if (other == null || other.count == 0) return;
        requireSameKey(other);
        mergeSamplesOnly(other);
        sessionCount += other.sessionCount;
        sumSessionMean += other.sumSessionMean;
        sumSessionMeanSq += other.sumSessionMeanSq;
        if (other.sessionCount > 0) {
            minSessionMean = Math.min(minSessionMean, other.minSessionMean);
            maxSessionMean = Math.max(maxSessionMean, other.maxSessionMean);
            latestSessionMean = other.latestSessionMean;
            latestSessionSamples = other.latestSessionSamples;
        }
        sumWithinVarianceWeighted += other.sumWithinVarianceWeighted;
        withinVarianceWeight += other.withinVarianceWeight;
    }

    /** Merge one uncommitted run and record it as exactly one completed session. */
    void mergeCompletedSession(MapEstimateEvidenceBucket sessionBucket) {
        if (sessionBucket == null || sessionBucket.count == 0) return;
        requireSameKey(sessionBucket);
        double mean = sessionBucket.meanMap();
        double sd = sessionBucket.mapStdDev();
        long samples = sessionBucket.count;
        mergeSamplesOnly(sessionBucket);
        sessionCount++;
        sumSessionMean += mean;
        sumSessionMeanSq += mean * mean;
        minSessionMean = Math.min(minSessionMean, mean);
        maxSessionMean = Math.max(maxSessionMean, mean);
        if (Double.isFinite(sd)) {
            sumWithinVarianceWeighted += sd * sd * samples;
            withinVarianceWeight += samples;
        }
        latestSessionMean = mean;
        latestSessionSamples = samples;
    }

    private void mergeSamplesOnly(MapEstimateEvidenceBucket other) {
        count += other.count;
        sumTps += other.sumTps; sumRpm += other.sumRpm; sumMap += other.sumMap; sumMapSq += other.sumMapSq;
        minMap = Math.min(minMap, other.minMap); maxMap = Math.max(maxMap, other.maxMap);
        sumClt += other.sumClt; cltCount += other.cltCount;
        sumMat += other.sumMat; matCount += other.matCount;
    }

    private void requireSameKey(MapEstimateEvidenceBucket other) {
        if (other.tpsKey != tpsKey || other.rpmKey != rpmKey) throw new IllegalArgumentException("bucket key mismatch");
    }

    /** Upgrade a dev16/v1 pooled bucket as one legacy completed session. */
    static MapEstimateEvidenceBucket restoredV1(int tpsKey, int rpmKey, long count,
            double sumTps, double sumRpm, double sumMap, double sumMapSq,
            double minMap, double maxMap, double sumClt, long cltCount,
            double sumMat, long matCount) {
        MapEstimateEvidenceBucket b = restoredV2(tpsKey,rpmKey,count,sumTps,sumRpm,sumMap,sumMapSq,
                minMap,maxMap,sumClt,cltCount,sumMat,matCount,
                0,0,0,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0,0,Double.NaN,0);
        if (count > 0) {
            double mean = b.meanMap();
            double sd = b.mapStdDev();
            b.sessionCount = 1;
            b.sumSessionMean = mean;
            b.sumSessionMeanSq = mean * mean;
            b.minSessionMean = mean;
            b.maxSessionMean = mean;
            if (Double.isFinite(sd)) {
                b.sumWithinVarianceWeighted = sd * sd * count;
                b.withinVarianceWeight = count;
            }
            b.latestSessionMean = mean;
            b.latestSessionSamples = count;
        }
        return b;
    }

    static MapEstimateEvidenceBucket restoredV2(int tpsKey, int rpmKey, long count,
            double sumTps, double sumRpm, double sumMap, double sumMapSq,
            double minMap, double maxMap, double sumClt, long cltCount,
            double sumMat, long matCount, long sessionCount,
            double sumSessionMean, double sumSessionMeanSq,
            double minSessionMean, double maxSessionMean,
            double sumWithinVarianceWeighted, long withinVarianceWeight,
            double latestSessionMean, long latestSessionSamples) {
        MapEstimateEvidenceBucket b = new MapEstimateEvidenceBucket(tpsKey, rpmKey);
        b.count=count; b.sumTps=sumTps; b.sumRpm=sumRpm; b.sumMap=sumMap; b.sumMapSq=sumMapSq;
        b.minMap=minMap; b.maxMap=maxMap; b.sumClt=sumClt; b.cltCount=cltCount; b.sumMat=sumMat; b.matCount=matCount;
        b.sessionCount=sessionCount; b.sumSessionMean=sumSessionMean; b.sumSessionMeanSq=sumSessionMeanSq;
        b.minSessionMean=minSessionMean; b.maxSessionMean=maxSessionMean;
        b.sumWithinVarianceWeighted=sumWithinVarianceWeighted; b.withinVarianceWeight=withinVarianceWeight;
        b.latestSessionMean=latestSessionMean; b.latestSessionSamples=latestSessionSamples;
        return b;
    }

    public long count() { return count; }
    public double meanTps() { return count == 0 ? Double.NaN : sumTps / count; }
    public double meanRpm() { return count == 0 ? Double.NaN : sumRpm / count; }
    public double meanMap() { return count == 0 ? Double.NaN : sumMap / count; }
    public double mapStdDev() {
        if (count < 2) return Double.NaN;
        double m = meanMap(); return Math.sqrt(Math.max(0.0, sumMapSq / count - m*m));
    }
    public double mapRange() { return count == 0 ? Double.NaN : maxMap - minMap; }
    public double minMap() { return count == 0 ? Double.NaN : minMap; }
    public double maxMap() { return count == 0 ? Double.NaN : maxMap; }
    public double meanClt() { return cltCount == 0 ? Double.NaN : sumClt / cltCount; }
    public double meanMat() { return matCount == 0 ? Double.NaN : sumMat / matCount; }
    public long sessionCount() { return sessionCount; }
    public double betweenSessionStdDev() {
        if (sessionCount < 2) return Double.NaN;
        double mean = sumSessionMean / sessionCount;
        return Math.sqrt(Math.max(0.0, sumSessionMeanSq / sessionCount - mean * mean));
    }
    public double betweenSessionRange() {
        return sessionCount < 2 ? Double.NaN : maxSessionMean - minSessionMean;
    }
    public double withinSessionRmsStdDev() {
        return withinVarianceWeight <= 0 ? Double.NaN
                : Math.sqrt(Math.max(0.0, sumWithinVarianceWeighted / withinVarianceWeight));
    }
    public double latestSessionMean() { return latestSessionMean; }
    public long latestSessionSamples() { return latestSessionSamples; }

    long rawCount(){return count;} double rawSumTps(){return sumTps;} double rawSumRpm(){return sumRpm;}
    double rawSumMap(){return sumMap;} double rawSumMapSq(){return sumMapSq;} double rawMinMap(){return minMap;}
    double rawMaxMap(){return maxMap;} double rawSumClt(){return sumClt;} long rawCltCount(){return cltCount;}
    double rawSumMat(){return sumMat;} long rawMatCount(){return matCount;}
    long rawSessionCount(){return sessionCount;} double rawSumSessionMean(){return sumSessionMean;}
    double rawSumSessionMeanSq(){return sumSessionMeanSq;} double rawMinSessionMean(){return minSessionMean;}
    double rawMaxSessionMean(){return maxSessionMean;} double rawSumWithinVarianceWeighted(){return sumWithinVarianceWeighted;}
    long rawWithinVarianceWeight(){return withinVarianceWeight;} double rawLatestSessionMean(){return latestSessionMean;}
    long rawLatestSessionSamples(){return latestSessionSamples;}
}
