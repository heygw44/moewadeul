package com.moewadeul.trust.infra.profile;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrustRecordMapper {

    TrustRecordRow selectCompanionRecord(
            @Param("memberId") long memberId,
            @Param("revealCutoff") Instant revealCutoff,
            @Param("recentSince") Instant recentSince);

    List<TagCountRow> selectRevealedTagCounts(
            @Param("memberId") long memberId, @Param("revealCutoff") Instant revealCutoff);
}
