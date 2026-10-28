package com.moewadeul.basecamp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "basecamp_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BasecampApplication {

    public static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "basecamp_id")
    private Basecamp basecamp;

    @Column(name = "applicant_id")
    private long applicantId;

    private String message;

    @Enumerated(EnumType.STRING)
    private BasecampApplicationStatus status;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private BasecampApplication(Basecamp basecamp, long applicantId, String message, Instant now) {
        this.basecamp = basecamp;
        this.applicantId = applicantId;
        this.message = message;
        this.status = BasecampApplicationStatus.PENDING;
        this.appliedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    static BasecampApplication submit(Basecamp basecamp, long applicantId, String message, Instant now) {
        requireValidMessage(message);
        return new BasecampApplication(basecamp, applicantId, message, now);
    }

    public boolean isFrom(long memberId) {
        return applicantId == memberId;
    }

    public boolean isPending() {
        return status == BasecampApplicationStatus.PENDING;
    }

    void approve(Instant now) {
        this.status = BasecampApplicationStatus.APPROVED;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    void reject(Instant now) {
        this.status = BasecampApplicationStatus.REJECTED;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    void cancel(Instant now) {
        this.status = BasecampApplicationStatus.CANCELED;
        this.updatedAt = now;
    }

    void expire(Instant now) {
        this.status = BasecampApplicationStatus.EXPIRED;
        this.updatedAt = now;
    }

    void resubmit(String message, Instant now) {
        requireValidMessage(message);
        this.message = message;
        this.status = BasecampApplicationStatus.PENDING;
        this.decidedAt = null;
        this.appliedAt = now;
        this.updatedAt = now;
    }

    private static void requireValidMessage(String message) {
        if (message != null && message.length() > MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException("신청 메시지는 " + MESSAGE_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
