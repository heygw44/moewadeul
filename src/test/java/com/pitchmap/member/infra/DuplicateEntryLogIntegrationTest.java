package com.pitchmap.member.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.MemberBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 유니크 제약 위반이 로그에 원값을 남기지 않는지 확인한다.
 *
 * <p>MySQL은 유니크 제약을 어긴 값을 "Duplicate entry '값' for key '제약'" 메시지에 그대로 담는다. Hibernate는 이 메시지를 받으면
 * 기본으로 WARN 로그에 남기므로, 이메일이나 CI 해시처럼 로그에 남기면 안 되는 값이 운영 로그에 찍힌다. 서비스는 이 예외를 중복 오류 코드로
 * 바꿔 응답하므로, 서버는 Hibernate의 SQL 오류 로그를 끄고 예상하지 못한 예외만 공통 예외 처리기에서 로그로 남긴다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class DuplicateEntryLogIntegrationTest {

    @Autowired
    private MemberJpaRepository memberRepository;

    @Test
    @DisplayName("[NFR-07] 같은 이메일로 두 번 저장해 유니크 제약을 어겨도 로그에 이메일 원값이 남지 않는다")
    void duplicateEmailIsNotLogged(CapturedOutput output) {
        // given
        String email = TestSequence.email();
        memberRepository.saveAndFlush(new MemberBuilder()
                .email(email)
                .nickname(TestSequence.nickname())
                .build());

        // when
        assertThatThrownBy(() -> memberRepository.saveAndFlush(new MemberBuilder()
                        .email(email)
                        .nickname(TestSequence.nickname())
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // then
        assertThat(output.getAll()).doesNotContain(email);
    }
}
