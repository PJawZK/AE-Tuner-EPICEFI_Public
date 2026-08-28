package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.util.List;

/**
 * Evaluates whether MAP spread inside one tight Direct neighborhood is real
 * inconsistency or a coherent local MAP-vs-TPS/RPM gradient.
 *
 * The fitted plane is diagnostic only. It never becomes the proposed table
 * value. Direct value authority remains in MapEstimateSurface.
 */
final class MapEstimateLocalQuality {
    static final class Result {
        final double residualStdDevKpa;
        final double maxSupportedBucketResidualKpa;
        final boolean gradientUsed;
        final int bucketCount;

        Result(double residualStdDevKpa, double maxSupportedBucketResidualKpa,
               boolean gradientUsed, int bucketCount) {
            this.residualStdDevKpa = residualStdDevKpa;
            this.maxSupportedBucketResidualKpa = maxSupportedBucketResidualKpa;
            this.gradientUsed = gradientUsed;
            this.bucketCount = bucketCount;
        }
    }

    private MapEstimateLocalQuality() { }

    static Result evaluate(List<MapEstimateEvidenceBucket> buckets,
                           double targetTps, double targetRpm,
                           int minimumSamples, double fallbackStdDev) {
        if (buckets == null || buckets.isEmpty()) {
            return new Result(fallbackStdDev, Double.NaN, false, 0);
        }

        int usable = 0;
        double weight = 0.0;
        double mx = 0.0, my = 0.0, mz = 0.0;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (MapEstimateEvidenceBucket b : buckets) {
            long n = b.count();
            double t = b.meanTps(), r = b.meanRpm(), z = b.meanMap();
            if (n <= 0 || !Double.isFinite(t) || !Double.isFinite(r) || !Double.isFinite(z)) continue;
            double x = t - targetTps;
            double y = (r - targetRpm) / 100.0;
            double w = n;
            usable++;
            weight += w;
            mx += w * x; my += w * y; mz += w * z;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        if (usable < 3 || weight <= 0.0) {
            return new Result(fallbackStdDev, Double.NaN, false, usable);
        }
        mx /= weight; my /= weight; mz /= weight;

        double sxx = 0.0, syy = 0.0, sxy = 0.0, sxz = 0.0, syz = 0.0;
        for (MapEstimateEvidenceBucket b : buckets) {
            long n = b.count();
            double t = b.meanTps(), r = b.meanRpm(), z = b.meanMap();
            if (n <= 0 || !Double.isFinite(t) || !Double.isFinite(r) || !Double.isFinite(z)) continue;
            double x = t - targetTps - mx;
            double y = (r - targetRpm) / 100.0 - my;
            double dz = z - mz;
            double w = n;
            sxx += w * x * x;
            syy += w * y * y;
            sxy += w * x * y;
            sxz += w * x * dz;
            syz += w * y * dz;
        }

        double bx = 0.0, by = 0.0;
        boolean fitted = false;
        double xSpan = maxX - minX;
        double ySpan = maxY - minY;

        // A two-dimensional plane can perfectly explain arbitrary three-point
        // evidence, so require at least five independent micro-buckets before
        // granting the extra degree of freedom.
        double det = sxx * syy - sxy * sxy;
        double scale = Math.max(1.0, sxx * syy);
        if (usable >= 5 && xSpan >= 0.45 && ySpan >= 0.45
                && Math.abs(det) > 1.0e-8 * scale) {
            bx = (sxz * syy - syz * sxy) / det;
            by = (syz * sxx - sxz * sxy) / det;
            fitted = Double.isFinite(bx) && Double.isFinite(by);
        }

        // Degenerate local clouds are still useful when they demonstrate a
        // clear one-dimensional TPS or RPM slope.
        if (!fitted) {
            if (xSpan >= 0.45 && sxx > 1.0e-9 && sxx >= syy) {
                bx = sxz / sxx;
                by = 0.0;
                fitted = Double.isFinite(bx);
            } else if (ySpan >= 0.45 && syy > 1.0e-9) {
                bx = 0.0;
                by = syz / syy;
                fitted = Double.isFinite(by);
            }
        }
        if (!fitted) {
            return new Result(fallbackStdDev, Double.NaN, false, usable);
        }

        double weightedResidualVariance = 0.0;
        double maxSupportedResidual = Double.NaN;
        int minimumBucketSupport = Math.max(3, minimumSamples / 10);
        for (MapEstimateEvidenceBucket b : buckets) {
            long n = b.count();
            double t = b.meanTps(), r = b.meanRpm(), z = b.meanMap();
            if (n <= 0 || !Double.isFinite(t) || !Double.isFinite(r) || !Double.isFinite(z)) continue;
            double x = t - targetTps;
            double y = (r - targetRpm) / 100.0;
            double predicted = mz + bx * (x - mx) + by * (y - my);
            double residual = z - predicted;
            double within = b.mapStdDev();
            double withinVariance = Double.isFinite(within) ? within * within : 0.0;
            weightedResidualVariance += n * (residual * residual + withinVariance);
            if (n >= minimumBucketSupport) {
                double abs = Math.abs(residual);
                maxSupportedResidual = Double.isFinite(maxSupportedResidual)
                        ? Math.max(maxSupportedResidual, abs) : abs;
            }
        }
        double residualStdDev = Math.sqrt(Math.max(0.0, weightedResidualVariance / weight));
        return new Result(residualStdDev, maxSupportedResidual, true, usable);
    }
}
