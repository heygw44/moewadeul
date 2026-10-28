package com.moewadeul.trip.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.spot.application.ActiveSpotChecker;
import com.moewadeul.trip.domain.SoloTrip;
import com.moewadeul.trip.domain.SoloTripRepository;
import com.moewadeul.trip.infra.SoloTripMemberLockMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SoloTripCommandService {

    private final ActiveSpotChecker activeSpotChecker;
    private final SoloTripRepository soloTripRepository;
    private final SoloTripMemberLockMapper soloTripMemberLockMapper;
    private final Clock clock;

    @Transactional
    public long register(long memberId, SoloTripRegisterCommand command) {
        // 잠금이 트랜잭션의 첫 쿼리여야 한다. 그래야 아래 일정 조회가 잠금을 얻은 뒤의 스냅숏을 읽어서, 먼저 끝난 요청이 저장한 일정이 보인다.
        soloTripMemberLockMapper.lockMember(memberId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KoreaTime.ZONE);
        activeSpotChecker.requireActive(command.spotId());
        SoloTrip trip = SoloTrip.register(
                memberId,
                command.spotId(),
                new SoloTrip.Schedule(command.startDate(), command.endDate()),
                today,
                soloTripRepository.findByMemberIdAndEndDateAfter(memberId, today),
                now);
        return soloTripRepository.saveAndFlush(trip).getId();
    }

    @Transactional
    public void delete(long memberId, long soloTripId) {
        SoloTrip trip = soloTripRepository
                .findById(soloTripId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!trip.isRegisteredBy(memberId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        soloTripRepository.delete(trip);
    }
}
