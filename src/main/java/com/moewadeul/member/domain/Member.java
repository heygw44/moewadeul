package com.moewadeul.member.domain;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    public static final int NICKNAME_MIN_LENGTH = 2;
    public static final int NICKNAME_MAX_LENGTH = 20;
    public static final int EMAIL_MAX_LENGTH = 254;

    public static final String WITHDRAWN_NICKNAME_PREFIX = "탈퇴회원_";

    private static final Pattern WITHDRAWN_NICKNAME_PATTERN =
            Pattern.compile("^" + Pattern.quote(WITHDRAWN_NICKNAME_PREFIX) + "[0-9]+$");

    // 글자(Lo) 범주라서 범주 검사로는 걸러지지 않지만 화면에는 아무것도 그리지 않는 한글 채움 문자다.
    private static final Set<Integer> BLANK_LETTERS = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0);

    public static final String NICKNAME_RULE_MESSAGE = "닉네임은 " + NICKNAME_MIN_LENGTH + "~" + NICKNAME_MAX_LENGTH
            + "자여야 합니다. 앞뒤 공백, 연속 공백, 보이지 않는 문자는 쓸 수 없고, '" + WITHDRAWN_NICKNAME_PREFIX + "숫자' 형태도 쓸 수 없습니다.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    private String nickname;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_age_group")
    private AgeGroup selfAgeGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_gender")
    private Gender selfGender;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "suspended_until")
    private Instant suspendedUntil;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private Member(Email email, String passwordHash, String nickname, Instant now) {
        this.email = email.value();
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.status = MemberStatus.UNVERIFIED;
        this.role = MemberRole.USER;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Member register(Email email, String passwordHash, String nickname, Instant now) {
        if (email == null || passwordHash == null || now == null) {
            throw new IllegalArgumentException("회원 생성에 필요한 값이 null입니다.");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 비어 있습니다.");
        }
        requireValidNickname(nickname);
        return new Member(email, passwordHash, nickname, now);
    }

    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    public boolean canAuthenticateWithPassword() {
        return !isWithdrawn() && passwordHash != null;
    }

    public void changePassword(String passwordHash, Instant now) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 비어 있습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("비밀번호를 바꾼 시각이 null입니다.");
        }
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.updatedAt = now;
    }

    public void withdraw(Instant now) {
        requireNow(now);
        if (isWithdrawn()) {
            return;
        }
        this.email = null;
        this.passwordHash = null;
        this.selfAgeGroup = null;
        this.selfGender = null;
        this.nickname = WITHDRAWN_NICKNAME_PREFIX + id;
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.updatedAt = now;
    }

    public void suspendTemporarily(Instant until, Instant now) {
        if (until == null || now == null) {
            throw new IllegalArgumentException("정지 종료 시각이나 수정 시각이 null입니다.");
        }
        if (isWithdrawn()) {
            return;
        }
        if (status == MemberStatus.SUSPENDED && (suspendedUntil == null || suspendedUntil.isAfter(until))) {
            return;
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspendedUntil = until;
        this.updatedAt = now;
    }

    public void suspendPermanently(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("수정 시각이 null입니다.");
        }
        if (isWithdrawn()) {
            return;
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspendedUntil = null;
        this.updatedAt = now;
    }

    public boolean isSuspensionOverAt(Instant now) {
        return status == MemberStatus.SUSPENDED && suspendedUntil != null && !suspendedUntil.isAfter(now);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isAwaitingEmailVerification() {
        return status == MemberStatus.UNVERIFIED;
    }

    public boolean releaseSuspensionIfExpired(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각이 null입니다.");
        }
        if (!isSuspensionOverAt(now)) {
            return false;
        }
        endSuspension(now);
        return true;
    }

    private void endSuspension(Instant now) {
        this.status = isEmailVerified() ? MemberStatus.ACTIVE : MemberStatus.UNVERIFIED;
        this.suspendedUntil = null;
        this.updatedAt = now;
    }

    public void resyncSuspension(boolean permanent, Instant latestEnd, Instant now) {
        requireNow(now);
        if (isWithdrawn()) {
            return;
        }
        if (permanent) {
            suspendPermanently(now);
            return;
        }
        if (latestEnd != null && latestEnd.isAfter(now)) {
            this.status = MemberStatus.SUSPENDED;
            this.suspendedUntil = latestEnd;
            this.updatedAt = now;
            return;
        }
        if (status != MemberStatus.SUSPENDED) {
            return;
        }
        endSuspension(now);
    }

    public void changeNickname(String nickname, Instant now) {
        requireValidNickname(nickname);
        requireNow(now);
        this.nickname = nickname;
        this.updatedAt = now;
    }

    public void changeSelfAgeGroup(AgeGroup selfAgeGroup, Instant now) {
        requireNow(now);
        this.selfAgeGroup = selfAgeGroup;
        this.updatedAt = now;
    }

    public void changeSelfGender(Gender selfGender, Instant now) {
        requireNow(now);
        this.selfGender = selfGender;
        this.updatedAt = now;
    }

    /**
     * DB 유니크 제약은 NBSP와 한글 채움 문자를 다른 글자로 보고,
     * 폭 없는 공백은 아예 무시한다. 그래서 그대로 두면 {@code hiker}에 NBSP를 붙인 닉네임이 {@code hiker}를 사칭하며 통과한다.
     */
    public static boolean isValidNickname(String nickname) {
        if (nickname == null) {
            return false;
        }
        int length = nickname.codePointCount(0, nickname.length());
        if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH) {
            return false;
        }
        if (WITHDRAWN_NICKNAME_PATTERN.matcher(nickname).matches()) {
            return false;
        }
        boolean misplacedSpace = nickname.startsWith(" ") || nickname.endsWith(" ") || nickname.contains("  ");
        return !misplacedSpace && nickname.codePoints().noneMatch(Member::isHiddenOrSpaceLike);
    }

    private static boolean isHiddenOrSpaceLike(int codePoint) {
        if (codePoint == ' ') {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SPACE_SEPARATOR,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR -> true;
            default -> BLANK_LETTERS.contains(codePoint);
        };
    }

    static void requireValidNickname(String nickname) {
        if (!isValidNickname(nickname)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, NICKNAME_RULE_MESSAGE);
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("수정 시각이 null입니다.");
        }
    }
}
