package com.moewadeul.basecamp.domain;

import java.util.Collection;
import java.util.Optional;

public interface BasecampRepository {

    Basecamp saveAndFlush(Basecamp basecamp);

    Optional<Basecamp> findById(Long id);

    Optional<Basecamp> findByIdForUpdate(Long id);

    void flush();

    long countByLeaderIdAndStatusIn(long leaderId, Collection<BasecampStatus> statuses);
}
