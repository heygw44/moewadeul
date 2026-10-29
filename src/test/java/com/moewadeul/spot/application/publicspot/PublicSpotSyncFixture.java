package com.moewadeul.spot.application.publicspot;

import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.PublicSpotSetting;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

final class PublicSpotSyncFixture {

    static final double SEOUL_LATITUDE = 37.5665;
    static final double SEOUL_LONGITUDE = 126.978;
    static final double CHUNCHEON_LATITUDE = 37.8813;
    static final double CHUNCHEON_LONGITUDE = 127.7298;
    static final Duration NEXT_SYNC = Duration.ofDays(1);

    private final JdbcTemplate jdbcTemplate;

    PublicSpotSyncFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    static PublicSpotCommand forest(String externalId, LocalDate sourceDate) {
        return new PublicSpotCommand(
                externalId,
                "솔숲 자연휴양림",
                CHUNCHEON_LATITUDE,
                CHUNCHEON_LONGITUDE,
                "강원 춘천시 남산면 1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                sourceDate,
                Set.of());
    }

    static PublicSpotCommand campsiteWithSettings(String externalId, Set<PublicSpotSetting> settings) {
        return new PublicSpotCommand(
                externalId,
                "입지 야영장 " + externalId,
                CHUNCHEON_LATITUDE,
                CHUNCHEON_LONGITUDE,
                "강원 춘천시 남산면 1",
                "일반야영장",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                settings);
    }

    static PublicSpotCommand campsite(String externalId, String name, double latitude, double longitude) {
        return campsite(externalId, name, latitude, longitude, null, null, null);
    }

    static PublicSpotCommand campsite(
            String externalId,
            String name,
            double latitude,
            double longitude,
            PublicSpotOperatingStatus operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil) {
        Map<String, String> facilities = Map.of("sbrsCl", "전기,무선인터넷", "toiletCo", "4", "animalCmgCl", "불가능");
        return new PublicSpotCommand(
                externalId,
                name,
                latitude,
                longitude,
                "서울 중구 세종대로 110",
                "일반야영장",
                facilities,
                "02-000-0000",
                "https://example.com",
                operatingStatus,
                closedFrom,
                closedUntil,
                null,
                Set.of());
    }

    static PublicSpotCommand seoulCampsite(String externalId, String name) {
        return campsite(externalId, name, SEOUL_LATITUDE, SEOUL_LONGITUDE);
    }

    List<String> findSettings(String externalId) {
        return jdbcTemplate.queryForList(
                "SELECT setting FROM public_spot_setting WHERE spot_id = ?",
                String.class,
                findSpotId(PublicSpotSource.GOCAMPING, externalId));
    }

    LocalDate findSourceDate(PublicSpotSource source, String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT source_date FROM public_spot_detail WHERE source = ? AND external_id = ?",
                LocalDate.class,
                source.name(),
                externalId);
    }

    void markSourceRemovedBySql(long spotId, Instant removedAt) {
        jdbcTemplate.update(
                "UPDATE public_spot_detail SET source_removed_at = ? WHERE spot_id = ?",
                Timestamp.from(removedAt),
                spotId);
    }

    void setSpotStatus(long spotId, String status) {
        jdbcTemplate.update("UPDATE spot SET status = ? WHERE id = ?", status, spotId);
    }

    int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count;
    }

    long findSpotId(PublicSpotSource source, String externalId) {
        Long spotId = jdbcTemplate.queryForObject(
                "SELECT spot_id FROM public_spot_detail WHERE source = ? AND external_id = ?",
                Long.class,
                source.name(),
                externalId);
        return spotId;
    }

    StoredSpot findSpot(PublicSpotSource source, String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT s.type, s.status, s.name, s.address, ST_Latitude(s.location) AS latitude,"
                        + " ST_Longitude(s.location) AS longitude, s.weather_nx, s.weather_ny, s.park_warning,"
                        + " s.created_at, s.updated_at, d.category, d.facilities, d.phone, d.homepage, d.synced_at,"
                        + " d.updated_at AS detail_updated_at, d.operating_status, d.closed_from, d.closed_until,"
                        + " d.source_removed_at"
                        + " FROM public_spot_detail d JOIN spot s ON s.id = d.spot_id"
                        + " WHERE d.source = ? AND d.external_id = ?",
                (rs, rowNum) -> new StoredSpot(
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getString("name"),
                        rs.getString("address"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("weather_nx"),
                        rs.getInt("weather_ny"),
                        rs.getBoolean("park_warning"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getString("category"),
                        rs.getString("facilities"),
                        rs.getString("phone"),
                        rs.getString("homepage"),
                        rs.getTimestamp("synced_at").toInstant(),
                        rs.getTimestamp("detail_updated_at").toInstant(),
                        rs.getString("operating_status"),
                        rs.getObject("closed_from", LocalDate.class),
                        rs.getObject("closed_until", LocalDate.class),
                        toInstantOrNull(rs.getTimestamp("source_removed_at"))),
                source.name(),
                externalId);
    }

    record StoredSpot(
            String type,
            String status,
            String name,
            String address,
            double latitude,
            double longitude,
            int weatherNx,
            int weatherNy,
            boolean parkWarning,
            Instant createdAt,
            Instant updatedAt,
            String category,
            String facilities,
            String phone,
            String homepage,
            Instant syncedAt,
            Instant detailUpdatedAt,
            String operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil,
            Instant sourceRemovedAt) {}

    private static Instant toInstantOrNull(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
