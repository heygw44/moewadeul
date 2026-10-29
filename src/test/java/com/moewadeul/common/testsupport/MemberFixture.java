package com.moewadeul.common.testsupport;

import static com.moewadeul.member.domain.MemberBuilder.aMember;

import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberBuilder;
import com.moewadeul.member.infra.MemberJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

public class MemberFixture {

    private final MemberJpaRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberFixture(MemberJpaRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Member save() {
        return save(aMember());
    }

    public Member save(MemberBuilder member) {
        return memberRepository.saveAndFlush(member.build());
    }

    public Member saveWithPassword(String rawPassword) {
        return saveWithPassword(aMember(), rawPassword);
    }

    public Member saveWithPassword(MemberBuilder member, String rawPassword) {
        return save(member.passwordHash(passwordEncoder.encode(rawPassword)));
    }
}
