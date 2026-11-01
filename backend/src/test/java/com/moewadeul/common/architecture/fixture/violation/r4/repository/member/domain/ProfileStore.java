package com.moewadeul.common.architecture.fixture.violation.r4.repository.member.domain;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface ProfileStore extends Repository<Object, Long> {}
