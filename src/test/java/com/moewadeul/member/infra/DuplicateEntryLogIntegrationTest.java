package com.moewadeul.member.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.MemberBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Test
    @DisplayName("JPA로 저장하다 유니크 제약을 어기면 예외 타입이 DuplicateKeyException이 아니어도 고유 제약 위반으로 판별한다")
    void jpaUniqueViolationIsDuplicateEntry() {
        // given
        String email = TestSequence.email();
        memberRepository.saveAndFlush(new MemberBuilder()
                .email(email)
                .nickname(TestSequence.nickname())
                .build());

        // when, then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(new MemberBuilder()
                        .email(email)
                        .nickname(TestSequence.nickname())
                        .build()))
                .isInstanceOfSatisfying(
                        DataIntegrityViolationException.class,
                        e -> assertThat(UniqueViolations.isDuplicateEntry(e)).isTrue());
    }
}
