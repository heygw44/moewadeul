package com.moewadeul.trip.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SoloTripRepository {

    SoloTrip saveAndFlush(SoloTrip trip);

    Optional<SoloTrip> findById(Long id);

    void delete(SoloTrip trip);

    List<SoloTrip> findByMemberIdAndEndDateAfter(long memberId, LocalDate date);
}
