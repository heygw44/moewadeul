package com.moewadeul.notification.domain.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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
@Table(name = "notification_setting")
@IdClass(NotificationSettingId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting implements Persistable<NotificationSettingId> {

    @Id
    @Column(name = "member_id")
    private long memberId;

    @Id
    private String type;

    @Column(name = "email_enabled")
    private boolean emailEnabled;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    private NotificationSetting(long memberId, String type, boolean emailEnabled) {
        this.memberId = memberId;
        this.type = type;
        this.emailEnabled = emailEnabled;
    }

    public static NotificationSetting of(long memberId, String type, boolean emailEnabled) {
        if (type == null) {
            throw new IllegalArgumentException("알림 종류가 null입니다.");
        }
        return new NotificationSetting(memberId, type, emailEnabled);
    }

    @Override
    public NotificationSettingId getId() {
        return new NotificationSettingId(memberId, type);
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
