package com.moewadeul.spot.infra.bakji;

import com.moewadeul.spot.domain.bakji.BakjiConfirmation;
import com.moewadeul.spot.domain.bakji.BakjiConfirmationId;
import com.moewadeul.spot.domain.bakji.BakjiConfirmationRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BakjiConfirmationJpaRepository
        extends JpaRepository<BakjiConfirmation, BakjiConfirmationId>, BakjiConfirmationRepository {

    @Override
    BakjiConfirmation saveAndFlush(BakjiConfirmation confirmation);
}
