package com.moewadeul.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "program_vacancy_alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramVacancyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id")
    private long programId;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "created_at")
    private Instant createdAt;
}
