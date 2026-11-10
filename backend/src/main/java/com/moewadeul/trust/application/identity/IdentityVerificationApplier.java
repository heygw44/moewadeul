package com.moewadeul.trust.application.identity;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.member.application.MemberWithdrawalService;
import com.moewadeul.trust.application.profile.TrustSummary;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.identity.CiHash;
import com.moewadeul.trust.domain.identity.IdentityProviderType;
import com.moewadeul.trust.domain.identity.IdentityVerification;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.identity.VerifiedIdentity;
import com.moewadeul.trust.infra.identity.IdentityVerificationInsertMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IdentityVerificationApplier {

    private static final int CI_BUCKET_HEX_DIGITS = 4;

    private final MemberWithdrawalService memberWithdrawalService;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final IdentityVerificationInsertMapper identityVerificationInsertMapper;
    private final CiHasher ciHasher;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    // 회원 행과 CI 칸 행을 이 순서로 배타 잠금해서 같은 회원, 같은 CI의 요청을 INSERT 전에 한 줄로 세운다.
    // 유니크 검사의 간격 잠금끼리 서로를 기다리는 교착 상태를 피하려는 것이다. 존재 확인은 두 잠금 뒤에 읽어야 앞선 요청의 커밋이 스냅숏에 보인다.
    @Transactional
    IdentityVerificationResult apply(long memberId, VerifiedIdentity identity, IdentityProviderType providerType) {
        memberWithdrawalService.lockUnlessWithdrawn(memberId);
        CiHash ciHash = ciHasher.hash(identity.ci());
        lockCiBucket(ciHash);
        if (identityVerificationRepository.existsByMemberId(memberId)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        if (identityVerificationRepository.existsByCiHash(ciHash)) {
            throw new BusinessException(TrustErrorCode.IDENTITY_CI_DUPLICATED);
        }
        Instant now = clock.instant();
        identityVerificationInsertMapper.insert(
                IdentityVerification.verify(memberId, identity, ciHash, providerType, now));
        IdentityVerification saved =
                identityVerificationRepository.findByMemberId(memberId).orElseThrow();
        TrustSummary summary = trustSummaryService.summarizeOf(saved, memberId, now);
        return new IdentityVerificationResult(
                summary.identityVerified(), saved.isAdult(Year.from(now.atZone(KoreaTime.ZONE))), summary.trustLevel());
    }

    private void lockCiBucket(CiHash ciHash) {
        int bucket = Integer.parseInt(ciHash.value().substring(0, CI_BUCKET_HEX_DIGITS), 16);
        if (identityVerificationInsertMapper.lockCiBucket(bucket) == null) {
            throw new IllegalStateException("identity_ci_lock에 칸 " + bucket + "이 없다");
        }
    }
}
