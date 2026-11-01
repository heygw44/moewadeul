package com.moewadeul.trip.infra;

import com.moewadeul.trip.domain.SoloTrip;
import com.moewadeul.trip.domain.SoloTripRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoloTripJpaRepository extends JpaRepository<SoloTrip, Long>, SoloTripRepository {

    @Override
    SoloTrip saveAndFlush(SoloTrip trip);

    @Override
    Optional<SoloTrip> findById(Long id);

    @Override
    void delete(SoloTrip trip);

    @Override
    List<SoloTrip> findByMemberIdAndEndDateAfter(long memberId, LocalDate date);
}
