package com.moewadeul.spot.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotRepository;
import com.moewadeul.spot.domain.SpotStatus;
import com.moewadeul.spot.domain.SpotType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActiveSpotChecker {

    private final SpotRepository spotRepository;

    @Transactional(readOnly = true)
    public void requireActive(long spotId) {
        if (!isActive(spotId)) {
            throw BusinessException.notFound();
        }
    }

    @Transactional(readOnly = true)
    public boolean isActive(long spotId) {
        return spotRepository
                .findById(spotId)
                .filter(ActiveSpotChecker::isActiveSpot)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isWarningBakji(long spotId) {
        Spot spot = spotRepository
                .findById(spotId)
                .filter(ActiveSpotChecker::isActiveSpot)
                .orElseThrow(BusinessException::notFound);
        return spot.getType() == SpotType.BAKJI && spot.isParkWarning();
    }

    private static boolean isActiveSpot(Spot spot) {
        return spot.getStatus() == SpotStatus.ACTIVE;
    }
}
