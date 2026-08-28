package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Persistent learned-state model; stores compact summaries, not raw log rows. */
public final class MapEstimateMemory {
    public static final int FORMAT_VERSION = 2;
    public static final double TPS_BUCKET = 1.0;
    public static final double RPM_BUCKET = 100.0;

    private final String configuration;
    private final double[] tpsAxis;
    private final double[] rpmAxis;
    private final LinkedHashMap<Long, MapEstimateEvidenceBucket> buckets = new LinkedHashMap<Long, MapEstimateEvidenceBucket>();

    public MapEstimateMemory(String configuration, double[] tpsAxis, double[] rpmAxis) {
        this.configuration = configuration == null ? "" : configuration;
        this.tpsAxis = tpsAxis == null ? new double[0] : tpsAxis.clone();
        this.rpmAxis = rpmAxis == null ? new double[0] : rpmAxis.clone();
        if (this.tpsAxis.length == 0 || this.rpmAxis.length == 0) throw new IllegalArgumentException("MAP Estimate axes required");
    }

    public void add(double tps, double rpm, double map, double clt, double mat) {
        if (!Double.isFinite(tps) || !Double.isFinite(rpm) || !Double.isFinite(map)) return;
        int tk = (int)Math.round(tps / TPS_BUCKET);
        int rk = (int)Math.round(rpm / RPM_BUCKET);
        long key = key(tk,rk);
        MapEstimateEvidenceBucket b = buckets.get(key);
        if (b == null) { b = new MapEstimateEvidenceBucket(tk,rk); buckets.put(key,b); }
        b.add(tps,rpm,map,clt,mat);
    }

    /** Exact aggregate merge used for copies/load; session provenance is preserved exactly. */
    public void merge(MapEstimateMemory other) {
        requireCompatible(other);
        for (MapEstimateEvidenceBucket incoming : other.buckets.values()) {
            long key = key(incoming.tpsKey,incoming.rpmKey);
            MapEstimateEvidenceBucket own = buckets.get(key);
            if (own == null) {
                own = MapEstimateEvidenceBucket.restoredV2(incoming.tpsKey,incoming.rpmKey,
                        incoming.rawCount(),incoming.rawSumTps(),incoming.rawSumRpm(),incoming.rawSumMap(),incoming.rawSumMapSq(),
                        incoming.rawMinMap(),incoming.rawMaxMap(),incoming.rawSumClt(),incoming.rawCltCount(),incoming.rawSumMat(),incoming.rawMatCount(),
                        incoming.rawSessionCount(),incoming.rawSumSessionMean(),incoming.rawSumSessionMeanSq(),incoming.rawMinSessionMean(),incoming.rawMaxSessionMean(),
                        incoming.rawSumWithinVarianceWeighted(),incoming.rawWithinVarianceWeight(),incoming.rawLatestSessionMean(),incoming.rawLatestSessionSamples());
                buckets.put(key,own);
            } else own.merge(incoming);
        }
    }

    /** Merge an uncommitted capture as one independent completed tuning session. */
    public void mergeCompletedSession(MapEstimateMemory completedSession) {
        requireCompatible(completedSession);
        for (MapEstimateEvidenceBucket incoming : completedSession.buckets.values()) {
            if (incoming.count() <= 0) continue;
            long key = key(incoming.tpsKey,incoming.rpmKey);
            MapEstimateEvidenceBucket own = buckets.get(key);
            if (own == null) { own = new MapEstimateEvidenceBucket(incoming.tpsKey,incoming.rpmKey); buckets.put(key,own); }
            own.mergeCompletedSession(incoming);
        }
    }

    void putRestored(MapEstimateEvidenceBucket bucket) { buckets.put(key(bucket.tpsKey,bucket.rpmKey), bucket); }
    public List<MapEstimateEvidenceBucket> buckets() { return new ArrayList<MapEstimateEvidenceBucket>(buckets.values()); }
    public int bucketCount() { return buckets.size(); }
    public long sampleCount() { long n=0; for(MapEstimateEvidenceBucket b:buckets.values()) n+=b.count(); return n; }
    public String configuration() { return configuration; }
    public double[] tpsAxis(){return tpsAxis.clone();} public double[] rpmAxis(){return rpmAxis.clone();}

    public void requireCompatible(MapEstimateMemory other) {
        if (other == null || !configuration.equals(other.configuration) || !same(tpsAxis,other.tpsAxis) || !same(rpmAxis,other.rpmAxis))
            throw new IllegalArgumentException("MAP Estimate memory identity/axes do not match current working tune");
    }

    private static long key(int tps, int rpm) { return (((long)tps)<<32) ^ (rpm & 0xffffffffL); }
    private static boolean same(double[] a,double[] b){ if(a.length!=b.length)return false; for(int i=0;i<a.length;i++)if(Math.abs(a[i]-b[i])>1e-6)return false; return true; }
}
