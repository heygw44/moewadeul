package com.moewadeul.spot.domain.bakji;

public interface BakjiConfirmationRepository {

    BakjiConfirmation saveAndFlush(BakjiConfirmation confirmation);

    boolean existsBySpotIdAndMemberId(long spotId, long memberId);

    long countBySpotId(long spotId);
}
