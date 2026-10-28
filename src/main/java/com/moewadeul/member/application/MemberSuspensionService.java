package com.moewadeul.member.application;

import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.infra.MemberSuspensionMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberSuspensionService {

    private final MemberRepository memberRepository;
    private final MemberSuspensionMapper memberSuspensionMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedMember lockForSanction(long memberId) {
        return new LockedMember(memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("제재할 회원이 없습니다. memberId=" + memberId)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedMember lockForSanctionAlongWith(long memberId, long otherMemberId) {
        LockedMember first = lockForSanction(Math.min(memberId, otherMemberId));
        LockedMember second = lockForSanction(Math.max(memberId, otherMemberId));
        return first.id() == memberId ? first : second;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void suspendTemporarily(LockedMember member, Instant until) {
        member.member().suspendTemporarily(until, Instant.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void suspendPermanently(LockedMember member) {
        member.member().suspendPermanently(Instant.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resyncSuspension(LockedMember member, boolean permanent, Instant latestEnd) {
        member.member().resyncSuspension(permanent, latestEnd, Instant.now(clock));
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
