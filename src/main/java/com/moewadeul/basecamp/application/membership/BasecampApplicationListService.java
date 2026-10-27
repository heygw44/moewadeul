package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.infra.membership.BasecampApplicationMapper;
import com.moewadeul.basecamp.infra.membership.BasecampApplicationRow;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
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
    public BasecampApplicationPage list(long basecampId, long leaderId, BasecampApplicationListQuery query) {
        Basecamp basecamp = basecampRepository
                .findById(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        long offset = (long) query.page() * query.size();
        List<BasecampApplicationRow> rows =
                basecampApplicationMapper.selectByStatus(basecampId, query.status(), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<BasecampApplicationItem> content =
                rows.stream().limit(query.size()).map(this::toItem).toList();
        return new BasecampApplicationPage(content, query.page(), query.size(), hasNext);
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
