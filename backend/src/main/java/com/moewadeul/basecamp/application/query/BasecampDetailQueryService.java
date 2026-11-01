package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.JoinEligibility;
import com.moewadeul.basecamp.application.JoinEligibilityChecker;
import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampMember;
import com.moewadeul.basecamp.domain.BasecampMemberRole;
import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.basecamp.domain.JoinCondition;
import com.moewadeul.basecamp.infra.query.BasecampSearchMapper;
import com.moewadeul.basecamp.infra.query.SpotSummaryRow;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.trust.application.profile.MemberProfileQueryService;
import com.moewadeul.trust.application.profile.MemberTrustProfile;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampDetailQueryService {

    private final BasecampRepository basecampRepository;
    private final BasecampSearchMapper basecampSearchMapper;
    private final MemberProfileQueryService memberProfileQueryService;
    private final JoinEligibilityChecker joinEligibilityChecker;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BasecampDetail find(Long viewerId, long basecampId) {
        Basecamp basecamp = basecampRepository.findById(basecampId).orElseThrow(BusinessException::notFound);
        SpotSummaryRow spot = basecampSearchMapper.selectSpotSummary(basecamp.getSpotId());
        List<BasecampDetail.DetailMember> members = readMembers(basecamp);
        BasecampDetail.DetailMember leader = findLeader(basecamp, members);
        BasecampRelation relation = basecamp.relationOf(viewerId);
        return new BasecampDetail(
                basecamp.getId(),
                basecamp.getTitle(),
                basecamp.getDescription(),
                spot == null
                        ? null
                        : new BasecampDetail.SpotSummary(
                                spot.spotId(), spot.name(), spot.type(), spot.lat(), spot.lng()),
                basecamp.getStartDate(),
                basecamp.getEndDate(),
                basecamp.getCapacity().getValue(),
                members.size(),
                basecamp.getStatus(),
                toSummary(basecamp.getJoinCondition()),
                leader,
                members,
                relation,
                visibleContactInfo(basecamp, viewerId),
                spot == null ? null : eligibilityOf(basecamp, viewerId));
    }

    private BasecampDetail.DetailMember findLeader(Basecamp basecamp, List<BasecampDetail.DetailMember> members) {
        return members.stream()
                .filter(member -> member.memberId() == basecamp.getLeaderId())
                .findFirst()
                .orElseGet(() -> toDetailMember(
                        memberProfileQueryService.findIncludingWithdrawn(basecamp.getLeaderId()),
                        BasecampMemberRole.LEADER));
    }

    private List<BasecampDetail.DetailMember> readMembers(Basecamp basecamp) {
        return basecamp.getMembers().stream()
                .filter(BasecampMember::isActive)
                .sorted(Comparator.comparing((BasecampMember member) -> !member.isLeader())
                        .thenComparing(BasecampMember::getJoinedAt)
                        .thenComparing(BasecampMember::getId))
                .map(this::toDetailMember)
                .toList();
    }

    private BasecampDetail.DetailMember toDetailMember(BasecampMember member) {
        return toDetailMember(memberProfileQueryService.findIncludingWithdrawn(member.getMemberId()), member.getRole());
    }

    private static BasecampDetail.DetailMember toDetailMember(MemberTrustProfile profile, BasecampMemberRole role) {
        return new BasecampDetail.DetailMember(
                profile.memberId(),
                profile.nickname(),
                role,
                profile.ageGroup(),
                profile.ageGroupVerified(),
                profile.gender(),
                profile.genderVerified(),
                profile.trustLevel(),
                profile.completedCompanions());
    }

    private JoinEligibility eligibilityOf(Basecamp basecamp, Long viewerId) {
        if (viewerId == null || basecamp.getStatus() != BasecampStatus.RECRUITING) {
            return null;
        }
        return joinEligibilityChecker.judge(basecamp, viewerId);
    }

    private String visibleContactInfo(Basecamp basecamp, Long viewerId) {
        if (viewerId == null || !basecamp.canViewContact(viewerId, clock.instant())) {
            return null;
        }
        return basecamp.getContactInfo();
    }

    private static JoinConditionSummary toSummary(JoinCondition condition) {
        return new JoinConditionSummary(
                condition.getMinTrustLevel() == null
                        ? null
                        : condition.getMinTrustLevel().level(),
                condition.getAgeGroupMin() == null
                        ? null
                        : condition.getAgeGroupMin().decade(),
                condition.getAgeGroupMax() == null
                        ? null
                        : condition.getAgeGroupMax().decade(),
                condition.isSameGenderOnly());
    }
}
