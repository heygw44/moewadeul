package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.infra.membership.BasecampApplicationMapper;
import com.moewadeul.basecamp.infra.membership.BasecampApplicationRow;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.trust.application.profile.MemberProfileQueryService;
import com.moewadeul.trust.application.profile.MemberTrustProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampApplicationListService {

    private final BasecampRepository basecampRepository;
    private final BasecampApplicationMapper basecampApplicationMapper;
    private final MemberProfileQueryService memberProfileQueryService;

    @Transactional(readOnly = true)
    public PageSlice<BasecampApplicationItem> list(long basecampId, long leaderId, BasecampApplicationListQuery query) {
        Basecamp basecamp = basecampRepository
                .findById(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        List<BasecampApplicationRow> rows = basecampApplicationMapper.selectByStatus(
                basecampId,
                query.status(),
                PageSlice.offsetOf(query.page(), query.size()),
                PageSlice.fetchSize(query.size()));
        return PageSlice.of(rows, query.page(), query.size()).map(this::toItem);
    }

    private BasecampApplicationItem toItem(BasecampApplicationRow row) {
        MemberTrustProfile profile = memberProfileQueryService.findIncludingWithdrawn(row.applicantId());
        return new BasecampApplicationItem(
                row.applicationId(),
                row.status().name(),
                row.message(),
                row.appliedAt(),
                new BasecampApplicationItem.Applicant(
                        profile.memberId(),
                        profile.nickname(),
                        profile.ageGroup(),
                        profile.ageGroupVerified(),
                        profile.gender(),
                        profile.genderVerified(),
                        profile.trustLevel(),
                        profile.completedCompanions()));
    }
}
