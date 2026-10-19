package com.moewadeul.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 한 명의 알림함에 들어가는 알림 한 건. 회원은 다른 모듈의 엔티티라서 ID로만 가리킨다.
 * 같은 이벤트로 같은 회원에게 알림이 두 번 생기지 않도록 dedupKey는 DB에서 유일하다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    private String type;

    private String title;

    private String body;

    private String link;

    @Column(name = "dedup_key")
    private String dedupKey;

    @Column(name = "read_at")
    private Instant readAt;

    // 메일 열은 처음 저장할 때 상태만 넣고, 그 뒤로는 메일 발송 작업이 조건부 UPDATE로만 바꾼다.
    // 엔티티로 알림을 읽음 처리할 때 Hibernate가 읽어 둔 예전 값으로 이 열들을 덮어쓰지 않도록 updatable = false로 둔다.
    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", updatable = false)
    private NotificationEmailStatus emailStatus;

    @Column(name = "email_attempt_count", insertable = false, updatable = false)
    private int emailAttemptCount;

    @Column(name = "email_next_attempt_at", insertable = false, updatable = false)
    private Instant emailNextAttemptAt;

    @Column(name = "email_sent_at", insertable = false, updatable = false)
    private Instant emailSentAt;

    @Column(name = "created_at")
    private Instant createdAt;

    private Notification(
            long memberId,
            String type,
            String title,
            String body,
            String link,
            String dedupKey,
            NotificationEmailStatus emailStatus,
            Instant now) {
        this.memberId = memberId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
        this.dedupKey = dedupKey;
        this.emailStatus = emailStatus;
        this.createdAt = now;
    }

    /**
     * 호출하면 안 읽은 상태의 알림을 만든다. link는 연결할 화면이 없으면 null이다. emailStatus는 메일을 보낼 후보면 PENDING,
     * 보내지 않기로 했으면 SKIPPED다. 메일을 보낸 결과(SENT, FAILED)는 메일 발송 작업만 기록한다.
     */
    public static Notification create(
            long memberId,
            String type,
            String title,
            String body,
            String link,
            String dedupKey,
            NotificationEmailStatus emailStatus,
            Instant now) {
        if (type == null || title == null || body == null || dedupKey == null || now == null) {
            throw new IllegalArgumentException("알림의 종류, 제목, 본문, 중복 방지 키, 생성 시각은 비어 있을 수 없습니다.");
        }
        if (emailStatus != NotificationEmailStatus.PENDING && emailStatus != NotificationEmailStatus.SKIPPED) {
            throw new IllegalArgumentException("새 알림의 메일 상태는 PENDING이나 SKIPPED여야 합니다. emailStatus=" + emailStatus);
        }
        return new Notification(memberId, type, title, body, link, dedupKey, emailStatus, now);
    }

    /** 호출하면 아직 읽지 않은 알림을 now에 읽은 것으로 바꾼다. 이미 읽었다면 처음 읽은 시각을 그대로 둔다. */
    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}
