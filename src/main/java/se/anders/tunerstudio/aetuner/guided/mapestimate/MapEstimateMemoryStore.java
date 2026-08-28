package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Human-readable, atomic, one-current-plus-one-backup persistent memory store. */
public final class MapEstimateMemoryStore {
    public static final String FILE_NAME = "map-estimate.memory";
    public static final String PREVIOUS_FILE_NAME = "map-estimate.previous.memory";
    private static final String MAGIC_V1 = "AE_TUNER_MAP_ESTIMATE_MEMORY,1";
    private static final String MAGIC_V2 = "AE_TUNER_MAP_ESTIMATE_MEMORY,2";
    private static final String HEADER_V1 = "bucket_tps,bucket_rpm,count,sum_tps,sum_rpm,sum_map,sum_map_sq,min_map,max_map,sum_clt,clt_count,sum_mat,mat_count";
    private static final String HEADER_V2 = HEADER_V1
            + ",session_count,sum_session_mean,sum_session_mean_sq,min_session_mean,max_session_mean"
            + ",sum_within_variance_weighted,within_variance_weight,latest_session_mean,latest_session_count";

    public static final class LoadResult {
        public final MapEstimateMemory memory;
        public final boolean recoveredFromPrevious;
        public final String status;
        LoadResult(MapEstimateMemory memory, boolean recoveredFromPrevious, String status) {
            this.memory = memory;
            this.recoveredFromPrevious = recoveredFromPrevious;
            this.status = status == null ? "" : status;
        }
    }

    private final Path directory;
    public MapEstimateMemoryStore(Path directory) { this.directory = directory; }
    public Path directory(){ return directory; }
    public Path currentPath(){ return directory.resolve(FILE_NAME); }
    public Path previousPath(){ return directory.resolve(PREVIOUS_FILE_NAME); }

    public void save(MapEstimateMemory memory) throws IOException {
        if (memory == null) throw new IllegalArgumentException("memory required");
        Files.createDirectories(directory);
        Path temp = directory.resolve(FILE_NAME + ".tmp");
        write(temp,memory);
        MapEstimateMemory validated = load(temp);
        memory.requireCompatible(validated);
        if (validated.sampleCount()!=memory.sampleCount()) throw new IOException("memory validation sample-count mismatch");
        if (Files.exists(currentPath())) Files.copy(currentPath(), previousPath(), StandardCopyOption.REPLACE_EXISTING);
        promote(temp,currentPath());
    }

    public LoadResult loadBest(MapEstimateMemory emptyForCurrentTune) throws IOException {
        if (emptyForCurrentTune == null) throw new IllegalArgumentException("current-tune memory identity required");
        Exception currentFailure = null;
        if (Files.exists(currentPath())) {
            try {
                boolean legacy = isLegacyV1(currentPath());
                MapEstimateMemory current = load(currentPath());
                emptyForCurrentTune.requireCompatible(current);
                return new LoadResult(current,false, legacy
                        ? "Loaded dev16 MAP Estimate memory and upgraded its pooled evidence as one legacy session; the next successful Finish writes memory format v2."
                        : "Loaded existing MAP Estimate memory with completed-session provenance.");
            } catch (Exception ex) {
                currentFailure = ex;
            }
        }
        if (Files.exists(previousPath())) {
            try {
                MapEstimateMemory previous = load(previousPath());
                emptyForCurrentTune.requireCompatible(previous);
                Files.createDirectories(directory);
                Path repair = directory.resolve(FILE_NAME + ".repair.tmp");
                write(repair, previous);
                promote(repair,currentPath());
                return new LoadResult(previous,true,
                        "Recovered MAP Estimate memory from the previous backup because the current file was unavailable or incompatible.");
            } catch (Exception ignored) {
                // Neither retained file is safe to merge into this table identity.
            }
        }
        String suffix = currentFailure == null ? "" : " Current memory was not compatible with the loaded table.";
        return new LoadResult(emptyForCurrentTune,false,
                "No compatible MAP Estimate memory was loaded; starting with empty learned state." + suffix);
    }

    public MapEstimateMemory loadCurrent() throws IOException { return load(currentPath()); }

    public MapEstimateMemory load(Path path) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String magic = in.readLine();
            boolean v1 = MAGIC_V1.equals(magic);
            boolean v2 = MAGIC_V2.equals(magic);
            if (!v1 && !v2) throw new IOException("unsupported MAP Estimate memory format");
            String configuration = value(in.readLine(),"configuration");
            double[] tps = axis(value(in.readLine(),"tps_axis"));
            double[] rpm = axis(value(in.readLine(),"rpm_axis"));
            String header=in.readLine();
            if (v1 && !HEADER_V1.equals(header)) throw new IOException("invalid MAP Estimate memory v1 header");
            if (v2 && !HEADER_V2.equals(header)) throw new IOException("invalid MAP Estimate memory v2 header");
            MapEstimateMemory memory=new MapEstimateMemory(configuration,tps,rpm);
            String line;
            while((line=in.readLine())!=null){
                if(line.trim().isEmpty())continue;
                String[] p=line.split(",",-1);
                MapEstimateEvidenceBucket b;
                if (v1) {
                    if(p.length!=13) throw new IOException("invalid v1 memory row: "+line);
                    b=MapEstimateEvidenceBucket.restoredV1(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Long.parseLong(p[2]),
                            Double.parseDouble(p[3]),Double.parseDouble(p[4]),Double.parseDouble(p[5]),Double.parseDouble(p[6]),Double.parseDouble(p[7]),Double.parseDouble(p[8]),
                            Double.parseDouble(p[9]),Long.parseLong(p[10]),Double.parseDouble(p[11]),Long.parseLong(p[12]));
                } else {
                    if(p.length!=22) throw new IOException("invalid v2 memory row: "+line);
                    b=MapEstimateEvidenceBucket.restoredV2(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Long.parseLong(p[2]),
                            Double.parseDouble(p[3]),Double.parseDouble(p[4]),Double.parseDouble(p[5]),Double.parseDouble(p[6]),Double.parseDouble(p[7]),Double.parseDouble(p[8]),
                            Double.parseDouble(p[9]),Long.parseLong(p[10]),Double.parseDouble(p[11]),Long.parseLong(p[12]),Long.parseLong(p[13]),
                            Double.parseDouble(p[14]),Double.parseDouble(p[15]),Double.parseDouble(p[16]),Double.parseDouble(p[17]),Double.parseDouble(p[18]),Long.parseLong(p[19]),
                            Double.parseDouble(p[20]),Long.parseLong(p[21]));
                }
                memory.putRestored(b);
            }
            return memory;
        }
    }

    private static void write(Path path, MapEstimateMemory m) throws IOException {
        try(BufferedWriter out=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){
            out.write(MAGIC_V2+"\n");
            out.write("configuration="+sanitize(m.configuration())+"\n");
            out.write("tps_axis="+join(m.tpsAxis())+"\n");
            out.write("rpm_axis="+join(m.rpmAxis())+"\n");
            out.write(HEADER_V2+"\n");
            for(MapEstimateEvidenceBucket b:m.buckets()){
                out.write(String.format(Locale.ROOT,
                        "%d,%d,%d,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%d,%.12g,%d,%d,%.12g,%.12g,%.12g,%.12g,%.12g,%d,%.12g,%d%n",
                        b.tpsKey,b.rpmKey,b.rawCount(),b.rawSumTps(),b.rawSumRpm(),b.rawSumMap(),b.rawSumMapSq(),b.rawMinMap(),b.rawMaxMap(),b.rawSumClt(),b.rawCltCount(),b.rawSumMat(),b.rawMatCount(),
                        b.rawSessionCount(),b.rawSumSessionMean(),b.rawSumSessionMeanSq(),b.rawMinSessionMean(),b.rawMaxSessionMean(),b.rawSumWithinVarianceWeighted(),b.rawWithinVarianceWeight(),b.rawLatestSessionMean(),b.rawLatestSessionSamples()));
            }
        }
    }

    private static boolean isLegacyV1(Path path) {
        try (BufferedReader in=Files.newBufferedReader(path,StandardCharsets.UTF_8)) { return MAGIC_V1.equals(in.readLine()); }
        catch (Exception ex) { return false; }
    }
    private static void promote(Path temporary, Path target) throws IOException {
        try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
    }
    private static String sanitize(String s){return s==null?"":s.replace("\n"," ").replace("\r"," ").replace(",","_");}
    private static String value(String line,String key)throws IOException{String p=key+"=";if(line==null||!line.startsWith(p))throw new IOException("missing "+key);return line.substring(p.length());}
    private static String join(double[] a){StringBuilder b=new StringBuilder();for(int i=0;i<a.length;i++){if(i>0)b.append(';');b.append(String.format(Locale.ROOT,"%.10g",a[i]));}return b.toString();}
    private static double[] axis(String s){String[] p=s.split(";");double[] a=new double[p.length];for(int i=0;i<p.length;i++)a[i]=Double.parseDouble(p[i]);return a;}
}
