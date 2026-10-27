package com.moewadeul.member.application;

import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.infra.MemberSuspensionMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberSuspensionService {

    private final MemberRepository memberRepository;
    private final MemberSuspensionMapper memberSuspensionMapper;
    private final Clock clock;

    @Transactional
    public void suspendTemporarily(long memberId, Instant until) {
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("정지할 회원이 없습니다. memberId=" + memberId));
        member.suspendTemporarily(until, Instant.now(clock));
    }

    @Transactional
    public void lockForSanction(long memberId) {
        memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("제재할 회원이 없습니다. memberId=" + memberId));
    }

    @Transactional
    public void suspendPermanently(long memberId) {
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("정지할 회원이 없습니다. memberId=" + memberId));
        member.suspendPermanently(Instant.now(clock));
    }

    @Transactional
    public void resyncSuspension(long memberId, boolean permanent, Instant latestEnd) {
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("정지를 다시 계산할 회원이 없습니다. memberId=" + memberId));
        member.resyncSuspension(permanent, latestEnd, Instant.now(clock));
    }

    @Transactional
    public boolean releaseIfExpired(long memberId) {
        return memberRepository
                .findByIdForUpdate(memberId)
                .map(member -> member.releaseSuspensionIfExpired(Instant.now(clock)))
                .orElse(false);
    }

    @Transactional
    public int releaseExpired(Instant now) {
        return memberSuspensionMapper.releaseExpired(now);
    }
}
