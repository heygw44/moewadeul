package com.moewadeul.member.infra.signup;

import com.moewadeul.member.domain.signup.DisposableEmailDomain;
import com.moewadeul.member.domain.signup.DisposableEmailDomainRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisposableEmailDomainJpaRepository
        extends JpaRepository<DisposableEmailDomain, String>, DisposableEmailDomainRepository {

    @Override
    DisposableEmailDomain save(DisposableEmailDomain domain);

    @Override
    Optional<DisposableEmailDomain> findById(String domain);
}
