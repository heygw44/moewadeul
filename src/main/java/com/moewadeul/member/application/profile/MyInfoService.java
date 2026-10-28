package com.moewadeul.member.application.profile;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.web.PatchField;
import com.moewadeul.member.application.MemberUniqueConstraintTranslator;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.Nickname;
import com.moewadeul.member.domain.SelfAgeGroup;
import com.moewadeul.member.domain.SelfGender;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyInfoService {

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
            member.changeSelfAgeGroup(parseSelfAgeGroup(selfAgeGroup.value()), now);
        }
        PatchField<String> selfGender = command.selfGender();
        if (selfGender.present()) {
            member.changeSelfGender(parseSelfGender(selfGender.value()), now);
        }
        flushOrTranslate();
        return MyInfo.from(member);
    }

    private Member loadMember(long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
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
            throw new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        member.changeNickname(validated.value(), now);
    }

    private static SelfAgeGroup parseSelfAgeGroup(String name) {
        if (name == null) {
            return null;
        }
        return SelfAgeGroup.parse(name);
    }

    private static SelfGender parseSelfGender(String name) {
        if (name == null) {
            return null;
        }
        return SelfGender.parse(name);
    }

    private void flushOrTranslate() {
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw MemberUniqueConstraintTranslator.translate(e);
        }
    }
}
