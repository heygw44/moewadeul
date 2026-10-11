package com.pitchmap.trip.infra;

import com.pitchmap.trip.domain.SoloTrip;
import com.pitchmap.trip.domain.SoloTripRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
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
