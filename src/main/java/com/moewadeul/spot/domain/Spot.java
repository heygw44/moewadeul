package com.moewadeul.spot.domain;

import com.moewadeul.common.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "spot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Spot {

    public static final int NAME_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private SpotType type;

    private String name;

    @Getter(AccessLevel.NONE)
    private Point location;

    private String address;

    @Column(name = "weather_nx")
    private short weatherNx;

    @Column(name = "weather_ny")
    private short weatherNy;

    @Column(name = "park_warning")
    private boolean parkWarning;

    @Column(name = "protected_area_id")
    private Long protectedAreaId;

    @Column(name = "area_checked_at")
    private Instant areaCheckedAt;

    @Enumerated(EnumType.STRING)
    private SpotStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private long version;

    private Spot(SpotType type, String name, GeoPoint location, WeatherGrid weatherGrid, Instant now) {
        this.type = type;
        this.name = name;
        this.location = location.toPoint();
        this.weatherNx = (short) weatherGrid.nx();
        this.weatherNy = (short) weatherGrid.ny();
        this.status = SpotStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Spot bakji(String name, GeoPoint location, ParkAreaJudgement judgement, Instant now) {
        if (name == null || location == null || judgement == null || now == null) {
            throw new IllegalArgumentException("박지를 만드는 데 필요한 값이 null입니다.");
        }
        requireValidName(name);
        Spot spot = new Spot(SpotType.BAKJI, name, location, WeatherGrid.from(location), now);
        spot.applyParkAreaJudgement(judgement);
        return spot;
    }

    public void applyParkAreaJudgement(ParkAreaJudgement judgement) {
        if (judgement == null) {
            throw new IllegalArgumentException("공원 경계 판정 결과가 null입니다.");
        }
        this.parkWarning = judgement.parkWarning();
        this.protectedAreaId = judgement.protectedAreaId();
        this.areaCheckedAt = judgement.checkedAt();
    }

    public void rename(String name, Instant now) {
        requireValidName(name);
        this.name = name;
        this.updatedAt = now;
    }

    public void moveTo(GeoPoint newLocation, ParkAreaJudgement judgement, Instant now) {
        if (newLocation == null || judgement == null || now == null) {
            throw new IllegalArgumentException("장소를 옮기는 데 필요한 값이 null입니다.");
        }
        WeatherGrid grid = WeatherGrid.from(newLocation);
        this.location = newLocation.toPoint();
        this.weatherNx = (short) grid.nx();
        this.weatherNy = (short) grid.ny();
        applyParkAreaJudgement(judgement);
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.status = SpotStatus.DELETED;
        this.updatedAt = now;
    }

    public void markPendingReview(Instant now) {
        if (status != SpotStatus.ACTIVE) {
            throw new IllegalStateException("지도에 보이는 장소만 검토 대기로 바꿀 수 있습니다. 현재 상태: " + status);
        }
        this.status = SpotStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    public void hide(Instant now) {
        if (status != SpotStatus.ACTIVE && status != SpotStatus.PENDING_REVIEW) {
            throw new BusinessException(SpotErrorCode.SPOT_INVALID_STATE);
        }
        this.status = SpotStatus.HIDDEN;
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        if (status != SpotStatus.PENDING_REVIEW && status != SpotStatus.HIDDEN) {
            throw new BusinessException(SpotErrorCode.SPOT_INVALID_STATE);
        }
        this.status = SpotStatus.ACTIVE;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return status == SpotStatus.DELETED;
    }

    public boolean isPublicData() {
        return type == SpotType.CAMPSITE || type == SpotType.FOREST;
    }

    public boolean isActiveBakji() {
        return type == SpotType.BAKJI && status == SpotStatus.ACTIVE;
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("박지 이름이 비어 있습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("장소 이름은 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    public GeoPoint getLocation() {
        return GeoPoint.from(location);
    }
}
