package com.moewadeul.member.infra.signup;

import com.moewadeul.member.domain.signup.AdminDisposableEmailDomain;
import com.moewadeul.member.domain.signup.AdminDisposableEmailDomainRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminDisposableEmailDomainJpaRepository
        extends JpaRepository<AdminDisposableEmailDomain, Long>, AdminDisposableEmailDomainRepository {

    @Override
    AdminDisposableEmailDomain save(AdminDisposableEmailDomain domain);

    @Override
    Optional<AdminDisposableEmailDomain> findById(Long id);

    @Override
    void delete(AdminDisposableEmailDomain domain);
}
