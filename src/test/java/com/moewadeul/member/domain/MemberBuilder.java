package com.moewadeul.member.domain;

import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

public class MemberBuilder {

    private static final String DEFAULT_PASSWORD_HASH = "$2a$10$" + "a".repeat(53);

    private String email;
    private String passwordHash = DEFAULT_PASSWORD_HASH;
    private String nickname;
    private Instant now = MutableClock.DEFAULT_INSTANT;
    private MemberStatus status;
    private Instant suspendedUntil;
    private Instant emailVerifiedAt;

    public static MemberBuilder aMember() {
        return new MemberBuilder();
    }

    public MemberBuilder email(String email) {
        this.email = email;
        return this;
    }

    public MemberBuilder passwordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        return this;
    }

    public MemberBuilder nickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public MemberBuilder now(Instant now) {
        this.now = now;
        return this;
    }

    public MemberBuilder status(MemberStatus status) {
        this.status = status;
        return this;
    }

    public MemberBuilder suspendedUntil(Instant suspendedUntil) {
        this.suspendedUntil = suspendedUntil;
        return this;
    }

    public MemberBuilder emailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
        return this;
    }

    public Member build() {
        String resolvedEmail = email != null ? email : TestSequence.email();
        String resolvedNickname = nickname != null ? nickname : TestSequence.nickname();
        Member member = Member.register(Email.of(resolvedEmail), passwordHash, resolvedNickname, now);
        if (status != null) {
            ReflectionTestUtils.setField(member, "status", status);
        }
        ReflectionTestUtils.setField(member, "suspendedUntil", suspendedUntil);
        ReflectionTestUtils.setField(member, "emailVerifiedAt", emailVerifiedAt);
        return member;
    }
}
