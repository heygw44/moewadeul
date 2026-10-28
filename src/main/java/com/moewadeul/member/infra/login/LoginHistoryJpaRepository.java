package com.moewadeul.member.infra.login;

import com.moewadeul.member.domain.login.LoginHistory;
import com.moewadeul.member.domain.login.LoginHistoryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginHistoryJpaRepository extends JpaRepository<LoginHistory, Long>, LoginHistoryRepository {

    @Override
    LoginHistory save(LoginHistory loginHistory);

    @Override
    @Query("""
            SELECT h.createdAt FROM LoginHistory h
            WHERE h.attemptedEmail = :attemptedEmail
              AND h.success = false
              AND h.createdAt > :after
              AND h.createdAt > COALESCE(
                  (SELECT m.passwordChangedAt FROM Member m WHERE m.email = :attemptedEmail), :after)
              AND h.id > COALESCE(
                  (SELECT MAX(s.id) FROM LoginHistory s
                   WHERE s.attemptedEmail = :attemptedEmail AND s.success = true), 0L)
            ORDER BY h.createdAt ASC
            """)
    List<Instant> findFailureTimesSinceLastSuccess(
            @Param("attemptedEmail") String attemptedEmail, @Param("after") Instant after);
}
