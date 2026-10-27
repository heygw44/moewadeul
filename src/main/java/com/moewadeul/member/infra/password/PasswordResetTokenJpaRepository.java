package com.moewadeul.member.infra.password;

import com.moewadeul.member.domain.password.PasswordResetToken;
import com.moewadeul.member.domain.password.PasswordResetTokenRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenJpaRepository
        extends JpaRepository<PasswordResetToken, Long>, PasswordResetTokenRepository {

    @Override
    PasswordResetToken save(PasswordResetToken token);

    @Override
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
