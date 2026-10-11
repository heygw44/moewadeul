package com.moewadeul.trip.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.spot.application.ActiveSpotChecker;
import com.moewadeul.trip.domain.SoloTrip;
import com.moewadeul.trip.domain.SoloTripRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 혼자 가는 일정을 등록하고, 자기 일정을 지운다.
 *
 * <p>일정을 등록할 수 있는 장소는 지도에 보이는(ACTIVE) 장소뿐이다. 베이스캠프와 달리 공원 경계 경고 박지도 막지 않는다. 다른 회원을 모으는 일이
 * 아니고, 장소 상세가 경고와 야영 금지 안내를 그대로 보여 주기 때문이다.
 *
 * <p>서비스는 회원의 진행 예정 일정을 저장하기 전에 읽어 개수와 밤 겹침을 확인할 뿐 잠그지 않는다. 그래서 같은 회원의 요청 두 개가 동시에 들어오면 둘 다
 * 검사를 통과할 수 있다. 베이스캠프 열기도 같은 방식이고, 한 사람이 거의 동시에 연달아 등록하는 경우는 드물어서 지금은 막지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SoloTripCommandService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final ActiveSpotChecker activeSpotChecker;
    private final SoloTripRepository soloTripRepository;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 일정을 저장하고 일정 ID를 돌려준다.
     *
     * <p>출발일이 내일부터 60일 안이 아니거나 1~3박이 아니면 SOLO_TRIP_SCHEDULE_INVALID로, 진행 예정 일정이 이미 3개면 SOLO_TRIP_LIMIT로,
     * 그중 하나와 밤이 겹치면 SOLO_TRIP_OVERLAP으로 거부한다. 장소가 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다.
     */
    @Transactional
    public long register(long memberId, SoloTripRegisterCommand command) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KOREA);
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

    /** 호출하면 memberId인 회원의 일정을 지운다. 일정이 없으면 NOT_FOUND로, 다른 회원의 일정이면 ACCESS_DENIED로 거부한다. */
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
