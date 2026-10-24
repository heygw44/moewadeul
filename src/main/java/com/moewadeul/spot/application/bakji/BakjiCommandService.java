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

/**
 * 회원이 박지를 제보하고, 자기가 제보한 박지를 고치거나 지운다.
 *
 * <p>서비스는 제보하거나 좌표를 바꿀 때 새 좌표로 공원 경계 판정을 한 번 하고, 결과를 박지 행에 저장한다. 응답에는 저장한 판정 결과와, 같은
 * 좌표 50m 안에 이미 있는 박지(중복 후보)를 싣는다. 중복 후보가 있어도 제보는 막지 않는다. 같은 박지를 다시 제보하는 것인지는 제보자가 정한다.
 *
 * <p>고치거나 지울 수 있는 박지는 지도에 보이는(ACTIVE) 박지뿐이다. 장소가 없거나, 박지가 아니거나, ACTIVE가 아니면 서비스는 NOT_FOUND로
 * 거부한다. 숨긴 박지가 있는지 드러내지 않으려고 이 경우들을 구분하지 않는다. 그다음 다른 회원이 제보한 박지면 ACCESS_DENIED로 거부한다.
 */
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

    /**
     * 호출하면 memberId인 회원의 제보로 박지를 저장하고 결과를 돌려준다.
     *
     * <p>좌표가 기상청 격자 밖이거나, 값이 규칙을 어기거나, 통신 상태·바닥 유형이 허용 값이 아니면 서비스는 INVALID_INPUT으로 거부한다.
     */
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

    /**
     * 호출하면 제보자 memberId가 요청에 담은 필드만 고치고 결과를 돌려준다. 요청에 없는 필드는 그대로 둔다.
     *
     * <p>좌표가 실제로 바뀔 때만 서비스가 새 좌표로 공원 경계를 다시 판정한다. 같은 좌표를 다시 보내면 판정은 그대로다.
     *
     * <p>박지 행을 잠그지 않고 읽으므로, 그 사이 다른 쓰기가 먼저 커밋하면 커밋할 때 낙관적 잠금 충돌로 실패한다. 제보 상세만 고치는 요청도 같다. 다시
     * 시도는 {@link BakjiEditService}가 한다. 컨트롤러는 그 서비스를 부른다.
     */
    @Transactional
    public BakjiSubmission update(long memberId, long spotId, BakjiUpdateCommand command) {
        Spot spot = loadActiveBakjiToUpdate(spotId, command);
        BakjiDetail detail = loadOwnDetail(spotId, memberId);
        Instant now = clock.instant();
        applySpotChanges(spot, command, now);
        applyDetailChanges(detail, command, now);
        return submissionOf(spot);
    }

    /** 호출하면 제보자 memberId의 박지를 삭제 상태로 바꾼다. 행은 지우지 않는다. */
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

    // 제보 상세만 고치는 요청은 박지 행을 바꾸지 않는다. 그래서 그대로 두면 JPA가 박지 행의 낙관적 잠금 번호를 검사하지 않고, 읽은 뒤 관리자가
    // 숨기거나 신고가 쌓여 검토 대기로 바뀌어도 수정이 그대로 커밋된다. 이를 막으려고 서비스는 상세를 고치는 요청이면 커밋할 때 박지 행의 번호를
    // 올리도록 읽는다. 번호만 다시 읽어 비교하는 방식(OPTIMISTIC)은 쓰지 않는다. REPEATABLE READ에서는 일반 SELECT가 트랜잭션 처음의
    // 스냅샷을 읽어서, 그 사이 커밋된 번호를 보지 못하기 때문이다. 이름이나 좌표만 고치는 요청은 박지 행을 쓸 때 JPA가 번호를 검사하고,
    // 아무 필드도 없는 요청은 아무것도 쓰지 않으므로 둘 다 번호를 따로 올리지 않는다.
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

    // 박지에 상세 행이 없는 것은 데이터가 어긋난 경우라서, 박지가 없는 경우와 같이 NOT_FOUND로 거부한다.
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

    // 위도와 경도는 한 쌍으로 움직인다. 하나만 바꾸는 요청은 어느 좌표로 옮기려는 것인지 알 수 없어서 거부한다.
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

    // 물과 화장실 유무는 제보할 때 꼭 적는 값이라 null로 지울 수 없다.
    private static boolean facilityOrCurrent(PatchField<Boolean> field, boolean current, String topic) {
        if (!field.present()) {
            return current;
        }
        if (field.value() == null) {
            throw invalidInput(topic + " 지울 수 없어요.");
        }
        return field.value();
    }

    // 저장하거나 고친 박지를 MyBatis로 읽기 전에 flush하지 않는다. 중복 후보 조회가 읽는 행은 이 박지를 뺀 다른 박지뿐이고,
    // 이 트랜잭션은 다른 박지를 바꾸지 않아서 아직 DB에 반영되지 않은 변경이 조회 결과에 영향을 주지 않는다.
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

    // 장소 상세와 같이, 가리키는 경계 행이 없으면 근거 없이 경고와 고지·안내만 준다.
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

    // 위도·경도 범위를 어기면 GeoPoint가 IllegalArgumentException을 던진다. 요청 값 때문이므로 INVALID_INPUT으로 바꾼다.
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
