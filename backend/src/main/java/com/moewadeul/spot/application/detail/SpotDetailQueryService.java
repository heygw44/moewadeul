package com.moewadeul.spot.application.detail;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.image.application.ImageView;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.spot.application.ParkWarningTexts;
import com.moewadeul.spot.application.SpotClosedNow;
import com.moewadeul.spot.application.SpotParkWarning;
import com.moewadeul.spot.domain.SpotType;
import com.moewadeul.spot.infra.detail.SpotConfirmedProgramRow;
import com.moewadeul.spot.infra.detail.SpotConfirmedStayRow;
import com.moewadeul.spot.infra.detail.SpotDetailMapper;
import com.moewadeul.spot.infra.detail.SpotDetailRow;
import com.moewadeul.spot.infra.detail.SpotRatingRow;
import com.moewadeul.spot.infra.detail.SpotRecentReviewRow;
import com.moewadeul.spot.infra.detail.SpotRecruitingBasecampRow;
import com.moewadeul.spot.infra.detail.SpotReviewImageRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class SpotDetailQueryService {

    private static final int RECENT_REVIEW_COUNT = 3;

    private static final TypeReference<Map<String, String>> FACILITIES_TYPE = new TypeReference<>() {};

    private final SpotDetailMapper spotDetailMapper;
    private final ImageStorage imageStorage;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SpotDetail findDetail(long spotId) {
        SpotDetailRow row = spotDetailMapper.selectActiveDetail(spotId).orElseThrow(BusinessException::notFound);
        LocalDate today = KoreaTime.today(clock);
        return new SpotDetail(
                row.spotId(),
                row.type(),
                row.name(),
                row.lat(),
                row.lng(),
                row.address(),
                toParkWarning(row),
                toBakjiDetail(row),
                toPublicDetail(row, today),
                toRating(spotDetailMapper.selectRating(spotId)),
                readRecentReviews(spotId),
                toExpectedPeople(readStays(spotId, today), today),
                spotDetailMapper.selectRecruitingBasecamps(spotId).stream()
                        .map(SpotDetailQueryService::toRecruitingBasecamp)
                        .toList());
    }

    private List<SpotConfirmedStayRow> readStays(long spotId, LocalDate today) {
        List<SpotConfirmedStayRow> stays = new ArrayList<>(spotDetailMapper.selectConfirmedStays(spotId, today));
        stays.addAll(spotDetailMapper.selectSoloTrips(spotId, today));
        Instant startOfToday = today.atStartOfDay(KoreaTime.ZONE).toInstant();
        for (SpotConfirmedProgramRow program : spotDetailMapper.selectConfirmedPrograms(spotId, startOfToday)) {
            stays.add(new SpotConfirmedStayRow(
                    KoreaTime.dateOf(program.startAt()), KoreaTime.dateOf(program.endAt()), program.headcount()));
        }
        return stays;
    }

    private static List<SpotExpectedPeople> toExpectedPeople(List<SpotConfirmedStayRow> stays, LocalDate today) {
        Map<LocalDate, Integer> countByNight = new TreeMap<>();
        for (SpotConfirmedStayRow stay : stays) {
            LocalDate firstNight = stay.startDate().isBefore(today) ? today : stay.startDate();
            for (LocalDate night = firstNight; night.isBefore(stay.endDate()); night = night.plusDays(1)) {
                countByNight.merge(night, stay.headcount(), Integer::sum);
            }
        }
        return countByNight.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new SpotExpectedPeople(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static SpotRecruitingBasecamp toRecruitingBasecamp(SpotRecruitingBasecampRow row) {
        return new SpotRecruitingBasecamp(
                row.basecampId(),
                row.title(),
                row.startDate(),
                row.endDate(),
                row.capacity(),
                row.headcount(),
                new SpotJoinCondition(row.minTrustLevel(), row.ageGroupMin(), row.ageGroupMax(), row.sameGenderOnly()));
    }

    private static SpotRating toRating(SpotRatingRow row) {
        return new SpotRating(row.average(), row.count());
    }

    private List<SpotRecentReview> readRecentReviews(long spotId) {
        List<SpotRecentReviewRow> rows = spotDetailMapper.selectRecentReviews(spotId, RECENT_REVIEW_COUNT);
        Map<Long, List<ImageView>> images = new HashMap<>();
        if (!rows.isEmpty()) {
            List<Long> reviewIds =
                    rows.stream().map(SpotRecentReviewRow::reviewId).toList();
            for (SpotReviewImageRow image : spotDetailMapper.selectReviewImages(reviewIds)) {
                images.computeIfAbsent(image.reviewId(), id -> new ArrayList<>())
                        .add(new ImageView(image.imageId(), imageStorage.presignView(image.objectKey())));
            }
        }
        return rows.stream()
                .map(row -> toRecentReview(row, images.getOrDefault(row.reviewId(), List.of())))
                .toList();
    }

    private static SpotRecentReview toRecentReview(SpotRecentReviewRow row, List<ImageView> images) {
        return new SpotRecentReview(
                row.reviewId(),
                row.authorId(),
                row.authorNickname(),
                row.visitedDate(),
                row.rating(),
                row.content(),
                List.copyOf(images),
                row.createdAt());
    }

    private static SpotParkWarning toParkWarning(SpotDetailRow row) {
        if (!row.parkWarning()) {
            return SpotParkWarning.notWarned();
        }
        return SpotParkWarning.warned(row.areaName(), row.areaSource(), row.areaSourceDate());
    }

    private static SpotBakjiDetail toBakjiDetail(SpotDetailRow row) {
        if (row.type() != SpotType.BAKJI || row.reporterId() == null) {
            return null;
        }
        return new SpotBakjiDetail(
                row.description(),
                row.hasWater(),
                row.hasToilet(),
                row.signalLevel(),
                row.confirmationCount(),
                row.reporterId(),
                row.reporterNickname(),
                ParkWarningTexts.bakjiCheckNoticeOf(row.parkWarning()));
    }

    private SpotPublicDetail toPublicDetail(SpotDetailRow row, LocalDate today) {
        if (row.type() == SpotType.BAKJI || row.source() == null) {
            return null;
        }
        boolean closedNow =
                SpotClosedNow.isClosedOn(row.type(), row.operatingStatus(), row.closedFrom(), row.closedUntil(), today);
        return new SpotPublicDetail(
                row.source(),
                row.category(),
                toFacilities(row.facilities()),
                row.phone(),
                row.homepage(),
                row.sourceDate(),
                row.checkedAt(),
                row.operatingStatus(),
                row.closedFrom(),
                row.closedUntil(),
                closedNow);
    }

    private SpotFacilities toFacilities(String facilitiesJson) {
        if (facilitiesJson == null) {
            return null;
        }
        Map<String, String> source = jsonMapper.readValue(facilitiesJson, FACILITIES_TYPE);
        if (source == null || source.isEmpty()) {
            return null;
        }
        return new SpotFacilities(
                source.get("toiletCo"),
                source.get("swrmCo"),
                source.get("wtrplCo"),
                source.get("brazierCl"),
                source.get("sbrsCl"),
                source.get("sbrsEtc"),
                source.get("posblFcltyCl"),
                source.get("posblFcltyEtc"),
                source.get("animalCmgCl"));
    }
}
