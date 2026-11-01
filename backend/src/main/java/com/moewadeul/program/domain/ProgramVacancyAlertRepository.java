package com.moewadeul.program.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProgramVacancyAlertRepository {

    int subscribe(long programId, long memberId, Instant now);

    void unsubscribe(long programId, long memberId);

    void deleteByMember(long memberId);

    List<Long> lockAlertTargets(long programId, Instant notifiedBefore);

    void markNotified(long programId, Collection<Long> memberIds, Instant now);
}
