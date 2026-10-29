package com.moewadeul.basecamp.api.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.query.BasecampDetail;
import com.moewadeul.basecamp.application.query.BasecampSpotType;
import com.moewadeul.basecamp.domain.BasecampMemberRole;
import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import java.time.LocalDate;
import java.util.List;

public sealed interface BasecampDetailResponse {

    String SAFETY_NOTICE = "출발 전 일정을 가족이나 지인과 공유하세요. 플랫폼 밖 송금 요구는 신고해 주세요.";

    static BasecampDetailResponse from(BasecampDetail detail, boolean loggedIn) {
        SpotResponse spot = SpotResponse.from(detail.spot());
        if (!loggedIn) {
            return anonymous(detail, spot);
        }
        List<MemberEntry> members =
                detail.members().stream().map(MemberEntry::from).toList();
        MemberEntry leader = MemberEntry.from(detail.leader());
        return new Member(
                detail.basecampId(),
                detail.title(),
                detail.description(),
                spot,
                detail.startDate(),
                detail.endDate(),
                detail.capacity(),
                detail.headcount(),
                detail.status(),
                detail.joinCondition(),
                new LeaderEntry(leader.memberId(), leader.nickname(), leader.trustLevel()),
                members,
                detail.myRelation(),
                detail.contactInfo(),
                SAFETY_NOTICE,
                detail.eligibility() == null ? null : detail.eligibility().canApply(),
                detail.eligibility() == null
                        ? null
                        : detail.eligibility().unmetReasons().stream()
                                .map(Enum::name)
                                .toList());
    }

    private static Anonymous anonymous(BasecampDetail detail, SpotResponse spot) {
        List<AnonymousMemberEntry> members = detail.members().stream()
                .map(member -> new AnonymousMemberEntry(member.memberId(), member.nickname(), member.role()))
                .toList();
        return new Anonymous(
                detail.basecampId(),
                detail.title(),
                detail.description(),
                spot,
                detail.startDate(),
                detail.endDate(),
                detail.capacity(),
                detail.headcount(),
                detail.status(),
                detail.joinCondition(),
                new AnonymousLeader(detail.leader().memberId(), detail.leader().nickname()),
                members,
                BasecampRelation.NONE,
                SAFETY_NOTICE);
    }

    record Anonymous(
            long basecampId,
            String title,
            String description,
            @JsonInclude(JsonInclude.Include.NON_NULL) SpotResponse spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            BasecampStatus status,
            JoinConditionSummary joinCondition,
            AnonymousLeader leader,
            List<AnonymousMemberEntry> members,
            BasecampRelation myRelation,
            String safetyNotice)
            implements BasecampDetailResponse {}

    record Member(
            long basecampId,
            String title,
            String description,
            @JsonInclude(JsonInclude.Include.NON_NULL) SpotResponse spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            BasecampStatus status,
            JoinConditionSummary joinCondition,
            LeaderEntry leader,
            List<MemberEntry> members,
            BasecampRelation myRelation,
            @JsonInclude(JsonInclude.Include.NON_NULL) String contactInfo,
            String safetyNotice,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canApply,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> unmetReasons)
            implements BasecampDetailResponse {}

    record SpotResponse(long spotId, String name, BasecampSpotType type, double lat, double lng) {

        static SpotResponse from(BasecampDetail.SpotSummary summary) {
            if (summary == null) {
                return null;
            }
            return new SpotResponse(summary.spotId(), summary.name(), summary.type(), summary.lat(), summary.lng());
        }
    }

    record AnonymousLeader(long memberId, String nickname) {}

    record AnonymousMemberEntry(long memberId, String nickname, BasecampMemberRole role) {}

    record LeaderEntry(long memberId, String nickname, int trustLevel) {}

    record MemberEntry(
            long memberId,
            String nickname,
            BasecampMemberRole role,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {

        static MemberEntry from(BasecampDetail.DetailMember member) {
            return new MemberEntry(
                    member.memberId(),
                    member.nickname(),
                    member.role(),
                    member.ageGroup(),
                    member.ageGroupVerified(),
                    member.gender(),
                    member.genderVerified(),
                    member.trustLevel(),
                    member.completedCompanions());
        }
    }
}
