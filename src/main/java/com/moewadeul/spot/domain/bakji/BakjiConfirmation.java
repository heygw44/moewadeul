package com.moewadeul.spot.domain.bakji;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 기본 키를 호출하는 쪽이 직접 정하기 때문에, Spring Data의 {@code save()}는 이 엔티티가 새 것인지 알 수 없다. 그래서 키가 있는 객체를
 * 기존 행으로 보고 먼저 SELECT를 한 뒤 병합(merge)한다. 이 엔티티는 {@link Persistable}을 구현하고, 저장하거나 읽기 전에는 새 것이라고
 * 답해서 곧바로 INSERT를 보낸다.
 */
@Entity
@Table(name = "bakji_confirmation")
@IdClass(BakjiConfirmationId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BakjiConfirmation implements Persistable<BakjiConfirmationId> {

    @Id
    @Column(name = "spot_id")
    private long spotId;

    @Id
    @Column(name = "member_id")
    private long memberId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    private BakjiConfirmation(long spotId, long memberId, Instant now) {
        this.spotId = spotId;
        this.memberId = memberId;
        this.createdAt = now;
    }

    public static BakjiConfirmation of(long spotId, long memberId, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("박지 확인 시각이 null입니다.");
        }
        return new BakjiConfirmation(spotId, memberId, now);
    }

    @Override
    public BakjiConfirmationId getId() {
        return new BakjiConfirmationId(spotId, memberId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
