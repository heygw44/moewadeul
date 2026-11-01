package com.moewadeul.member.infra.signup;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DisposableEmailDomainMapper {

    int insertPublicIfAbsent(@Param("domains") List<String> domains, @Param("createdAt") Instant createdAt);
}
