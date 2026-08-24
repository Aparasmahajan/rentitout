package io.radius.common.support;

/**
 * A member's home point is never stored exactly. Rounding to roughly 100 m is
 * enough for "2 km away" and not enough to find someone's front door.
 */
public final class Geo {

    private static final double METRES_PER_DEG_LAT = 111_320d;
    private static final double PRECISION_M = 100d;

    public static double roundLat(double lat) {
        double step = PRECISION_M / METRES_PER_DEG_LAT;
        return Math.round(lat / step) * step;
    }

    public static double roundLon(double lon, double lat) {
        double metresPerDegLon = METRES_PER_DEG_LAT * Math.cos(Math.toRadians(lat));
        if (Math.abs(metresPerDegLon) < 1) return lon;      // at the poles, leave it alone
        double step = PRECISION_M / metresPerDegLon;
        return Math.round(lon / step) * step;
    }

    /**
     * Snap a point to a coarser grid. Used for anonymous callers: a guest can
     * browse, but should not be handed a precise map of who owns what and
     * where. Signed-in members still see the ~100 m points.
     */
    public static double[] coarsen(double lat, double lon, double metres) {
        double latStep = metres / METRES_PER_DEG_LAT;
        double snappedLat = Math.round(lat / latStep) * latStep;
        double metresPerDegLon = METRES_PER_DEG_LAT * Math.cos(Math.toRadians(lat));
        if (Math.abs(metresPerDegLon) < 1) return new double[]{snappedLat, lon};
        double lonStep = metres / metresPerDegLon;
        return new double[]{snappedLat, Math.round(lon / lonStep) * lonStep};
    }

    /** Great-circle distance in metres. Only for display; filtering happens in PostGIS. */
    public static double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Geo() {}
}
