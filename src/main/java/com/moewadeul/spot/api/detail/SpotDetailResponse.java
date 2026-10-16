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

// expectedPeople은 오늘부터 야영하는 밤별 인원이고, 인원이 있는 날짜만 날짜 순으로 준다. recruitingBasecamps는 모집 중인 베이스캠프만 출발일 순으로 준다.
// 둘 다 없으면 빈 배열이다.
// bakji.notice는 경고가 아닌 박지에만 내보낸다. parkWarning은 경고가 아니면 warned만 내보낸다. 날씨는 이 응답에 넣지 않고 SpotWeatherResponse로 따로 준다.
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

    // 베이스캠프 검색 결과의 항목에서 장소와 상태, 신청 가능 여부를 뺀 모양이다. 신청 가능 여부는 로그인한 회원마다 달라서 베이스캠프 상세에서 본다.
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

    // 평균은 후기가 없으면 null이다. 후기 수는 항상 준다.
    public record Rating(Double average, long count) {

        static Rating from(SpotRating rating) {
            return new Rating(rating.average(), rating.count());
        }
    }

    public record Author(long memberId, String nickname) {}

    // 후기 목록의 항목과 같은 모양이다. 작성자는 회원 ID와 닉네임만 내보낸다.
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
