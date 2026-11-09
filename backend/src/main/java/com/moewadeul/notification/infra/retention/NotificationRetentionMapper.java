package com.moewadeul.notification.infra.retention;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationRetentionMapper {

    List<Long> selectExpiredReadIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    int deleteExpiredRead(@Param("ids") List<Long> ids, @Param("cutoff") Instant cutoff);
}
