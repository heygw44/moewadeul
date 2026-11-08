package com.moewadeul.member.domain.signup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminDisposableEmailDomainRepository {

    boolean existsByDomainIn(Collection<String> domains);

    boolean existsByDomain(String domain);

    List<AdminDisposableEmailDomain> findAllByOrderByIdDesc();

    Optional<AdminDisposableEmailDomain> findById(Long id);

    AdminDisposableEmailDomain save(AdminDisposableEmailDomain domain);

    void delete(AdminDisposableEmailDomain domain);
}
