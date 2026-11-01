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

    @Transactional(readOnly = true)
    public List<SoloTripItem> listUpcoming(long memberId) {
        LocalDate today = KoreaTime.today(clock);
        return soloTripMapper.selectUpcomingByMember(memberId, today).stream()
                .map(SoloTripItem::from)
                .toList();
    }
}
