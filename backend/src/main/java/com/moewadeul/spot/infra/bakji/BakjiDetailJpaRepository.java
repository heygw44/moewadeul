package com.moewadeul.spot.infra.bakji;

import com.moewadeul.spot.domain.bakji.BakjiDetail;
import com.moewadeul.spot.domain.bakji.BakjiDetailRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BakjiDetailJpaRepository extends JpaRepository<BakjiDetail, Long>, BakjiDetailRepository {

    @Override
    BakjiDetail save(BakjiDetail bakjiDetail);

    @Override
    Optional<BakjiDetail> findById(Long spotId);
}
