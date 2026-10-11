package com.pitchmap.trip.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SoloTripRepository {

    SoloTrip saveAndFlush(SoloTrip trip);

    Optional<SoloTrip> findById(Long id);

    void delete(SoloTrip trip);

    /** 호출하면 memberId인 회원의 일정 중 종료일이 date보다 뒤인 것을 읽는다. */
    List<SoloTrip> findByMemberIdAndEndDateAfter(long memberId, LocalDate date);
}
