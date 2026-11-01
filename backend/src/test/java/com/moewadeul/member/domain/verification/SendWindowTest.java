package com.moewadeul.member.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendWindowTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Test
    @DisplayName("[F-01][EV-03] 두 기간을 더하면 횟수는 합치고 가장 오래된 시각은 이른 쪽이다")
    void plusSumsCountsAndKeepsEarlierOldest() {
        // given
        SendWindow issued = new SendWindow(3, NOW.minus(Duration.ofMinutes(10)));
        SendWindow queued = new SendWindow(2, NOW.minus(Duration.ofMinutes(30)));

        // when & then
        assertThat(issued.plus(queued)).isEqualTo(new SendWindow(5, NOW.minus(Duration.ofMinutes(30))));
        assertThat(queued.plus(issued)).isEqualTo(new SendWindow(5, NOW.minus(Duration.ofMinutes(30))));
    }

    @Test
    @DisplayName("[F-01][EV-03] 한쪽에 발송이 없으면 다른 쪽의 시각을 그대로 쓴다")
    void plusKeepsOtherOldestWhenOneSideIsEmpty() {
        // given
        SendWindow empty = new SendWindow(0, null);
        SendWindow sent = new SendWindow(4, NOW.minus(Duration.ofMinutes(5)));

        // when & then
        assertThat(empty.plus(sent)).isEqualTo(sent);
        assertThat(sent.plus(empty)).isEqualTo(sent);
        assertThat(empty.plus(empty)).isEqualTo(empty);
    }
}
