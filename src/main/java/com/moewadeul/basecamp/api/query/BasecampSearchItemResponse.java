package com.moewadeul.basecamp.api.query;

import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.query.BasecampSearchItem;
import com.moewadeul.basecamp.application.query.BasecampSpotType;
import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.basecamp.domain.JoinUnmetReason;
import java.time.LocalDate;
import java.util.List;

public sealed interface BasecampSearchItemResponse {

    static BasecampSearchItemResponse from(BasecampSearchItem item) {
        SpotResponse spot = new SpotResponse(
                item.spot().spotId(),
                item.spot().name(),
                item.spot().type(),
                item.spot().lat(),
                item.spot().lng());
        if (item.eligibility() == null) {
            return new Anonymous(
                    item.basecampId(),
                    item.title(),
                    spot,
                    item.startDate(),
                    item.endDate(),
                    item.capacity(),
                    item.headcount(),
                    item.status(),
                    item.joinCondition());
        }
        return new Member(
                item.basecampId(),
                item.title(),
                spot,
                item.startDate(),
                item.endDate(),
                item.capacity(),
                item.headcount(),
                item.status(),
                item.joinCondition(),
                item.eligibility().canApply(),
                item.eligibility().unmetReasons());
    }

    public record Anonymous(
            long basecampId,
            String title,
            SpotResponse spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            BasecampStatus status,
            JoinConditionSummary joinCondition)
            implements BasecampSearchItemResponse {}

    public record Member(
            long basecampId,
            String title,
            SpotResponse spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            BasecampStatus status,
            JoinConditionSummary joinCondition,
            boolean canApply,
            List<JoinUnmetReason> unmetReasons)
            implements BasecampSearchItemResponse {}

    public record SpotResponse(long spotId, String name, BasecampSpotType type, double lat, double lng) {}
}
