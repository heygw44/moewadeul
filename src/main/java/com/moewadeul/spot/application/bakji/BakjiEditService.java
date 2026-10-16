package com.moewadeul.spot.application.bakji;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.spot.domain.SpotErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 제보자의 박지 수정과 삭제를 다른 쓰기와 겹쳐도 덮어쓰지 않게 처리한다.
 *
 * <p>박지 행은 재판정, 관리자 숨김, 신고처럼 여러 쓰기가 함께 고친다. 박지 수정은 행을 잠그지 않고 읽으므로, 읽은 뒤 다른 쓰기가 먼저 커밋하면 커밋할 때
 * 낙관적 잠금 번호가 맞지 않아 실패한다. 그러면 서비스는 박지를 다시 읽어 같은 요청을 한 번 더 적용한다. 그래서 그 사이 바뀐 판정이나 상태는 지키면서
 * 제보자의 수정도 반영된다. 다시 읽었더니 숨겨졌거나 삭제됐으면 그때의 거부(NOT_FOUND 등)를 그대로 돌려준다.
 *
 * <p>트랜잭션을 다시 시작해야 하므로 이 서비스에는 트랜잭션이 없고, 실제 처리는 {@link BakjiCommandService}의 트랜잭션이 한다. 두 번째도 충돌하면
 * BAKJI_CONCURRENTLY_MODIFIED로 거부한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BakjiEditService {

    private final BakjiCommandService bakjiCommandService;

    /** 호출하면 제보자 memberId의 박지를 command대로 고치고 결과를 돌려준다. 거부하는 경우는 {@link BakjiCommandService#update}와 같다. */
    public BakjiSubmission update(long memberId, long spotId, BakjiUpdateCommand command) {
        try {
            return bakjiCommandService.update(memberId, spotId, command);
        } catch (OptimisticLockingFailureException e) {
            log.warn("bakji update conflicted, retrying once spotId={}", spotId);
            return retryOnce(() -> bakjiCommandService.update(memberId, spotId, command));
        }
    }

    /** 호출하면 제보자 memberId의 박지를 삭제 상태로 바꾼다. 거부하는 경우는 {@link BakjiCommandService#delete}와 같다. */
    public void delete(long memberId, long spotId) {
        try {
            bakjiCommandService.delete(memberId, spotId);
        } catch (OptimisticLockingFailureException e) {
            log.warn("bakji delete conflicted, retrying once spotId={}", spotId);
            retryOnce(() -> {
                bakjiCommandService.delete(memberId, spotId);
                return null;
            });
        }
    }

    private static <T> T retryOnce(Attempt<T> attempt) {
        try {
            return attempt.run();
        } catch (OptimisticLockingFailureException e) {
            log.warn(
                    "bakji edit conflicted again, giving up reason={}",
                    e.getClass().getSimpleName());
            throw new BusinessException(SpotErrorCode.BAKJI_CONCURRENTLY_MODIFIED);
        }
    }

    @FunctionalInterface
    private interface Attempt<T> {
        T run();
    }
}
