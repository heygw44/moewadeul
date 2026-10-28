package com.moewadeul.spot.domain.bakji;

import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bakji_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BakjiDetail {

    @Id
    @Column(name = "spot_id")
    private Long spotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "spot_id")
    @Getter(AccessLevel.NONE)
    private Spot spot;

    @Column(name = "reporter_id")
    private long reporterId;

    private String description;

    @Column(name = "has_water")
    private boolean hasWater;

    @Column(name = "has_toilet")
    private boolean hasToilet;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_level")
    private BakjiSignalLevel signalLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "ground_type")
    private BakjiGroundType groundType;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private BakjiDetail(Spot spot, long reporterId, BakjiContent content, Instant now) {
        this.spot = spot;
        this.reporterId = reporterId;
        this.description = content.description();
        this.hasWater = content.hasWater();
        this.hasToilet = content.hasToilet();
        this.signalLevel = content.signalLevel();
        this.groundType = content.groundType();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static BakjiDetail of(Spot spot, long reporterId, BakjiContent content, Instant now) {
        if (spot == null || content == null || now == null) {
            throw new IllegalArgumentException("박지 상세를 만드는 데 필요한 값이 null입니다.");
        }
        if (spot.getId() == null) {
            throw new IllegalArgumentException("박지 상세는 저장된 장소에만 붙일 수 있습니다.");
        }
        if (spot.getType() != SpotType.BAKJI) {
            throw new IllegalArgumentException("박지가 아닌 장소에는 박지 상세를 붙일 수 없습니다.");
        }
        return new BakjiDetail(spot, reporterId, content, now);
    }

    public boolean isReportedBy(long memberId) {
        return reporterId == memberId;
    }

    public void changeDescription(String description, Instant now) {
        BakjiContent.requireValidDescription(description);
        this.description = description;
        this.updatedAt = now;
    }

    public void changeFacilities(boolean hasWater, boolean hasToilet, Instant now) {
        this.hasWater = hasWater;
        this.hasToilet = hasToilet;
        this.updatedAt = now;
    }

    public void changeSignalLevel(BakjiSignalLevel signalLevel, Instant now) {
        this.signalLevel = signalLevel;
        this.updatedAt = now;
    }

    public void changeGroundType(BakjiGroundType groundType, Instant now) {
        this.groundType = groundType;
        this.updatedAt = now;
    }
}
