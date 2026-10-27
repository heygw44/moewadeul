package com.moewadeul.trust.domain.report;

import java.util.Optional;

public interface MemberReportRepository {

    MemberReport saveAndFlush(MemberReport report);

    Optional<MemberReport> findById(Long id);

    Optional<MemberReport> findByIdForUpdate(long id);
}
