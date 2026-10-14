package com.moewadeul.aisearch.application;

import com.moewadeul.aisearch.infra.AiSearchUsageMapper;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 보관 기간(90일)이 지난 AI 검색 사용량 행을 지운다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSearchUsageCleanupService {

    // 사용량 기록은 하루 한도와 비용을 가리는 데만 쓰므로 오래 둘 이유가 없다. 이 일수보다 더 오래된 날짜의 행을 지운다.
    static final int RETENTION_DAYS = 90;

    // 한 번에 지우는 행 수의 상한이다. 쌓인 행이 많을 때 한 번의 삭제가 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final AiSearchUsageMapper usageMapper;
    private final Clock clock;

    /**
     * 호출하면 오늘(한국 날짜)에서 보관 일수를 뺀 날짜보다 앞선 행을 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지 되풀이하고,
     * 지운 행 수를 돌려준다. 묶음마다 따로 커밋한다. 전체를 한 트랜잭션에 넣으면 나눠 지우는 뜻이 없어지기 때문이다.
     */
    public int deleteExpiredUsage() {
        LocalDate cutoff = AiSearchDay.today(clock).minusDays(RETENTION_DAYS);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = usageMapper.deleteBefore(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("ai search usage deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
