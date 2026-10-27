package com.moewadeul.spot.application.bakji;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.web.PatchField;
import com.moewadeul.spot.application.ParkWarningTexts;
import com.moewadeul.spot.application.SpotParkWarning;
import com.moewadeul.spot.application.SpotSearchBox;
import com.moewadeul.spot.application.park.ParkAreaJudgeService;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotRepository;
import com.moewadeul.spot.domain.WeatherGrid;
import com.moewadeul.spot.domain.bakji.BakjiContent;
import com.moewadeul.spot.domain.bakji.BakjiDetail;
import com.moewadeul.spot.domain.bakji.BakjiDetailRepository;
import com.moewadeul.spot.domain.bakji.BakjiGroundType;
import com.moewadeul.spot.domain.bakji.BakjiSignalLevel;
import com.moewadeul.spot.infra.bakji.BakjiDuplicateCondition;
import com.moewadeul.spot.infra.bakji.BakjiDuplicateMapper;
import com.moewadeul.spot.infra.bakji.BakjiDuplicateRow;
import com.moewadeul.spot.infra.park.ParkAreaJudgeMapper;
import com.moewadeul.spot.infra.park.ProtectedAreaEvidenceRow;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BakjiCommandService {

    private static final int DUPLICATE_RADIUS_METERS = 50;
    private static final int MAX_DUPLICATE_CANDIDATES = 10;

    private final SpotRepository spotRepository;
    private final BakjiDetailRepository bakjiDetailRepository;
    private final ParkAreaJudgeService parkAreaJudgeService;
    private final ParkAreaJudgeMapper parkAreaJudgeMapper;
    private final BakjiDuplicateMapper bakjiDuplicateMapper;
    private final Clock clock;

    @Transactional
    public BakjiSubmission report(long memberId, BakjiReportCommand command) {
        Instant now = clock.instant();
        String name = requireName(command.name());
        GeoPoint location = toLocation(command.lat(), command.lng());
        requireInServiceArea(location);
        requireValidDescription(command.description());
        BakjiContent content = new BakjiContent(
                command.description(),
                command.hasWater(),
                command.hasToilet(),
                parseEnum(BakjiSignalLevel.class, command.signalLevel(), "통신 상태는"),
                parseEnum(BakjiGroundType.class, command.groundType(), "바닥 유형은"));

        ParkAreaJudgement judgement = parkAreaJudgeService.judge(location);
        Spot spot = spotRepository.save(Spot.bakji(name, location, judgement, now));
        bakjiDetailRepository.save(BakjiDetail.of(spot, memberId, content, now));
        return submissionOf(spot);
    }

    @Transactional
    public BakjiSubmission update(long memberId, long spotId, BakjiUpdateCommand command) {
        Spot spot = loadActiveBakjiToUpdate(spotId, command);
        BakjiDetail detail = loadOwnDetail(spotId, memberId);
        Instant now = clock.instant();
        applySpotChanges(spot, command, now);
        applyDetailChanges(detail, command, now);
        return submissionOf(spot);
    }

    @Transactional
    public void delete(long memberId, long spotId) {
        Spot spot = loadActiveBakji(spotId);
        loadOwnDetail(spotId, memberId);
        spot.delete(clock.instant());
    }

    private Spot loadActiveBakji(long spotId) {
        return spotRepository
                .findById(spotId)
                .filter(Spot::isActiveBakji)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private Spot loadActiveBakjiToUpdate(long spotId, BakjiUpdateCommand command) {
        if (!changesDetail(command)) {
            return loadActiveBakji(spotId);
        }
        return spotRepository
                .findByIdForVersionIncrement(spotId)
                .filter(Spot::isActiveBakji)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private static boolean changesDetail(BakjiUpdateCommand command) {
        return command.description().present()
                || command.hasWater().present()
                || command.hasToilet().present()
                || command.signalLevel().present()
                || command.groundType().present();
    }

    private BakjiDetail loadOwnDetail(long spotId, long memberId) {
        BakjiDetail detail = bakjiDetailRepository
                .findById(spotId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!detail.isReportedBy(memberId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return detail;
    }

    private void applySpotChanges(Spot spot, BakjiUpdateCommand command, Instant now) {
        if (command.name().present()) {
            spot.rename(requireName(command.name().value()), now);
        }
        moveIfChanged(spot, command, now);
    }

    private void moveIfChanged(Spot spot, BakjiUpdateCommand command, Instant now) {
        PatchField<Double> lat = command.lat();
        PatchField<Double> lng = command.lng();
        if (lat.present() != lng.present()) {
            throw invalidInput("위도와 경도는 함께 보내 주세요.");
        }
        if (!lat.present()) {
            return;
        }
        if (lat.value() == null || lng.value() == null) {
            throw invalidInput("위도와 경도는 지울 수 없어요.");
        }
        GeoPoint newLocation = toLocation(lat.value(), lng.value());
        if (newLocation.equals(spot.getLocation())) {
            return;
        }
        requireInServiceArea(newLocation);
        spot.moveTo(newLocation, parkAreaJudgeService.judge(newLocation), now);
    }

    private void applyDetailChanges(BakjiDetail detail, BakjiUpdateCommand command, Instant now) {
        if (command.description().present()) {
            requireValidDescription(command.description().value());
            detail.changeDescription(command.description().value(), now);
        }
        if (command.hasWater().present() || command.hasToilet().present()) {
            detail.changeFacilities(
                    facilityOrCurrent(command.hasWater(), detail.isHasWater(), "물 유무는"),
                    facilityOrCurrent(command.hasToilet(), detail.isHasToilet(), "화장실 유무는"),
                    now);
        }
        if (command.signalLevel().present()) {
            detail.changeSignalLevel(
                    parseEnum(BakjiSignalLevel.class, command.signalLevel().value(), "통신 상태는"), now);
        }
        if (command.groundType().present()) {
            detail.changeGroundType(
                    parseEnum(BakjiGroundType.class, command.groundType().value(), "바닥 유형은"), now);
        }
    }

    private static boolean facilityOrCurrent(PatchField<Boolean> field, boolean current, String topic) {
        if (!field.present()) {
            return current;
        }
        if (field.value() == null) {
            throw invalidInput(topic + " 지울 수 없어요.");
        }
        return field.value();
    }

    private BakjiSubmission submissionOf(Spot spot) {
        boolean warned = spot.isParkWarning();
        SpotParkWarning parkWarning = warned ? warningOf(spot) : SpotParkWarning.notWarned();
        String guide = warned ? ParkWarningTexts.WARNED_GUIDE : ParkWarningTexts.NORMAL_GUIDE;
        return new BakjiSubmission(
                spot.getId(),
                parkWarning,
                guide,
                ParkWarningTexts.bakjiCheckNoticeOf(warned),
                findDuplicateCandidates(spot));
    }

    private SpotParkWarning warningOf(Spot spot) {
        ProtectedAreaEvidenceRow area = spot.getProtectedAreaId() == null
                ? null
                : parkAreaJudgeMapper.selectAreaEvidence(spot.getProtectedAreaId());
        if (area == null) {
            return SpotParkWarning.warned(null, null, null);
        }
        return SpotParkWarning.warned(area.name(), area.source(), area.sourceDate());
    }

    private List<BakjiDuplicateCandidate> findDuplicateCandidates(Spot spot) {
        GeoPoint location = spot.getLocation();
        SpotSearchBox box =
                SpotSearchBox.around(location.latitude(), location.longitude(), DUPLICATE_RADIUS_METERS / 1000.0);
        BakjiDuplicateCondition condition = new BakjiDuplicateCondition(
                location.latitude(),
                location.longitude(),
                DUPLICATE_RADIUS_METERS,
                box.swLat(),
                box.swLng(),
                box.neLat(),
                box.neLng(),
                spot.getId(),
                MAX_DUPLICATE_CANDIDATES);
        return bakjiDuplicateMapper.selectDuplicates(condition).stream()
                .map(BakjiCommandService::toCandidate)
                .toList();
    }

    private static BakjiDuplicateCandidate toCandidate(BakjiDuplicateRow row) {
        return new BakjiDuplicateCandidate(row.spotId(), row.name(), (int) Math.round(row.distanceMeters()));
    }

    private static String requireName(String name) {
        if (name == null) {
            throw invalidInput("이름은 지울 수 없어요.");
        }
        if (name.isBlank()) {
            throw invalidInput("이름은 비워 둘 수 없어요.");
        }
        if (name.length() > Spot.NAME_MAX_LENGTH) {
            throw invalidInput("이름은 " + Spot.NAME_MAX_LENGTH + "자 이하여야 해요.");
        }
        return name;
    }

    private static void requireValidDescription(String description) {
        if (description != null && description.length() > BakjiContent.DESCRIPTION_MAX_LENGTH) {
            throw invalidInput("설명은 " + BakjiContent.DESCRIPTION_MAX_LENGTH + "자 이하여야 해요.");
        }
    }

    private static GeoPoint toLocation(double lat, double lng) {
        try {
            return new GeoPoint(lat, lng);
        } catch (IllegalArgumentException e) {
            throw invalidInput(e.getMessage());
        }
    }

    private static void requireInServiceArea(GeoPoint location) {
        if (!WeatherGrid.covers(location)) {
            throw invalidInput("서비스 지역 밖 좌표예요.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String topic) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(value))
                .findFirst()
                .orElseThrow(() -> invalidInput(topic + " " + allowedValues(type) + " 중 하나여야 해요."));
    }

    private static String allowedValues(Class<? extends Enum<?>> type) {
        return String.join(
                ", ", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
