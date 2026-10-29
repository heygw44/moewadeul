package com.moewadeul.member.infra.verification;

import com.moewadeul.member.domain.verification.SendWindow;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SignupDuplicateAttemptMapper {

    int insert(@Param("requestIp") String requestIp, @Param("now") Instant now);

    SendWindow summarizeByIpSince(@Param("requestIp") String requestIp, @Param("since") Instant since);

    int deleteBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
