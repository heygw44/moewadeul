package com.moewadeul.common.moderation;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.ErrorCode;
import com.moewadeul.common.error.UniqueViolations;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * InnoDB의 기본 격리 수준(REPEATABLE READ)에서는 트랜잭션이 첫 일반 SELECT를 실행한 시점의 스냅샷을 읽는다. 그래서 4번째와 5번째 신고가 동시에
 * 들어오면 두 트랜잭션이 서로의 미커밋 행을 보지 못해 둘 다 4건으로 세고, 검토 대기로 바꾸는 일을 놓친다. 이를 막으려고 accept를 부르는
 * 트랜잭션은 첫 쿼리로 대상 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽어야 한다. 같은 대상의 신고가 줄을 서고, 뒤 트랜잭션은 앞
 * 트랜잭션이 커밋한 뒤에 스냅샷을 만들어 커밋된 신고를 모두 센다.
 */
public record ReportIntake(String uniqueConstraint, ErrorCode alreadyReported) {

    public long accept(BooleanSupplier alreadyExists, Runnable saveAndFlush, LongSupplier countUnreviewed) {
        if (alreadyExists.getAsBoolean()) {
            throw new BusinessException(alreadyReported);
        }
        try {
            saveAndFlush.run();
        } catch (DataIntegrityViolationException e) {
            throw UniqueViolations.translate(e, uniqueConstraint, alreadyReported);
        }
        return countUnreviewed.getAsLong();
    }
}
