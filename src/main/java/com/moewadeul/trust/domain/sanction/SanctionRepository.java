package com.moewadeul.trust.domain.sanction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SanctionRepository {

    Sanction save(Sanction sanction);

    Optional<Byte> findHighestConfirmedLevel(long memberId);

    boolean existsActiveSuspension(long memberId, Instant now);

    boolean existsCiRetentionSanction(long memberId);

    Optional<Sanction> findById(Long id);

    Optional<Long> findMemberIdById(long sanctionId);

    Optional<Sanction> findByIdForUpdate(long id);

    List<Sanction> findActiveSuspensions(long memberId);

    List<Sanction> findUnrevokedTemporaryByReportId(long reportId);
}
