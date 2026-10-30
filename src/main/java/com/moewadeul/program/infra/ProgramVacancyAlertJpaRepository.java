package com.moewadeul.program.infra;

import com.moewadeul.program.domain.ProgramVacancyAlert;
import com.moewadeul.program.domain.ProgramVacancyAlertRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramVacancyAlertJpaRepository
        extends JpaRepository<ProgramVacancyAlert, Long>, ProgramVacancyAlertRepository {

    @Override
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT IGNORE INTO program_vacancy_alert (program_id, member_id, created_at)
            VALUES (:programId, :memberId, :now)
            """)
    int subscribe(@Param("programId") long programId, @Param("memberId") long memberId, @Param("now") Instant now);

    @Override
    @Modifying
    @Query("delete from ProgramVacancyAlert v where v.programId = :programId and v.memberId = :memberId")
    void unsubscribe(@Param("programId") long programId, @Param("memberId") long memberId);

    // 잠금 읽기는 다른 트랜잭션이 잠근 행의 커밋을 기다린 뒤 최신 알림 시각으로 조건을 본다.
    // 바깥 SELECT의 FOR UPDATE는 하위 쿼리의 신청 행을 잠그지 않는다.
    @Override
    @Query(nativeQuery = true, value = """
            SELECT v.member_id FROM program_vacancy_alert v
            WHERE v.program_id = :programId
              AND NOT EXISTS (
                  SELECT 1 FROM program_application a
                  WHERE a.program_id = v.program_id
                    AND a.member_id = v.member_id
                    AND a.status IN ('PENDING_PAYMENT', 'CONFIRMED'))
              AND (v.notified_at IS NULL OR v.notified_at <= :notifiedBefore)
            ORDER BY v.id
            FOR UPDATE
            """)
    List<Long> lockAlertTargets(@Param("programId") long programId, @Param("notifiedBefore") Instant notifiedBefore);

    @Override
    @Modifying
    @Query("""
            update ProgramVacancyAlert v
            set v.notifiedAt = :now
            where v.programId = :programId
              and v.memberId in :memberIds
            """)
    void markNotified(
            @Param("programId") long programId,
            @Param("memberIds") Collection<Long> memberIds,
            @Param("now") Instant now);
}
