package com.moewadeul.member.application.password;

import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Password;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경에서 트랜잭션 하나로 묶는 부분이다. 세션 삭제는 {@link PasswordChangeService}가 이 트랜잭션이 커밋된 뒤에 한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordChangeApplier {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 호출하면 회원을 다시 읽어 비밀번호 해시와 비밀번호를 바꾼 시각을 갱신한다.
     * 바꾼 시각이 갱신되면 로그인 잠금 계산은 그 전의 실패를 세지 않는다.
     */
    @Transactional
    public void apply(long memberId, Password newPassword) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
        member.changePassword(passwordEncoder.encode(newPassword.value()), Instant.now(clock));
    }

    // 서비스가 바로 앞에서 같은 회원을 읽었으므로 일어나기 어렵다. 회원 ID만 메시지에 싣는다.
    private static IllegalStateException memberNotFound(long memberId) {
        return new IllegalStateException("비밀번호를 바꿀 회원이 없습니다. memberId=" + memberId);
    }
}
