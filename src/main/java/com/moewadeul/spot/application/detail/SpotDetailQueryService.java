package com.moewadeul.spot.application.detail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpotDetailQueryService {

    private final SpotDetailReader spotDetailReader;

    public SpotDetail findDetail(long spotId) {
        return spotDetailReader.read(spotId);
    }
}
