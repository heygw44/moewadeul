package com.moewadeul.trust.infra.companion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CompanionReviewMapper {

    Optional<CompanionReviewWriteContextRow> selectWriteContext(
            @Param("basecampId") long basecampId,
            @Param("reviewerId") long reviewerId,
            @Param("revieweeId") long revieweeId);

    List<CompanionReviewPendingRow> selectPending(
            @Param("memberId") long memberId, @Param("writableSince") Instant writableSince);

    List<CompanionReviewReceivedRow> selectReceived(
            @Param("memberId") long memberId, @Param("offset") long offset, @Param("limit") int limit);

    List<CompanionReviewPublicRow> selectPublicByReviewee(
            @Param("memberId") long memberId,
            @Param("revealCutoff") Instant revealCutoff,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
