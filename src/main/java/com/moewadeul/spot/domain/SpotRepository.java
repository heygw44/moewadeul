package com.moewadeul.spot.domain;

import java.util.Optional;

public interface SpotRepository {

    Spot save(Spot spot);

    Optional<Spot> findById(Long id);

    Optional<Spot> findByIdForUpdate(long id);

    Optional<Spot> findByIdForVersionIncrement(long id);
}
