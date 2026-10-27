package com.moewadeul.member.infra.verification;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberActivationMapper {

    int activate(@Param("memberId") long memberId, @Param("now") Instant now);
}
