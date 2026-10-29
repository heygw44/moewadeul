package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.application.JoinApplicants;
import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.JoinEligibility;
import com.moewadeul.basecamp.domain.BasecampSchedule;
import com.moewadeul.basecamp.domain.JoinCondition;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.moewadeul.basecamp.domain.PriorRelation;
import com.moewadeul.basecamp.infra.query.BasecampSearchCondition;
import com.moewadeul.basecamp.infra.query.BasecampSearchMapper;
import com.moewadeul.basecamp.infra.query.BasecampSearchRow;
import com.moewadeul.basecamp.infra.query.ViewerHistoryRow;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.spot.application.SpotSearchBox;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampSearchService {

    private static final double METERS_PER_KILOMETER = 1000;

    private final BasecampSearchMapper basecampSearchMapper;
    private final TrustSummaryService trustSummaryService;

    @Transactional(readOnly = true)
    public PageSlice<BasecampSearchItem> search(Long viewerId, BasecampSearchQuery query) {
        List<BasecampSearchRow> rows = basecampSearchMapper.selectRecruiting(
                toCondition(query), PageSlice.offsetOf(query.page(), query.size()), PageSlice.fetchSize(query.size()));
        PageSlice<BasecampSearchRow> slice = PageSlice.of(rows, query.page(), query.size());
        EligibilityJudge judge = viewerId == null ? null : judgeFor(viewerId, slice.content());
        return slice.map(row -> toItem(row, judge));
    }

    private static BasecampSearchCondition toCondition(BasecampSearchQuery query) {
        BasecampSearchCondition.Area area = null;
        BasecampSearchCondition.Nearby nearby = null;
        switch (query.region()) {
            case BasecampSearchQuery.Area region ->
                area = new BasecampSearchCondition.Area(region.swLat(), region.swLng(), region.neLat(), region.neLng());
            case BasecampSearchQuery.Radius radius -> nearby = toNearby(radius);
        }
        return new BasecampSearchCondition(
                area,
                nearby,
                query.fromDate(),
                query.toDate(),
                query.hasVacancy(),
                query.spotTypes(),
                query.nights(),
                query.maxTrustLevel(),
                query.noAgeLimit(),
                query.noSameGenderOnly());
    }

    private static BasecampSearchCondition.Nearby toNearby(BasecampSearchQuery.Radius radius) {
        SpotSearchBox box = SpotSearchBox.around(radius.lat(), radius.lng(), radius.radiusKm());
        return new BasecampSearchCondition.Nearby(
                radius.lat(),
                radius.lng(),
                radius.radiusKm() * METERS_PER_KILOMETER,
                box.swLat(),
                box.swLng(),
                box.neLat(),
                box.neLng());
    }

    private EligibilityJudge judgeFor(long viewerId, List<BasecampSearchRow> pageRows) {
        if (pageRows.isEmpty()) {
            return null;
        }
        Applicant applicant = JoinApplicants.from(trustSummaryService.detail(viewerId));
        List<Long> basecampIds =
                pageRows.stream().map(BasecampSearchRow::basecampId).toList();
        Map<Long, PriorRelation> priorRelations = new HashMap<>();
        for (ViewerHistoryRow history : basecampSearchMapper.selectViewerHistory(viewerId, basecampIds)) {
            priorRelations.put(
                    history.basecampId(), PriorRelation.of(history.memberStatus(), history.applicationStatus()));
        }
        return new EligibilityJudge(
                applicant, priorRelations, basecampSearchMapper.selectInProgressSchedules(viewerId));
    }

    private static BasecampSearchItem toItem(BasecampSearchRow row, EligibilityJudge judge) {
        JoinConditionSummary condition = new JoinConditionSummary(
                row.minTrustLevel(), row.ageGroupMin(), row.ageGroupMax(), row.sameGenderOnly());
        return new BasecampSearchItem(
                row.basecampId(),
                row.title(),
                new BasecampSearchItem.SpotSummary(
                        row.spotId(), row.spotName(), row.spotType(), row.spotLat(), row.spotLng()),
                row.startDate(),
                row.endDate(),
                row.capacity(),
                row.headcount(),
                row.status(),
                condition,
                judge == null ? null : judge.judge(row));
    }

    private record EligibilityJudge(
            Applicant applicant, Map<Long, PriorRelation> priorRelations, List<BasecampSchedule> schedules) {

        JoinEligibility judge(BasecampSearchRow row) {
            JoinCondition condition = toJoinCondition(row);
            PriorRelation prior = priorRelations.getOrDefault(row.basecampId(), PriorRelation.NONE);
            return JoinEligibility.of(
                    JoinEligibilityPolicy.unmetReasons(condition, applicant, hasDateConflict(row), prior));
        }

        private boolean hasDateConflict(BasecampSearchRow row) {
            return JoinEligibilityPolicy.hasDateConflict(
                    new BasecampSchedule(row.basecampId(), row.startDate(), row.endDate()), schedules);
        }

        private static JoinCondition toJoinCondition(BasecampSearchRow row) {
            JoinCondition condition = JoinCondition.none();
            if (row.minTrustLevel() != null) {
                condition = condition.withMinTrustLevel(row.minTrustLevel());
            }
            if (row.ageGroupMin() != null && row.ageGroupMax() != null) {
                condition = condition.withAgeGroupRange(row.ageGroupMin(), row.ageGroupMax());
            }
            if (row.sameGenderOnly()) {
                condition = condition.withSameGenderOnly(row.requiredGender());
            }
            return condition;
        }
    }
}
