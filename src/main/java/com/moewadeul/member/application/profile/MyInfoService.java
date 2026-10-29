package com.moewadeul.member.application.profile;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.patch.PatchField;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.member.application.MemberUniqueConstraintTranslator;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Nickname;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyInfoService {

    private static final String AGE_GROUP_MESSAGE = "연령대는 " + EnumParser.names(AgeGroup.class) + " 중 하나여야 해요.";
    private static final String GENDER_MESSAGE = "성별은 " + EnumParser.names(Gender.class) + " 중 하나여야 해요.";

    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MyInfo find(long memberId) {
        return MyInfo.from(loadMember(memberId));
    }

    @Transactional
    public MyInfo update(long memberId, MyInfoUpdateCommand command) {
        Member member = loadMember(memberId);
        Instant now = Instant.now(clock);
        if (command.nickname().present()) {
            changeNickname(member, command.nickname().value(), now);
        }
        PatchField<String> selfAgeGroup = command.selfAgeGroup();
        if (selfAgeGroup.present()) {
            member.changeSelfAgeGroup(
                    EnumParser.parseNullable(AgeGroup.class, selfAgeGroup.value(), AGE_GROUP_MESSAGE), now);
        }
        PatchField<String> selfGender = command.selfGender();
        if (selfGender.present()) {
            member.changeSelfGender(EnumParser.parseNullable(Gender.class, selfGender.value(), GENDER_MESSAGE), now);
        }
        flushOrTranslate();
        return MyInfo.from(member);
    }

    private Member loadMember(long memberId) {
        return memberRepository.findById(memberId).orElseThrow(BusinessException::notFound);
    }

    private void changeNickname(Member member, String nickname, Instant now) {
        if (nickname == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "닉네임은 지울 수 없어요.");
        }
        if (nickname.equals(member.getNickname())) {
            return;
        }
        Nickname validated = new Nickname(nickname);
        if (memberRepository.existsByNicknameAndIdNot(validated, member.getId())) {
            throw new BusinessException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        member.changeNickname(validated.value(), now);
    }

    private void flushOrTranslate() {
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw MemberUniqueConstraintTranslator.translate(e);
        }
    }
}
