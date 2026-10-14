package com.moewadeul.trip.application;

import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.trip.infra.SoloTripMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SoloTripQueryService {

    private final SoloTripMapper soloTripMapper;
    private final Clock clock;

    /** 호출하면 memberId인 회원의 일정 중 종료일이 오늘(한국 날짜)보다 뒤인 것을 출발일 순서로 돌려준다. */
    @Transactional(readOnly = true)
    public List<SoloTripItem> listUpcoming(long memberId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KoreaTime.ZONE);
        return soloTripMapper.selectUpcomingByMember(memberId, today).stream()
                .map(SoloTripItem::from)
                .toList();
    }
}
