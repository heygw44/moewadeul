package com.moewadeul.member.application.login;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.application.LoginAttemptService;
import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.member.domain.Email;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.MemberSuspendedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인 유스케이스. 이 클래스에는 일부러 {@code @Transactional}을 걸지 않는다.
 * 실패하면 예외를 던지는데, 트랜잭션 안에서 던지면 실패 기록이 롤백되기 때문이다(자세한 이유는 {@link LoginAttemptService}).
 * 기록을 읽고 쓰는 일은 {@link LoginAttemptService}가 메서드마다 따로 커밋한다.
 */
@Service
public class MemberLoginService {

    private static final int ATTEMPTED_EMAIL_MAX_LENGTH = 254;

    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final MemberSuspensionService memberSuspensionService;
    private final MemberSessions memberSessions;
    private final Clock clock;

    // 가입하지 않은 이메일로 시도해도 비밀번호 비교를 한 번 한다. 그렇지 않으면 BCrypt를 건너뛴 만큼 응답이 빨라져서,
    // 응답 시간만 재도 가입 여부를 알 수 있다. 해시는 주입받은 인코더로 직접 만들어 해시 강도를 실제 회원 해시와 맞춘다.
    private final String dummyPasswordHash;

    public MemberLoginService(
            MemberRepository memberRepository,
            LoginAttemptService loginAttemptService,
            PasswordEncoder passwordEncoder,
            MemberSuspensionService memberSuspensionService,
            MemberSessions memberSessions,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.loginAttemptService = loginAttemptService;
        this.passwordEncoder = passwordEncoder;
        this.memberSuspensionService = memberSuspensionService;
        this.memberSessions = memberSessions;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResult login(LoginCommand command) {
        String attemptedEmail = normalizeEmail(command.email());
        rejectIfLocked(attemptedEmail);
        Member authenticated = authenticate(attemptedEmail, command);
        Member member = releaseIfSuspensionEnded(authenticated);
        rejectIfSuspended(member);
        loginAttemptService.recordSuccess(member.getId(), attemptedEmail, command.ip());
        return new LoginResult(
                member.getId(),
                member.getNickname(),
                member.getStatus(),
                member.getRole(),
                member.getPasswordChangedAt());
    }

    /**
     * 호출하면 로그인한 뒤에 그 회원이 여전히 로그인할 수 있는지 DB에서 다시 읽어 확인한다. 세션을 만든 직후에 부른다.
     * 비밀번호 재설정, 이용 정지, 탈퇴는 커밋한 뒤 그 회원의 세션을 한 번만 지운다. 로그인 요청이 비밀번호를 비교하는 사이에
     * 이 일이 끝났다면 그때는 지울 세션이 없었으므로, 뒤늦게 만든 세션이 그대로 남는다. 그래서 세션을 만든 뒤에 회원을 다시 읽어
     * 바뀐 것이 보이면 호출하는 쪽이 세션을 버리게 예외를 던진다.
     *
     * @param loggedIn {@link #login}이 돌려준 결과
     * @throws MemberException 탈퇴했거나 로그인한 뒤에 비밀번호가 바뀌었으면 LOGIN_FAILED
     * @throws MemberSuspendedException 로그인한 뒤에 이용이 정지됐으면 MEMBER_SUSPENDED
     */
    public void ensureStillLoginable(LoginResult loggedIn) {
        Member current = memberRepository
                .findById(loggedIn.memberId())
                .filter(found -> found.getStatus() != MemberStatus.WITHDRAWN)
                .filter(found -> Objects.equals(found.getPasswordChangedAt(), loggedIn.passwordChangedAt()))
                .orElseThrow(() -> new MemberException(MemberErrorCode.LOGIN_FAILED));
        rejectIfSuspended(current);
    }

    /**
     * 호출하면 {@link #ensureStillLoginable}로 다시 확인하고, 로그인할 수 없게 바뀌었으면 그 세션을 저장소에서 지운다. 세션을 저장소에 저장한
     * 뒤에 부른다. 컨트롤러 안에서 확인한 뒤 응답을 커밋해 세션을 저장하기까지 사이에 재설정이나 정지가 끝나도, 이 호출이 저장된 세션을 본다.
     * 재설정·정지가 세션을 지우는 시점이 이 확인보다 앞이면 이 호출이 바뀐 것을 읽고, 뒤이면 그쪽이 저장된 세션을 지우므로 세션이 남지 않는다.
     */
    public void revokeSessionIfMemberChanged(LoginResult loggedIn, String sessionId) {
        try {
            ensureStillLoginable(loggedIn);
        } catch (MemberException e) {
            memberSessions.invalidate(sessionId);
        }
    }

    // 이메일 형식이 올바르지 않아도 500이 아니라 일반 로그인 실패로 다룬다. 그래서 형식 검사에서 거부된 값은
    // 소문자로 바꾸고 칼럼 길이에 맞춰 잘라서 실패 기록의 이메일로 쓴다.
    private static String normalizeEmail(String rawEmail) {
        try {
            return Email.of(rawEmail).value();
        } catch (IllegalArgumentException e) {
            String lowerCased = rawEmail.toLowerCase(Locale.ROOT);
            return lowerCased.substring(0, Math.min(lowerCased.length(), ATTEMPTED_EMAIL_MAX_LENGTH));
        }
    }

    // 잠긴 동안의 시도는 비밀번호를 확인하지 않고 기록하지도 않는다. 기록하면 실패 구간이 계속 뒤로 밀려서 잠금이 끝나지 않는다.
    private void rejectIfLocked(String attemptedEmail) {
        Optional<Duration> retryAfter = loginAttemptService.findRetryAfter(attemptedEmail);
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
    }

    private Member authenticate(String attemptedEmail, LoginCommand command) {
        Member member = memberRepository
                .findByEmail(attemptedEmail)
                .filter(found -> found.getStatus() != MemberStatus.WITHDRAWN)
                .orElse(null);
        String passwordHash = member != null ? member.getPasswordHash() : dummyPasswordHash;
        boolean authenticated = passwordEncoder.matches(command.password(), passwordHash) && member != null;
        // 비교는 트랜잭션 밖에서 했으므로 그사이 같은 이메일의 다른 요청이 실패를 쌓아 잠겼을 수 있다.
        // 잠겨 있으면 비밀번호가 맞았어도 로그인시키지 않고 LOGIN_LOCKED로 거부한다.
        Long memberId = member != null ? member.getId() : null;
        loginAttemptService.settle(memberId, attemptedEmail, command.ip(), authenticated);
        if (!authenticated) {
            throw new MemberException(MemberErrorCode.LOGIN_FAILED);
        }
        return member;
    }

    // 정지 기간이 끝났으면 정지 만료 작업이 도는 주기를 기다리지 않고 로그인할 때 바로 풀어 준다. 그래야 정지가 기간이 끝나는 시각에 정확히 풀린다.
    // 풀면 다른 요청이 바꾼 값까지 반영해서 회원을 다시 읽는다.
    private Member releaseIfSuspensionEnded(Member member) {
        if (member.getStatus() != MemberStatus.SUSPENDED || member.getSuspendedUntil() == null) {
            return member;
        }
        Instant now = clock.instant();
        if (member.getSuspendedUntil().isAfter(now)) {
            return member;
        }
        memberSuspensionService.releaseIfExpired(member.getId());
        return memberRepository.findById(member.getId()).orElse(member);
    }

    // 정지 여부는 비밀번호가 맞은 뒤에 알린다. 비밀번호를 모르는 사람에게 계정이 정지됐다는 사실을 알리지 않기 위해서다.
    // 정지로 거부한 시도는 실패로 세지 않는다. 비밀번호는 맞았기 때문이다.
    private void rejectIfSuspended(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new MemberSuspendedException(member.getSuspendedUntil());
        }
    }
}
