package com.moewadeul.spot.application.bakji;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.spot.domain.SpotErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BakjiEditService {

    private final BakjiCommandService bakjiCommandService;

    public BakjiSubmission update(long memberId, long spotId, BakjiUpdateCommand command) {
        try {
            return bakjiCommandService.update(memberId, spotId, command);
        } catch (OptimisticLockingFailureException e) {
            log.warn("bakji update conflicted, retrying once spotId={}", spotId);
            return retryOnce(() -> bakjiCommandService.update(memberId, spotId, command));
        }
    }

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
