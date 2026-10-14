package com.moewadeul.memory.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.memory.domain.Memory;
import com.moewadeul.memory.domain.MemoryErrorCode;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.domain.report.MemoryReport;
import com.moewadeul.memory.domain.report.MemoryReportReason;
import com.moewadeul.memory.domain.report.MemoryReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 공개 추억 기록을 신고한다. 작성자가 자기 기록을 신고하는 것도 막지 않는다.
 *
 * <p>신고할 수 있는 기록은 다른 사람에게 보이는 기록, 곧 공개이면서 ACTIVE인 기록뿐이다. 없거나 비공개이거나 검토 대기·숨김이면 서비스는
 * NOT_FOUND로 거부한다. 그런 기록이 있는지 드러내지 않으려고 이 경우들을 구분하지 않는다.
 *
 * <p>한 회원은 같은 기록을 한 번만 신고한다. 서비스는 먼저 이미 했는지 조회해서 거부한다. 하지만 같은 회원의 요청 두 개가 동시에 조회를 통과할 수
 * 있으므로, 저장할 때 DB가 던지는 유니크 제약 위반도 같은 중복 오류로 바꾼다.
 *
 * <p>검토 전 신고가 {@value #PENDING_REVIEW_REPORT_THRESHOLD}건에 이르면 서비스는 같은 트랜잭션에서 기록을 검토 대기로 바꿔 다른 사람에게서
 * 감춘다. 신고 수를 따로 저장하지 않고 신고 행을 센다. 그런데 InnoDB의 기본 격리 수준(REPEATABLE READ)에서는 트랜잭션이 첫 일반 SELECT를 실행한
 * 시점의 스냅샷을 읽는다. 그래서 4번째와 5번째 신고가 동시에 들어오면 두 트랜잭션이 서로의 미커밋 행을 보지 못해 둘 다 4건으로 세고, 검토 대기로
 * 바꾸는 일을 놓친다. 이를 막으려고 신고 트랜잭션은 첫 쿼리로 기록 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 같은 기록의 신고가 줄을 서고,
 * 뒤 트랜잭션은 앞 트랜잭션이 커밋한 뒤에 스냅샷을 만들어 커밋된 신고를 모두 센다. 그래서 이 잠금 쿼리를 어떤 조회보다 앞에 둬야 한다.
 */
@Service
@RequiredArgsConstructor
public class MemoryReportService {

    private static final int PENDING_REVIEW_REPORT_THRESHOLD = 5;

    private final MemoryRepository memoryRepository;
    private final MemoryReportRepository memoryReportRepository;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 신고를 저장한다. 저장한 뒤 검토 전 신고가 {@value #PENDING_REVIEW_REPORT_THRESHOLD}건 이상이면 기록을 검토 대기로
     * 바꾼다. 기록이 없거나 다른 사람에게 보이지 않으면 NOT_FOUND로, 이미 신고했으면 MEMORY_ALREADY_REPORTED로, 모르는 사유이거나 내용이 너무 길면
     * INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public void report(long memberId, long memoryId, MemoryReportCommand command) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        Memory memory = memoryRepository
                .findByIdForUpdate(memoryId)
                .filter(Memory::isVisibleToOthers)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        MemoryReportReason reason = parseReason(command.reason());
        String content = command.content();
        if (content != null && content.length() > MemoryReport.CONTENT_MAX_LENGTH) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT, "신고 내용은 " + MemoryReport.CONTENT_MAX_LENGTH + "자 이하여야 해요.");
        }
        if (memoryReportRepository.existsByMemoryIdAndReporterId(memoryId, memberId)) {
            throw alreadyReported();
        }
        Instant now = clock.instant();
        try {
            memoryReportRepository.saveAndFlush(MemoryReport.of(memoryId, memberId, reason, content, now));
        } catch (DataIntegrityViolationException e) {
            throw alreadyReported();
        }
        if (memoryReportRepository.countByMemoryIdAndReviewedAtIsNull(memoryId) >= PENDING_REVIEW_REPORT_THRESHOLD) {
            memory.markPendingReview(now);
        }
    }

    private static MemoryReportReason parseReason(String value) {
        return Arrays.stream(MemoryReportReason.values())
                .filter(reason -> reason.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.INVALID_INPUT, "신고 사유는 " + allowedReasons() + " 중 하나여야 해요."));
    }

    private static String allowedReasons() {
        return String.join(
                ", ", Arrays.stream(MemoryReportReason.values()).map(Enum::name).toList());
    }

    private static BusinessException alreadyReported() {
        return new BusinessException(MemoryErrorCode.MEMORY_ALREADY_REPORTED);
    }
}
