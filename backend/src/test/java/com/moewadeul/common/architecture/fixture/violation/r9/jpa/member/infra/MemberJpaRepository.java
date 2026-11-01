package com.moewadeul.common.architecture.fixture.violation.r9.jpa.member.infra;

import com.moewadeul.common.architecture.fixture.violation.r9.jpa.member.domain.MemberRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface MemberJpaRepository extends MemberRepository, JpaRepository<Object, Long> {}
