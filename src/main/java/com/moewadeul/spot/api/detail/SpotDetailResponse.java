package com.moewadeul.spot.api.detail;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.spot.api.ParkWarningResponse;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.detail.SpotBakjiDetail;
import com.moewadeul.spot.application.detail.SpotDetail;
import com.moewadeul.spot.application.detail.SpotExpectedPeople;
import com.moewadeul.spot.application.detail.SpotFacilities;
import com.moewadeul.spot.application.detail.SpotJoinCondition;
import com.moewadeul.spot.application.detail.SpotPublicDetail;
import com.moewadeul.spot.application.detail.SpotRating;
import com.moewadeul.spot.application.detail.SpotRecentReview;
import com.moewadeul.spot.application.detail.SpotRecruitingBasecamp;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.domain.SpotType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SpotDetailResponse(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        String address,
        Bakji bakji,
        PublicDetail publicDetail,
        ParkWarningResponse parkWarning,
        Rating rating,
        List<RecentReview> recentReviews,
        List<ExpectedPeople> expectedPeople,
        List<RecruitingBasecamp> recruitingBasecamps) {

    static SpotDetailResponse from(SpotDetail detail) {
        return new SpotDetailResponse(
                detail.spotId(),
                detail.type(),
                detail.name(),
                detail.lat(),
                detail.lng(),
                detail.address(),
                detail.bakji() == null ? null : Bakji.from(detail.bakji()),
                detail.publicDetail() == null ? null : PublicDetail.from(detail.publicDetail()),
                ParkWarningResponse.from(detail.parkWarning()),
                Rating.from(detail.rating()),
                detail.recentReviews().stream().map(RecentReview::from).toList(),
                detail.expectedPeople().stream().map(ExpectedPeople::from).toList(),
                detail.recruitingBasecamps().stream()
                        .map(RecruitingBasecamp::from)
                        .toList());
    }

    public record ExpectedPeople(LocalDate date, int count) {

        static ExpectedPeople from(SpotExpectedPeople expectedPeople) {
            return new ExpectedPeople(expectedPeople.date(), expectedPeople.count());
        }
    }

    public record RecruitingBasecamp(
            long basecampId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            JoinCondition joinCondition) {

        static RecruitingBasecamp from(SpotRecruitingBasecamp basecamp) {
            return new RecruitingBasecamp(
                    basecamp.basecampId(),
                    basecamp.title(),
                    basecamp.startDate(),
                    basecamp.endDate(),
                    basecamp.capacity(),
                    basecamp.headcount(),
                    JoinCondition.from(basecamp.joinCondition()));
        }
    }

    public record JoinCondition(
            Integer minTrustLevel, Integer ageGroupMin, Integer ageGroupMax, boolean sameGenderOnly) {

        static JoinCondition from(SpotJoinCondition condition) {
            return new JoinCondition(
                    condition.minTrustLevel(),
                    condition.ageGroupMin(),
                    condition.ageGroupMax(),
                    condition.sameGenderOnly());
        }
    }

    public record Bakji(
            String description,
            boolean hasWater,
            boolean hasToilet,
            String signalLevel,
            long confirmationCount,
            Reporter reporter,
            @JsonInclude(JsonInclude.Include.NON_NULL) String notice) {

        static Bakji from(SpotBakjiDetail bakji) {
            return new Bakji(
                    bakji.description(),
                    bakji.hasWater(),
                    bakji.hasToilet(),
                    bakji.signalLevel(),
                    bakji.confirmationCount(),
                    new Reporter(bakji.reporterId(), bakji.reporterNickname()),
                    bakji.notice());
        }
    }

    public record Reporter(long memberId, String nickname) {}

    public record PublicDetail(
            PublicSpotSource source,
            String category,
            Facilities facilities,
            String phone,
            String homepage,
            LocalDate sourceDate,
            Instant checkedAt,
            PublicSpotOperatingStatus operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil,
            boolean closedNow) {

        static PublicDetail from(SpotPublicDetail detail) {
            return new PublicDetail(
                    detail.source(),
                    detail.category(),
                    detail.facilities() == null ? null : Facilities.from(detail.facilities()),
                    detail.phone(),
                    detail.homepage(),
                    detail.sourceDate(),
                    detail.checkedAt(),
                    detail.operatingStatus(),
                    detail.closedFrom(),
                    detail.closedUntil(),
                    detail.closedNow());
        }
    }

    public record Facilities(
            String toiletCount,
            String showerCount,
            String sinkCount,
            String brazier,
            String amenities,
            String amenitiesEtc,
            String nearbyFacilities,
            String nearbyFacilitiesEtc,
            String petPolicy) {

        static Facilities from(SpotFacilities facilities) {
            return new Facilities(
                    facilities.toiletCount(),
                    facilities.showerCount(),
                    facilities.sinkCount(),
                    facilities.brazier(),
                    facilities.amenities(),
                    facilities.amenitiesEtc(),
                    facilities.nearbyFacilities(),
                    facilities.nearbyFacilitiesEtc(),
                    facilities.petPolicy());
        }
    }

    public record Rating(Double average, long count) {

        static Rating from(SpotRating rating) {
            return new Rating(rating.average(), rating.count());
        }
    }

    public record Author(long memberId, String nickname) {}

    public record RecentReview(
            long reviewId,
            Author author,
            LocalDate visitedDate,
            int rating,
            String content,
            List<Image> images,
            Instant createdAt) {

        static RecentReview from(SpotRecentReview review) {
            return new RecentReview(
                    review.reviewId(),
                    new Author(review.authorId(), review.authorNickname()),
                    review.visitedDate(),
                    review.rating(),
                    review.content(),
                    review.images().stream()
                            .map(image -> new Image(image.imageId(), image.url()))
                            .toList(),
                    review.createdAt());
        }

        public record Image(long imageId, String url) {}
    }
}
