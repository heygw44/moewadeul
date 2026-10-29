package com.moewadeul.member.application.profile;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberProfile find(long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(BusinessException::notFound);
        return MemberProfile.from(member);
    }

    @Transactional(readOnly = true)
    public MemberProfile findIncludingWithdrawn(long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(BusinessException::notFound);
        return MemberProfile.from(member);
    }
}
