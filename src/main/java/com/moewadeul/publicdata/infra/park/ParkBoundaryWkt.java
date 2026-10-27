package com.moewadeul.publicdata.infra.park;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

final class ParkBoundaryWkt {

    private static final double MAX_ABS_LONGITUDE = 180;
    private static final double MAX_ABS_LATITUDE = 90;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    // JTS의 WKTReader는 스레드 안전하지 않다.
    private final WKTReader wktReader = new WKTReader(geometryFactory);
    private final WKTWriter wktWriter = new WKTWriter();

    String toMultiPolygonWkt(String wkt) {
        Geometry geometry = parse(wkt);
        if (!(geometry instanceof MultiPolygon) && !(geometry instanceof Polygon)) {
            throw new IllegalArgumentException("MULTIPOLYGON이나 POLYGON이 아닙니다. type=" + geometry.getGeometryType());
        }
        if (geometry.isEmpty()) {
            throw new IllegalArgumentException("빈 도형입니다.");
        }
        requireLongitudeLatitudeRange(geometry);
        if (geometry instanceof Polygon polygon) {
            return wktWriter.write(geometryFactory.createMultiPolygon(new Polygon[] {polygon}));
        }
        return wkt;
    }

    // JTS는 고리가 닫히지 않은 도형을 만나면 ParseException이 아니라 IllegalArgumentException을 던진다. 그 예외는 그대로 호출하는 쪽에 올라간다.
    private Geometry parse(String wkt) {
        try {
            return wktReader.read(wkt);
        } catch (ParseException e) {
            throw new IllegalArgumentException("WKT를 읽지 못했습니다. " + e.getMessage(), e);
        }
    }

    // 이 메서드는 범위 안인 조건을 먼저 계산하고, 그 조건이 거짓이면 거부한다. NaN은 어떤 크기 비교에서도 false가 되므로, 이렇게 써야 NaN도 걸러진다.
    private static void requireLongitudeLatitudeRange(Geometry geometry) {
        for (Coordinate coordinate : geometry.getCoordinates()) {
            boolean inRange =
                    Math.abs(coordinate.getX()) <= MAX_ABS_LONGITUDE && Math.abs(coordinate.getY()) <= MAX_ABS_LATITUDE;
            if (!inRange) {
                throw new IllegalArgumentException(
                        "좌표가 경도 -180~180, 위도 -90~90 범위 밖입니다. 경도=" + coordinate.getX() + ", 위도=" + coordinate.getY());
            }
        }
    }
}
