package com.moewadeul.member.domain.signup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_disposable_email_domain")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminDisposableEmailDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String domain;

    @Column(name = "created_at")
    private Instant createdAt;

    private AdminDisposableEmailDomain(String domain, Instant createdAt) {
        this.domain = domain;
        this.createdAt = createdAt;
    }

    public static AdminDisposableEmailDomain add(String domain, Instant now) {
        if (domain == null || domain.isBlank() || now == null) {
            throw new IllegalArgumentException("관리자 차단 도메인 생성에 필요한 값이 올바르지 않습니다.");
        }
        return new AdminDisposableEmailDomain(domain.toLowerCase(Locale.ROOT), now);
    }
}
