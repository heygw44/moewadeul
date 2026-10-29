package com.moewadeul.review.application;

import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.review.infra.SpotReviewMapper;
import com.moewadeul.review.infra.SpotReviewRow;
import com.moewadeul.spot.application.ActiveSpotChecker;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpotReviewQueryService {

    private final ActiveSpotChecker activeSpotChecker;
    private final SpotReviewMapper spotReviewMapper;
    private final ReviewImageReader reviewImageReader;

    @Transactional(readOnly = true)
    public PageSlice<SpotReviewItem> list(long spotId, int page, int size) {
        activeSpotChecker.requireActive(spotId);
        List<SpotReviewRow> rows =
                spotReviewMapper.selectBySpot(spotId, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        PageSlice<SpotReviewRow> slice = PageSlice.of(rows, page, size);
        Map<Long, List<SpotReviewImage>> images = reviewImageReader.readByReviewIds(
                slice.content().stream().map(SpotReviewRow::reviewId).toList());
        return slice.map(row -> SpotReviewItem.from(row, images.getOrDefault(row.reviewId(), List.of())));
    }
}
