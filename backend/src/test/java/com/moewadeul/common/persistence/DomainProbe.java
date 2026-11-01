package com.moewadeul.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "disposable_email_domain")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainProbe {

    @Id
    private String domain;

    private String source;

    @Column(name = "created_at")
    private Instant createdAt;

    public DomainProbe(String domain, String source, Instant createdAt) {
        this.domain = domain;
        this.source = source;
        this.createdAt = createdAt;
    }
}
