package com.moewadeul.aisearch.infra;

import com.moewadeul.aisearch.application.TokenTotals;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiSearchUsageMapper {

    int insertIfAbsent(@Param("memberId") long memberId, @Param("date") LocalDate date, @Param("now") Instant now);

    int selectRequestCountForUpdate(@Param("memberId") long memberId, @Param("date") LocalDate date);

    int incrementRequestCount(
            @Param("memberId") long memberId, @Param("date") LocalDate date, @Param("now") Instant now);

    int addTokens(
            @Param("memberId") long memberId,
            @Param("date") LocalDate date,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("now") Instant now);

    TokenTotals sumTokensOn(@Param("date") LocalDate date);

    int deleteBefore(@Param("cutoff") LocalDate cutoff, @Param("limit") int limit);
}
