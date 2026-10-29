package com.moewadeul.trust.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.identity.Gender;
import com.moewadeul.trust.domain.identity.IdentityProviderType;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.identity.VerifiedIdentity;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityVerificationApplierTest {

    private static final long MEMBER_ID = 7L;

    private final IdentityVerificationRepository repository = mock(IdentityVerificationRepository.class);
    private final IdentityVerificationApplier applier = new IdentityVerificationApplier(
            repository, mock(CiHasher.class), mock(TrustSummaryService.class), Clock.systemUTC());

    @Test
    @DisplayName("이미 본인확인한 회원이 같은 CI로 다시 요청하면 IDENTITY_ALREADY_VERIFIED이고 저장하지 않는다")
    void alreadyVerifiedMemberWithDuplicatedCi() {
        when(repository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(repository.existsByCiHash(any())).thenReturn(true);
        VerifiedIdentity identity = new VerifiedIdentity("ci-raw", 1995, Gender.FEMALE);

        assertThatThrownBy(() -> applier.apply(MEMBER_ID, identity, IdentityProviderType.FAKE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.IDENTITY_ALREADY_VERIFIED));
        verify(repository, never()).saveAndFlush(any());
    }
}
