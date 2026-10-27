package com.moewadeul.review.application;

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
    public SpotReviewPage list(long spotId, int page, int size) {
        activeSpotChecker.requireActive(spotId);
        long offset = (long) page * size;
        List<SpotReviewRow> rows = spotReviewMapper.selectBySpot(spotId, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<SpotReviewRow> pageRows = rows.stream().limit(size).toList();
        Map<Long, List<SpotReviewImage>> images = reviewImageReader.readByReviewIds(
                pageRows.stream().map(SpotReviewRow::reviewId).toList());
        List<SpotReviewItem> content = pageRows.stream()
                .map(row -> SpotReviewItem.from(row, images.getOrDefault(row.reviewId(), List.of())))
                .toList();
        return new SpotReviewPage(content, page, size, hasNext);
    }
}
