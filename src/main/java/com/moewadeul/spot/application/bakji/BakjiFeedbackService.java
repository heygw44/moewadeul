package com.moewadeul.spot.application.bakji;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.EnumParser;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotErrorCode;
import com.moewadeul.spot.domain.SpotRepository;
import com.moewadeul.spot.domain.bakji.BakjiConfirmation;
import com.moewadeul.spot.domain.bakji.BakjiConfirmationRepository;
import com.moewadeul.spot.domain.bakji.BakjiReport;
import com.moewadeul.spot.domain.bakji.BakjiReportReason;
import com.moewadeul.spot.domain.bakji.BakjiReportRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * InnoDB의 기본 격리 수준(REPEATABLE READ)에서는 트랜잭션이 첫 일반 SELECT를 실행한 시점의 스냅샷을 읽는다. 그래서 4번째와 5번째 신고가
 * 동시에 들어오면 두 트랜잭션이 서로의 미커밋 행을 보지 못해 둘 다 4건으로 세고, 검토 대기로 바꾸는 일을 놓친다. 이를 막으려고 신고 트랜잭션은
 * 첫 쿼리로 박지 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 같은 박지의 신고가 줄을 서고, 뒤 트랜잭션은 앞 트랜잭션이 커밋한 뒤에
 * 스냅샷을 만들어 커밋된 신고를 모두 센다. 그래서 이 잠금 쿼리를 어떤 조회보다 앞에 둬야 한다.
 */
@Service
@RequiredArgsConstructor
public class BakjiFeedbackService {

    private static final int PENDING_REVIEW_REPORT_THRESHOLD = 5;
    private static final String REASON_MESSAGE = "신고 사유는 " + EnumParser.names(BakjiReportReason.class) + " 중 하나여야 해요.";

    private final SpotRepository spotRepository;
    private final BakjiConfirmationRepository bakjiConfirmationRepository;
    private final BakjiReportRepository bakjiReportRepository;
    private final Clock clock;

    @Transactional
    public long confirm(long memberId, long spotId) {
        Spot spot = spotRepository
                .findById(spotId)
                .filter(Spot::isActiveBakji)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (bakjiConfirmationRepository.existsBySpotIdAndMemberId(spot.getId(), memberId)) {
            throw alreadyConfirmed();
        }
        saveConfirmation(BakjiConfirmation.of(spot.getId(), memberId, clock.instant()));
        return bakjiConfirmationRepository.countBySpotId(spot.getId());
    }

    @Transactional
    public void reportProblem(long memberId, long spotId, BakjiProblemReportCommand command) {
        Spot spot = spotRepository
                .findByIdForUpdate(spotId)
                .filter(Spot::isActiveBakji)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        BakjiReportReason reason = EnumParser.parse(BakjiReportReason.class, command.reason(), REASON_MESSAGE);
        if (bakjiReportRepository.existsBySpotIdAndReporterId(spot.getId(), memberId)) {
            throw alreadyReported();
        }
        Instant now = clock.instant();
        saveReport(BakjiReport.of(spot.getId(), memberId, reason, command.content(), now));
        if (bakjiReportRepository.countBySpotIdAndReviewedAtIsNull(spot.getId()) >= PENDING_REVIEW_REPORT_THRESHOLD) {
            spot.markPendingReview(now);
        }
    }

    private void saveConfirmation(BakjiConfirmation confirmation) {
        try {
            bakjiConfirmationRepository.saveAndFlush(confirmation);
        } catch (DataIntegrityViolationException e) {
            throw alreadyConfirmed();
        }
    }

    private void saveReport(BakjiReport report) {
        try {
            bakjiReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw alreadyReported();
        }
    }

    private static BusinessException alreadyConfirmed() {
        return new BusinessException(SpotErrorCode.BAKJI_ALREADY_CONFIRMED);
    }

    private static BusinessException alreadyReported() {
        return new BusinessException(SpotErrorCode.BAKJI_ALREADY_REPORTED);
    }
}
