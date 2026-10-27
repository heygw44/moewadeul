package com.moewadeul.spot.domain;

/** 변환식은 기상청 단기예보 오픈 API 활용가이드에 실린 람베르트 정각원추도법(Lambert Conformal Conic) C 예제를 그대로 옮겼다. */
public record WeatherGrid(int nx, int ny) {

    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_SPACING_KM = 5.0;
    private static final double FIRST_STANDARD_PARALLEL_RADIANS = Math.toRadians(30.0);
    private static final double SECOND_STANDARD_PARALLEL_RADIANS = Math.toRadians(60.0);
    private static final double ORIGIN_LONGITUDE_RADIANS = Math.toRadians(126.0);
    private static final double ORIGIN_LATITUDE_RADIANS = Math.toRadians(38.0);

    private static final double ORIGIN_X_GRID = 210.0 / GRID_SPACING_KM;
    private static final double ORIGIN_Y_GRID = 675.0 / GRID_SPACING_KM;

    private static final double GRID_INDEX_OFFSET = 1.5;

    private static final int GRID_WIDTH = 149;
    private static final int GRID_HEIGHT = 253;

    private static final double GRID_RADIUS = EARTH_RADIUS_KM / GRID_SPACING_KM;
    private static final double CONE_CONSTANT = coneConstant();
    private static final double SCALE_FACTOR = scaleFactor();
    private static final double ORIGIN_RADIUS = radiusAt(ORIGIN_LATITUDE_RADIANS);

    public WeatherGrid {
        if (!isInRange(nx, ny)) {
            throw new IllegalArgumentException(
                    "기상청 격자 범위(X 1~" + GRID_WIDTH + ", Y 1~" + GRID_HEIGHT + ")를 벗어났습니다. nx=" + nx + ", ny=" + ny);
        }
    }

    public static WeatherGrid from(GeoPoint point) {
        ProjectedIndex index = project(point);
        return new WeatherGrid(index.nx(), index.ny());
    }

    public static boolean covers(GeoPoint point) {
        ProjectedIndex index = project(point);
        return isInRange(index.nx(), index.ny());
    }

    private static boolean isInRange(int nx, int ny) {
        return nx >= 1 && nx <= GRID_WIDTH && ny >= 1 && ny <= GRID_HEIGHT;
    }

    private static ProjectedIndex project(GeoPoint point) {
        double radius = radiusAt(Math.toRadians(point.latitude()));
        double theta = longitudeOffsetFromOrigin(point.longitude()) * CONE_CONSTANT;
        double x = radius * Math.sin(theta) + ORIGIN_X_GRID;
        double y = ORIGIN_RADIUS - radius * Math.cos(theta) + ORIGIN_Y_GRID;
        return new ProjectedIndex(toGridIndex(x), toGridIndex(y));
    }

    private record ProjectedIndex(int nx, int ny) {}

    private static double longitudeOffsetFromOrigin(double longitude) {
        double offset = Math.toRadians(longitude) - ORIGIN_LONGITUDE_RADIANS;
        if (offset > Math.PI) {
            return offset - 2 * Math.PI;
        }
        if (offset < -Math.PI) {
            return offset + 2 * Math.PI;
        }
        return offset;
    }

    private static int toGridIndex(double projected) {
        return (int) Math.floor(projected + GRID_INDEX_OFFSET);
    }

    private static double coneConstant() {
        double cosineRatio = Math.cos(FIRST_STANDARD_PARALLEL_RADIANS) / Math.cos(SECOND_STANDARD_PARALLEL_RADIANS);
        double tangentRatio =
                conformalTangent(SECOND_STANDARD_PARALLEL_RADIANS) / conformalTangent(FIRST_STANDARD_PARALLEL_RADIANS);
        return Math.log(cosineRatio) / Math.log(tangentRatio);
    }

    private static double scaleFactor() {
        return Math.pow(conformalTangent(FIRST_STANDARD_PARALLEL_RADIANS), CONE_CONSTANT)
                * Math.cos(FIRST_STANDARD_PARALLEL_RADIANS)
                / CONE_CONSTANT;
    }

    private static double radiusAt(double latitudeRadians) {
        return GRID_RADIUS * SCALE_FACTOR / Math.pow(conformalTangent(latitudeRadians), CONE_CONSTANT);
    }

    private static double conformalTangent(double latitudeRadians) {
        return Math.tan(Math.PI / 4 + latitudeRadians / 2);
    }
}
